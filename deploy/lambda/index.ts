import {
  DescribeInstancesCommand,
  EC2Client,
  StartInstancesCommand,
  StopInstancesCommand,
} from "@aws-sdk/client-ec2";
import { GetParameterCommand, ParameterNotFound, PutParameterCommand, SSMClient } from "@aws-sdk/client-ssm";
import { CognitoJwtVerifier } from "aws-jwt-verify";
import { checkOrigin, reap, status, wake, type Deps } from "./core";
import { cachedValue, isReapEvent } from "./params";

interface FunctionUrlEvent {
  rawPath?: string;
  headers?: Record<string, string | undefined>;
  body?: string | null;
  isBase64Encoded?: boolean;
  requestContext?: { http?: { method?: string; path?: string } };
  source?: string;
  "detail-type"?: string;
  reap?: boolean;
}

const ec2Client = new EC2Client({});
const ssmClient = new SSMClient({});

async function decryptedParam(name: string): Promise<string> {
  const r = await ssmClient.send(new GetParameterCommand({ Name: name, WithDecryption: true }));
  return r.Parameter?.Value ?? "";
}

// Each comes from its env var when set, else from SSM once per cold start.
const turnstileSecret = cachedValue(
  process.env.TURNSTILE_SECRET,
  process.env.TURNSTILE_SECRET_PARAM,
  decryptedParam,
);
const cognitoClientIds = cachedValue(
  process.env.COGNITO_CLIENT_IDS,
  process.env.COGNITO_CLIENT_IDS_PARAM,
  decryptedParam,
);

let verifier: ReturnType<typeof makeVerifier> | undefined;
function makeVerifier(clientIds: string) {
  const clientId = clientIds
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
  if (clientId.length === 0) throw new Error("no Cognito client ids");
  return CognitoJwtVerifier.create({
    userPoolId: process.env.USER_POOL_ID ?? "",
    tokenUse: "id",
    clientId,
  });
}

function buildDeps(): Deps {
  return {
    ec2: {
      async describeState(id) {
        const r = await ec2Client.send(new DescribeInstancesCommand({ InstanceIds: [id] }));
        const i = r.Reservations?.[0]?.Instances?.[0];
        return {
          state: i?.State?.Name ?? "stopped",
          launchTime: i?.LaunchTime ? new Date(i.LaunchTime).getTime() : 0,
        };
      },
      async start(id) {
        await ec2Client.send(new StartInstancesCommand({ InstanceIds: [id] }));
      },
      async stop(id) {
        await ec2Client.send(new StopInstancesCommand({ InstanceIds: [id] }));
      },
    },
    ssm: {
      async get(name) {
        try {
          const r = await ssmClient.send(new GetParameterCommand({ Name: name }));
          return r.Parameter?.Value ?? null;
        } catch (e) {
          if (e instanceof ParameterNotFound) return null;
          throw e;
        }
      },
      async put(name, value) {
        await ssmClient.send(
          new PutParameterCommand({ Name: name, Value: value, Type: "String", Overwrite: true }),
        );
      },
    },
    async verifyTurnstile(token) {
      const res = await fetch("https://challenges.cloudflare.com/turnstile/v0/siteverify", {
        method: "POST",
        headers: { "content-type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({ secret: await turnstileSecret(), response: token }),
        signal: AbortSignal.timeout(5000),
      });
      if (!res.ok) return false;
      const j = (await res.json()) as { success?: boolean };
      return j.success === true;
    },
    async verifyIdToken(token) {
      verifier ??= makeVerifier(await cognitoClientIds());
      await verifier.verify(token);
      return true;
    },
    now: () => Date.now(),
    instanceId: process.env.INSTANCE_ID ?? "",
    hoursCap: Number(process.env.HOURS_CAP ?? 150),
    originSecret: process.env.ORIGIN_SECRET ?? "",
  };
}

function json(statusCode: number, body: unknown) {
  return {
    statusCode,
    headers: { "content-type": "application/json", "cache-control": "no-store" },
    body: JSON.stringify(body),
  };
}

export async function handler(event: FunctionUrlEvent) {
  const deps = buildDeps();

  if (isReapEvent(event)) {
    await reap(deps);
    return { reaped: true };
  }

  const method = event.requestContext?.http?.method ?? "";
  const path = event.rawPath ?? event.requestContext?.http?.path ?? "";
  const headers = event.headers ?? {};

  if (!checkOrigin(deps, headers)) {
    return json(403, { error: "forbidden" });
  }

  if (method === "GET" && path === "/api/status") {
    return json(200, await status(deps));
  }
  if (method === "POST" && path === "/api/wake") {
    const raw = event.isBase64Encoded && event.body
      ? Buffer.from(event.body, "base64").toString("utf8")
      : event.body;
    const r = await wake(deps, raw, headers);
    return json(r.status, r.body);
  }
  return json(404, { error: "not found" });
}
