# Deploying the telerobot to AWS

The web version runs as two CDK stacks, deployed by one script:

| Stack | Region | Holds |
|---|---|---|
| `TelerobotEdge` | us-east-1 | The CloudFront-scope WAF web ACL (a per-IP rate limit, AWS managed rule groups). |
| `Telerobot` | eu-west-2 | CloudFront, the UI/archive/artifacts buckets, Cognito, the lab instance (t4g.small), the status/wake/reaper Lambda, the budget. |

The lab instance sleeps until someone visits. The wake Lambda starts it, the
gateway stops it 15 minutes after the last session, and the reaper Lambda
(every 15 minutes) stops it if the gateway's heartbeat goes stale. A month's
running time is capped at 150 hours (`hoursCap` in `deploy/cdk.json`).

## Prerequisites

* PowerShell 7, Git, Node 22 or later (24 is tested), AWS CLI v2 with
  credentials for the target account.
* For a full deploy: JDK 21 at `~/tools/jdk21` and Maven at
  `~/tools/maven/bin/mvn.cmd`.
* CDK bootstrapped in **both** regions:
  `npx cdk bootstrap aws://<account>/eu-west-2 aws://<account>/us-east-1`
  (from `deploy/`).
* `git config user.email` set: it is the budget alert address. It is passed
  to CDK as context and never written to the repository.

## Deploying

```powershell
pwsh -File deploy/deploy.ps1              # build from source, deploy, smoke test
pwsh -File deploy/deploy.ps1 -InfraOnly   # stacks and parameters only, no artifacts
pwsh -File deploy/deploy.ps1 -SkipTests   # skip the test suites (retries only)
```

`deploy.ps1` refuses to run on a dirty working tree. In full mode it:

1. clones the committed `HEAD` into a temp directory and builds it there:
   `mvn -B clean verify -Dgit.commit=<sha>` (every test, including `it` and
   `hostile`), then `npm ci`, `npm test`, `npm run lint` and `npm run build`
   in `webui/`. No prebuilt jar from anywhere else is ever used;
2. checks the account: the Lambda concurrency pool must keep 10 unreserved
   executions after the API's reservation of 2, and the default EBS key
   must be AWS managed (a customer-managed key would need `kms:CreateGrant`
   for the wake Lambda);
