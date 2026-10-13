# Web UI and AWS deployment — design

Date: 2026-10-13. Status: approved in conversation, awaiting review of this
written spec.

## Intent

**What Barney asked for:** a 2026 web version of the telerobot UI that he can
make available on AWS, and that is safe against 2026 hackers and AI agents.

**What he chose:**

* **Audience:** open to the public, like the original. Anyone can drive the
  shared robot, taking turns, while others watch and chat.
* **UI:** a faithful recreation of the February 2000 TA200001 applet in the
  browser, with all of its features.
* **Hosting:** wakes on demand. The lab sleeps when idle, and the first visitor
  sees "Turning the robot on…" while it starts.
* **Accounts:** guests, plus sign-in (Google or email) for a registered level
  that outranks guests.
* **Human check:** Cloudflare Turnstile for guests.

**Assumed, open to correction:**

* An AWS-provided `*.cloudfront.net` URL to start with; a custom domain can be
  added later.
* Barney's own account is the admin.
* Chat is not stored.

**Success:**

* A visitor opens the URL on a current desktop browser. If the lab is asleep,
  it wakes.
* The visitor can do everything the applet could:
  * log in as a guest or sign in;
  * see the three cameras with the 3D overlays;
  * drag the stick cursor and command the robot from it;
  * pick and place blocks, measure, share workspace points and chat.
* It costs about $5 a month idle, and only cents per hour while in use.
* Nothing the public can reach can run code on the server, and abuse is
  limited by caps and limits rather than by hoping.

**Not in scope:** an LLM or AI features, a mobile-first redesign, persistence
across lab restarts (the blocks reset), and RMI access.

## Architecture

```
 browser (TS UI) ──HTTPS/WSS──> CloudFront (+ AWS WAF)
                                  ├── /*        S3: static UI
                                  ├── /api/*    Lambda: status / wake
                                  └── /ws, /healthz ──> EC2 (Elastic IP), docker:
                                                         gateway :8080 (only published port)
                                                           │ one MOM login per browser
                                                         MOM :4444, RobotProxy, VirtualLab,
                                                         camera server (all on 127.0.0.1)
 Cognito user pool (Google + email) ── ID tokens checked by the gateway
```

The 2000 servers run unchanged: MOM, RobotProxy, the camera server and
VirtualLab. A new **gateway** is the only public endpoint. It turns each
browser's WebSocket into an ordinary MOM client login, so MOM's sessions,
channels, tokens and user levels still decide everything. Its token policy is
HUWManager: a higher level can take control at once, anyone can take it 180 s
after the last grab, and the owner can release it.

### Repository layout

| Path | What |
|---|---|
| `pom.xml` | Becomes a parent with modules. The existing code moves to module `lab` unchanged, with source/target 1.8 so the local applet still runs on JDK 8. |
| `gateway/` | New Maven module, Java 21: `au.edu.uwa.mech.web.*`. Depends on `lab`, Jetty 12 (WebSocket and HTTP) and nimbus-jose-jwt. |
| `webui/` | New TypeScript UI: Vite, vitest and Playwright, no UI framework. |
| `deploy/` | New AWS CDK app (TypeScript), Dockerfile, `compose.yaml`, `deploy.ps1`. |
| `web/`, `start.*` | Kept: the local applet route keeps working. |

## Components

### Gateway (`gateway/`)

**Units:**

* **`GatewayServer`**
  * Starts Jetty with `/ws` (WebSocket) and `/healthz`.
  * Checks that the Origin is the site's own and that the CloudFront secret
    header is present.
  * Enforces global caps: viewers, connections per IP, and handshakes per
    second.
* **`Session`**
  * Holds one browser's state: identity, level, nickname, chosen camera, rate
    buckets and last human input time.
  * Owns one `SessionRoot` logged into the local MOM.
* **`Auth`**
  * Takes a `hello` and returns an identity, or refuses it.
  * Guests: verifies the Turnstile token with Cloudflare `siteverify`, then
    the user is a guest.
  * Signed-in users: verifies the Cognito ID token (signature via JWKS,
    issuer, audience, expiry and `token_use`), then the user is registered.
    Membership of the `admin` Cognito group makes them an admin.
