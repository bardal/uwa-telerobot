// Port of devices/robot/SimpleCommandPanel (with the console lines Client prints
// and the status line CommandList drives): the command editor, Send command,
// Control Robot / Release Robot, Clear, and the Status, Robot Position and
// Controller lines.

import type { Out } from '../net/protocol';
import { formatPose } from '../overlays/coords';
import { button, h } from './dom';

type CommandStatus = Extract<Out, { t: 'commandStatus' }>;
type RobotStateMsg = Extract<Out, { t: 'robotState' }>;

// Command states (Command.NOTSENT .. ERROR) and how the gateway's states map on to them
const SENDING = 1;
const EXECUTING = 2;
const COMPLETED = 3;
const ERROR = 4;
const STATE_OF: Record<CommandStatus['state'], number> = { queued: SENDING, sent: EXECUTING, done: COMPLETED, error: ERROR };

/** Command.stateToString(), padded as in Java. */
export function stateToString(s: number): string {
  switch (s) {
    case 0: return 'Unsent   ';
    case SENDING: return 'Sending  ';
    case EXECUTING: return 'Executing';
    case COMPLETED: return 'Finished ';
    case ERROR: return 'Error    ';
    default: return 'Invalid State';
  }
}

interface Cmd { no: number; state: number }

/**
 * CommandList's bookkeeping. Commands are numbered 1, 2, ... in the order this page
 * first hears of them, as the applet's Command IDs counted its own commands.
 */
export class CommandList {
  private readonly byId = new Map<string, Cmd>();
  private readonly byNo = new Map<number, Cmd>();
  private next = 1;
  private lastSent = 1;
  private lastReceived = 1;
  private currentExecuting = 1;

  /** Applies a status; returns the command's number and whether it was new. */
  update(id: string, state: number): { no: number; isNew: boolean } {
    let c = this.byId.get(id);
    const isNew = !c;
    if (!c) {
      c = { no: this.next++, state: 0 };
      this.byId.set(id, c);
      this.byNo.set(c.no, c);
    }
    c.state = state;
    const sent = state > 0;
    if (c.no > this.lastSent && sent) this.lastSent = c.no;
    if (c.no > this.lastReceived && state > SENDING) this.lastReceived = c.no;
    if (state >= EXECUTING) this.currentExecuting = c.no;
    const cur = this.byNo.get(this.currentExecuting);
    if (cur && cur.state >= COMPLETED && sent) this.currentExecuting = c.no;
    return { no: c.no, isNew };
  }

  backlog(): number {
    return this.lastSent - this.lastReceived;
  }

  current(): Cmd | null {
    return this.byNo.get(this.currentExecuting) ?? null;
  }

  /** The Status line, as SimpleCommandPanel.CommandListener writes it. */
  statusLine(): string | null {
    const c = this.current();
    if (!c) return null;
    const b = this.backlog();
    return `Status: ${stateToString(c.state)} command ${c.no}. ${b} command${b === 1 ? ' ' : 's'} queued`;
  }
}

export interface RobotPanelDeps {
  /** Send command: the editor text as one script. */
  onSend: (script: string) => void;
  /** Control Robot (true) or Release Robot (false). */
  onControl: (take: boolean) => void;
  /** TheConsole.println. */
  println: (text: string) => void;
}

/** RobotState.toDisplayString(): exeState, Pose3d.toDisplayString(), gripper. */
export function robotStateDisplay(s: RobotStateMsg): string {
  return `${s.moving ? 'Moving' : 'Stopped'} ${formatPose(s)} gripper ${s.gripperOpen ? 'open' : 'closed'}`;
}

export class RobotPanel {
  readonly el: HTMLElement;
  readonly editor: HTMLTextAreaElement;
  readonly bSend: HTMLButtonElement;
  readonly bControl: HTMLButtonElement;
  readonly bClear: HTMLButtonElement;
  readonly lCommandStatus: HTMLElement;
  readonly lRobotPosition: HTMLElement;
  readonly lController: HTMLElement;
  private inControlNow = false;
  private commands = new CommandList();

  constructor(private readonly deps: RobotPanelDeps) {
    this.editor = h('textarea', { class: 'command-editor', rows: 5, spellcheck: 'false', 'aria-label': 'Command editor' });
    this.bSend = button('Send command', () => this.deps.onSend(this.editor.value));
    this.bControl = button('Control Robot', () => this.deps.onControl(!this.inControlNow));
    this.bClear = button('Clear', () => { this.editor.value = ''; });
    this.lCommandStatus = h('div', { class: 'awt-label status-line', text: 'Command Status:' });
    this.lRobotPosition = h('div', { class: 'awt-label position-line', text: 'Robot Position :' });
    this.lController = h('div', { class: 'awt-label controller-line', text: 'Robot Controller :' });
    this.el = h(
      'div',
      { class: 'command-panel' },
      h('div', { class: 'awt-label editor-label', text: 'Command editor' }),
      this.editor,
      h('div', { class: 'button-row' }, this.bSend, this.bControl, this.bClear),
      h('div', { class: 'robot-lines' }, this.lCommandStatus, this.lRobotPosition, this.lController),
    );
    this.inControl(false);
    this.bControl.disabled = true;
  }

  get controlling(): boolean {
    return this.inControlNow;
  }

  /** CommandEditor.appendTextCommand: TextArea.append, no separator added. */
  appendTextCommand(command: string): void {
    this.editor.value += command;
    this.editor.scrollTop = this.editor.scrollHeight;
  }

  /** setConnected: Control Robot is usable once the robot contexts are joined. */
  setConnected(b: boolean): void {
    this.bControl.disabled = !b;
  }

  newRobotState(s: RobotStateMsg): void {
    this.lRobotPosition.textContent = `Robot Position :${robotStateDisplay(s)}`;
  }

  /** newController / noController. */
  setController(nick: string | null, us: boolean): void {
    if (nick === null) {
      this.inControl(false);
      this.deps.println('The robot is free');
      this.lController.textContent = 'No Controller';
    } else {
      this.lController.textContent = `Controller : ${nick}`;
      this.deps.println(`New controller of the robot : ${nick}`);
      this.inControl(us);
    }
  }

  inControl(b: boolean): void {
    this.bSend.disabled = !b;
    this.inControlNow = b;
    this.bControl.textContent = b ? 'Release Robot' : 'Control Robot';
  }

  /** Client's console lines and the CommandList status line. */
  commandStatus(m: CommandStatus): void {
    const state = STATE_OF[m.state];
    if (state === undefined) return;
    const { no, isNew } = this.commands.update(m.id, state);
    if (isNew) this.deps.println(`Sending command ${no} ${m.script}`);
    if (m.state === 'done') this.deps.println(`Command ${no} completed successfully`);
    else if (m.state === 'error') this.deps.println(`Command ${no} had a problem :${m.text}`);
    const line = this.commands.statusLine();
    if (line) this.lCommandStatus.textContent = line;
  }
}
