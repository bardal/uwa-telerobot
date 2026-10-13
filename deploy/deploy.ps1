#Requires -Version 7.2
<#
.SYNOPSIS
  Builds the committed HEAD from source and deploys the UWA Telerobot to AWS.

.DESCRIPTION
  Full mode (the default):
    1. refuses a dirty working tree;
    2. clones HEAD into a temp directory and builds it there: Maven (all tests,
       stamped with the commit) and the web UI (npm ci, test, lint, build);
    3. creates any missing SSM parameter (never overwrites one);
    4. runs the pre-flight checks, then `cdk deploy --all` from the clone's
       deploy/ (after npm ci, test and tsc there; never the working tree);
    5. activates the `project` cost-allocation tag;
    6. uploads the jars and config templates to artifacts/<sha>/ and points
       /telerobot/commit at <sha> (before step 4 when the stack exists, so an
       instance the deploy replaces boots straight into <sha>), reboots a
       surviving running instance, syncs the UI and invalidates it;
    7. tries the CloudFront flat-rate Free plan;
    8. resets the smoke user's password and runs deploy/smoke.mjs.

  -InfraOnly does 1, the clone (not the build), 3, 4, 5, 7 and the smoke
  user, and uploads nothing:
  /telerobot/commit is created as "none", so the instance's fetch unit waits
  (and gives up after 10 minutes) until a full deploy uploads artifacts.

  -SkipTests skips the Maven, web UI and deploy/ test suites (not lint, not
  the type check). Use it only to retry a deploy whose tests already passed.

  Secrets are generated here and written only to SSM. Nothing secret, and not
  the alert email address, is ever written to the repository.

.EXAMPLE
  pwsh -File deploy/deploy.ps1
.EXAMPLE
  pwsh -File deploy/deploy.ps1 -InfraOnly
