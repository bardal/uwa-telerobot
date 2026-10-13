# Deploying the telerobot to AWS

The web version runs as two CDK stacks, deployed by one script:

| Stack | Region | Holds |
|---|---|---|
| `TelerobotEdge` | us-east-1 | The CloudFront-scope WAF web ACL (rate limits, AWS managed rule groups). |
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
3. runs `npm ci`, `npm test` and `tsc` in `deploy/`;
4. creates the SSM parameters below that are missing (it never overwrites
   one);
5. runs `npx cdk deploy --all --require-approval never -c alertEmail=<git email>`
   (the first run takes 10–20 minutes, mostly CloudFront);
6. activates the `project` cost-allocation tag, which the budget filters on;
7. uploads `lab.jar`, `gateway.jar` and `config/{MOM.xml,lab.xml,RobotProxy.properties}`
   to `s3://<ArtifactsBucket>/artifacts/<sha>/`, sets `/telerobot/commit` to
   the sha, and reboots the instance if it is running (it fetches the
   commit's artifacts on every boot);
8. syncs `webui/dist` to the UI bucket (never touching `config.json`, which
   the stack writes) and invalidates `/`, `/index.html` and `/config.json`;
9. tries the CloudFront flat-rate Free plan (below);
10. creates the Cognito user `smoke@telerobot.invalid` if needed, gives it a
    fresh random password, and runs `node deploy/smoke.mjs <url>` with the
    password in an environment variable only;
11. prints the URL, the stack outputs and a summary.

`-InfraOnly` skips the build, the uploads, the UI sync and the smoke test.
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

1. In the Cloudflare dashboard, create a Turnstile widget for the
   distribution's domain (`<id>.cloudfront.net`, from the `Url` output).
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
`commandStatus done`), the public archive index, that `/archive/private/*`
is refused, and that a WebSocket with a foreign `Origin` is refused. Every
request carries a `User-Agent`: the WAF's common rule set blocks requests
without one.

## Costs

* Asleep: about $4.50 a month: the Elastic IP's public IPv4 address
  ($0.005 an hour, running or not, about $3.65), the 8 GB gp3 root volume
  (about $0.75), and S3, SSM and CloudWatch pennies.
* Awake: t4g.small at about $0.02 an hour; the 150-hour cap is under $3.
* CloudFront and WAF: on the flat-rate Free plan, $0. Otherwise
  pay-as-you-go: the web ACL is $5 a month plus $1 for each of its four rules
  and rule groups (about $9 in all), plus $0.60 per million requests, plus
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

The guide also lists WAF "Rule Groups" as unsupported on every plan. Getting
onto the Free plan would mean giving up the security headers and the per-IP
cap, so the distribution stays on pay-as-you-go (about $9 a month for the web
ACL).

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
