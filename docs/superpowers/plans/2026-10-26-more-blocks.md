# More Blocks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A fuller simulated table with six piece kinds, and Add / Randomise / Reset Blocks in the applet's Robot menu for whoever is in control (or an admin).

**Architecture:** The lab's `World` owns pieces, placement and support; `Scene` draws each shape. The lab admin port gains `blocks add|randomise|reset`, and after a change the lab re-shoots every camera itself. The gateway adds one browser message, checks controller-or-admin, calls the admin port and broadcasts a console line. The web UI adds three menu items enabled only while allowed.

**Tech Stack:** Java 21 (lab, gateway; JUnit 4 in lab, JUnit 5 in gateway), TypeScript + Vitest (webui).

**Spec:** `docs/superpowers/specs/2026-10-26-more-blocks-design.md`

## Global Constraints

- Keep the five original pieces exactly as now: red (120,150,0°), blue (330,120,20°), yellow (200,360,−15°), green (400,330,45°) 30 mm cubes; bar (260,200,70°) 60×30×30, colours unchanged. Smoke move `x330,y120,z90` and BridgeIT must still pass.
- Cap: 20 pieces.
- No sleeps or wall-clock waits in tests (`NoSleepInTestsTest`, `noRealTimers.test.ts` enforce it).
- Console texts (exact): `<nick> added a <kind>`, `<nick> randomised the table`, `<nick> reset the blocks`. Refusals (to the asker only): `the table is full (20 blocks)`, `wait for the arm to stop`, `put the block down first`.
- Menu labels (exact): `Add Block`, `Randomise Blocks`, `Reset Blocks`, after a separator at the end of the Robot menu.
- Builds: `~/tools/maven/bin/mvn verify` with `JAVA_HOME=~/tools/jdk21`; webui `npm test`, `npm run lint`, `npm run build`.

## Review Focus

1. A block command arriving while the arm is mid-move or holding a piece: refused cleanly, world unchanged (tests in Task 1).
2. Randomise with 20 pieces on the table: every piece still placed, none overlapping, all in bounds (Task 1, `randomiseTwentyHasNoOverlaps`).
3. Control changes hands between the menu showing and the click: the gateway re-checks and refuses (Task 4, guest refused).
4. Dropping a piece onto a roof, or across a gap: lands on the highest non-roof surface under its base (Task 1).
5. The lab admin port unreachable (lab asleep or restarting): the asker gets `lab-unavailable`, nobody gets a console line (Task 4).

---

### Task 1: Pieces, placement and support in the World

**Files:**
- Create: `lab/src/main/java/au/edu/uwa/mech/sim/Shape.java`
- Modify: `lab/src/main/java/au/edu/uwa/mech/sim/Block.java`, `lab/src/main/java/au/edu/uwa/mech/sim/World.java`
- Test: `lab/src/test/java/au/edu/uwa/mech/sim/BlocksTest.java`

**Interfaces:**
- Produces: `enum Shape { CUBE, BAR, COLUMN, ROOF, ARCH, CYLINDER }` with `String noun()` (`"cube"`, `"bar"`, `"column"`, `"roof"`, `"arch"`, `"cylinder"`).
- `Block` gains `public final Shape shape`; new 10-arg constructor `Block(String name, Shape shape, double x, double y, double z, double yawDeg, double sx, double sy, double sz, Color colour)`; the existing 9-arg constructor stays and means `CUBE` if sx==sy==sz else `BAR`. `copy()` keeps the shape. `boolean supports()` is false for ROOF.
- `World`: `public record Change(boolean ok, String text)`; `public Change addRandom()`, `public Change randomise()`, `public Change resetBlocks()`; `public static final int MAX_BLOCKS = 20`; package-private `World(java.util.Random random)` constructor and `static World withDefaultBlocks(Random random)`; the existing no-arg forms use `new Random()`.
- Success texts: `added a <noun>`, `randomised the table`, `reset the blocks`. Refusals: as in Global Constraints.

