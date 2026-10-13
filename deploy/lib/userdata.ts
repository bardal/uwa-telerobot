/**
 * Renders the lab instance's cloud-init script: packages, the telerobot user,
 * the systemd units and the CloudWatch agent config.
 *
 * cloud-init runs this once, on the first boot. Everything that must be fresh
 * on every boot (the artifacts of the current commit, the PINs, the configs)
 * lives in the telerobot-fetch oneshot unit, so a new deploy only needs a
 * restart.
 *
 * Values in the options may be CDK tokens; they are interpolated into the
 * script text, never interpreted by bash (every heredoc here is quoted).
 */

/** Must equal `SerialFilter.PATTERN` in the lab module. */
export const SERIAL_FILTER =
  "au.edu.uwa.mech.**;Jama.**;java.lang.*;java.util.*;java.awt.Point;java.awt.Rectangle;[*;!*";

/** SSM String parameters the stack writes once the distribution and clients exist. */
export const STACK_PARAMS = {
  allowedOrigin: "/telerobot/stack/allowed-origin",
  cognitoClientIds: "/telerobot/stack/cognito-client-ids",
} as const;

export interface UserDataOptions {
  artifactsBucket: string;
  archiveBucket: string;
  region: string;
  cognitoIssuer: string;
  logGroup: string;
}

const HOME = "/var/lib/telerobot";
const LAB_JAR = `${HOME}/lib/lab.jar`;
/** Heaps sized for 2 GB plus 1 GB swap; on OOM the JVM exits and systemd restarts it. */
const java = (xmx: string) => `/usr/bin/java -Xmx${xmx} -XX:+ExitOnOutOfMemoryError`;
const LAB_FLAGS = `"-Djdk.serialFilter=${SERIAL_FILTER}" -Dtelerobot.bind=127.0.0.1 -Djava.awt.headless=true`;

/**
 * Decision 1's hardening and more, plus restart on failure. Not
 * MemoryDenyWriteExecute: it breaks the JIT.
 */
const SERVICE_COMMON = `User=telerobot
Group=telerobot
WorkingDirectory=${HOME}/run
NoNewPrivileges=yes
ProtectSystem=strict
PrivateTmp=yes
ReadWritePaths=${HOME}
ProtectHome=yes
PrivateDevices=yes
ProtectKernelTunables=yes
ProtectKernelModules=yes
ProtectControlGroups=yes
RestrictAddressFamilies=AF_INET AF_INET6 AF_UNIX
CapabilityBoundingSet=
LockPersonality=yes
RestrictNamespaces=yes
Restart=on-failure
RestartSec=5
# VirtualLab hung 90 s (the default) on SIGTERM once MOM was gone.
TimeoutStopSec=15
# A JVM stopped by SIGTERM exits 143: a clean stop, not a failure.
SuccessExitStatus=143`;

/** MOM, VirtualLab and RobotProxy only talk over loopback; this also cuts them off from IMDS. */
const LOOPBACK_ONLY = "IPAddressDeny=any\nIPAddressAllow=localhost";

function service(o: {
  name: string;
  description: string;
  /** Units this one needs started first (Requires= + After=). */
  requires?: string[];
  /**
   * Units this one is bound to (BindsTo= + After=): when one of them fails or
   * stops (a crash, an OOM exit) this unit stops too, rather than keep dead MOM
   * sessions; MOM's Wants= starts it again when MOM comes back.
   */
  bindsTo?: string[];
  /**
   * Units this one pulls in whenever it starts (Wants=). MOM wants the bound
   * units: a MOM crash stops them and its restart job only try-restarts them,
   * so without this they stayed down after MOM came back.
   */
  wants?: string[];
  /** Local ports that must be listening before this unit starts. */
  ports?: string;
  execStart: string;
  extra?: string;
}): string {
  const svc = (names: string[]) => names.map((n) => `${n}.service`).join(" ");
  const bindsTo = o.bindsTo ?? [];
  const requires = o.requires ?? [];
  const lines = [
    ...(o.wants?.length ? [`Wants=${svc(o.wants)}`] : []),
    ...(bindsTo.length ? [`BindsTo=${svc(bindsTo)}`] : []),
    ...(requires.length ? [`Requires=${svc(requires)}`] : []),
    `After=${svc([...bindsTo, ...requires])}`,
  ];
  return `cat > /etc/systemd/system/telerobot-${o.name}.service <<'UNIT'
[Unit]
Description=${o.description}
${lines.join("\n")}
StartLimitIntervalSec=0

[Service]
${SERVICE_COMMON}
${o.extra ? o.extra + "\n" : ""}${o.ports ? `ExecStartPre=/usr/local/bin/telerobot-wait-port ${o.ports}\n` : ""}ExecStart=${o.execStart}

[Install]
WantedBy=multi-user.target
UNIT
`;
}

