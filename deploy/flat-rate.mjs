// Subscribes the distribution to the CloudFront flat-rate Free plan
// (Decision 6), or reports the subscription it already has.
//
//   node deploy/flat-rate.mjs <distribution-arn> <web-acl-arn>
//
// Uses the SDK rather than `aws pricing-plan-manager`, which older AWS CLI v2
// builds don't have. The endpoint is in us-east-1. Always exits 0 unless the
// arguments are wrong: a failed subscription leaves the distribution on
// pay-as-you-go, which deploy.ps1 reports and carries on from.
//
// Prints one line starting "FLAT-RATE:" that deploy.ps1 records.

import { randomUUID } from "node:crypto";
import {
  CreateSubscriptionCommand,
  ListSubscriptionsCommand,
  PricingPlanManagerClient,
} from "@aws-sdk/client-pricing-plan-manager";

const [distributionArn, webAclArn] = process.argv.slice(2);
if (!/^arn:aws:cloudfront::\d{12}:distribution\/[A-Z0-9]+$/.test(distributionArn ?? "")) {
  console.error("usage: node flat-rate.mjs <distribution-arn> <web-acl-arn>");
  process.exit(2);
}
if (!/^arn:aws:wafv2:us-east-1:\d{12}:global\/webacl\//.test(webAclArn ?? "")) {
  console.error("the second argument must be a CLOUDFRONT-scope (global) web ACL ARN");
  process.exit(2);
}

const client = new PricingPlanManagerClient({ region: "us-east-1" });
const describe = (s) =>
  `${s.planFamily} ${s.planTier}${s.usageLevel ? `/${s.usageLevel}` : ""} ${s.status}` +
  `${s.statusReason ? ` (${s.statusReason})` : ""} ${s.arn}`;

async function existing() {
  const all = [];
  let nextToken;
  do {
    const page = await client.send(new ListSubscriptionsCommand({ nextToken }));
    all.push(...(page.subscriptionSummaries ?? []));
    nextToken = page.nextToken;
  } while (nextToken);
  return all;
}

try {
  const subs = await existing();
  const mine = subs.find((s) => (s.resourceArns ?? []).includes(distributionArn));
  if (mine) {
    console.log(`FLAT-RATE: already subscribed: ${describe(mine)}`);
    process.exit(0);
  }
  const free = subs.filter((s) => s.planFamily === "CloudFront" && s.planTier === "FREE");
  console.log(`The account has ${free.length} CloudFront Free plan subscription(s) on other distributions.`);

  const { subscription } = await client.send(
    new CreateSubscriptionCommand({
      planFamily: "CloudFront",
      planTier: "FREE",
      usageLevel: "DEFAULT",
      resourceArns: [distributionArn, webAclArn],
      approvalMode: "IMMEDIATE",
      clientToken: randomUUID(),
    }),
  );
  console.log(`FLAT-RATE: subscribed: ${describe(subscription)}`);
} catch (e) {
  const name = e?.name ?? "Error";
  console.log(`FLAT-RATE: not subscribed (${name}: ${e?.message ?? e}); the distribution stays pay-as-you-go`);
}
