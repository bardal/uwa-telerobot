# More blocks on the table: design

Date: 2026-10-26. Status: approved by Barney in chat ("build it").

## Intent

The simulated table has five plain blocks (four 30 mm cubes and one 60 mm bar). Visitors in 1994-2004
built towers, bridges, archways, houses with roofs and columns with lintels (the hall of fame gallery,
`webui/src/content/gallery.json`). The table should offer enough pieces, and the right shapes, to build
those again, and the person driving should be able to add pieces, scatter them, or put the table back.

## Goals

1. A fuller standard table: about 12-15 pieces of six kinds, in the existing colours
   (red, blue, yellow, green, wood).
2. Six piece kinds: **cube** (30 mm, as now), **bar** (60 and 90 mm long, 30x30 section), **column**
   (30x30, 60 mm high), **roof** (triangular prism), **arch** (a 90x30x30 block with a half-round cut-out
   underneath), **cylinder** (round, about 30 mm across and 30 mm high).
3. Three new commands in the applet (2000) interface's **Robot** menu, below a separator:
   **Add Block**, **Randomise Blocks**, **Reset Blocks**.
   - **Add Block**: one random piece, laid flat in a free spot on the table. Cap of 20 pieces; at the
     cap, nothing is added and the console says the table is full.
   - **Randomise Blocks**: every piece is laid flat at a random free spot and random rotation, none
     overlapping, all inside the area the arm and the cameras can reach.
   - **Reset Blocks**: the standard table. The arm is not moved (unlike the admin "reset", which also
     homes the arm).
4. Only the person in control of the robot can use them; admins always can. The menu items are greyed
   out for everyone else.
5. Every use prints a line in everyone's console, e.g. `uat randomised the table`,
   `uat added a roof`, `uat reset the blocks`.
6. Everyone's camera images show the new table straight away, without pressing New Image.
7. Pieces can span: a piece rests on the highest surface anywhere under its base, so a bar or an
   arch laid across two columns stays up.

## Non-goals

- No toppling, sliding or collapse. Pieces rest where they are dropped, as now.
- The forms (1996) interface gets no block buttons.
- No block with a hole, and no peg-in-hole fit (Barney did not pick it).
- The table is not kept when the lab sleeps: a woken lab starts from the standard table.
- Moves, snapshots and chat are archived as now; block commands are not archived.

## Anti-goals

- A guest who is only watching must not be able to wreck someone's build.
- Nothing may break the existing pick-and-place: the smoke test's move `x330,y120,z90` and the
  gateway's BridgeIT pick-and-place must still pass. Keep the blue cube at (330, 120) in the standard
  table, and keep the other four original pieces where they are, so the tests and the calibrated look
  are unchanged.
- No wall-clock waits or sleeps in tests (repo rule).

## Decisions taken with Barney

- Shapes: cube, bar, column, roof, arch, cylinder.
- Placement: the Robot menu, not a row of buttons.
- Who: the controller, plus admins; a console line for everyone.
- Reset Blocks is included.

## Calls made by Claude (Barney can overrule)

- Support uses the whole base (goal 7). Today only the point under the centre counts.
- Nothing can be stacked on a roof: a roof's top does not count as a support surface, so a piece
  dropped onto a roof falls past it to whatever else is underneath. (Simplest honest behaviour without
  real physics.)
- An arch is a box as far as support and gripping go; only its picture has the cut-out. So a piece
  dropped where an arch stands lands on top of the arch, never inside the opening.
- A cylinder grips like a box whose width is its diameter, at any spin.
- Pieces are picked up as now: the gripper closes across whatever width lies under the tool point; a
  90 mm piece can only be gripped across its 30 mm side.
- If the arm is moving or holding a piece, Randomise and Reset are refused with a console message
  ("wait for the arm to stop" / "put the block down first"). Add is allowed when holding, refused while
  moving.
- Random placement uses a seeded `java.util.Random` that tests can inject.

## How it fits the existing code (for the builder)

- **Lab** (`lab/.../sim`): `Block` gets a `shape` (enum). `World` gets `addRandom`, `randomise`,
  `resetBlocks` (all synchronized, all `fire()` listeners), a cap, and footprint-based
  `supportHeight`. `Scene.addBlock` draws each shape (it already has `box` and `cylinder` helpers;
  roof and arch need triangle-mesh prisms). The standard table lives in `addDefaultBlocks`.
- **Lab admin port** (`VirtualRobotServer.serveAdminClient`): today only `reset`. Add
  `blocks add`, `blocks randomise`, `blocks reset`, answering `ok <text>` (the text says what happened,
  e.g. the kind added) or `error <reason>`.
- **Gateway**: a new browser message `{t:"blocks", action:"add"|"randomise"|"reset"}` (`In.Blocks`,
  `Protocol` TYPES table, rate-limit bucket). `Session` checks controller-or-admin exactly as `command`
  checks control, then the registry talks to the lab admin port like `resetLab()` does, broadcasts an
  `Out.Console` line to every session on success. Refusals go only to the person who asked.
- **Fresh images come from the lab, not the gateway.** The camera `Server` refuses a photo request
  from anyone but the controller while someone holds control, so an admin's request would fail. Instead,
  after a successful block command the lab calls the camera server's existing `takePhotos()` (all
  cameras, published to every viewer). `VirtualLab` wires the robot server's admin handler to it.
- Columns and cylinders stand upright when "laid flat" (blocks only rotate about z). The reachable,
  visible area comes from the arm's limits and the three camera views.
- **Web UI** (`webui/src/ui/controlPage.ts`): three items under a separator at the end of the Robot
  menu, enabled only while this user is the controller or an admin (use the menu item state support
  already used for Workspace checks), sending the new message. `webui/src/net/protocol.ts` gets the type.
- **Dev mode**: `InProcessLab` runs the same VirtualLab, so the feature works locally for checking.

## Testing

- Lab unit tests: each shape renders (Scene produces triangles for it); support across two columns;
  nothing rests on a roof; randomise gives no overlaps and stays in bounds; add stops at the cap;
  reset restores the standard table without moving the arm; refusals while moving or holding.
- Admin port test for the three new lines.
- Gateway tests: a non-controller guest is refused (`not-in-control`), the controller and an admin are
  accepted, and every session gets the console line.
- Web UI test: the items are disabled unless in control, and send the message.
- Run the existing suites (`mvn verify`, webui `npm test`, lint) and the `--preview` render to eyeball
  each camera with the new standard table.
- Before handover: click through it in the dev build in a browser (UAT), with a screenshot of the new
  table from each camera.

## Left to the builder

Exact sizes and colours of the extra pieces and where they stand on the standard table (clear of the
original five, inside reach, visible to all three cameras); the random-placement algorithm (rejection
sampling is fine); mesh details for the roof and arch.