* **`MomBridge`**
  * Logs into MOM as the service account for the user's level, with the
    user's own nickname:
    * guest → `guest`;
    * registered → `registered` (UserLevel Registered);
    * admin → `admin` (UserLevel Team).
    The service-account PINs are random per deployment, kept in SSM, and
    written into `users.ini` when the container starts. They are never in the
    repo.
  * Attaches the existing `devices.robot.Client` and `CameraManagerClient`,
    plus listeners on `chat.channel` and `workspace.channel`.
  * Translates MOM events into outgoing messages and incoming messages into
    MOM calls.
* **`Protocol`**
  * Strict JSON parsing into typed records.
  * Unknown types or fields are an error, and so is any text over 4 KB.
  * Each field has its own limits.
* **`Limits`**
  * Token buckets per session and per IP.
  * Checks path scripts with the existing `sim.PathScript` parser: at most 400
    characters, 30 waypoints and 4 queued commands.
  * Chat: at most 300 characters, 1 message a second (burst 5), plus a
    blocklist.
  * Workspace points: at most 20 per user.
* **`Idle`**
  * Human input means a command, chat, cursor or menu action.
  * A session with no human input for 10 minutes is closed; every session
    closes after 2 hours.
  * With no sessions for 15 minutes, the gateway calls
    `ec2:StopInstances` on its own instance. It is disabled locally.
  * While running, the gateway adds the elapsed minutes to this month's
    running-hours parameter every 10 minutes.
* **`Admin`**
  * Admin only: kick (by nickname), ban (by Cognito `sub` or hashed IP, kept
    in memory and in an SSM parameter), and reset the lab (VirtualLab gets a
    reset hook that restores the default blocks).

**Messages.** WebSocket text frames carry JSON. Each image is a text header
followed by one binary frame.

| Direction | Type | Fields |
|---|---|---|
| → | `hello` | `nick`, and either `turnstile` or `idToken` |
| → | `command` | `script` |
| → | `takeControl`, `release` | — |
| → | `chat` | `text` |
| → | `selectCamera` | `camera` |
| → | `cameraSettings` | `camera`, `on`, `width`, `height`, `zoom`, `quality`, `fixed` |
| → | `newImage` | `camera` |
| → | `workspaceAdd` | `x`, `y`, `z`, `label` |
| → | `workspaceDelete` | `id` |
| → | `activity` | — (cursor drags; counts as human input; rate-limited) |
| → | `admin` | `action` (`kick`, `ban` or `reset`), `nick` |
| ← | `welcome` | `nick`, `level`, `cameras[]`, `limits` |
| ← | `robotState` | `x`, `y`, `z`, `spin`, `tilt`, `gripperOpen`, `moving` |
| ← | `controller` | `nick` or null |
| ← | `commandStatus` | `id`, `state` (`queued`, `sent`, `done` or `error`), `text` |
| ← | `users` | `nicks[]` |
| ← | `chat` | `nick`, `text`, `time` |
| ← | `workspace` | `points[]` |
| ← | `image` | `camera`, `seq`, `width`, `height`, `calibration[12]`, then a binary JPEG |
| ← | `lab` | `state` (`ready` or `stopping`), `message` |
| ← | `error` | `code`, `message` |

Only the selected camera's images are sent to a browser. When the browser
switches camera, the latest frame is sent at once.

### Web UI (`webui/`)

**Pages:**

* The login page: nickname, Guest (with Turnstile) or Sign in (Cognito
  Hosted UI with PKCE). It shows the original's help text.
* "Turning the robot on…", with progress and the wake errors.
* The control page.

**The control page** keeps the TA200001 layout and look:

