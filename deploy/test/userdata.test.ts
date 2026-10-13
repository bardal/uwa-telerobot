import { describe, expect, it } from "vitest";
import { SERIAL_FILTER, renderUserData } from "../lib/userdata";

const script = renderUserData({
  artifactsBucket: "art-bucket",
  archiveBucket: "arch-bucket",
  region: "eu-west-2",
  cognitoIssuer: "https://cognito-idp.eu-west-2.amazonaws.com/pool",
  logGroup: "/telerobot/lab",
});

function unit(name: string): string {
  const m = script.match(new RegExp(`cat > /etc/systemd/system/${name}\\.service <<'UNIT'\\n([\\s\\S]*?)\\nUNIT\\n`));
  expect(m, name).not.toBeNull();
  return m![1];
}

describe("user data", () => {
  it("installs Corretto 21 headless with fontconfig and a sans font", () => {
    expect(script).toContain("dnf install -y java-21-amazon-corretto-headless fontconfig dejavu-sans-fonts\n");
  });

  it("sets up the CloudWatch agent from the repo only after the units start, warning on failure", () => {
    const start = script.indexOf("systemctl start --no-block telerobot-gateway");
    const enable = script.indexOf("systemctl enable telerobot-fetch");
    const agent = script.indexOf("dnf install -y amazon-cloudwatch-agent");
    expect(enable).toBeGreaterThan(0);
    expect(start).toBeGreaterThan(enable);
    expect(agent).toBeGreaterThan(start);
    const tail = script.slice(start);
    expect(tail).toMatch(/set \+e\nif ! \{\n  dnf install -y amazon-cloudwatch-agent &&/);
    expect(tail).toContain("WARNING: CloudWatch agent setup failed");
    expect(script).not.toContain("amazoncloudwatch-agent.s3.amazonaws.com");
    expect(script.indexOf("amazon-cloudwatch-agent")).toBe(agent + "dnf install -y ".length);
  });

  it("adds a 1 GB swapfile once and enables it on boot", () => {
    expect(script).toContain("if [ ! -f /swapfile ]; then\n  dd if=/dev/zero of=/swapfile bs=1M count=1024\n");
    expect(script).toContain(
      "grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap defaults 0 0' >> /etc/fstab",
    );
    expect(script.indexOf("/swapfile")).toBeLessThan(script.indexOf("dnf install"));
  });

  it("caps each JVM heap and exits on OOM", () => {
    for (const [name, xmx] of [
      ["mom", "128m"],
      ["lab", "384m"],
      ["proxy", "96m"],
      ["gateway", "384m"],
    ]) {
      expect(unit(`telerobot-${name}`)).toContain(`ExecStart=/usr/bin/java -Xmx${xmx} -XX:+ExitOnOutOfMemoryError `);
    }
  });

  it("uses the exact serialization filter", () => {
    expect(SERIAL_FILTER).toBe(
      "au.edu.uwa.mech.**;Jama.**;java.lang.*;java.util.*;java.awt.Point;java.awt.Rectangle;[*;!*",
    );
  });

  it("re-fetches the current commit from SSM on every boot before MOM", () => {
    const fetch = unit("telerobot-fetch");
    expect(fetch).toContain("Type=oneshot");
    expect(fetch).toContain("Before=telerobot-mom.service");
    expect(script).toContain("param /telerobot/commit");
    const mom = unit("telerobot-mom");
    expect(mom).toContain("Requires=telerobot-fetch.service");
    expect(mom).toContain("After=telerobot-fetch.service");
  });

  it("makes run/logs, where MOM and RobotProxy open their logs at start, on every boot", () => {
    // MOMServer opens logs/users.log and RobotProxy logs/RobotCommands.log
    // relative to WorkingDirectory=run; without the directory both exit.
    const fetchScript = script.slice(script.indexOf("cat > /usr/local/bin/telerobot-fetch"), script.indexOf("\nFETCH\n"));
    expect(fetchScript).toContain("install -d -o telerobot -g telerobot -m 0750 /var/lib/telerobot/run /var/lib/telerobot/run/logs\n");
    expect(unit("telerobot-mom")).toContain("WorkingDirectory=/var/lib/telerobot/run\n");
    expect(unit("telerobot-proxy")).toContain("WorkingDirectory=/var/lib/telerobot/run\n");
  });

  it("writes users.ini with the five accounts and drops team", () => {
    for (const [user, level] of [
      ["guest", "Guest"],
      ["registered", "Registered"],
      ["admin", "Team"],
      ["robot", "Registered"],
      ["camera", "Registered"],
    ]) {
      expect(script).toMatch(new RegExp(`\\[${user}\\]\\nname=[^\\n]+\\nPIN=[^\\n]+\\nUserLevel=${level}\\n`));
    }
    expect(script).toMatch(/\[guest\]\nname=Guest\nPIN=-1\n/);
    expect(script).not.toContain("[team]");
  });

  it("hardens all four units and gives the three lab units the filter and the bind", () => {
    for (const name of ["mom", "lab", "proxy", "gateway"]) {
      const u = unit(`telerobot-${name}`);
      for (const flag of [
        "User=telerobot",
        "NoNewPrivileges=yes",
        "ProtectSystem=strict",
        "PrivateTmp=yes",
        "ReadWritePaths=/var/lib/telerobot",
        "Restart=on-failure",
        "ProtectHome=yes",
        "PrivateDevices=yes",
        "ProtectKernelTunables=yes",
        "ProtectKernelModules=yes",
        "ProtectControlGroups=yes",
        "RestrictAddressFamilies=AF_INET AF_INET6 AF_UNIX",
        "CapabilityBoundingSet=\n",
        "LockPersonality=yes",
        "RestrictNamespaces=yes",
      ]) {
        expect(u, `${name}: ${flag}`).toContain(flag);
      }
      expect(u).not.toContain("MemoryDenyWriteExecute");
      // loopback only, except the gateway, which needs AWS endpoints and IMDS
      expect(u.includes("IPAddressDeny=any\nIPAddressAllow=localhost\n")).toBe(name !== "gateway");
      expect(u.includes("-Djava.awt.headless=true")).toBe(name !== "gateway");
      // MOM starts first; the others wait for the ports they connect to.
      expect(u.includes("ExecStartPre=/usr/local/bin/telerobot-wait-port ")).toBe(name !== "mom");
      const lab = name !== "gateway";
      expect(u.includes(`"-Djdk.serialFilter=${SERIAL_FILTER}"`)).toBe(lab);
      expect(u.includes("-Dtelerobot.bind=127.0.0.1")).toBe(lab);
    }
    expect(unit("telerobot-mom")).toContain("au.edu.uwa.mech.mom.server.MOMServer ../config/MOM.xml");
    expect(unit("telerobot-lab")).toContain("au.edu.uwa.mech.sim.VirtualLab ../config/lab.xml");
    expect(unit("telerobot-proxy")).toContain("au.edu.uwa.mech.devices.robot.Server ../config/RobotProxy.properties");
    expect(unit("telerobot-gateway")).toContain(
      " -jar /var/lib/telerobot/lib/gateway.jar --port 8080 --mom-host 127.0.0.1 --mom-port 4444 --lab-admin-port 30564",
    );
  });

  it("orders the units mom -> lab -> proxy -> gateway", () => {
    const after = (name: string) => unit(name).match(/^After=(.*)$/m)![1].split(" ");
    expect(after("telerobot-mom")).toEqual(["telerobot-fetch.service"]);
    expect(after("telerobot-lab")).toEqual(["telerobot-mom.service"]);
    expect(after("telerobot-proxy")).toEqual(["telerobot-mom.service", "telerobot-lab.service"]);
    expect(after("telerobot-gateway")).toEqual(["telerobot-mom.service", "telerobot-lab.service", "telerobot-proxy.service"]);
    expect(unit("telerobot-gateway")).toMatch(/^Requires=telerobot-proxy\.service$/m);
  });

  it("binds the lab, proxy and gateway to MOM (and the lab), so a MOM restart restarts them", () => {
    // BindsTo= + After=: a MOM restart (Restart=on-failure, ExitOnOutOfMemoryError)
    // restarts every bound unit, and MOM going down for good stops them,
    // instead of leaving them holding dead MOM sessions.
    const bindsTo = (name: string) => (unit(name).match(/^BindsTo=(.*)$/m)?.[1] ?? "").split(" ").filter(Boolean);
    expect(bindsTo("telerobot-mom")).toEqual([]);
    expect(bindsTo("telerobot-lab")).toEqual(["telerobot-mom.service"]);
    expect(bindsTo("telerobot-proxy")).toEqual(["telerobot-mom.service", "telerobot-lab.service"]);
    expect(bindsTo("telerobot-gateway")).toEqual(["telerobot-mom.service", "telerobot-lab.service"]);
    // every BindsTo= target is also ordered After= (the strong form)
    for (const name of ["telerobot-lab", "telerobot-proxy", "telerobot-gateway"]) {
      const after = unit(name).match(/^After=(.*)$/m)![1].split(" ");
      for (const b of bindsTo(name)) expect(after, `${name} after ${b}`).toContain(b);
      // no Requires= duplicating a BindsTo=
      const requires = (unit(name).match(/^Requires=(.*)$/m)?.[1] ?? "").split(" ");
      for (const b of bindsTo(name)) expect(requires).not.toContain(b);
    }
    // MOM still needs this boot's fetch
    expect(unit("telerobot-mom")).toMatch(/^Requires=telerobot-fetch\.service$/m);
  });

  it("gives the gateway its environment", () => {
    for (const v of [
      "ARCHIVE_BUCKET=arch-bucket",
      "AWS_REGION=eu-west-2",
      "COGNITO_ISSUER=https://cognito-idp.eu-west-2.amazonaws.com/pool",
      "ORIGIN_SECRET_PARAM=/telerobot/origin-secret",
      "TURNSTILE_SECRET_PARAM=/telerobot/turnstile-secret",
    ]) {
      expect(script).toContain(v);
    }
    expect(script).toContain("ALLOWED_ORIGIN=$allowed_origin");
    expect(script).toContain("COGNITO_CLIENT_IDS=$client_ids");
  });

  it("ships journald for the units to /telerobot/lab", () => {
    const m = script.match(/<<'CWAGENT'[^\n]*\n([\s\S]*?)\nCWAGENT\n/);
    expect(m).not.toBeNull();
    const cfg = JSON.parse(m![1]);
    const entry = cfg.logs.logs_collected.journald.collect_list[0];
    expect(entry.log_group_name).toBe("/telerobot/lab");
    expect(entry.units).toEqual(
      expect.arrayContaining(["telerobot-mom", "telerobot-lab", "telerobot-proxy", "telerobot-gateway"]),
    );
  });

  it("waits for ports from /proc/net/tcp, not netlink", () => {
    expect(script).toContain("/proc/net/tcp /proc/net/tcp6");
    expect(script).not.toMatch(/\bss -/);
  });

  it("has no unescaped template leftovers", () => {
    expect(script).not.toContain("undefined");
    expect(script.startsWith("#!/bin/bash\n")).toBe(true);
  });
});
