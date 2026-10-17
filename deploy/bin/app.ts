import { App } from "aws-cdk-lib";
import { EdgeStack } from "../lib/edge-stack";
import { TelerobotStack } from "../lib/telerobot-stack";

const app = new App();
const ctx = (key: string): string | undefined => {
  const v = app.node.tryGetContext(key);
  return v === undefined || v === null || v === "" ? undefined : String(v);
};

const alertEmail = ctx("alertEmail");
if (!alertEmail) {
  throw new Error("Pass the budget alert address: -c alertEmail=<address> (deploy.ps1 uses git config user.email)");
}
const hoursCap = Number(ctx("hoursCap") ?? 150);
if (!Number.isFinite(hoursCap) || hoursCap <= 0) throw new Error("hoursCap must be a positive number");

// The lab's DNS record (cdk.json); deploy.ps1 looks up its hosted zone.
const labRecord = ctx("labRecord");
const labZoneId = ctx("labZoneId");
if (labRecord && !labZoneId) {
  throw new Error(`Pass the hosted zone holding ${labRecord}: -c labZoneId=<id> (deploy.ps1 looks it up)`);
}

const account = process.env.CDK_DEFAULT_ACCOUNT;

const edge = new EdgeStack(app, "TelerobotEdge", {
  env: { account, region: "us-east-1" },
  crossRegionReferences: true,
  description: "UWA Telerobot: CloudFront web ACL",
});

new TelerobotStack(app, "Telerobot", {
  env: { account, region: "eu-west-2" },
  crossRegionReferences: true,
  description: "UWA Telerobot: web UI, gateway lab instance, wake Lambda, archive",
  webAclArn: edge.webAclArn,
  alertEmail,
  cfPrefixList: ctx("cfPrefixList") ?? "pl-93a247fa",
  hoursCap,
  googleClientId: ctx("googleClientId"),
  googleClientSecret: ctx("googleClientSecret"),
  labDns: labRecord && labZoneId ? { zoneId: labZoneId, recordName: labRecord } : undefined,
  siteDomain: ctx("siteDomain"),
  siteCertArn: ctx("siteCertArn"),
});