const FETCH_SCRIPT = `#!/bin/bash
# telerobot-fetch: runs as root on every boot, before MOM. Pulls the artifacts
# of the commit named in SSM /telerobot/commit and renders the configs with
# the PINs from SSM. Retries for 10 minutes: on the first deploy the instance
# boots before deploy.ps1 has uploaded the artifacts and set the commit.
set -euo pipefail
. /etc/telerobot/telerobot.env
export AWS_REGION AWS_DEFAULT_REGION="$AWS_REGION"
umask 077

STAGE=${HOME}/stage
CFG=${HOME}/config

param() { aws ssm get-parameter --name "$1" --with-decryption --query Parameter.Value --output text; }
fail() { echo "telerobot-fetch: $*" >&2; return 1; }

fetch() {
  local commit src f u pin
  commit=$(param /telerobot/commit) || return 1
  [[ "$commit" =~ ^[0-9a-f]{7,40}$ ]] || fail "/telerobot/commit is not a commit: $commit" || return 1
  src="s3://$ARTIFACTS_BUCKET/artifacts/$commit"
  rm -rf "$STAGE" && mkdir -p "$STAGE/config" || return 1
  for f in lab.jar gateway.jar config/MOM.xml config/lab.xml config/RobotProxy.properties; do
    aws s3 cp --only-show-errors "$src/$f" "$STAGE/$f" || return 1
  done
  for u in registered admin robot camera; do
    pin=$(param "/telerobot/pins/$u") || return 1
    # PINs go into MOM URLs, sed replacements and an ini file: keep them plain.
    [[ "$pin" =~ ^[A-Za-z0-9_-]{12,}$ ]] || fail "/telerobot/pins/$u must be 12+ chars of [A-Za-z0-9_-]" || return 1
    printf -v "pin_$u" '%s' "$pin"
  done
  allowed_origin=$(param ${STACK_PARAMS.allowedOrigin}) || return 1
  [[ "$allowed_origin" =~ ^https://[a-z0-9.-]+$ ]] || fail "bad allowed origin: $allowed_origin" || return 1
  client_ids=$(param ${STACK_PARAMS.cognitoClientIds}) || return 1
  [[ "$client_ids" =~ ^[a-z0-9]+(,[a-z0-9]+)*$ ]] || fail "bad client ids: $client_ids" || return 1
  COMMIT=$commit
}

for attempt in $(seq 1 60); do
  if fetch; then break; fi
  [ "$attempt" -lt 60 ] || { echo "telerobot-fetch: giving up" >&2; exit 1; }
  sleep 10
done

# The local configs carry the *-local PINs; substitute the real ones.
sed "s|camera-local|$pin_camera|g" "$STAGE/config/lab.xml" > "$STAGE/lab.xml"
sed "s|robot-local|$pin_robot|g" "$STAGE/config/RobotProxy.properties" > "$STAGE/RobotProxy.properties"
if grep -qE 'camera-local|robot-local' "$STAGE/lab.xml" "$STAGE/RobotProxy.properties" \\
  || ! grep -qF ":$pin_camera:" "$STAGE/lab.xml" || ! grep -qF "$pin_robot" "$STAGE/RobotProxy.properties"; then
  echo "telerobot-fetch: PIN substitution failed" >&2
  exit 1
fi

cat > "$STAGE/users.ini" <<USERS
; MOM user database, written by telerobot-fetch from SSM.

[guest]
name=Guest
PIN=-1
UserLevel=Guest

[registered]
name=Registered user
PIN=$pin_registered
UserLevel=Registered

[admin]
name=Administrator
PIN=$pin_admin
UserLevel=Team

[robot]
name=Robot proxy
PIN=$pin_robot
UserLevel=Registered

[camera]
name=Camera server
PIN=$pin_camera
UserLevel=Registered
USERS

cat > "$STAGE/gateway.env" <<GATEWAY
ALLOWED_ORIGIN=$allowed_origin
COGNITO_CLIENT_IDS=$client_ids
GATEWAY

# MOMServer and RobotProxy open logs/*.log in their working directory at start.
install -d -o telerobot -g telerobot -m 0750 ${HOME}/run ${HOME}/run/logs
install -o root -g telerobot -m 0640 "$STAGE/config/MOM.xml" "$CFG/MOM.xml"
install -o root -g telerobot -m 0640 "$STAGE/lab.xml" "$CFG/lab.xml"
install -o root -g telerobot -m 0640 "$STAGE/RobotProxy.properties" "$CFG/RobotProxy.properties"
install -o root -g telerobot -m 0640 "$STAGE/users.ini" "$CFG/users.ini"
install -o root -g telerobot -m 0640 "$STAGE/gateway.env" /etc/telerobot/gateway.env
install -o root -g root -m 0644 "$STAGE/lab.jar" ${HOME}/lib/lab.jar
install -o root -g root -m 0644 "$STAGE/gateway.jar" ${HOME}/lib/gateway.jar
rm -rf "$STAGE"
echo "telerobot-fetch: commit $COMMIT ready"
`;

