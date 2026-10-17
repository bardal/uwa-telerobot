import { createHash } from "node:crypto";
import { fileURLToPath } from "node:url";
import {
  Annotations,
  Aws,
  CfnOutput,
  Duration,
  Fn,
  Lazy,
  SecretValue,
  Stack,
  Tags,
  Token,
  type StackProps,
} from "aws-cdk-lib";
import * as budgets from "aws-cdk-lib/aws-budgets";
import * as cloudfront from "aws-cdk-lib/aws-cloudfront";
import * as origins from "aws-cdk-lib/aws-cloudfront-origins";
import * as cognito from "aws-cdk-lib/aws-cognito";
import * as ec2 from "aws-cdk-lib/aws-ec2";
import * as events from "aws-cdk-lib/aws-events";
import * as targets from "aws-cdk-lib/aws-events-targets";
import * as iam from "aws-cdk-lib/aws-iam";
import * as lambda from "aws-cdk-lib/aws-lambda";
import { NodejsFunction, OutputFormat } from "aws-cdk-lib/aws-lambda-nodejs";
import * as logs from "aws-cdk-lib/aws-logs";
import * as s3 from "aws-cdk-lib/aws-s3";
import * as s3deploy from "aws-cdk-lib/aws-s3-deployment";
import * as ssm from "aws-cdk-lib/aws-ssm";
import type { Construct } from "constructs";
import { CSP } from "./csp";
import { STACK_PARAMS, renderUserData } from "./userdata";

export interface TelerobotStackProps extends StackProps {
  /** ARN of the CLOUDFRONT web ACL from the edge stack. */
  webAclArn: string;
  /** Budget alerts go here; passed as context, never committed. */
  alertEmail: string;
  /** The CloudFront origin-facing managed prefix list in this region. */
  cfPrefixList?: string;
  /** Wake cap in hours a month. */
  hoursCap?: number;
  /** Google sign-in turns on when both are given. */
  googleClientId?: string;
  googleClientSecret?: string;
}

export const ORIGIN_HEADER = "X-Telerobot-Origin";
const LAB_TAG = { key: "telerobot", value: "lab" };
/** Cost-allocation tag on every resource; the budget filters on it. */
export const PROJECT_TAG = { key: "project", value: "uwa-telerobot" };
const LOG_GROUP = "/telerobot/lab";
const PARAM = {
  originSecret: "/telerobot/origin-secret",
  turnstileSecret: "/telerobot/turnstile-secret",
  turnstileSiteKey: "/telerobot/turnstile-sitekey",
  heartbeat: "/telerobot/heartbeat",
  bans: "/telerobot/bans",
  archiveBytes: "/telerobot/archive-bytes",
  minutes: "/telerobot/minutes/*",
};
export const ARCHIVE_CSP = "default-src 'none'; img-src 'self'; sandbox";
/** What the site's documents get on top of the managed SecurityHeadersPolicy. */
export const SITE_HEADERS: Record<string, string> = {
  "content-security-policy": CSP,
  "permissions-policy": "camera=(), microphone=(), geolocation=()",
  "cross-origin-opener-policy": "same-origin",
};
export const ARCHIVE_HEADERS: Record<string, string> = { "content-security-policy": ARCHIVE_CSP };

/**
 * A viewer-response CloudFront Function that sets `headers`. Custom response
 * headers policies are Business-tier only on the flat-rate plans, so the CSP
 * and friends come from a function (which the Free plan includes). Functions
 * do not run on responses of 400 and above; those still get the managed
 * policy's HSTS, nosniff, frame and referrer headers.
 */
export function setHeadersCode(headers: Record<string, string>): string {
  return [
    "function handler(event) {",
    "  var h = event.response.headers;",
    ...Object.entries(headers).map(([k, v]) => `  h[${JSON.stringify(k)}] = { value: ${JSON.stringify(v)} };`),
    "  return event.response;",
    "}",
  ].join("\n");
}
const DEPLOY_DIR = fileURLToPath(new URL("..", import.meta.url));

