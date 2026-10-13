import { existsSync, readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { App } from "aws-cdk-lib";
import { Match, Template } from "aws-cdk-lib/assertions";
import { beforeAll, describe, expect, it } from "vitest";
import { CSP } from "../lib/csp";
import { EdgeStack } from "../lib/edge-stack";
import { ARCHIVE_CSP, TelerobotStack } from "../lib/telerobot-stack";

const ACCOUNT = "123456789012";
const ORIGIN_HEADER = "X-Telerobot-Origin";

type Resources = Record<string, { Type: string; Properties?: any; DependsOn?: string | string[] }>;

let app: App;
let edge: Template;
let main: Template;

beforeAll(() => {
  // Skip esbuild bundling: these tests are about the template, not the bundle.
  app = new App({ context: { "aws:cdk:bundling-stacks": [] } });
  const edgeStack = new EdgeStack(app, "Edge", {
    env: { account: ACCOUNT, region: "us-east-1" },
    crossRegionReferences: true,
  });
  const mainStack = new TelerobotStack(app, "Main", {
    env: { account: ACCOUNT, region: "eu-west-2" },
    crossRegionReferences: true,
    webAclArn: edgeStack.webAclArn,
    alertEmail: "test@example.invalid",
  });
  edge = Template.fromStack(edgeStack);
  main = Template.fromStack(mainStack);
});

function only<T = any>(t: Template, type: string): [string, T] {
  const found = Object.entries(t.findResources(type));
  expect(found, type).toHaveLength(1);
  return [found[0][0], found[0][1].Properties as T];
}

function statements(t: Template): any[] {
  const out: any[] = [];
  for (const type of ["AWS::IAM::Policy", "AWS::IAM::Role"]) {
    for (const r of Object.values(t.findResources(type))) {
      const p = r.Properties ?? {};
      for (const s of p.PolicyDocument?.Statement ?? []) out.push(s);
      for (const pol of p.Policies ?? []) for (const s of pol.PolicyDocument.Statement) out.push(s);
    }
  }
  return out;
}

const actions = (s: any): string[] => [s.Action].flat();

describe("edge stack", () => {
  it("is a CLOUDFRONT web ACL with the managed rule groups and two rate limits", () => {
    const [, acl] = only(edge, "AWS::WAFv2::WebACL");
    expect(acl.Scope).toBe("CLOUDFRONT");
    const groups = acl.Rules.filter((r: any) => r.Statement.ManagedRuleGroupStatement).map(
      (r: any) => r.Statement.ManagedRuleGroupStatement.Name,
    );
    expect(groups.sort()).toEqual(["AWSManagedRulesCommonRuleSet", "AWSManagedRulesKnownBadInputsRuleSet"]);

    const rate = acl.Rules.filter((r: any) => r.Statement.RateBasedStatement);
    expect(rate).toHaveLength(2);
    for (const r of rate) {
      expect(r.Action).toEqual({ Block: {} });
      expect(r.Statement.RateBasedStatement.AggregateKeyType).toBe("IP");
      expect(r.Statement.RateBasedStatement.EvaluationWindowSec).toBe(300);
    }
    const global = rate.find((r: any) => !r.Statement.RateBasedStatement.ScopeDownStatement);
    const wake = rate.find((r: any) => r.Statement.RateBasedStatement.ScopeDownStatement);
    expect(global.Statement.RateBasedStatement.Limit).toBe(300);
    expect(wake.Statement.RateBasedStatement.Limit).toBe(20);
    expect(wake.Statement.RateBasedStatement.ScopeDownStatement.ByteMatchStatement).toMatchObject({
      SearchString: "/api/wake",
      PositionalConstraint: "EXACTLY",
      FieldToMatch: { UriPath: {} },
    });
  });

  it("outputs the web ACL ARN (deploy.ps1 needs it for the flat-rate plan)", () => {
    const [id] = only(edge, "AWS::WAFv2::WebACL");
    edge.hasOutput("WebAclArn", { Value: { "Fn::GetAtt": [id, "Arn"] } });
  });
});

describe("telerobot stack", () => {
  it("has no dependency cycle", () => {
    const res = main.toJSON().Resources as Resources;
    const deps = new Map<string, Set<string>>();
    const walk = (v: any, into: Set<string>) => {
      if (Array.isArray(v)) v.forEach((x) => walk(x, into));
      else if (v && typeof v === "object") {
        if (typeof v.Ref === "string" && res[v.Ref]) into.add(v.Ref);
        if (v["Fn::GetAtt"] && res[v["Fn::GetAtt"][0]]) into.add(v["Fn::GetAtt"][0]);
        Object.values(v).forEach((x) => walk(x, into));
      }
    };
    for (const [id, r] of Object.entries(res)) {
      const s = new Set<string>([r.DependsOn ?? []].flat());
      walk(r.Properties, s);
      deps.set(id, s);
    }
    const state = new Map<string, number>();
    const visit = (id: string, path: string[]) => {
      if (state.get(id) === 2) return;
      if (state.get(id) === 1) throw new Error("cycle: " + [...path, id].join(" -> "));
      state.set(id, 1);
      for (const d of deps.get(id) ?? []) visit(d, [...path, id]);
      state.set(id, 2);
    };
    for (const id of deps.keys()) visit(id, []);
  });

  it("admits only tcp/8080 from the CloudFront prefix list", () => {
    const [, sg] = only(main, "AWS::EC2::SecurityGroup");
    expect(sg.SecurityGroupIngress).toEqual([
      expect.objectContaining({ IpProtocol: "tcp", FromPort: 8080, ToPort: 8080, SourcePrefixListId: "pl-93a247fa" }),
    ]);
    expect(sg.SecurityGroupIngress[0].CidrIp).toBeUndefined();
    // no explicit egress: a VPC security group then allows all egress
    expect(sg.SecurityGroupEgress).toBeUndefined();
    expect(sg.VpcId).toBeUndefined();
    main.resourceCountIs("AWS::EC2::SecurityGroupIngress", 0);
  });

  it("runs the instance with IMDSv2 required, no key pair, stop on shutdown, gp3 8 GB encrypted", () => {
    const [ltId, lt] = only(main, "AWS::EC2::LaunchTemplate");
    expect(lt.LaunchTemplateData.MetadataOptions.HttpTokens).toBe("required");
    const [, inst] = only(main, "AWS::EC2::Instance");
    expect(inst.KeyName).toBeUndefined();
    expect(inst.LaunchTemplate.LaunchTemplateId).toEqual({ Ref: ltId });
    expect(inst.InstanceType).toBe("t4g.small");
    expect(inst.InstanceInitiatedShutdownBehavior).toBe("stop");
    expect(inst.BlockDeviceMappings).toEqual([
      { DeviceName: "/dev/xvda", Ebs: expect.objectContaining({ VolumeSize: 8, VolumeType: "gp3", Encrypted: true }) },
    ]);
    expect(inst.Tags).toEqual(expect.arrayContaining([{ Key: "telerobot", Value: "lab" }]));
    main.resourceCountIs("AWS::EC2::EIP", 1);
    main.resourceCountIs("AWS::EC2::EIPAssociation", 1);
  });

  it("allows ec2:StopInstances and StartInstances only on the tagged instance", () => {
    const [instId] = only(main, "AWS::EC2::Instance");
    const ec2Control = statements(main).filter((s) =>
      actions(s).some((a) => a === "ec2:StopInstances" || a === "ec2:StartInstances"),
    );
    // the instance role (stop) and the Lambda role (start, stop)
    expect(ec2Control.length).toBe(2);
    for (const s of ec2Control) {
      expect(s.Effect).toBe("Allow");
      expect(s.Condition).toEqual({ StringEquals: { "ec2:ResourceTag/telerobot": "lab" } });
      expect(JSON.stringify(s.Resource)).toContain(`{"Ref":"${instId}"}`);
      expect(s.Resource).not.toBe("*");
    }
  });

  it("lets neither the instance nor the Lambda read SSM parameters outside /telerobot/", () => {
    // Statements of the roles we write (CDK's own cross-region export reader
    // has a separate role scoped to /cdk/exports/).
    const all = Object.entries(main.findResources("AWS::IAM::Policy"))
      .filter(([id]) => id.startsWith("LabRole") || id.startsWith("ApiServiceRole"))
      .flatMap(([, p]) => p.Properties.PolicyDocument.Statement);
    expect(all.length).toBeGreaterThan(10);
    const paramRead = (s: any) => actions(s).some((a) => /^ssm:(GetParameter|\*)/.test(a) || a === "*");
    for (const s of all.filter((s) => s.Effect === "Allow" && paramRead(s))) {
      for (const r of [s.Resource].flat()) {
        expect(JSON.stringify(r)).toMatch(/:parameter\/telerobot\//);
      }
    }
    // no managed policy that grants ssm:GetParameter* on "*"
    for (const r of Object.values(main.findResources("AWS::IAM::Role"))) {
      expect(JSON.stringify(r.Properties.ManagedPolicyArns ?? [])).not.toContain("AmazonSSMManagedInstanceCore");
    }
    const deny = all.filter((s) => s.Effect === "Deny" && s.NotResource);
    expect(deny).toHaveLength(1);
    expect(actions(deny[0]).sort()).toEqual([
      "ssm:GetParameter",
      "ssm:GetParameterHistory",
      "ssm:GetParameters",
      "ssm:GetParametersByPath",
    ]);
    expect(JSON.stringify(deny[0].NotResource)).toContain(":parameter/telerobot/*");

    const decrypt = all.filter((s) => actions(s).includes("kms:Decrypt"));
    expect(decrypt).toHaveLength(2);
    for (const s of decrypt) {
      expect(s.Condition).toEqual({
        StringEquals: { "kms:ViaService": "ssm.eu-west-2.amazonaws.com" },
        StringLike: { "kms:EncryptionContext:PARAMETER_ARN": "arn:aws:ssm:*:*:parameter/telerobot/*" },
      });
    }
    // Session Manager still works
    expect(all.some((s) => actions(s).includes("ssmmessages:OpenDataChannel"))).toBe(true);
  });

  it("uses standard CPU credits", () => {
    const [, lt] = only(main, "AWS::EC2::LaunchTemplate");
    expect(lt.LaunchTemplateData.CreditSpecification).toEqual({ CpuCredits: "standard" });
  });

  it("tags both stacks with project=uwa-telerobot", () => {
    const tag = { Key: "project", Value: "uwa-telerobot" };
    const [, acl] = only(edge, "AWS::WAFv2::WebACL");
    expect(acl.Tags).toContainEqual(tag);
    const [, inst] = only(main, "AWS::EC2::Instance");
    expect(inst.Tags).toContainEqual(tag);
    expect(inst.PropagateTagsToVolumeOnCreation).toBe(true);
    const [, dist] = only(main, "AWS::CloudFront::Distribution");
    expect(dist.Tags).toContainEqual(tag);
    for (const b of Object.values(main.findResources("AWS::S3::Bucket"))) expect(b.Properties.Tags).toContainEqual(tag);
  });

  it("gives every log group 14-day retention, including the BucketDeployment's", () => {
    const groups = main.findResources("AWS::Logs::LogGroup");
    expect(Object.keys(groups).length).toBe(3);
    for (const g of Object.values(groups)) expect(g.Properties.RetentionInDays).toBe(14);
    const deployFn = Object.values(main.findResources("AWS::Lambda::Function")).find((f) =>
      JSON.stringify(f.Properties.Layers ?? []).includes("AwsCliLayer"),
    )!;
    expect(deployFn.Properties.LoggingConfig.LogGroup.Ref).toMatch(/^UiConfigLogs/);
  });

  it("blocks public access on every bucket, and CloudFront reads only public/* of the archive", () => {
    const buckets = main.findResources("AWS::S3::Bucket");
    expect(Object.keys(buckets).length).toBe(3);
    for (const b of Object.values(buckets)) {
      expect(b.Properties.PublicAccessBlockConfiguration).toEqual({
        BlockPublicAcls: true,
        BlockPublicPolicy: true,
        IgnorePublicAcls: true,
        RestrictPublicBuckets: true,
      });
    }
    const archiveId = Object.keys(buckets).find((k) => k.startsWith("Archive"))!;
    const policy = Object.values(main.findResources("AWS::S3::BucketPolicy")).find(
      (p) => p.Properties.Bucket.Ref === archiveId,
    )!;
    const cf = policy.Properties.PolicyDocument.Statement.filter(
      (s: any) => s.Principal?.Service === "cloudfront.amazonaws.com",
    );
    expect(cf).toHaveLength(1);
    expect(cf[0].Action).toBe("s3:GetObject");
    expect(JSON.stringify(cf[0].Resource)).toContain("/public/*");
  });

  it("has five behaviours, with the origin header on the EC2 and Lambda origins", () => {
    const [, dist] = only(main, "AWS::CloudFront::Distribution");
    const cfg = dist.DistributionConfig;
    const behaviours = cfg.CacheBehaviors as any[];
    expect(behaviours.map((b) => b.PathPattern).sort()).toEqual(["/api/*", "/archive/public/*", "/healthz", "/ws"]);
    expect(cfg.DefaultCacheBehavior).toBeDefined();

    const originOf = (pattern: string) =>
      cfg.Origins.find((o: any) => o.Id === behaviours.find((b) => b.PathPattern === pattern).TargetOriginId);
    const headerNames = (o: any) => (o.OriginCustomHeaders ?? []).map((h: any) => h.HeaderName);

    const api = originOf("/api/*");
    expect(headerNames(api)).toEqual([ORIGIN_HEADER]);
    expect(api.CustomOriginConfig.OriginProtocolPolicy).toBe("https-only");

    const ec2 = originOf("/ws");
    expect(originOf("/healthz").Id).toBe(ec2.Id);
    expect(headerNames(ec2)).toEqual([ORIGIN_HEADER]);
    expect(ec2.CustomOriginConfig).toMatchObject({ HTTPPort: 8080, OriginProtocolPolicy: "http-only" });
    expect(JSON.stringify(ec2.DomainName)).toContain(".eu-west-2.compute.amazonaws.com");

    // The gateway's per-IP admission needs CloudFront-Viewer-Address.
    const [orpId, orp] = only(main, "AWS::CloudFront::OriginRequestPolicy");
    expect(orp.OriginRequestPolicyConfig.HeadersConfig).toEqual({
      HeaderBehavior: "allViewerAndWhitelistCloudFront",
      Headers: ["CloudFront-Viewer-Address"],
    });
    for (const p of ["/ws", "/healthz"]) {
      expect(behaviours.find((b) => b.PathPattern === p).OriginRequestPolicyId).toEqual({ Ref: orpId });
    }

    const archive = behaviours.find((b) => b.PathPattern === "/archive/public/*");
    expect(archive.FunctionAssociations).toEqual([expect.objectContaining({ EventType: "viewer-request" })]);
    expect(cfg.WebACLId).toBeDefined();

    const api_b = behaviours.find((b) => b.PathPattern === "/api/*");
    expect(api_b.AllowedMethods.sort()).toEqual(["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]);
  });

  it("strips /archive in the viewer-request function", () => {
    const [, fn] = only(main, "AWS::CloudFront::Function");
    const handler = new Function(`${fn.FunctionCode}; return handler;`)();
    const out = handler({ request: { uri: "/archive/public/index/days.json" } });
    expect(out.uri).toBe("/public/index/days.json");
  });

  it("sends the CSP from webui/csp.json and the security headers", () => {
    const json = JSON.parse(readFileSync(new URL("../../webui/csp.json", import.meta.url), "utf8"));
    expect(CSP).toBe(json.csp);
    const policies = main.findResources("AWS::CloudFront::ResponseHeadersPolicy");
    expect(Object.keys(policies)).toHaveLength(2);
    const byCsp = (csp: string) =>
      Object.entries(policies).find(
        ([, p]) =>
          p.Properties.ResponseHeadersPolicyConfig.SecurityHeadersConfig.ContentSecurityPolicy
            .ContentSecurityPolicy === csp,
      )!;
    const [siteId, site] = byCsp(CSP);
    const sec = site.Properties.ResponseHeadersPolicyConfig.SecurityHeadersConfig;
    expect(sec.StrictTransportSecurity).toMatchObject({ AccessControlMaxAgeSec: 63072000, IncludeSubdomains: true });
    expect(sec.FrameOptions.FrameOption).toBe("DENY");
    expect(sec.ReferrerPolicy.ReferrerPolicy).toBe("strict-origin-when-cross-origin");
    expect(sec.ContentTypeOptions).toBeDefined();
    expect(site.Properties.ResponseHeadersPolicyConfig.CustomHeadersConfig.Items).toEqual([
      { Header: "Permissions-Policy", Value: "camera=(), microphone=(), geolocation=()", Override: true },
      { Header: "Cross-Origin-Opener-Policy", Value: "same-origin", Override: true },
    ]);

    // the archive serves inert images under its own policy
    const [archiveId, archive] = byCsp(ARCHIVE_CSP);
    expect(ARCHIVE_CSP).toBe("default-src 'none'; img-src 'self'; sandbox");
    const asec = archive.Properties.ResponseHeadersPolicyConfig.SecurityHeadersConfig;
    expect(asec.StrictTransportSecurity).toMatchObject({ AccessControlMaxAgeSec: 63072000, IncludeSubdomains: true });
    expect(asec.FrameOptions.FrameOption).toBe("DENY");
    expect(asec.ContentTypeOptions).toBeDefined();

    const [, dist] = only(main, "AWS::CloudFront::Distribution");
    const cfg = dist.DistributionConfig;
    for (const b of [cfg.DefaultCacheBehavior, ...cfg.CacheBehaviors]) {
      const want = b.PathPattern === "/archive/public/*" ? archiveId : siteId;
      expect(b.ResponseHeadersPolicyId, b.PathPattern ?? "default").toEqual({ Ref: want });
    }
  });

  it("gives the Lambda arm64, 128 MB and a reserved concurrency of 2", () => {
    main.hasResourceProperties("AWS::Lambda::Function", {
      Handler: "index.handler",
      Architectures: ["arm64"],
      MemorySize: 128,
      ReservedConcurrentExecutions: 2,
    });
    main.hasResourceProperties("AWS::Lambda::Url", { AuthType: "NONE" });
    main.hasResourceProperties("AWS::Events::Rule", {
      ScheduleExpression: "rate(15 minutes)",
      Targets: [Match.objectLike({ Input: JSON.stringify({ reap: true }) })],
    });
  });

  it("sets a $20 monthly budget emailing the alert address at 50% and 100%", () => {
    const [, b] = only(main, "AWS::Budgets::Budget");
    expect(b.Budget).toMatchObject({ BudgetLimit: { Amount: 20, Unit: "USD" }, TimeUnit: "MONTHLY" });
    // only this project's costs (the account is shared)
    expect(b.Budget.CostFilters).toEqual({ TagKeyValue: ["user:project$uwa-telerobot"] });
    expect(b.NotificationsWithSubscribers.map((n: any) => n.Notification.Threshold).sort()).toEqual([100, 50]);
    for (const n of b.NotificationsWithSubscribers) {
      expect(n.Subscribers).toEqual([{ SubscriptionType: "EMAIL", Address: "test@example.invalid" }]);
    }
  });

  it("has a web client with PKCE code flow and a smoke client with admin password auth only", () => {
    const clients = Object.values(main.findResources("AWS::Cognito::UserPoolClient")).map((c) => c.Properties);
    expect(clients).toHaveLength(2);
    const web = clients.find((c) => c.AllowedOAuthFlows)!;
    const smoke = clients.find((c) => !c.AllowedOAuthFlows)!;
    expect(web.AllowedOAuthFlows).toEqual(["code"]);
    expect(web.AllowedOAuthScopes.sort()).toEqual(["email", "openid"]);
    expect(web.GenerateSecret).toBe(false);
    expect(smoke.GenerateSecret).toBe(false);
    expect(smoke.ExplicitAuthFlows.sort()).toEqual(["ALLOW_ADMIN_USER_PASSWORD_AUTH", "ALLOW_REFRESH_TOKEN_AUTH"]);
    main.hasResourceProperties("AWS::Cognito::UserPoolGroup", { GroupName: "admin" });
    main.hasResourceProperties("AWS::Cognito::UserPool", {
      Policies: { PasswordPolicy: Match.objectLike({ MinimumLength: 12 }) },
      AutoVerifiedAttributes: ["email"],
    });
    main.hasResourceProperties("AWS::Cognito::UserPoolDomain", { Domain: "uwa-telerobot-789012" });
    main.resourceCountIs("AWS::Cognito::UserPoolIdentityProvider", 0);
  });

  it("writes a production config.json with no dev key", () => {
    // Source.jsonData stages the file as a directory asset in the assembly.
    const dir = app.synth().directory;
    const files = readdirSync(dir)
      .filter((d) => d.startsWith("asset.") && existsSync(join(dir, d, "config.json")))
      .map((d) => join(dir, d, "config.json"));
    expect(files).toHaveLength(1);
    // Tokens (the client id, the site key) are deploy-time markers; quote them to parse.
    const text = readFileSync(files[0], "utf8").replace(/<<marker:0xbaba:(\d+)>>/g, '"marker $1"');
    const cfg = JSON.parse(text);
    expect(cfg).not.toHaveProperty("dev");
    expect(Object.keys(cfg).sort()).toEqual(["clientId", "redirectUri", "region", "turnstileSiteKey", "userPoolDomain"]);
    expect(cfg.region).toBe("eu-west-2");
    // the dev config in webui/public is the one that must never ship
    const devCfg = JSON.parse(readFileSync(new URL("../../webui/public/config.json", import.meta.url), "utf8"));
    expect(devCfg.dev).toBe(true);
  });

  it("never creates the secrets or pins in SSM", () => {
    for (const p of Object.values(main.findResources("AWS::SSM::Parameter"))) {
      const name: string = p.Properties.Name;
      expect(name.startsWith("/telerobot/stack/") || !name.startsWith("/telerobot/")).toBe(true);
    }
  });
});