const WAIT_PORT_SCRIPT = `#!/bin/bash
# telerobot-wait-port PORT...: wait up to 2 minutes for each local TCP port to
# be listening. Reads the kernel's socket table (/proc/net/tcp*, state 0A is
# LISTEN) rather than connecting, so the old servers never see a stray
# connection. No netlink (ss), which RestrictAddressFamilies denies.
listening() {
  awk -v p=":$(printf '%04X' "$1")" '$4 == "0A" && substr($2, length($2) - 4) == p { found = 1 } END { exit !found }' /proc/net/tcp /proc/net/tcp6 2>/dev/null
}
for port in "$@"; do
  for i in $(seq 1 120); do
    if listening "$port"; then continue 2; fi
    sleep 1
  done
  echo "telerobot-wait-port: port $port is not listening" >&2
  exit 1
done
`;

function cwAgentConfig(logGroup: string): string {
  return JSON.stringify(
    {
      agent: { run_as_user: "root" },
      logs: {
        logs_collected: {
          journald: {
            collect_list: [
              {
                log_group_name: logGroup,
                log_stream_name: "{instance_id}",
                units: ["telerobot-fetch", "telerobot-mom", "telerobot-lab", "telerobot-proxy", "telerobot-gateway"],
              },
            ],
          },
        },
      },
    },
    null,
    2,
  );
}

