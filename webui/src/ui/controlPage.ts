// The TA200001 control page: the menu bar, Robot over Console in the controls
// column, Cameras beside them, Chat along the bottom, and the canvas popup.
// It shows what the gateway sends and turns the user's actions into In messages.

import type { In, Out } from '../net/protocol';
import { Axes } from '../overlays/axes';
import { StickCursorCoords } from '../overlays/coords';
import { Path } from '../overlays/path';
import { PointMeasure } from '../overlays/pointMeasure';
import type { StickCursor } from '../overlays/stickCursor';
import { TapeMeasure } from '../overlays/tapeMeasure';
import type { Overlay3D, Pose } from '../overlays/types';
import { WorkspacePoints, type WorkspacePoint } from '../overlays/workspacePoints';
import { pointAsGBHString, poseAsGBHString, randomState, stateAsGBHString } from '../pathscript';
import { CamerasPanel } from './camerasPanel';
import { ChatPanel } from './chatPanel';
import { ConsolePanel } from './consolePanel';
import { addPointDialog, confirmDialog, deletePointDialog } from './dialogs';
import { borderPanel, h } from './dom';
import { MenuBar } from './menuBar';
import { check, item, PopupMenu, separator } from './popupMenu';
import { RobotPanel } from './robotPanel';

type ImageHeader = Extract<Out, { t: 'image' }>;
type RobotStateMsg = Extract<Out, { t: 'robotState' }>;

/** new Pose3d(250,250,100,0,0): spin 0 rad is -90 in GBH degrees. */
export const HOME: Pose = { x: 250, y: 250, z: 100, spin: -90, tilt: 0 };
/** A fresh RobotState: everything 0, so spin is -90 degrees. */
const NO_STATE: Pose = { x: 0, y: 0, z: 0, spin: -90, tilt: 0 };
export const ACTIVITY_MS = 500; // activity at most 2 a second

export interface ControlPageDeps {
  send: (m: In) => void;
  /** "Really logout?" answered Yes. */
  logout: () => void;
  /** Help > Brief Help. */
  openHelp: () => void;
  /** Help > History. */
  openHistory: () => void;
  now?: () => number;
  /** Decodes a JPEG frame; defaults to createImageBitmap. */
  decode?: (b: Blob) => Promise<ImageBitmap>;
}

export class ControlPage {
  readonly el: HTMLElement;
  readonly menuBar: MenuBar;
  readonly popup: PopupMenu;
  readonly robot: RobotPanel;
  readonly console: ConsolePanel;
  readonly cameras: CamerasPanel;
  readonly chat: ChatPanel;

  readonly path = new Path();
  readonly axes = new Axes();
  readonly tapeMeasure = new TapeMeasure();
  readonly pointMeasure = new PointMeasure();
  readonly workspace = new WorkspacePoints();
  readonly coords: StickCursorCoords;

  private stickPose: Pose = { ...HOME };
  private showingCursor = true;
  private ourNick: string | null = null;
  private controllerNick: string | null = null;
  private lastState: RobotStateMsg | null = null;
  private pointIds = new Map<string, string>();
  private lastActivity = -Infinity;
  private readonly now: () => number;
  private disposed = false;

  constructor(private readonly deps: ControlPageDeps) {
    this.now = deps.now ?? Date.now;
    const println = (s: string) => this.console.println(s);
    this.console = new ConsolePanel(8);
    this.coords = new StickCursorCoords(() => this.stickPose);
    this.robot = new RobotPanel({
      onSend: (script) => this.deps.send({ t: 'command', script }),
      onControl: (take) => this.deps.send(take ? { t: 'takeControl' } : { t: 'release' }),
      println,
    });
    this.chat = new ChatPanel((text) => this.deps.send({ t: 'chat', text }));
    this.cameras = new CamerasPanel({
      onSelect: (camera) => this.deps.send({ t: 'selectCamera', camera }),
      onSettings: (s) => this.deps.send(s),
      onNewImage: (camera) => this.deps.send({ t: 'newImage', camera }),
      onCursor: (pose, from) => this.cursorDragged(pose, from),
      onPopup: (x, y) => this.popup.show(x, y),
      shared: () => this.sharedOverlays(),
      println,
    });
    this.pointMeasure.enabled = false;
    this.tapeMeasure.enabled = false;
    for (const o of [this.path, this.tapeMeasure, this.pointMeasure, this.workspace]) o.onChange = () => this.cameras.invalidate();
    this.pointMeasure.onMessage = println;
    this.tapeMeasure.onMessage = println;

    this.menuBar = this.buildMenus();
    this.popup = this.buildPopup();

    const left = h(
      'div',
      { class: 'controls-column' },
      borderPanel('Robot', 'robot-border', this.robot.el),
      borderPanel('Console', 'console-border', this.console.el),
    );
    const top = h('div', { class: 'control-top' }, left, borderPanel('Cameras', 'cameras-border', this.cameras.el));
    const chatArea = h('div', { class: 'chat-area' }, borderPanel('Chat', 'chat-border', this.chat.el));
    this.el = h('div', { class: 'page control-page' }, this.menuBar.el, h('div', { class: 'control-body' }, top, chatArea), this.popup.el);
  }

