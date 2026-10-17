import { CfnOutput, Stack, Tags, type StackProps } from "aws-cdk-lib";
import * as wafv2 from "aws-cdk-lib/aws-wafv2";
import type { Construct } from "constructs";

/** us-east-1: the CLOUDFRONT-scope web ACL the distribution uses. */
export class EdgeStack extends Stack {
  readonly webAclArn: string;

  constructor(scope: Construct, id: string, props: StackProps) {
    super(scope, id, props);
    Tags.of(this).add("project", "uwa-telerobot");

    const visibility = (metricName: string) => ({
      cloudWatchMetricsEnabled: true,
      metricName,
      sampledRequestsEnabled: true,
    });
    // One plain per-IP rate limit, so the web ACL is eligible for the
    // CloudFront flat-rate Free plan (docs/DEPLOY_AWS.md).
    const acl = new wafv2.CfnWebACL(this, "WebAcl", {
      scope: "CLOUDFRONT",
      defaultAction: { allow: {} },
      visibilityConfig: visibility("telerobot"),
      rules: [
        {
          name: "RatePerIp",
          priority: 0,
          action: { block: {} },
          statement: {
            rateBasedStatement: { limit: 300, evaluationWindowSec: 300, aggregateKeyType: "IP" },
          },
          visibilityConfig: visibility("RatePerIp"),
        },
      ],
    });

    this.webAclArn = acl.attrArn;
    // deploy.ps1 reads it for the CloudFront flat-rate plan subscription.
    new CfnOutput(this, "WebAclArn", { value: acl.attrArn });
  }
}