export function renderUserData(o: UserDataOptions): string {
  return `#!/bin/bash
set -euxo pipefail

# 1 GB of swap under four JVMs on 2 GB. Idempotent.
if [ ! -f /swapfile ]; then
  dd if=/dev/zero of=/swapfile bs=1M count=1024
  chmod 0600 /swapfile
  mkswap /swapfile
fi
grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap defaults 0 0' >> /etc/fstab
swapon --show=NAME --noheadings | grep -qx /swapfile || swapon /swapfile

# fontconfig and a font: VirtualLab's renderer draws text with Font.SANS_SERIF.
dnf install -y java-21-amazon-corretto-headless fontconfig dejavu-sans-fonts

id telerobot >/dev/null 2>&1 || useradd --system --user-group --home-dir ${HOME} --no-create-home --shell /sbin/nologin telerobot
install -d -o root -g telerobot -m 0750 ${HOME} ${HOME}/config ${HOME}/lib /etc/telerobot
install -d -o telerobot -g telerobot -m 0750 ${HOME}/run

cat > /etc/telerobot/telerobot.env <<'ENV'
ARTIFACTS_BUCKET=${o.artifactsBucket}
ARCHIVE_BUCKET=${o.archiveBucket}
AWS_REGION=${o.region}
COGNITO_ISSUER=${o.cognitoIssuer}
ORIGIN_SECRET_PARAM=/telerobot/origin-secret
TURNSTILE_SECRET_PARAM=/telerobot/turnstile-secret
ENV
chmod 0640 /etc/telerobot/telerobot.env
chgrp telerobot /etc/telerobot/telerobot.env

cat > /usr/local/bin/telerobot-fetch <<'FETCH'
${FETCH_SCRIPT}FETCH
cat > /usr/local/bin/telerobot-wait-port <<'WAITPORT'
${WAIT_PORT_SCRIPT}WAITPORT
chmod 0755 /usr/local/bin/telerobot-fetch /usr/local/bin/telerobot-wait-port

cat > /etc/systemd/system/telerobot-fetch.service <<'UNIT'
[Unit]
Description=Telerobot: fetch the current commit's artifacts and render configs
Wants=network-online.target
After=network-online.target
Before=telerobot-mom.service

[Service]
Type=oneshot
RemainAfterExit=yes
TimeoutStartSec=15min
ExecStart=/usr/local/bin/telerobot-fetch

[Install]
WantedBy=multi-user.target
UNIT

${service({
  name: "mom",
  description: "Telerobot MOM server",
  requires: ["telerobot-fetch"],
  wants: ["telerobot-lab", "telerobot-proxy", "telerobot-gateway"],
  extra: LOOPBACK_ONLY,
  execStart: `${java("128m")} ${LAB_FLAGS} -cp ${LAB_JAR} au.edu.uwa.mech.mom.server.MOMServer ../config/MOM.xml`,
})}
${service({
  name: "lab",
  description: "Telerobot VirtualLab (simulated robot and cameras)",
  bindsTo: ["telerobot-mom"],
  ports: "4444",
  extra: LOOPBACK_ONLY,
  execStart: `${java("384m")} ${LAB_FLAGS} -cp ${LAB_JAR} au.edu.uwa.mech.sim.VirtualLab ../config/lab.xml`,
})}
${service({
  name: "proxy",
  description: "Telerobot RobotProxy",
  // MOM for its session, the lab for the robot server's ports
  bindsTo: ["telerobot-mom", "telerobot-lab"],
  ports: "4444 30562 30563",
  extra: LOOPBACK_ONLY,
  execStart: `${java("96m")} ${LAB_FLAGS} -cp ${LAB_JAR} au.edu.uwa.mech.devices.robot.Server ../config/RobotProxy.properties`,
})}
${service({
  name: "gateway",
  description: "Telerobot web gateway",
  // MOM for its sessions, the lab for the admin port; the proxy reconnects
  // through MOM on its own, so it is only required, not bound.
  bindsTo: ["telerobot-mom", "telerobot-lab"],
  requires: ["telerobot-proxy"],
  ports: "4444 30564",
  // Needs AWS endpoints and IMDS, so no IPAddressDeny.
  extra: "EnvironmentFile=/etc/telerobot/telerobot.env\nEnvironmentFile=/etc/telerobot/gateway.env",
  execStart: `${java("384m")} -jar ${HOME}/lib/gateway.jar --port 8080 --mom-host 127.0.0.1 --mom-port 4444 --lab-admin-port 30564`,
})}
systemctl daemon-reload
systemctl enable telerobot-fetch telerobot-mom telerobot-lab telerobot-proxy telerobot-gateway
# This boot's transaction predates the units: start them now (this pulls in the
# whole Requires= chain) without making cloud-init wait for the fetch.
systemctl start --no-block telerobot-gateway

# Log shipping last, and only ever a warning: the lab must run without it.
# From the AL2023 repo (GPG-checked); journald collection needs agent
# 1.300070.0 or later, so an older repo build fails here and warns.
set +e
if ! {
  dnf install -y amazon-cloudwatch-agent &&
  cat > /opt/aws/amazon-cloudwatch-agent/etc/telerobot.json <<'CWAGENT' &&
${cwAgentConfig(o.logGroup)}
CWAGENT
  /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl -a fetch-config -m ec2 -s -c file:/opt/aws/amazon-cloudwatch-agent/etc/telerobot.json
}; then
  echo "WARNING: CloudWatch agent setup failed; the lab runs without log shipping" >&2
fi
exit 0
`;
}