- [ ] **Step 1: Write the failing tests** in `BlocksTest` (JUnit 4, seeded `new Random(1)`):
  - `standardTableHasOriginalFiveAndAboutFifteen`: 12 ≤ count ≤ 15; red/blue/yellow/green/bar at their original x,y,yaw; every shape kind present.
  - `barAcrossTwoColumnsRestsOnTop`: two columns 60 mm apart, a 90 mm bar dropped across them (via `addBlock`) has z == 60.
  - `nothingRestsOnARoof`: a cube added over a roof standing on the table has z == 0.
  - `addRandomStopsAtTwenty`: call until count == 20, next returns `ok=false`, text `the table is full (20 blocks)`, count stays 20.
  - `randomiseTwentyHasNoOverlaps`: fill to 20, `randomise()` ok; all z == 0 (or resting per support), no two footprints overlap, all inside the placement area.
  - `resetBlocksKeepsTheArm`: move the pose, `resetBlocks()`; pieces equal the standard table, pose unchanged.
  - `refusedWhileMovingOrHolding`: with `setPose(p, true)` randomise/reset/add → `wait for the arm to stop`; holding a piece (close on red) → randomise/reset → `put the block down first`, add allowed.
  - Every successful change notifies listeners once.
- [ ] **Step 2: Run** `mvn -q -pl lab test -Dtest=BlocksTest` → FAIL (does not compile).
- [ ] **Step 3: Implement.**
  - Support: `supportHeight(Block b)` = max `top()` of supporting blocks whose footprint overlaps b's footprint; overlap by sampling a 5×5 grid of points over b's rotated footprint (corners and edges included) against `footprintContains(px, py, 0)`. Used everywhere a piece comes to rest: `addBlock`, placement, and `opened()` (so a bar the gripper lays across two columns stays up). Add to the tests: `gripperLaysBarAcrossColumns` (pick up a 90 mm bar with real moves, release it over two columns, z == 60).
  - Placement area: x 80..440, y 80..400 (inside arm reach and all three camera views — check with `--preview`). Random free spot: rejection sampling, up to 200 tries per piece, random yaw 0..180°, footprints (grown by 5 mm) must not overlap; on failure for add → `the table is full (20 blocks)`.
  - Random kind for add: uniform over the six; colours from the five existing ones.
  - Standard table: the original five plus about 10 more (2 columns, 2 roofs, 2 arches, 2 cylinders, 1 90 mm bar, 1 cube), clear of the originals, not moved by later tasks.
  - Sizes: column 30×30×60; roof 60×30×30 (ridge along x); arch 90×30×30; cylinder 30 diameter × 30; bars 60 and 90 long.
- [ ] **Step 4: Run** the BlocksTest and `WorldTest` → PASS.
- [ ] **Step 5: Commit** `Lab: six piece kinds, a fuller standard table, add/randomise/reset, spanning support`.

### Task 2: Draw each shape

**Files:**
- Modify: `lab/src/main/java/au/edu/uwa/mech/sim/Scene.java`
- Test: `lab/src/test/java/au/edu/uwa/mech/sim/BlocksTest.java` (add)

**Interfaces:**
- Consumes: `Block.shape`, sizes from Task 1.
- Produces: `Scene.addBlock` handles all six; a `prism(double[][] profile, ...)` helper that extrudes a 2D profile along the block's y axis for ROOF (triangle) and ARCH (rectangle with a half-round cut-out underneath, radius 12 mm, 8 segments); CYLINDER uses the existing `cylinder`.

- [ ] **Step 1: Test** `everyShapeIsDrawn`: for each shape, a world with only that block; `Scene.of(snapshot)` has more triangles than the empty world; the roof's highest vertex z == 30 at the ridge and lowest == 0; the arch has no triangle covering the centre point of its underside below z 12.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement** in `Scene`.
- [ ] **Step 4: Run** → PASS; then `java ... VirtualLab --preview <scratch dir>` and look at all three PNGs: every piece visible, nothing off the table.
- [ ] **Step 5: Commit** `Lab: draw roofs, arches, columns and cylinders`.

### Task 3: Lab admin port and fresh photos

**Files:**
- Modify: `lab/src/main/java/au/edu/uwa/mech/sim/VirtualRobotServer.java`, `lab/src/main/java/au/edu/uwa/mech/sim/VirtualLab.java`, `lab/src/main/java/au/edu/uwa/mech/devices/camera/Server.java` (make `takePhotos()` public)
- Test: `lab/src/test/java/au/edu/uwa/mech/sim/WorldTest.java` (beside `adminPortResetsTheWorld`)

**Interfaces:**
- Consumes: `World.addRandom/randomise/resetBlocks`.
- Produces: admin lines `blocks add`, `blocks randomise`, `blocks reset` → reply `ok <text>` or `error <text>`; `VirtualRobotServer.onBlocksChanged(Runnable r)`, run after every `ok`. `VirtualLab.main` passes `server::takePhotos`. The block change runs under the same `motionLock` as `resetWorld` so it cannot interleave with a move.