  // ---- read-only views (the e2e hook) ----

  nick(): string | null {
    return this.ourNick;
  }

  controller(): string | null {
    return this.controllerNick;
  }

  robotState(): Omit<RobotStateMsg, 't'> | null {
    if (!this.lastState) return null;
    const { x, y, z, spin, tilt, gripperOpen, moving } = this.lastState;
    return { x, y, z, spin, tilt, gripperOpen, moving };
  }

  cursorPose(): Pose {
    return { ...this.stickPose };
  }

  selectedCamera(): string | null {
    return this.cameras.selected;
  }

  println(s: string): void {
    this.console.println(s);
  }

  // ---- gateway messages ----

  handle(m: Out): void {
    switch (m.t) {
      case 'welcome':
        this.welcome(m);
        break;
      case 'robotState':
        this.lastState = { ...m };
        this.robot.newRobotState(m);
        this.path.addPoint({ x: m.x, y: m.y, z: m.z });
        break;
      case 'controller': {
        this.controllerNick = m.nick;
        const us = m.nick !== null && m.nick === this.ourNick;
        this.robot.setController(m.nick, us);
        this.setPopupInControl(us);
        break;
      }
      case 'commandStatus':
        this.robot.commandStatus(m);
        break;
      case 'users':
        this.chat.setUsers(m.nicks);
        break;
      case 'chat':
        this.chat.message(m.nick, m.text);
        break;
      case 'workspace':
        this.setWorkspace(m.points);
        break;
      case 'cameraSettings':
        this.cameras.newSettings(m);
        break;
      case 'archived':
        this.console.println(`Archived ${m.kind} ${m.id}`);
        break;
      case 'console':
        this.console.println(m.text);
        break;
      case 'error':
        this.console.println(m.message);
        break;
      case 'lab':
        if (m.message) this.console.println(m.message);
        break;
      case 'image':
        break; // headers arrive through image()
    }
  }

  /** An image header and its JPEG: decoded, then shown on that camera's canvas. */
  async image(header: ImageHeader, jpeg: Blob): Promise<void> {
    if (!this.cameras.view(header.camera) || header.calibration.length !== 12) return;
    let bmp: ImageBitmap;
    try {
      bmp = await (this.deps.decode ?? ((b: Blob) => createImageBitmap(b)))(jpeg);
    } catch {
      return; // a bad frame is skipped; the next one replaces it
    }
    if (this.disposed) {
      bmp.close(); // logged out while it decoded
      return;
    }
    this.cameras.setImage(header.camera, header.seq, bmp, header.width, header.height, header.calibration);
  }

  /** Connection lost: Control Robot and the chat field wait for the next welcome. */
  disconnected(): void {
    this.robot.setConnected(false);
    this.robot.inControl(false);
    this.setPopupInControl(false);
    this.chat.setJoined(false);
  }

  dispose(): void {
    this.disposed = true;
    this.cameras.dispose();
    this.menuBar.dispose();
    this.popup.dispose();
  }

  private welcome(m: Extract<Out, { t: 'welcome' }>): void {
    this.ourNick = m.nick;
    this.cameras.resetSequence();
    this.robot.resetCommands();
    this.cameras.setCameras(m.cameras, this.stickPose, this.showingCursor);
    this.robot.setConnected(true);
    this.chat.setJoined(true);
    this.chat.setMaxLength(m.limits.chat);
    this.console.println(`Logged in OK as ${m.nick}`);
    // only the selected camera is streamed: (re)select it on every connection
    const cam = this.cameras.selected;
    if (cam) this.deps.send({ t: 'selectCamera', camera: cam });
  }

  private setWorkspace(points: Extract<Out, { t: 'workspace' }>['points']): void {
    this.pointIds.clear();
    const list: WorkspacePoint[] = [];
    for (const p of points) {
      let name = p.label;
      for (let n = 2; this.pointIds.has(name); n++) name = `${p.label}~${n}`;
      this.pointIds.set(name, p.id);
      list.push({ name, x: p.x, y: p.y, z: p.z });
    }
    this.workspace.setPoints(list);
  }

  private sharedOverlays(): Overlay3D[] {
    // TA200001.created: stick cursor (per canvas), path, axes, tape, point, coords, workspace
    return [this.path, this.axes, this.tapeMeasure, this.pointMeasure, this.coords, this.workspace];
  }

