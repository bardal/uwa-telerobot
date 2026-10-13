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
    const managed = (name: string, priority: number): wafv2.CfnWebACL.RuleProperty => ({
      name,
      priority,
      overrideAction: { none: {} },
      statement: { managedRuleGroupStatement: { vendorName: "AWS", name } },
      visibilityConfig: visibility(name),
    });

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
        {
          name: "WakeRatePerIp",
          priority: 1,
          action: { block: {} },
          statement: {
            rateBasedStatement: {
              limit: 20,
              evaluationWindowSec: 300,
              aggregateKeyType: "IP",
              scopeDownStatement: {
                byteMatchStatement: {
                  fieldToMatch: { uriPath: {} },
                  positionalConstraint: "EXACTLY",
                  searchString: "/api/wake",
                  textTransformations: [{ priority: 0, type: "NONE" }],
                },
              },
            },
          },
          visibilityConfig: visibility("WakeRatePerIp"),
        },
        managed("AWSManagedRulesCommonRuleSet", 2),
        managed("AWSManagedRulesKnownBadInputsRuleSet", 3),
      ],
    });

    this.webAclArn = acl.attrArn;
    // deploy.ps1 reads it for the CloudFront flat-rate plan subscription.
    new CfnOutput(this, "WebAclArn", { value: acl.attrArn });
  }
}