* Salmon background (#DC8C64), titled panels, and AWT-like controls in a
  modern font.
* Menus:
  * **Connection:** Logout.
  * **Robot:** Generate Random Move, Comand from cursor (keeping the original
    spelling), Command from selected point, Close gripper, Open gripper.
  * **Images:** Archive Images (disabled: there's no archive), Clear Path,
    Show Cursor, Show Axes, Show Points.
  * **Workspace:** Add Point, Delete Point, Measure point, Measure line.
  * **Help:** Brief Help.
* The right-click popup: align cursor with robot, set cursor to home, set
  cursor to point, move robot to cursor or to a point, open or close the
  gripper.
* Panels:
  * **Robot:** command editor, Send command, Release Robot (labelled Control
    Robot when not in control), Clear, and the Status, Robot Position and
    Controller lines.
  * **Console.**
  * **Cameras:** Colour, YCam and ZCam buttons; the canvas; settings for on,
    width, height, zoom, quality and fixed, with New Image and Change Settings
    buttons.
  * **Chat:** message box, log and Users list.
* The camera canvas fills its panel and scales the image to fit, as in
  794aadb.

**Modules, each a single job:**

* **`geometry/camera.ts`** is a port of `gui.overlaid.Camera`: `xyz2uv`,
  `uv2line`, `uv2xyzPlane` and `uv2xyzSurface`, with the display
  scale/offset transform.
* **`overlays/`** ports `StickCursor` (with `PlumblineCursor`, `SpinCursor`
  and `TiltCursor` parts and the same drag rules), `Path`, `Axes`,
  `TapeMeasure`, `PointMeasure`, `WorkspacePoints` and `StickCursorCoords`.
* **`net/gateway.ts`** handles the WebSocket client, typed messages and
  reconnect with backoff.
* **`net/wake.ts`** runs the status/wake flow.
* **`ui/`** holds one module per panel.
* **`pathscript.ts`** converts a pose to a GBH script, like
  `MMoveCommand.stateAsGBHString`, for "command from cursor".

Command checking stays on the gateway. The UI shows its errors.

**Security in the UI:**

* All text goes in as text, never as HTML.
* No inline script.
* A CSP is set by CloudFront.
* Chat links are not clickable.

### Wake and status (`deploy/lambda/`)

* **`GET /api/status`** returns the instance state (`stopped`, `pending`,
  `running`) and whether the gateway is healthy. Results are cached for 2 s.
* **Reaper:** an EventBridge schedule calls the same Lambda every 15 minutes.
  It stops the instance if `/healthz` has failed, or reported zero sessions,
  on two checks in a row. That covers a hung gateway.
* **`POST /api/wake`**
  * Requires a valid Turnstile token or Cognito ID token.
  * Starts the instance unless this month's running hours exceed the cap
    (default 150 h, a stack parameter).
  * Reserved concurrency is 2.
* **The page's wake flow:**
  1. Call status. If the lab is stopped, show the "Turning the robot on…"
     page.
  2. Call wake.
  3. Poll `/healthz` every 2 s, for up to 3 minutes, then connect.
  4. If the cap is reached, show "The robot is resting until next month".

### AWS stack (`deploy/`, CDK)

* **S3 bucket** (private, read through Origin Access Control) holding the
  built UI.
* **CloudFront distribution**
  * Behaviours:
    * `/*` from S3;
    * `/api/*` to the Lambda function URL (with OAC);
    * `/ws` and `/healthz` to the EC2 origin (the Elastic IP's public DNS
      name, over HTTP on port 8080), with the WebSocket upgrade allowed and no
      caching.
  * A secret header goes to the EC2 origin.
  * A response-headers policy sets:
    * HSTS;
    * a CSP allowing self, Turnstile and the Cognito domain;
    * `frame-ancestors 'none'`, `nosniff` and a referrer policy.
  * Use the flat-rate Free plan if it supports WebSocket origins and our
    behaviours; otherwise pay-as-you-go with a web ACL.
* **AWS WAF** with a rate-based rule per IP, the managed common rule set, and
  the known-bad-inputs rules.
* **Cognito** user pool with Google as an identity provider plus email
  sign-up, the Hosted UI and an `admin` group. Barney is added to the group
  after his first sign-in.
* **EC2** t4g.small (ARM, Amazon Linux 2023)
  * 8 GB gp3, an Elastic IP, IMDSv2 required, and no key pair.
  * The security group allows TCP 8080 only from the CloudFront origin-facing
    managed prefix list.
  * The instance role allows SSM core, CloudWatch Logs (14-day retention),
    ECR pull, `ec2:StopInstances` on itself, reading its SSM parameters, and
    writing only the ban-list and running-hours parameters.
  * Instance-initiated shutdown is set to stop.
  * User data installs Docker, pulls the image and runs it under systemd.
* **ECR** repository for the lab image, keeping the last 3 images.
* **SSM parameters:** the origin secret, the MOM service PINs, the Turnstile
  secret, the ban list and the running-hours counter.
* **AWS Budget:** $20 a month, with an email alert at 50% and 100%.

**The lab image** runs `eclipse-temurin:21-jre` as a non-root user, with a
read-only root filesystem and a tmpfs for `run/`.

* Its entrypoint writes `users.ini` from SSM.
* It starts MOM, VirtualLab, RobotProxy and the gateway in order, waiting for
  each port.
* MOM, RobotProxy and VirtualLab are given a JEP 290 serialization filter
  (`jdk.serialFilter`) that allows `au.edu.uwa.mech.**`, `Jama.**`,
  `java.lang.*`, `java.util.*`, primitive arrays and `java.awt.Point`, and
  rejects everything else.
* Only port 8080 is published, and the others bind to 127.0.0.1.

**`deploy.ps1`** builds the UI and the image (linux/arm64), pushes to ECR,
runs `cdk deploy`, uploads the UI and invalidates `index.html`. It is run by
Barney, never automatically.

## Data flow: a guest picks up a block

1. The page loads from CloudFront and calls `/api/status`. If the lab is
   stopped, the guest passes Turnstile, the page calls `/api/wake` and shows
   "Turning the robot on…", and polls `/healthz` until it answers.
2. The page opens the WSS `/ws` connection and sends `hello`.
3. The gateway verifies the Turnstile token and logs into MOM as
   `guest`:`<pin>`:`<nick>`, then sends `welcome`, `users`, `controller`, the
   last `robotState` and the latest image.
4. The guest clicks Control Robot, which sends `takeControl`. MOM grants the
   token if it's free or 180 s have passed since the last grab, and the
   gateway sends `controller` to everyone.
5. The guest drags the cursor. The UI projects the cursor with the image's
   calibration (scaled for display) and sends `activity` at most 2 times a
   second.
6. Choosing "Comand from cursor" builds a script. Sending it triggers this
   chain:
   1. The UI sends `command`.
   2. The gateway checks the limits and the PathScript parse, then calls
      `robotClient` with the move.
   3. MOM passes it to RobotProxy, which passes it to VirtualLab.
   4. Positions come back as `robotState`. While the robot moves, the camera
      server photographs every 500 ms, and the frames come back as `image`.
7. The command completes, and the gateway sends `commandStatus` `done`.

## Errors

* **A MOM login or connection is lost:** the gateway sends `error` with code
  `lab-unavailable` and closes. The UI reconnects with backoff and shows the
  wake page if the lab has stopped.
* **A message is rejected:** the gateway sends `error` with code
  `bad-message`, `rate-limited` or `not-in-control` and keeps the session. A
  session with 20 rejections in a minute is closed.
* **The gateway is stopping because of idle shutdown:** it sends `lab`
  `stopping` to everyone.
* **Wake fails:** the UI shows the reason (`cap-reached`, `starting`, or
  `error` with a retry).
* **The robot rejects a move:** the robot's own `4 n "text"` errors are shown
  in the Console, as in the applet.

## Security requirements (testable)

1. Only the gateway port is reachable from outside the container. MOM,
   RobotProxy and VirtualLab are not reachable from the host network. A test
   in `compose` asserts this.
2. Bytes from a browser never reach Java deserialization: the gateway only
   builds typed records. A test checks that a serialized-object payload is
   rejected as `bad-message`.
3. MOM's JVM has an allowlist serialization filter. A test checks that a MOM
   socket fed a disallowed class is rejected.
4. The gateway refuses:
   * a missing or wrong origin secret;
   * a foreign Origin;
   * a `hello` without valid Turnstile or Cognito proof;
   * an expired token, the wrong audience, or the wrong `token_use`;
   * an `admin` message from a non-admin.
   Each refusal has a test.
5. Every field limit and rate limit above has a test, and a flood test closes
   the abusive session while a second session is unaffected.
6. The cost caps hold:
   * Wake refuses once the hours cap is reached (unit test with a fake
     counter).
   * The gateway stops the instance 15 min after the last session (test with
     a fake clock and a fake EC2 client).
   * The reaper stops a running instance whose health fails or that has had
     no sessions on two checks in a row (unit test with fakes).
   * Idle and maximum-length sessions are closed.
7. UI: no `innerHTML` with dynamic data (a lint rule), and the CSP blocks
   inline script (Playwright checks for a CSP violation report).
8. Before the first deploy: run a `/security-review` pass and the hostile
   suite (`gateway` tests tagged `hostile`). `npm audit` and dependency
   checks are clean, and dependency versions are pinned, with Dependabot
   enabled.
9. Nothing secret is in the repo, and the CDK app reads secrets only from SSM.

## Testing

* **Java (`lab`):** the existing 19 tests keep passing. A new
  `GeometryFixtures` test writes `webui/test/fixtures/camera.json` from the
  three VirtualLab cameras: matrices, 3D points, Java pixels, and the
  `uv2line` and `uv2xyzPlane` results.
* **Java (`gateway`):**
  * `Protocol` and `Limits` unit tests, plus the security tests above.
  * A gateway integration test that starts MOM, VirtualLab and RobotProxy in
    process with a WebSocket client. It checks hello, images, control,
    command and pick-and-place, and two clients chatting.
* **TypeScript (vitest):**
  * `camera.ts` matches the fixtures to within 0.5 px.
  * The stick cursor drag rules match the Java `StickCursor`: base drag moves
    x/y, peak drag moves z, and the needles change spin and tilt.
  * The pose-to-script conversion matches `MMoveCommand`.
* **Playwright, against `docker compose`** with Turnstile test keys and dev
  sign-in:
  1. Mirror `AppletWalkthrough`: log in, wait for images, Colour, Control
     Robot, drag the cursor (base then peak), "Comand from cursor", check the
     robot reached the cursor, then pick and place red onto blue and check
     the robot state.
  2. Two browsers: chat both ways, then registered user B takes control from
     guest A at once.
  3. Resize the window: the canvas scales, and dragging still lands on
     target.
  4. Workspace points and the tape measure.
  5. The wake page with a fake status API.
* **After deploy:** `deploy/smoke.ps1` hits `/api/status`, wakes the lab,
  waits for `/healthz`, then opens a WSS session. It signs in as a Cognito
  test user that Barney creates, so production needs no Turnstile bypass, and
  checks for `welcome` and an image. Barney runs it.

## Local development

* `docker compose -f deploy/compose.yaml up` runs the lab image
  always-on with:
  * dev sign-in (a local signer whose JWKS the gateway trusts only in dev
    mode);
  * Turnstile test keys;
  * idle stop disabled.
* `npm run dev` in `webui/` proxies `/ws` and `/healthz` to it, and the
  status API is faked.

## Costs (estimates, eu-west-2)

* **Idle:** an Elastic IP at about $3.65 a month, 8 GB gp3 at about $0.75,
  and ECR, SSM and Lambda at about $0. That's about $4.50 a month.
* **Running:** t4g.small at about $0.019 an hour. At the 150 h cap, that's
  at most about $3 a month.
* **CloudFront and WAF:** $0 on the flat-rate Free plan, or about $7 a month
  plus traffic on pay-as-you-go. Image traffic is about 20 KB × 2 fps per
  watching viewer while the robot moves.
* **Cognito:** free at this scale.
* **Turnstile:** free.

## Open items for planning

* Confirm the CloudFront flat-rate Free plan supports WebSocket and Lambda
  function URL origins. If it doesn't, use pay-as-you-go with a web ACL.
* Confirm the `lab` module's server classes run on Java 21. They target 1.8
  and use RMI only optionally.
* Check whether the chat and workspace channel message formats used by
  `ChatPanel` and `WorkspaceClient` can be produced by the gateway without
  AWT. The model classes import AWT but run headless.
* Choose the region (default eu-west-2).