#>
param(
  [switch]$InfraOnly,
  [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'
# Native commands are checked through $LASTEXITCODE (some failures are fine).
$PSNativeCommandUseErrorActionPreference = $false
Set-StrictMode -Version Latest

$Region = 'eu-west-2'
$EdgeRegion = 'us-east-1'
$MainStack = 'Telerobot'
$EdgeStack = 'TelerobotEdge'
$SmokeUser = 'smoke@telerobot.invalid'
$ProjectTag = @{ Key = 'project'; Value = 'uwa-telerobot' }
$JavaHome = Join-Path $HOME 'tools\jdk21'
$Mvn = Join-Path $HOME 'tools\maven\bin\mvn.cmd'
# The config templates the instance's telerobot-fetch unit reads (lib/userdata.ts).
$ConfigTemplates = @('MOM.xml', 'lab.xml', 'RobotProxy.properties')

$Deploy = $PSScriptRoot
$Started = Get-Date
$Summary = [ordered]@{}

function Step([string]$Text) {
  Write-Host ''
  Write-Host "==> $Text" -ForegroundColor Cyan
}
function Note([string]$Text) { Write-Host "    $Text" }
function Warn([string]$Text) { Write-Host "    WARNING: $Text" -ForegroundColor Yellow }
function Abort([string]$Text) { throw "deploy.ps1: $Text" }

# Runs a native command in a directory and throws if it fails.
function Exec([string]$What, [string]$Dir, [scriptblock]$Block) {
  Push-Location $Dir
  try {
    & $Block
    if ($LASTEXITCODE -ne 0) { Abort "$What failed (exit $LASTEXITCODE)" }
  } finally {
    Pop-Location
  }
}

# Runs the AWS CLI; returns {Ok, Out, Err}. Throws unless -AllowFail.
function Invoke-Aws {
  param(
    [Parameter(Mandatory)][string[]]$Arguments,
    [string]$In = $Region,
    [switch]$AllowFail
  )
  $errFile = [IO.Path]::GetTempFileName()
  try {
    $out = & aws @Arguments --region $In --output json --no-cli-pager 2> $errFile
    $code = $LASTEXITCODE
    $err = (Get-Content -Raw -LiteralPath $errFile) ?? ''
  } finally {
    Remove-Item -LiteralPath $errFile -Force -ErrorAction SilentlyContinue
  }
  $result = [pscustomobject]@{ Ok = ($code -eq 0); Out = (@($out) -join "`n"); Err = $err.Trim() }
  if (-not $result.Ok -and -not $AllowFail) {
    Abort "aws $($Arguments[0]) $($Arguments[1]) failed: $($result.Err)"
  }
  $result
}
function Get-AwsJson {
  param([Parameter(Mandatory)][string[]]$Arguments, [string]$In = $Region)
  $r = Invoke-Aws -Arguments $Arguments -In $In
  if ($r.Out.Trim()) { $r.Out | ConvertFrom-Json } else { $null }
}

# Request bodies that hold secrets go through a private temp file, never the
# command line (which other processes can read).
$Private = Join-Path ([IO.Path]::GetTempPath()) ("telerobot-deploy-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $Private | Out-Null
function Invoke-AwsWithInput {
  param([string[]]$Command, [hashtable]$Body, [string]$In = $Region, [switch]$AllowFail)
  $file = Join-Path $Private ([guid]::NewGuid().ToString() + '.json')
  $Body | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $file -Encoding utf8NoBOM -NoNewline
  try {
    Invoke-Aws -Arguments ($Command + @('--cli-input-json', "file://$file")) -In $In -AllowFail:$AllowFail
  } finally {
    Remove-Item -LiteralPath $file -Force -ErrorAction SilentlyContinue
  }
}

# 32 random bytes, base64url without padding: 43 chars of [A-Za-z0-9_-], which
# the instance's PIN check (^[A-Za-z0-9_-]{12,}$) accepts.
function New-Secret {
  $bytes = [Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
  [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

# Creates the parameter if it is missing; never overwrites one.
function New-ParamIfMissing([string]$Name, [ValidateSet('String', 'SecureString')][string]$Type, [string]$Value) {
  $r = Invoke-AwsWithInput -Command @('ssm', 'put-parameter') -AllowFail -Body @{
    Name  = $Name
    Type  = $Type
    Value = $Value
    Tier  = 'Standard'
    Tags  = @(@{ Key = $ProjectTag.Key; Value = $ProjectTag.Value })
  }
  if ($r.Ok) { Note "created  $Name ($Type)"; return }
  if ($r.Err -match 'ParameterAlreadyExists') { Note "exists   $Name"; return }
  Abort "could not create $Name`: $($r.Err)"
}

function Get-StackOutputs([string]$Stack, [string]$In) {
  $s = Get-AwsJson -Arguments @('cloudformation', 'describe-stacks', '--stack-name', $Stack) -In $In
  $map = [ordered]@{}
  foreach ($o in @($s.Stacks[0].Outputs)) {
    if ($o.OutputKey -notlike 'ExportsOutput*') { $map[$o.OutputKey] = $o.OutputValue }
  }
  $map
}

$BuildDir = $null
$exitCode = 0
try {
  # ---------------------------------------------------------------- 1. tree
  Step 'Checking the working tree'
  $Repo = git -C $Deploy rev-parse --show-toplevel
  if ($LASTEXITCODE -ne 0 -or -not $Repo) { Abort 'not inside a git repository' }
  $Repo = "$Repo".Trim()
  $dirty = git -C $Repo status --porcelain
  if ($LASTEXITCODE -ne 0) { Abort "git status failed (exit $LASTEXITCODE)" }
  if ($dirty) {
    $dirty | Select-Object -First 20 | ForEach-Object { Note $_ }
    Abort 'the working tree is dirty; commit or stash first (deploys are built from the committed HEAD)'
  }
  $Sha = git -C $Repo rev-parse HEAD
  if ($LASTEXITCODE -ne 0 -or -not $Sha) { Abort "git rev-parse HEAD failed (exit $LASTEXITCODE)" }
  $Sha = "$Sha".Trim()
  if ($Sha -notmatch '^[0-9a-f]{40}$') { Abort "HEAD is not a commit sha: $Sha" }
  Note "HEAD $Sha"
  $Summary['Commit'] = $Sha
  $Summary['Mode'] = if ($InfraOnly) { 'infra-only' } else { 'full' }

  $Email = (git -C $Repo config user.email)
  if (-not $Email) { Abort 'git config user.email is empty; it is the budget alert address' }
  $Email = $Email.Trim()

  # ---------------------------------------------------------- 2. pre-flight
  Step 'Pre-flight checks'
  $who = Get-AwsJson -Arguments @('sts', 'get-caller-identity')
  $Account = $who.Account
  Note "account $Account, region $Region (web ACL in $EdgeRegion)"

  $stackExists = (Invoke-Aws -Arguments @('cloudformation', 'describe-stacks', '--stack-name', $MainStack) -AllowFail).Ok

  # Reserved concurrency 2 on the Api function must leave the account's
  # unreserved pool at 10 or more, or Lambda refuses the reservation.
  $lambda = Get-AwsJson -Arguments @('lambda', 'get-account-settings')
  $unreserved = [int]$lambda.AccountLimit.UnreservedConcurrentExecutions
  $alreadyReserved = 0
  if ($stackExists) {
    $res = Get-AwsJson -Arguments @('cloudformation', 'list-stack-resources', '--stack-name', $MainStack)
    $fnRes = @($res.StackResourceSummaries | Where-Object {
        $_.ResourceType -eq 'AWS::Lambda::Function' -and $_.LogicalResourceId -like 'Api*' })
    if ($fnRes.Count -eq 1) {
      $conc = Invoke-Aws -Arguments @('lambda', 'get-function-concurrency', '--function-name', $fnRes[0].PhysicalResourceId) -AllowFail
      if ($conc.Ok -and $conc.Out.Trim()) {
        $prop = ($conc.Out | ConvertFrom-Json).PSObject.Properties['ReservedConcurrentExecutions']
        if ($prop) { $alreadyReserved = [int]$prop.Value }
      }
    }
  }
  $after = $unreserved + $alreadyReserved - 2
  if ($after -lt 10) {
    Abort ("Lambda concurrency: the account has $unreserved unreserved executions; reserving 2 for the " +
      "telerobot API would leave $after, and Lambda requires at least 10. Request a higher " +
      "'Concurrent executions' quota in Service Quotas (eu-west-2), then rerun.")
  }
  Note "Lambda: $unreserved unreserved executions; $after left after the reservation (OK)"

  # The instance's root volume is encrypted with the account's default EBS key.
  # With a customer-managed key, starting it needs kms:CreateGrant, which the
  # wake Lambda does not have.
  $kmsKey = (Get-AwsJson -Arguments @('ec2', 'get-ebs-default-kms-key-id')).KmsKeyId
  if ($kmsKey -ne 'alias/aws/ebs') {
    $meta = (Get-AwsJson -Arguments @('kms', 'describe-key', '--key-id', $kmsKey)).KeyMetadata
    if ($meta.KeyManager -eq 'CUSTOMER') {
      Abort ("the default EBS encryption key in $Region is a customer-managed key ($kmsKey). " +
        "The wake Lambda would need kms:CreateGrant on it to start the instance. Either reset the " +
        "default (aws ec2 reset-ebs-default-kms-key-id --region $Region) or add a KmsKeyId and a " +
        "kms:CreateGrant/Decrypt grant for the Api role to lib/telerobot-stack.ts, then rerun.")
    }
  }
  Note "EBS default key: $kmsKey (AWS managed, OK)"

  # ------------------------------------------------------------ 3. build
  # Everything that is deployed, the CDK app included, comes from a clean
  # clone of the committed HEAD, never from the working tree.
  Step "Cloning $Sha"
  $BuildDir = Join-Path ([IO.Path]::GetTempPath()) ("telerobot-build-" + $Sha.Substring(0, 12) + '-' + [guid]::NewGuid().ToString().Substring(0, 8))
  Exec 'git clone' $Repo { git clone --no-hardlinks --quiet $Repo $BuildDir }
  Exec 'git checkout' $BuildDir { git checkout --quiet --detach $Sha }
  $cloned = git -C $BuildDir rev-parse HEAD
  if ($LASTEXITCODE -ne 0) { Abort "git rev-parse in the clone failed (exit $LASTEXITCODE)" }
  if ("$cloned".Trim() -ne $Sha) { Abort "the clone is at $cloned, not $Sha" }
  Note "clone: $BuildDir"
  $CdkDir = Join-Path $BuildDir 'deploy'

  if (-not $InfraOnly) {
    Step "Building $Sha in the clone"
    if (-not (Test-Path (Join-Path $JavaHome 'bin\java.exe'))) { Abort "no JDK 21 at $JavaHome" }
    if (-not (Test-Path $Mvn)) { Abort "no Maven at $Mvn" }

    $env:JAVA_HOME = $JavaHome
    $env:Path = (Join-Path $JavaHome 'bin') + [IO.Path]::PathSeparator + $env:Path
    $mvnArgs = @('-B', 'clean', 'verify', "-Dgit.commit=$Sha")
    if ($SkipTests) { $mvnArgs += '-DskipTests'; Warn 'Maven tests skipped (-SkipTests)' }
    Exec 'mvn verify' $BuildDir { & $Mvn @mvnArgs }

    $webui = Join-Path $BuildDir 'webui'
    Exec 'webui npm ci' $webui { npm ci --no-audit --no-fund }
    if ($SkipTests) { Warn 'web UI tests skipped (-SkipTests)' } else { Exec 'webui npm test' $webui { npm test } }
    Exec 'webui npm run lint' $webui { npm run lint }
    Exec 'webui npm run build' $webui { npm run build }

    $LabJar = Join-Path $BuildDir 'lab\target\telerobot.jar'
    $GatewayJar = Join-Path $BuildDir 'gateway\target\gateway.jar'
    $Dist = Join-Path $webui 'dist'
    foreach ($f in @($LabJar, $GatewayJar, (Join-Path $Dist 'index.html')) + ($ConfigTemplates | ForEach-Object { Join-Path $BuildDir "config\$_" })) {
      if (-not (Test-Path -LiteralPath $f)) { Abort "the build did not produce $f" }
    }
    Note 'built lab.jar, gateway.jar and webui/dist'
  }

  # ------------------------------------------------- 4. the CDK app itself
  Step 'Checking the CDK app (in the clone)'
  Exec 'deploy npm ci' $CdkDir { npm ci --no-audit --no-fund }
  if ($SkipTests) { Warn 'deploy/ tests skipped (-SkipTests)' } else { Exec 'deploy npm test' $CdkDir { npm test } }
  Exec 'deploy tsc' $CdkDir { npx tsc --noEmit }

  # ------------------------------------------------------ 5. SSM parameters
  Step 'Creating missing SSM parameters (existing ones are left alone)'
  foreach ($u in @('registered', 'admin', 'robot', 'camera')) {
    New-ParamIfMissing "/telerobot/pins/$u" SecureString (New-Secret)
  }
  # Turnstile's always-pass test keys until real ones replace them (Decision 5).
  New-ParamIfMissing '/telerobot/turnstile-secret' SecureString '1x0000000000000000000000000000000AA'
  New-ParamIfMissing '/telerobot/turnstile-sitekey' String '1x00000000000000000000AA'
  # A String: the stack reads it into CloudFront's origin header at deploy time.
  New-ParamIfMissing '/telerobot/origin-secret' String (New-Secret)
  New-ParamIfMissing '/telerobot/bans' String '[]'
  New-ParamIfMissing '/telerobot/heartbeat' String '{"time":0,"sessions":0,"lastSessionEnd":0,"commit":""}'
  New-ParamIfMissing '/telerobot/archive-bytes' String '0'
  New-ParamIfMissing '/telerobot/commit' String 'none'
  # /telerobot/stack/* belong to the stack; /telerobot/minutes/* to the Lambda.

  # ------------------------------------- 6a. artifacts before the stack update
  # A user-data change replaces the instance, and the new one fetches
  # /telerobot/commit on its first boot, while cdk deploy is still running.
  # So when the stack already exists, the artifacts go up and the commit is
  # set first: a new instance then boots straight into this commit, with no
  # reboot racing its cloud-init. (On the very first deploy there is no
  # bucket yet; the fetch unit waits for the commit, set after the deploy.)
  $ArtifactsUploaded = $false
  $InstanceBefore = $null
  function Publish-Artifacts([string]$Bucket) {
    $prefix = "s3://$Bucket/artifacts/$Sha"
    Step "Uploading artifacts to $prefix/"
    Invoke-Aws -Arguments @('s3', 'cp', '--only-show-errors', $LabJar, "$prefix/lab.jar") | Out-Null
    Invoke-Aws -Arguments @('s3', 'cp', '--only-show-errors', $GatewayJar, "$prefix/gateway.jar") | Out-Null
    foreach ($c in $ConfigTemplates) {
      Invoke-Aws -Arguments @('s3', 'cp', '--only-show-errors', (Join-Path $BuildDir "config\$c"), "$prefix/config/$c") | Out-Null
    }
    Note 'lab.jar, gateway.jar, config/{MOM.xml,lab.xml,RobotProxy.properties}'
    Invoke-Aws -Arguments @('ssm', 'put-parameter', '--name', '/telerobot/commit', '--type', 'String',
      '--value', $Sha, '--overwrite') | Out-Null
    Note "/telerobot/commit = $Sha"
  }
  if (-not $InfraOnly -and $stackExists) {
    $before = Get-StackOutputs $MainStack $Region
    $InstanceBefore = $before['InstanceId']
    if ($before['ArtifactsBucket']) {
      Publish-Artifacts $before['ArtifactsBucket']
      $ArtifactsUploaded = $true
    }
  }

  # ----------------------------------------------------------- 6. cdk deploy
  Step 'cdk deploy --all (CloudFront takes 10-20 minutes on the first run)'
  $cdkArgs = @('cdk', 'deploy', '--all', '--require-approval', 'never', '-c', "alertEmail=$Email")
  # Google sign-in (Decision 5) turns on when both are in the environment.
  if ($env:TELEROBOT_GOOGLE_CLIENT_ID -and $env:TELEROBOT_GOOGLE_CLIENT_SECRET) {
    $cdkArgs += @('-c', "googleClientId=$env:TELEROBOT_GOOGLE_CLIENT_ID", '-c', "googleClientSecret=$env:TELEROBOT_GOOGLE_CLIENT_SECRET")
    Note 'Google sign-in: on (TELEROBOT_GOOGLE_CLIENT_ID/SECRET)'
  } else {
    Note 'Google sign-in: off (set TELEROBOT_GOOGLE_CLIENT_ID and TELEROBOT_GOOGLE_CLIENT_SECRET to turn it on)'
  }
  Exec 'cdk deploy' $CdkDir { npx @cdkArgs }

  $Out = Get-StackOutputs $MainStack $Region
  $EdgeOut = Get-StackOutputs $EdgeStack $EdgeRegion
  $Url = $Out['Url']
  $DistributionId = $Out['DistributionId']
  $DistributionArn = "arn:aws:cloudfront::${Account}:distribution/$DistributionId"
  $WebAclArn = $EdgeOut['WebAclArn']
  if (-not $Url -or -not $DistributionId -or -not $WebAclArn) { Abort 'the stack outputs are incomplete' }

  # ------------------------------------------------- 7. cost-allocation tag
  Step "Activating the '$($ProjectTag.Key)' cost-allocation tag"
  $ce = Invoke-Aws -Arguments @('ce', 'update-cost-allocation-tags-status', '--cost-allocation-tags-status',
    "TagKey=$($ProjectTag.Key),Status=Active") -In $EdgeRegion -AllowFail
  # @(...) around the whole if: an assignment from an if unrolls an empty array to $null.
  $ceErrors = @(if ($ce.Ok -and $ce.Out.Trim()) { ($ce.Out | ConvertFrom-Json).PSObject.Properties['Errors'] | ForEach-Object { $_.Value } | Where-Object { $_ } })
  if ($ce.Ok -and $ceErrors.Count -eq 0) {
    Note 'active (the budget filters on it)'
    $Summary['Cost tag'] = 'active'
  } else {
    $why = if (-not $ce.Ok) { $ce.Err } else { ($ceErrors | ForEach-Object { "$($_.Code): $($_.Message)" }) -join '; ' }
    Warn "could not activate it yet: $why"
    Warn 'Billing only sees a tag up to 24 h after it first appears on a resource. Rerun deploy.ps1 (or the aws ce command) tomorrow; until then the telerobot budget reads $0.'
    $Summary['Cost tag'] = "not yet active ($why)"
  }

  # ------------------------------------------------ 8. artifacts and the UI
  if (-not $InfraOnly) {
    $Artifacts = $Out['ArtifactsBucket']
    $UiBucket = $Out['UiBucket']
    $InstanceId = $Out['InstanceId']
    if (-not $ArtifactsUploaded) { Publish-Artifacts $Artifacts }

    $state = (Get-AwsJson -Arguments @('ec2', 'describe-instances', '--instance-ids', $InstanceId,
        '--query', 'Reservations[0].Instances[0].State.Name')).ToString()
    $replaced = $InstanceBefore -and $InstanceBefore -ne $InstanceId
    if ($replaced -or -not $stackExists) {
      # A new instance: its first boot fetches the commit set above (or, on
      # the first deploy, waits for it). Rebooting could cut its cloud-init short.
      Note "instance $InstanceId is new ($state); its first boot fetches $Sha"
    } elseif ($state -eq 'pending' -or $state -eq 'running') {
      if ($state -eq 'pending') {
        Invoke-Aws -Arguments @('ec2', 'wait', 'instance-running', '--instance-ids', $InstanceId) | Out-Null
      }
      Invoke-Aws -Arguments @('ec2', 'reboot-instances', '--instance-ids', $InstanceId) | Out-Null
      Note "instance $InstanceId was running: rebooted, so it fetches $Sha"
    } else {
      Note "instance $InstanceId is $state; it fetches $Sha on its next wake"
    }

    Step "Syncing webui/dist to s3://$UiBucket"
    # config.json is the stack's production config (BucketDeployment): never
    # overwritten or deleted here. Hashed assets are cached; index.html is not.
    Invoke-Aws -Arguments @('s3', 'sync', '--only-show-errors', $Dist, "s3://$UiBucket",
      '--exclude', 'config.json', '--exclude', 'csp.json', '--exclude', 'index.html') | Out-Null
    Invoke-Aws -Arguments @('s3', 'cp', '--only-show-errors', (Join-Path $Dist 'index.html'), "s3://$UiBucket/index.html",
      '--cache-control', 'no-cache', '--content-type', 'text/html; charset=utf-8') | Out-Null
    $inv = Get-AwsJson -Arguments @('cloudfront', 'create-invalidation', '--distribution-id', $DistributionId,
      '--paths', '/', '/index.html', '/config.json')
    Note "invalidation $($inv.Invalidation.Id) for /, /index.html and /config.json"
  }

  # -------------------------------------------------- 9. flat-rate Free plan
  Step 'CloudFront flat-rate Free plan'
  $flat = & node (Join-Path $CdkDir 'flat-rate.mjs') $DistributionArn $WebAclArn 2>&1
  $flat | ForEach-Object { Note "$_" }
  $flatLine = @($flat | Where-Object { "$_" -like 'FLAT-RATE:*' }) | Select-Object -Last 1
  $Summary['Flat-rate plan'] = if ($flatLine) { "$flatLine".Substring(11) } else { "unknown (flat-rate.mjs exit $LASTEXITCODE)" }

  # ------------------------------------------------------- 10. smoke user
  Step "Smoke-test user $SmokeUser"
  $PoolId = $Out['UserPoolId']
  $get = Invoke-Aws -Arguments @('cognito-idp', 'admin-get-user', '--user-pool-id', $PoolId, '--username', $SmokeUser) -AllowFail
  if (-not $get.Ok) {
    if ($get.Err -notmatch 'UserNotFoundException') { Abort "admin-get-user failed: $($get.Err)" }
    Invoke-Aws -Arguments @('cognito-idp', 'admin-create-user', '--user-pool-id', $PoolId, '--username', $SmokeUser,
      '--user-attributes', "Name=email,Value=$SmokeUser", 'Name=email_verified,Value=true',
      '--message-action', 'SUPPRESS') | Out-Null
    Note 'created'
  } else {
    Note 'exists'
  }
  # A fresh password every run; it lives only in this process's environment.
  $SmokePassword = (New-Secret) + 'Aa1!'
  Invoke-AwsWithInput -Command @('cognito-idp', 'admin-set-user-password') -Body @{
    UserPoolId = $PoolId; Username = $SmokeUser; Password = $SmokePassword; Permanent = $true
  } | Out-Null
  Note 'new random permanent password set'

  # ------------------------------------------------------------ 11. smoke
  if (-not $InfraOnly) {
    Step "Smoke test against $Url"
    $env:SMOKE_USER_POOL_ID = $PoolId
    $env:SMOKE_CLIENT_ID = $Out['SmokeClientId']
    $env:SMOKE_PASSWORD = $SmokePassword
    $env:SMOKE_COMMIT = $Sha
    try {
      & node (Join-Path $CdkDir 'smoke.mjs') $Url
      $smokeCode = $LASTEXITCODE
    } finally {
      Remove-Item Env:SMOKE_PASSWORD -ErrorAction SilentlyContinue
    }
    $Summary['Smoke test'] = if ($smokeCode -eq 0) { 'passed' } else { "FAILED (exit $smokeCode)" }
    if ($smokeCode -ne 0) { $exitCode = 1 }
  } else {
    $Summary['Smoke test'] = 'not run (infra-only: no artifacts yet)'
  }
  $SmokePassword = $null

  # ------------------------------------------------------------ 12. report
  Step 'Done'
  Write-Host ''
  Write-Host "  $Url" -ForegroundColor Green
  Write-Host ''
  foreach ($k in $Out.Keys) { Note ("{0,-16} {1}" -f $k, $Out[$k]) }
  Note ("{0,-16} {1}" -f 'WebAclArn', $WebAclArn)
  Write-Host ''
  foreach ($k in $Summary.Keys) { Note ("{0,-16} {1}" -f $k, $Summary[$k]) }
  Note ("{0,-16} {1:n0} min" -f 'Took', ((Get-Date) - $Started).TotalMinutes)
  if ($InfraOnly) {
    Note ''
    Note 'Infra-only: the instance has no artifacts. Its telerobot-fetch unit retries for 10 minutes'
    Note 'and then fails; stop it (aws ec2 stop-instances) or let the reaper stop it.'
  }
} catch {
  Write-Host ''
  Write-Host "FAILED: $($_.Exception.Message)" -ForegroundColor Red
  $exitCode = 1
} finally {
  Remove-Item -LiteralPath $Private -Recurse -Force -ErrorAction SilentlyContinue
  if ($BuildDir -and (Test-Path -LiteralPath $BuildDir)) {
    try { Remove-Item -LiteralPath $BuildDir -Recurse -Force } catch { Warn "could not remove $BuildDir`: $($_.Exception.Message)" }
  }
}
exit $exitCode