  /** TA200001.update: one cursor was dragged; the others follow, and the drag counts as activity. */
  private cursorDragged(pose: Pose, from: StickCursor): void {
    this.stickPose = { ...pose };
    this.moveStickCursor(this.stickPose, from);
    const t = this.now();
    if (t - this.lastActivity >= ACTIVITY_MS) {
      this.lastActivity = t;
      this.deps.send({ t: 'activity' });
    }
  }

  /** moveStickCursor(pose, ignoreStick). */
  private moveStickCursor(pose: Pose, ignore: StickCursor | null): void {
    this.stickPose = { ...pose };
    for (const v of this.cameras.allViews()) if (v.stick !== ignore) v.stick.setPose(pose);
    this.cameras.invalidate();
  }

  private sendScript(script: string): void {
    this.deps.send({ t: 'command', script });
  }

  private selectedPoint(): WorkspacePoint | null {
    const p = this.workspace.selected;
    if (!p) this.console.println('No point selected');
    return p;
  }

  private buildMenus(): MenuBar {
    const mb: MenuBar = new MenuBar([
      { label: 'Connection', items: [item('Logout', () => confirmDialog('Really logout?', () => this.deps.logout()))] },
      {
        label: 'Robot',
        items: [
          item('Generate Random Move', () => this.robot.appendTextCommand(stateAsGBHString(randomState()))),
          item('Comand from cursor', () => this.robot.appendTextCommand(poseAsGBHString(this.stickPose))),
          item('Command from selected point', () => {
            const p = this.selectedPoint();
            if (p) this.robot.appendTextCommand(pointAsGBHString(p));
          }),
          item('Close gripper', () => this.robot.appendTextCommand('gclose')),
          item('Open gripper', () => this.robot.appendTextCommand('gopen')),
        ],
      },
      {
        label: 'Images',
        items: [
          item('Archive Images', () => this.deps.send({ t: 'archiveImages' })),
          separator,
          item('Clear Path', () => this.path.clear()),
          check('Show Cursor', this.showingCursor, (on) => {
            this.showingCursor = on;
            for (const v of this.cameras.allViews()) v.stick.visible = on;
            this.cameras.invalidate();
          }),
          check('Show Axes', true, (on) => {
            this.axes.visible = on;
            this.cameras.invalidate();
          }),
          check('Show Points', true, (on) => this.workspace.setVisible(on)),
        ],
      },
      {
        label: 'Workspace',
        items: [
          item('Add Point', () =>
            addPointDialog((p) => this.deps.send({ t: 'workspaceAdd', x: p.x, y: p.y, z: p.z, label: p.name }), (s) => this.console.println(s)),
          ),
          item('Delete Point', () =>
            deletePointDialog((name) => {
              const id = this.pointIds.get(name);
              if (id) this.deps.send({ t: 'workspaceDelete', id });
              else this.console.println(`No point named ${name}`);
            }, (s) => this.console.println(s)),
          ),
          check('Measure point', false, (checked) => {
            this.pointMeasure.enabled = checked;
            if (checked) {
              this.pointMeasure.start();
              this.tapeMeasure.enabled = false;
              mb.item('Workspace', 'Measure line').setState(false);
            }
            this.cameras.invalidate();
          }),
          check('Measure line', false, (checked) => {
            this.tapeMeasure.enabled = checked;
            if (checked) {
              this.tapeMeasure.start();
              this.pointMeasure.enabled = false;
              mb.item('Workspace', 'Measure point').setState(false);
            }
            this.cameras.invalidate();
          }),
        ],
      },
      {
        label: 'Help',
        items: [item('Brief Help', () => this.deps.openHelp()), item('History', () => this.deps.openHistory())],
      },
    ]);
    return mb;
  }

  /** ThePopUpMenu "Robot <-> Cursor"; the robot moves need control. */
  private buildPopup(): PopupMenu {
    const p = new PopupMenu('Robot <-> Cursor', [
      item('align cursor with robot', () => {
        const s = this.lastState;
        this.moveStickCursor(s ? { x: s.x, y: s.y, z: s.z, spin: s.spin, tilt: s.tilt } : NO_STATE, null);
      }),
      item('set cursor to home', () => this.moveStickCursor(HOME, null)),
      item('set cursor to point', () => {
        const pt = this.selectedPoint();
        if (pt) this.moveStickCursor({ ...this.stickPose, x: pt.x, y: pt.y, z: pt.z }, null);
      }),
      separator,
      item('move robot to cursor', () => this.sendScript(poseAsGBHString(this.stickPose)), false),
      item('move robot to point', () => {
        const pt = this.selectedPoint();
        if (pt) this.sendScript(pointAsGBHString(pt));
      }, false),
      item('open gripper', () => this.sendScript('gopen'), false),
      item('close gripper', () => this.sendScript('gclosed'), false),
    ]);
    return p;
  }

  private setPopupInControl(b: boolean): void {
    for (const label of ['move robot to cursor', 'move robot to point', 'open gripper', 'close gripper']) this.popup.item(label).setEnabled(b);
  }
}