3. runs `npm ci`, `npm test` and `tsc` in the clone's `deploy/`, which is
   also where `cdk deploy`, `flat-rate.mjs` and `smoke.mjs` run from (the
   working tree's `deploy/` is never deployed);
4. creates the SSM parameters below that are missing (it never overwrites
   one);
5. runs `npx cdk deploy --all --require-approval never -c alertEmail=<git email>`
   (the first run takes 10–20 minutes, mostly CloudFront);
6. activates the `project` cost-allocation tag, which the budget filters on;
7. uploads `lab.jar`, `gateway.jar` and `config/{MOM.xml,lab.xml,RobotProxy.properties}`
   to `s3://<ArtifactsBucket>/artifacts/<sha>/` and sets `/telerobot/commit`
   to the sha. When the stack already exists this happens *before* step 5:
   a user-data change replaces the instance, and the new one fetches the
   commit on its first boot while `cdk deploy` is still running. Afterwards
   it reboots the instance only if the same instance survived the deploy and
   is running (it fetches the commit's artifacts on every boot); a new
   instance is left alone so a reboot cannot cut its cloud-init short;
8. syncs `webui/dist` to the UI bucket (never touching `config.json`, which
   the stack writes) and invalidates `/`, `/index.html` and `/config.json`;
9. tries the CloudFront flat-rate Free plan (below);
10. creates the Cognito user `smoke@telerobot.invalid` if needed, gives it a
    fresh random password, and runs `node deploy/smoke.mjs <url>` with the
    password in an environment variable only;
11. prints the URL, the stack outputs and a summary.

`-InfraOnly` still clones `HEAD` (the CDK app comes from the clone) but skips the build, the uploads, the UI sync and the smoke test.
`/telerobot/commit` is created as `none`, so a freshly created instance's
`telerobot-fetch` unit retries for 10 minutes and then fails. That is
expected: stop the instance, or let the reaper stop it.

## SSM parameters

Created by `deploy.ps1` when missing:

| Name | Type | Initial value |
|---|---|---|
| `/telerobot/pins/{registered,admin,robot,camera}` | SecureString | random 32 bytes, base64url |
| `/telerobot/turnstile-secret` | SecureString | Turnstile's always-pass test secret |
| `/telerobot/turnstile-sitekey` | String | Turnstile's always-pass test site key |
| `/telerobot/origin-secret` | String | random 32 bytes, base64url (the `X-Telerobot-Origin` header) |
| `/telerobot/bans` | String | `[]` |
| `/telerobot/heartbeat` | String | `{"time":0,"sessions":0,"lastSessionEnd":0,"commit":""}` |
| `/telerobot/archive-bytes` | String | `0` |
| `/telerobot/commit` | String | `none`; the deployed sha after a full deploy |

Written by the stack itself (do not create them by hand):
`/telerobot/stack/allowed-origin` and `/telerobot/stack/cognito-client-ids`.
The Lambda writes `/telerobot/minutes/<YYYY-MM>`.

To rotate a value, overwrite it (`aws ssm put-parameter --overwrite ...`)
and redeploy: the origin secret and the site key are read into the template
on every `cdk deploy`, and the instance reads the rest on every boot.

## Real Turnstile keys

The first deploy uses Cloudflare's always-pass test keys, so guest login
works but proves nothing. To switch to real ones:

1. In the Cloudflare dashboard, create a Turnstile widget for both of the
   site's hostnames: `telerobot.daltons.net` (the `Url` output) and
   `<id>.cloudfront.net` (the `CloudFrontUrl` output).
2. Overwrite both parameters:
   ```powershell
   aws ssm put-parameter --region eu-west-2 --name /telerobot/turnstile-sitekey --type String --value <site key> --overwrite
   aws ssm put-parameter --region eu-west-2 --name /telerobot/turnstile-secret --type SecureString --value <secret> --overwrite
   ```
3. Run `deploy.ps1` again. It rewrites `config.json` with the new site key;
   the Lambda picks up the secret on its next cold start and the gateway on
   the instance's next boot.

## Google sign-in

Email sign-in works from the start. To add Google:

1. In Google Cloud Console, create an OAuth client (Web application) with
   the authorised redirect URI
   `https://uwa-telerobot-<last 6 digits of the account>.auth.eu-west-2.amazoncognito.com/oauth2/idpresponse`.
2. Put the client id and secret in the environment and deploy. `deploy.ps1`
   passes them to CDK as the `googleClientId` and `googleClientSecret`
   context values; they are never committed:
   ```powershell
   $env:TELEROBOT_GOOGLE_CLIENT_ID = '<id>'
   $env:TELEROBOT_GOOGLE_CLIENT_SECRET = '<secret>'
   pwsh -File deploy/deploy.ps1
   ```
   Set them for every later deploy too: a run without them removes the
   Google provider again. (The secret ends up in plain text in the
   synthesized template in `deploy/cdk.out`, which is git-ignored.)

## Becoming admin

Sign up on the site with your own email, then:

```powershell
aws cognito-idp admin-add-user-to-group --region eu-west-2 --user-pool-id <UserPoolId> --username <your email> --group-name admin
```

Sign out and in again so the ID token carries the group.

## Outputs

`Telerobot`: `Url`, `DistributionId`, `InstanceId`, `UserPoolId`, `ClientId`
(web), `SmokeClientId`, `UiBucket`, `ArtifactsBucket`, `ArchiveBucket`.
`TelerobotEdge`: `WebAclArn`.

```powershell
aws cloudformation describe-stacks --region eu-west-2 --stack-name Telerobot --query "Stacks[0].Outputs"
```

## Smoke test

`deploy.ps1` runs it after a full deploy. To run it by hand, set
`SMOKE_USER_POOL_ID`, `SMOKE_CLIENT_ID` and `SMOKE_PASSWORD` (reset the smoke
user's password with `aws cognito-idp admin-set-user-password --permanent`),
optionally `SMOKE_COMMIT`, then `node deploy/smoke.mjs <url>`. It checks
`/api/status`, signs in and wakes the lab, waits for `/healthz`, runs a
WebSocket session (welcome, an image and its JPEG, take control, a move to
`commandStatus done`, then its `archived` message), that `days.json` and the
day's index list that move, that the day's audit log (which now exists) is
refused at `/archive/private/audit/<day>.jsonl` and through `public/../`
traversals with no audit content in the body, and that a WebSocket with a
foreign `Origin` gets HTTP 403. Every request carries the site's `Origin`
and a `User-Agent`: the WAF's common rule set blocks requests without one.

## Costs

* Asleep: under $1 a month: the 8 GB gp3 root volume (about $0.75), and S3,
  SSM, CloudWatch and Route 53 query pennies. There is no Elastic IP (it was
  $3.65 a month, running or not): CloudFront reaches the lab at its IPv6
  address, which is free, and its auto-assigned public IPv4 address (for its
  own outbound calls) is released when it stops.
* Awake: t4g.small at about $0.02 an hour plus its public IPv4 address at
  $0.005 an hour; the 150-hour cap is under $4.
* CloudFront and WAF: on the flat-rate Free plan (subscribed 2026-10-17), $0.
  Otherwise pay-as-you-go: the web ACL is $5 a month plus $1 for each of its
  three rules and rule groups (about $8 in all), plus $0.60 per million requests, plus
  CloudFront requests and transfer.
* The `telerobot-monthly` budget ($20) emails the alert address at 50% and
  100%. It counts only resources tagged `project=uwa-telerobot`, once the
  cost-allocation tag is active (billing can take 24 hours to see a new tag;
  `deploy.ps1` retries the activation on every run).

## CloudFront flat-rate Free plan

`deploy.ps1` runs `deploy/flat-rate.mjs`, which uses the Pricing Plan
Manager API (us-east-1) through the AWS SDK, because older AWS CLI v2 builds
lack `aws pricing-plan-manager`. If the distribution already has a
subscription it reports it; otherwise it subscribes the distribution and the
web ACL to the `CloudFront` `FREE` tier, which activates at once. If that
fails (an account can hold only three Free-plan distributions) it prints the
reason, and the distribution stays on pay-as-you-go.

On the first deploy (2026-10-13) the API refused the subscription:
`ValidationException: The following resources are not eligible for this
subscription tier` (both the distribution and the web ACL). The CloudFront
guide's feature table lists, as Business-tier and above only, two things this
distribution depends on:

* **custom response header policies:** the CSP, HSTS, Permissions-Policy and
  COOP headers (`SecurityHeaders`, `ArchiveHeaders`);
* **custom origin request policies:** `GatewayRequest`, which forwards
  `CloudFront-Viewer-Address` so the gateway's per-IP connection cap sees the
  real client.

Since then the distribution uses only what the Free plan allows (a stack
test enforces no custom cache, origin request or response headers policy):

* the managed `SecurityHeadersPolicy` on every behaviour: HSTS (one year),
  nosniff, `X-Frame-Options: SAMEORIGIN` and the referrer policy, on every
  response including errors;
* viewer-response CloudFront Functions for the rest: `SiteHeaders` (CSP,
  Permissions-Policy, COOP) on the UI, `/api/*` and `/healthz`, and
  `ArchiveCsp` (the archive's inert-document CSP) on `/archive/public/*`.
  Functions do not run on responses of 400 and above, so error pages carry
  only the managed headers; `/ws` has none (the WebSocket upgrade);
* the managed `AllViewerAndCloudFrontHeaders-2022-06` origin request policy on
  `/ws` and `/healthz`, which carries `CloudFront-Viewer-Address` to the
  gateway's per-IP cap.

The guide also lists WAF "Rule Groups" as unsupported on every plan; that
means account-owned rule groups, while the Free plan's five-rule allowance
counts AWS Managed Rules. Whether it accepts the web ACL's two managed rule
groups and the `/api/wake` rate rule's scope-down is not documented: the
deploy's subscription attempt is the test. If it is refused again, the
distribution stays on pay-as-you-go (about $9 a month for the web ACL).

On 2026-10-17, with the managed policies and functions in place, the API
still named both resources; with `PriceClass_All` (previously
`PriceClass_100`) it names only the web ACL. So the distribution is eligible,
and something in the web ACL (the two managed rule groups or the scope-down
on `WakeRatePerIp`) is not.

Dropping the `AWSManagedRulesCommonRuleSet` and
`AWSManagedRulesKnownBadInputsRuleSet` groups (ca92ef7) was not enough: the
API still named the web ACL, on the deploy and on a retry minutes later. Of
what was left, the `WakeRatePerIp` scope-down (a byte match on the URI path,
20 wakes per 5 minutes per IP) was the only thing beyond the plain
"IP-based rate limiting" the Free plan lists, so that rule went too.

With `RatePerIp` (300 requests per 5 minutes per IP) alone, the subscription
went through (2467253, `ACTIVE` within minutes). The two managed groups then
went back in (1335729) and the plan stayed `ACTIVE`: the Free plan takes AWS
managed rule groups, within its five rules, but not a scope-down statement.

So the web ACL is `RatePerIp`, `AWSManagedRulesCommonRuleSet` and
`AWSManagedRulesKnownBadInputsRuleSet`. `/api/wake` has no limit of its own:
it falls under `RatePerIp`, a wake still needs a Turnstile or Cognito token
and the origin secret, and the 150-hour cap bounds what wakes can cost.

`RatePerIp` was raised from 300 to 1000 requests per 5 minutes on 2026-10-18:
the landing page and the preserved `/old/` pages are full of photos, and 300
blocked a keen visitor (or a shared office connection) browsing them.

Once subscribed, the web ACL can't be detached from the distribution; leaving
the plan means cancelling the subscription (Free plans cancel at once).

## The lab's network, its DNS record and the site domain

The lab has its own small VPC (`LabVpc`, 10.42.0.0/24 and an Amazon IPv6
/56): one public subnet in the region's first zone, an internet gateway,
routes for `0.0.0.0/0` and `::/0`, and no NAT. The instance gets one IPv6
address, which belongs to its network interface and so survives stop and
start; only replacing the instance (a user-data change) gives it a new one.
It also gets a public IPv4 address while it runs, for its own outbound calls
(S3, SSM, CloudWatch, Cognito, Turnstile); `deploy.ps1` fails if a running
lab has none.

CloudFront's origin for `/ws` and `/healthz` is an AAAA record, `labRecord`
in `deploy/cdk.json` (`telerobot-origin.scollops.net`, TTL 60), reached over
IPv6 only (the origin's `IpAddressType` is `ipv6`, supported since
September 2025). The security group admits port 8080 only from CloudFront's
IPv6 origin-facing prefix list (`pl-0d7c235a121ad5fd1`). The Api function
keeps the record at the instance's IPv6 address in every state: `deploy.ps1`
syncs it after `cdk deploy` (a replaced instance starts before the
state-change rule names it) and checks it after the smoke test, and every EC2
state change and reap tick re-checks it. The function may only UPSERT that
one AAAA record; `deploy.ps1` finds the hosted zone by the record's parent
name. While the lab is asleep CloudFront's requests to it time out (504 after
30 s); its address stays the lab's, so nothing reaches anyone else.

Why not a moving A record (d6e8167 replaced it): from b35d561 to then, an A
record `telerobot-lab.scollops.net` followed the auto-assigned IPv4 address
and was parked on `192.0.2.1` while the lab slept. On 2026-10-18 CloudFront
edges kept answering 502 "CloudFront wasn't able to resolve the origin domain
name" long after each change, although every Route 53 name server and public
resolver had the new address: ten minutes after a wake, 45 of 45 requests
through Manchester (`MAN50-P1`) and 14 of 15 through London (`LHR50-P4`)
failed, and a browser, which keeps one connection to one edge server, could
fail for minutes. With the fixed IPv6 address both edges answered 32 s after
the instance started, with no failures in the next 158 requests each.

`siteDomain` (`telerobot.daltons.net`) gets an ACM certificate in us-east-1,
which `deploy.ps1` requests. The domain's DNS is at justhostme (cPanel's Zone
Editor, which cannot delegate a subdomain: no NS records), so two CNAMEs are
added there by hand: the certificate's validation record, which `deploy.ps1`
prints and which must stay for renewals, and `telerobot` to the
distribution's `cloudfront.net` name. Once ACM issues the certificate,
`deploy.ps1` passes it to the stack and the distribution answers on both
names. `Url` becomes `https://telerobot.daltons.net` (`CloudFrontUrl` keeps
the other), both are allowed origins and Cognito redirect URLs, and
`config.json`'s `redirectUri` is empty so the UI redirects to whichever name
it was loaded from. The smoke test runs against `Url`, or against
`CloudFrontUrl` while the site domain does not resolve.

## Stopping and tearing down

* Stop the lab now: `aws ec2 stop-instances --region eu-west-2 --instance-ids <InstanceId>`.
* Remove everything: `npx cdk destroy --all` from `deploy/`. The buckets are
  retained (CDK's default), so delete them by hand if you mean it, and the
  SSM parameters under `/telerobot/` are not part of the stacks.

## Troubleshooting

* Instance logs go to CloudWatch Logs group `/telerobot/lab` (14 days).
  Session Manager works: `aws ssm start-session --target <InstanceId>`.
* `journalctl -u telerobot-fetch` on the instance shows why a boot could not
  fetch its artifacts (usually `/telerobot/commit` is `none`, or the
  artifacts of that commit were never uploaded).
* A failed `cdk deploy`: `aws cloudformation describe-stack-events --stack-name Telerobot`.