- [ ] **Step 1: Test** `adminPortChangesBlocks`: `blocks add` → `ok added a …`, count +1, callback ran once; `blocks nonsense` → `error unknown command`; `blocks reset` → `ok reset the blocks`.
- [ ] **Step 2: Run** → FAIL.  **Step 3: Implement.**  **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `Lab: blocks commands on the admin port; re-shoot the cameras after a change`.

### Task 4: Gateway message, permission and broadcast

**Files:**
- Modify: `gateway/.../web/In.java` (`record Blocks(String action)` with `action` required and one of `add|randomise|reset`, else BadMessage), `Protocol.java` (TYPES `"blocks"`), `Limits.java` (bucket `"blocks"`: burst 3, 1 a second), `Session.java` (`case In.Blocks b -> blocks(b)`), `SessionRegistry.java` (`void blocks(Session from, String action)` and a shared `labAdmin(String line)` used by `resetLab()` too)
- Test: `gateway/.../web/ProtocolTest.java`, `gateway/.../web/BridgeIT.java`

**Interfaces:**
- Consumes: the admin port lines from Task 3.
- Produces: `Session.blocks`: allowed if `nick.equals(b.controllerNick())` or level ADMIN, else `reject("not-in-control", "take control of the robot first")`; then runs `registry.blocks(this, action)` on the session queue. Registry: reply `ok X` → `broadcast(new Out.Console(from.nick() + " " + X))`; `error X` → `from.console(X)`; unreachable → `from.send(new Out.Error("lab-unavailable", "the lab did not answer"))`. No new `Out` type.

- [ ] **Step 1: Tests.** ProtocolTest: `{"t":"blocks","action":"add"}` parses; missing or unknown action is BadMessage. BridgeIT (new numbered step after the admin reset): a guest not in control sends Blocks → `not-in-control`; the controller sends `randomise` → every connected fake receives console `"<nick> randomised the table"` and the lab's red block has moved; an admin not in control sends `reset` → console `"D reset the blocks"` and red is back at (120,150).
- [ ] **Step 2: Run** `mvn -q -pl gateway verify -Dit.test=BridgeIT -Dtest=ProtocolTest` → FAIL.  **Step 3: Implement.**  **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `Gateway: blocks message for the controller or an admin, announced to everyone`.

### Task 5: Robot menu items

**Files:**
- Modify: `webui/src/net/protocol.ts` (`| {t:"blocks", action:"add"|"randomise"|"reset"}`), `webui/src/ui/controlPage.ts`
- Test: `webui/test/ui.test.ts`

**Interfaces:**
- Consumes: the `blocks` message.
- Produces: in the Robot menu, `separator`, then `item('Add Block' …)`, `item('Randomise Blocks' …)`, `item('Reset Blocks' …)`, each sending `{t:'blocks', action}`. A private `updateBlockItems()` sets `setEnabled(us || this.level === 'admin')` on all three; called from `welcome` (store `this.level = m.level`) and the `controller` case.

- [ ] **Step 1: Test** `blockItemsFollowControl`: after welcome as guest, the three items are disabled; after `controller` naming us, enabled, and clicking Randomise Blocks sends `{t:'blocks', action:'randomise'}`; after `controller` null, disabled; as admin, enabled without control.
- [ ] **Step 2: Run** `npx vitest run test/ui.test.ts` → FAIL.  **Step 3: Implement.**  **Step 4: Run** → PASS, plus `npm run lint`.
- [ ] **Step 5: Commit** `Web UI: Add, Randomise and Reset Blocks in the Robot menu`.

### Task 6: Whole-branch check and UAT

- [ ] Run `mvn verify` (all modules), webui `npm test`, `npm run lint`, `npm run build`: all pass.
- [ ] Run the dev build (gateway `--dev` with the in-process lab, as the repo's dev scripts do), open it in Chrome, log in as a guest: Robot menu items greyed; take control; Add Block, Randomise Blocks, Reset Blocks each change all three camera images and print the console line; build a bar across two columns with real moves and check it rests on top. Screenshot each camera of the standard table.
- [ ] Merge to main with `--no-ff` only after the branch review; deploying is a separate step that needs Barney's yes.