/** eu-west-2: everything except the web ACL. */
export class TelerobotStack extends Stack {
  constructor(scope: Construct, id: string, props: TelerobotStackProps) {
    super(scope, id, props);
    Tags.of(this).add(PROJECT_TAG.key, PROJECT_TAG.value);
    if (Token.isUnresolved(this.account) || Token.isUnresolved(this.region)) {
      throw new Error("TelerobotStack needs a concrete env (account and region)");
    }
    if (!props.alertEmail) throw new Error("alertEmail is required");

    const region = this.region;
    const paramArn = (name: string) => `arn:${Aws.PARTITION}:ssm:${region}:${this.account}:parameter${name}`;
    // Read by name at deploy time; deploy.ps1 creates these String parameters
    // before `cdk deploy`. The stack never creates a secret.
    const originSecret = ssm.StringParameter.valueForStringParameter(this, PARAM.originSecret);
    const turnstileSiteKey = ssm.StringParameter.valueForStringParameter(this, PARAM.turnstileSiteKey);

    // ---- buckets ----
    const privateBucket = {
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      encryption: s3.BucketEncryption.S3_MANAGED,
      enforceSSL: true,
      objectOwnership: s3.ObjectOwnership.BUCKET_OWNER_ENFORCED,
    };
    const ui = new s3.Bucket(this, "Ui", privateBucket);
    const archive = new s3.Bucket(this, "Archive", privateBucket);
    const artifacts = new s3.Bucket(this, "Artifacts", privateBucket);

    // ---- EC2 lab instance (default VPC: no subnet or VPC given) ----
    const eip = new ec2.CfnEIP(this, "LabIp", { domain: "vpc" });

    // No VpcId: the default VPC. Egress is left out, which gives a VPC
    // security group its default allow-all egress rule (CloudFormation only
    // accepts explicit egress rules together with a VpcId).
    const sg = new ec2.CfnSecurityGroup(this, "LabSg", {
      groupDescription: "Telerobot gateway: 8080 from CloudFront only",
      securityGroupIngress: [
        {
          ipProtocol: "tcp",
          fromPort: 8080,
          toPort: 8080,
          sourcePrefixListId: props.cfPrefixList ?? "pl-93a247fa",
          description: "CloudFront origin-facing",
        },
      ],
    });

    const pool = new cognito.UserPool(this, "Users", {
      selfSignUpEnabled: true,
      signInAliases: { email: true },
      autoVerify: { email: true },
      standardAttributes: { email: { required: true, mutable: true } },
      passwordPolicy: { minLength: 12 },
      mfa: cognito.Mfa.OFF,
      accountRecovery: cognito.AccountRecovery.EMAIL_ONLY,
    });

    const logGroup = new logs.LogGroup(this, "LabLogs", {
      logGroupName: LOG_GROUP,
      retention: logs.RetentionDays.TWO_WEEKS,
    });

    const role = new iam.Role(this, "LabRole", {
      assumedBy: new iam.ServicePrincipal("ec2.amazonaws.com"),
    });
    // Not AmazonSSMManagedInstanceCore: it allows ssm:GetParameter* on "*",
    // and this account holds other projects' parameters. This is the Session
    // Manager subset of it.
    role.addToPolicy(
      new iam.PolicyStatement({
        actions: [
          "ssm:UpdateInstanceInformation",
          "ssmmessages:CreateControlChannel",
          "ssmmessages:CreateDataChannel",
          "ssmmessages:OpenControlChannel",
          "ssmmessages:OpenDataChannel",
          "ec2messages:AcknowledgeMessage",
          "ec2messages:DeleteMessage",
          "ec2messages:FailMessage",
          "ec2messages:GetEndpoint",
          "ec2messages:GetMessages",
          "ec2messages:SendReply",
        ],
        resources: ["*"],
      }),
    );
    // Belt and braces against any future grant: no parameter outside /telerobot/.
    role.addToPolicy(
      new iam.PolicyStatement({
        effect: iam.Effect.DENY,
        actions: ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath", "ssm:GetParameterHistory"],
        notResources: [paramArn("/telerobot/*")],
      }),
    );
    const profile = new iam.CfnInstanceProfile(this, "LabProfile", { roles: [role.roleName] });

    const launchTemplate = new ec2.CfnLaunchTemplate(this, "LabLaunchTemplate", {
      launchTemplateData: {
        metadataOptions: { httpTokens: "required", httpEndpoint: "enabled", httpPutResponseHopLimit: 1 },
        // t4g defaults to unlimited credits, which can bill beyond the instance price.
        creditSpecification: { cpuCredits: "standard" },
      },
    });

    const userData = renderUserData({
      artifactsBucket: artifacts.bucketName,
      archiveBucket: archive.bucketName,
      region,
      cognitoIssuer: pool.userPoolProviderUrl,
      logGroup: logGroup.logGroupName,
    });
    const ami = ec2.MachineImage.latestAmazonLinux2023({ cpuType: ec2.AmazonLinuxCpuType.ARM_64 }).getImage(this);

    const instance = new ec2.CfnInstance(this, "Lab", {
      imageId: ami.imageId,
      instanceType: "t4g.small",
      iamInstanceProfile: profile.ref,
      securityGroupIds: [sg.attrGroupId],
      launchTemplate: { launchTemplateId: launchTemplate.ref, version: launchTemplate.attrLatestVersionNumber },
      blockDeviceMappings: [
        {
          deviceName: "/dev/xvda",
          ebs: { volumeSize: 8, volumeType: "gp3", encrypted: true, deleteOnTermination: true },
        },
      ],
      instanceInitiatedShutdownBehavior: "stop",
      propagateTagsToVolumeOnCreation: true,
      userData: Fn.base64(userData),
      tags: [
        { key: LAB_TAG.key, value: LAB_TAG.value },
        { key: "Name", value: "telerobot-lab" },
      ],
    });
    // cloud-init only runs user data on an instance's first boot, so a change
    // to it must replace the instance (as ec2.Instance's userDataCausesReplacement).
    instance.overrideLogicalId(
      Lazy.uncachedString({
        produce: (ctx) =>
          "Lab" + createHash("md5").update(JSON.stringify(ctx.resolve(userData))).digest("hex").slice(0, 16),
      }),
    );
    new ec2.CfnEIPAssociation(this, "LabIpAssociation", {
      allocationId: eip.attrAllocationId,
      instanceId: instance.ref,
    });

    const instanceArn = `arn:${Aws.PARTITION}:ec2:${region}:${this.account}:instance/${instance.ref}`;
    const onlyTagged = { StringEquals: { [`ec2:ResourceTag/${LAB_TAG.key}`]: LAB_TAG.value } };
    const ssmDecrypt = new iam.PolicyStatement({
      actions: ["kms:Decrypt"],
      resources: [`arn:${Aws.PARTITION}:kms:${region}:${this.account}:key/*`],
      conditions: {
        StringEquals: { "kms:ViaService": `ssm.${region}.amazonaws.com` },
        StringLike: { "kms:EncryptionContext:PARAMETER_ARN": "arn:aws:ssm:*:*:parameter/telerobot/*" },
      },
    });

    role.addToPolicy(
      new iam.PolicyStatement({
        actions: ["logs:CreateLogStream", "logs:PutLogEvents", "logs:DescribeLogStreams"],
        resources: [logGroup.logGroupArn],
      }),
    );
    role.addToPolicy(new iam.PolicyStatement({ actions: ["s3:GetObject"], resources: [artifacts.arnForObjects("*")] }));
    role.addToPolicy(
      new iam.PolicyStatement({
        actions: ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"],
        resources: [archive.arnForObjects("*")],
      }),
    );
    role.addToPolicy(new iam.PolicyStatement({ actions: ["s3:ListBucket"], resources: [archive.bucketArn] }));
    role.addToPolicy(
      new iam.PolicyStatement({
        actions: ["ssm:GetParameter", "ssm:GetParameters"],
        resources: [paramArn("/telerobot/*")],
      }),
    );
    role.addToPolicy(
      new iam.PolicyStatement({
        actions: ["ssm:PutParameter"],
        resources: [PARAM.heartbeat, PARAM.bans, PARAM.archiveBytes].map(paramArn),
      }),
    );
    role.addToPolicy(ssmDecrypt);
    role.addToPolicy(
      new iam.PolicyStatement({ actions: ["ec2:StopInstances"], resources: [instanceArn], conditions: onlyTagged }),
    );

    // ---- status / wake / reap Lambda ----
    const fn = new NodejsFunction(this, "Api", {
      entry: `${DEPLOY_DIR}lambda/index.ts`,
      handler: "handler",
      projectRoot: DEPLOY_DIR,
      depsLockFilePath: `${DEPLOY_DIR}package-lock.json`,
      runtime: lambda.Runtime.NODEJS_24_X,
      architecture: lambda.Architecture.ARM_64,
      memorySize: 128,
      timeout: Duration.seconds(10),
      reservedConcurrentExecutions: 2,
      logGroup: new logs.LogGroup(this, "ApiLogs", { retention: logs.RetentionDays.TWO_WEEKS }),
      bundling: { format: OutputFormat.CJS, externalModules: [], minify: true, target: "node24" },
      environment: {
        ORIGIN_SECRET: originSecret,
        TURNSTILE_SECRET_PARAM: PARAM.turnstileSecret,
        USER_POOL_ID: pool.userPoolId,
        // The client ids reach the Lambda through SSM: the web client's
        // callback URL needs the distribution, which needs this function.
        COGNITO_CLIENT_IDS_PARAM: STACK_PARAMS.cognitoClientIds,
        INSTANCE_ID: instance.ref,
        HOURS_CAP: String(props.hoursCap ?? 150),
      },
    });
    fn.addToRolePolicy(new iam.PolicyStatement({ actions: ["ec2:DescribeInstances"], resources: ["*"] }));
    fn.addToRolePolicy(
      new iam.PolicyStatement({
        actions: ["ec2:StartInstances", "ec2:StopInstances"],
        resources: [instanceArn],
        conditions: onlyTagged,
      }),
    );
    fn.addToRolePolicy(
      new iam.PolicyStatement({
        actions: ["ssm:GetParameter", "ssm:PutParameter"],
        resources: [paramArn(PARAM.minutes)],
      }),
    );
    fn.addToRolePolicy(
      new iam.PolicyStatement({
        actions: ["ssm:GetParameter"],
        resources: [PARAM.heartbeat, PARAM.turnstileSecret, STACK_PARAMS.cognitoClientIds].map(paramArn),
      }),
    );
    fn.addToRolePolicy(ssmDecrypt);
    // Auth NONE: the origin secret header is checked in code (Decision 2).
    const fnUrl = fn.addFunctionUrl({ authType: lambda.FunctionUrlAuthType.NONE });

    new events.Rule(this, "Reap", {
      schedule: events.Schedule.rate(Duration.minutes(15)),
      targets: [new targets.LambdaFunction(fn, { event: events.RuleTargetInput.fromObject({ reap: true }) })],
    });

    // ---- CloudFront ----
    const originHeaders = { [ORIGIN_HEADER]: originSecret };
    const ec2Domain = Fn.join("", ["ec2-", Fn.join("-", Fn.split(".", eip.ref)), `.${region}.compute.amazonaws.com`]);
    const ec2Origin = new origins.HttpOrigin(ec2Domain, {
      protocolPolicy: cloudfront.OriginProtocolPolicy.HTTP_ONLY,
      httpPort: 8080,
      customHeaders: originHeaders,
    });
    const apiOrigin = new origins.FunctionUrlOrigin(fnUrl, { customHeaders: originHeaders });

    // An imported view of the archive bucket, so the OAC origin does not add
    // its bucket-wide grant; the grant below covers public/* only.
    const archiveView = s3.Bucket.fromBucketAttributes(this, "ArchiveView", {
      bucketArn: archive.bucketArn,
      bucketName: archive.bucketName,
      bucketRegionalDomainName: archive.bucketRegionalDomainName,
    });
    Annotations.of(this).acknowledgeWarning("@aws-cdk/aws-cloudfront-origins:updateImportedBucketPolicyOac");
    const archiveOrigin = origins.S3BucketOrigin.withOriginAccessControl(archiveView);

    const stripArchive = new cloudfront.Function(this, "StripArchivePrefix", {
      runtime: cloudfront.FunctionRuntime.JS_2_0,
      comment: "/archive/public/x -> /public/x",
      code: cloudfront.FunctionCode.fromInline(
        [
          "function handler(event) {",
          "  var request = event.request;",
          "  if (request.uri.indexOf('/archive/') === 0) {",
          "    request.uri = request.uri.substring('/archive'.length);",
          "  }",
          "  return request;",
          "}",
        ].join("\n"),
      ),
    });

    // Only AWS-managed policies are allowed on the CloudFront flat-rate Free
    // plan: SECURITY_HEADERS gives HSTS, nosniff, X-Frame-Options, Referrer-Policy
    // (on every response, errors included); the functions add the CSP and the rest.
    const siteHeaders = new cloudfront.Function(this, "SiteHeaders", {
      runtime: cloudfront.FunctionRuntime.JS_2_0,
      comment: "CSP, Permissions-Policy, COOP",
      code: cloudfront.FunctionCode.fromInline(setHeadersCode(SITE_HEADERS)),
    });
    // Archived images are served as inert documents (Cache-Control comes from the objects).
    const archiveHeaders = new cloudfront.Function(this, "ArchiveCsp", {
      runtime: cloudfront.FunctionRuntime.JS_2_0,
      comment: "the archive's inert-document CSP",
      code: cloudfront.FunctionCode.fromInline(setHeadersCode(ARCHIVE_HEADERS)),
    });
    const onResponse = (fn: cloudfront.IFunction) => ({ function: fn, eventType: cloudfront.FunctionEventType.VIEWER_RESPONSE });

    const noCache = {
      viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
      cachePolicy: cloudfront.CachePolicy.CACHING_DISABLED,
      originRequestPolicy: cloudfront.OriginRequestPolicy.ALL_VIEWER_EXCEPT_HOST_HEADER,
      responseHeadersPolicy: cloudfront.ResponseHeadersPolicy.SECURITY_HEADERS,
    };
    // The gateway's per-IP admission reads CloudFront-Viewer-Address, which
    // ALL_VIEWER_EXCEPT_HOST_HEADER cannot carry. The EC2 origin does not care
    // about Host, so the managed all-viewer-and-CloudFront-headers policy does.
    const gatewayNoCache = { ...noCache, originRequestPolicy: cloudfront.OriginRequestPolicy.ALL_VIEWER_AND_CLOUDFRONT_2022 };
    const cached = {
      viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
      cachePolicy: cloudfront.CachePolicy.CACHING_OPTIMIZED,
      responseHeadersPolicy: cloudfront.ResponseHeadersPolicy.SECURITY_HEADERS,
    };

    const dist = new cloudfront.Distribution(this, "Cdn", {
      comment: "UWA Telerobot",
      defaultRootObject: "index.html",
      priceClass: cloudfront.PriceClass.PRICE_CLASS_100,
      httpVersion: cloudfront.HttpVersion.HTTP2_AND_3,
      webAclId: props.webAclArn,
      defaultBehavior: {
        origin: origins.S3BucketOrigin.withOriginAccessControl(ui),
        ...cached,
        functionAssociations: [onResponse(siteHeaders)],
      },
      additionalBehaviors: {
        "/api/*": {
          origin: apiOrigin,
          ...noCache,
          allowedMethods: cloudfront.AllowedMethods.ALLOW_ALL,
          functionAssociations: [onResponse(siteHeaders)],
        },
        "/archive/public/*": {
          origin: archiveOrigin,
          ...cached,
          functionAssociations: [
            { function: stripArchive, eventType: cloudfront.FunctionEventType.VIEWER_REQUEST },
            onResponse(archiveHeaders),
          ],
        },
        // No response function on the WebSocket upgrade.
        "/ws": { origin: ec2Origin, ...gatewayNoCache },
        "/healthz": { origin: ec2Origin, ...gatewayNoCache, functionAssociations: [onResponse(siteHeaders)] },
      },
    });

    archive.addToResourcePolicy(
      new iam.PolicyStatement({
        principals: [new iam.ServicePrincipal("cloudfront.amazonaws.com")],
        actions: ["s3:GetObject"],
        resources: [archive.arnForObjects("public/*")],
        conditions: {
          StringEquals: {
            "AWS:SourceArn": `arn:${Aws.PARTITION}:cloudfront::${this.account}:distribution/${dist.distributionId}`,
          },
        },
      }),
    );

    const siteUrl = `https://${dist.distributionDomainName}`;

    // ---- Cognito clients, domain, group, IdP ----
    const domainPrefix = `uwa-telerobot-${this.account.slice(-6)}`;
    pool.addDomain("Domain", { cognitoDomain: { domainPrefix } });
    pool.addGroup("Admin", { groupName: "admin" });

    const idps = [cognito.UserPoolClientIdentityProvider.COGNITO];
    let google: cognito.UserPoolIdentityProviderGoogle | undefined;
    if (props.googleClientId && props.googleClientSecret) {
      google = new cognito.UserPoolIdentityProviderGoogle(this, "Google", {
        userPool: pool,
        clientId: props.googleClientId,
        clientSecretValue: SecretValue.unsafePlainText(props.googleClientSecret),
        scopes: ["openid", "email"],
        attributeMapping: { email: cognito.ProviderAttribute.GOOGLE_EMAIL },
      });
      idps.push(cognito.UserPoolClientIdentityProvider.GOOGLE);
    }

    const web = pool.addClient("Web", {
      generateSecret: false,
      authFlows: {},
      oAuth: {
        flows: { authorizationCodeGrant: true },
        scopes: [cognito.OAuthScope.OPENID, cognito.OAuthScope.EMAIL],
        callbackUrls: [`${siteUrl}/`],
        logoutUrls: [`${siteUrl}/`],
      },
      supportedIdentityProviders: idps,
      preventUserExistenceErrors: true,
    });
    (web.node.defaultChild as cognito.CfnUserPoolClient).explicitAuthFlows = ["ALLOW_REFRESH_TOKEN_AUTH"];
    if (google) web.node.addDependency(google);

    // Used only by smoke.mjs, through admin-initiate-auth.
    const smoke = pool.addClient("Smoke", {
      generateSecret: false,
      authFlows: { adminUserPassword: true },
      disableOAuth: true,
      preventUserExistenceErrors: true,
    });

    // Known only once the distribution and clients exist; the instance and
    // the Lambda read them at runtime, which keeps the template acyclic.
    new ssm.StringParameter(this, "AllowedOriginParam", {
      parameterName: STACK_PARAMS.allowedOrigin,
      stringValue: siteUrl,
    });
    new ssm.StringParameter(this, "ClientIdsParam", {
      parameterName: STACK_PARAMS.cognitoClientIds,
      stringValue: Fn.join(",", [web.userPoolClientId, smoke.userPoolClientId]),
    });

    // ---- /config.json (the rest of the UI is synced by deploy.ps1) ----
    new s3deploy.BucketDeployment(this, "UiConfig", {
      destinationBucket: ui,
      sources: [
        s3deploy.Source.jsonData("config.json", {
          region,
          userPoolDomain: `${domainPrefix}.auth.${region}.amazoncognito.com`,
          clientId: web.userPoolClientId,
          turnstileSiteKey,
          redirectUri: `${siteUrl}/`,
        }),
      ],
      prune: false,
      logGroup: new logs.LogGroup(this, "UiConfigLogs", { retention: logs.RetentionDays.TWO_WEEKS }),
      distribution: dist,
      distributionPaths: ["/config.json"],
    });

    // ---- budget ----
    new budgets.CfnBudget(this, "Budget", {
      budget: {
        budgetName: "telerobot-monthly",
        budgetType: "COST",
        timeUnit: "MONTHLY",
        budgetLimit: { amount: 20, unit: "USD" },
        // Needs the user-defined cost-allocation tag activated (deploy.ps1).
        costFilters: { TagKeyValue: [`user:${PROJECT_TAG.key}$` + PROJECT_TAG.value] },
      },
      notificationsWithSubscribers: [50, 100].map((threshold) => ({
        notification: {
          notificationType: "ACTUAL",
          comparisonOperator: "GREATER_THAN",
          threshold,
          thresholdType: "PERCENTAGE",
        },
        subscribers: [{ subscriptionType: "EMAIL", address: props.alertEmail }],
      })),
    });

    // ---- outputs ----
    new CfnOutput(this, "Url", { value: siteUrl });
    new CfnOutput(this, "DistributionId", { value: dist.distributionId });
    new CfnOutput(this, "InstanceId", { value: instance.ref });
    new CfnOutput(this, "UserPoolId", { value: pool.userPoolId });
    new CfnOutput(this, "ClientId", { value: web.userPoolClientId });
    new CfnOutput(this, "SmokeClientId", { value: smoke.userPoolClientId });
    new CfnOutput(this, "UiBucket", { value: ui.bucketName });
    new CfnOutput(this, "ArtifactsBucket", { value: artifacts.bucketName });
    new CfnOutput(this, "ArchiveBucket", { value: archive.bucketName });
  }
}
