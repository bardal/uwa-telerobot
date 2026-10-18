// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { In, Out } from '../src/net/protocol';
import { ChatPanel } from '../src/ui/chatPanel';
import { ControlPage, HOME } from '../src/ui/controlPage';
import { LoginPage, HELP_TEXT } from '../src/ui/loginPage';
import { CAPPED, DALTON_THESIS, EXPECT, FACTS, FACT_MS, SLOW, WAKING, WIKIPEDIA, WakingPage, progressAt } from '../src/ui/wakingPage';

const ctxStub = new Proxy({}, { get: () => () => undefined, set: () => true }) as unknown as CanvasRenderingContext2D;

beforeEach(() => {
  document.body.replaceChildren();
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(ctxStub as never);
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      disconnect() {}
      unobserve() {}
    },
  );
  vi.stubGlobal('devicePixelRatio', 1);
});

const welcome: Out = {
  t: 'welcome',
  nick: 'ann~2',
  level: 'guest',
  cameras: ['Colour', 'YCam', 'ZCam'],
  commit: 'abc',
  limits: { script: 400, waypoints: 30, chat: 300 },
};

function page() {
  const sent: In[] = [];
  const logout = vi.fn();
  const p = new ControlPage({ send: (m) => sent.push(m), logout, openHelp: vi.fn(), openHistory: vi.fn(), now: () => 0 });
  document.body.appendChild(p.el);
  return { p, sent, logout };
}

const menuLabels = (root: HTMLElement, menu: string): string[] => {
  const title = [...root.querySelectorAll('.menu-title')].find((t) => t.textContent === menu)!;
  const list = title.parentElement!.querySelector('.menu-list')!;
  return [...list.querySelectorAll('.menu-label')].map((x) => x.textContent ?? '');
};

const menuItem = (root: HTMLElement, menu: string, label: string): HTMLElement => {
  const title = [...root.querySelectorAll('.menu-title')].find((t) => t.textContent === menu)!;
  return [...title.parentElement!.querySelectorAll('.menu-item')].find((x) => x.querySelector('.menu-label')!.textContent === label) as HTMLElement;
};

describe('chat', () => {
  it('renders <b>x</b> literally', () => {
    const c = new ChatPanel(vi.fn());
    document.body.appendChild(c.el);
    c.message('ann', '<b>x</b>');
    expect(c.receiveArea.value).toBe('ann: <b>x</b>\n');
    expect(c.el.querySelector('b')).toBeNull();
    c.setUsers(['<i>bob</i>', 'ann']);
    expect(c.users()).toEqual(['<i>bob</i>', 'ann']);
    expect(c.el.querySelector('i')).toBeNull();
  });

  it('sends on Enter once joined and keeps the text selected', () => {
    const onSend = vi.fn();
    const c = new ChatPanel(onSend);
    c.sendField.value = 'hi';
    c.sendField.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    expect(onSend).not.toHaveBeenCalled();
    c.setJoined(true);
    c.sendField.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    expect(onSend).toHaveBeenCalledWith('hi');
    expect(c.sendField.value).toBe('hi');
  });

  it('control page chat messages and users are text', () => {
    const { p } = page();
    p.handle({ t: 'chat', id: '1', nick: '<script>', text: '<b>x</b>', time: 1 });
    expect(p.chat.receiveArea.value).toBe('<script>: <b>x</b>\n');
    expect(p.el.querySelector('script, b')).toBeNull();
  });
});

describe('robot panel', () => {
  it('Send command is disabled when not in control', () => {
    const { p, sent } = page();
    expect(p.robot.bSend.disabled).toBe(true);
    expect(p.robot.bControl.textContent).toBe('Control Robot');
    p.handle(welcome);
    p.handle({ t: 'controller', nick: 'bob' });
    expect(p.robot.bSend.disabled).toBe(true);
    expect(p.robot.lController.textContent).toBe('Controller : bob');
    p.handle({ t: 'controller', nick: 'ann~2' });
    expect(p.robot.bSend.disabled).toBe(false);
    expect(p.robot.bControl.textContent).toBe('Release Robot');
    p.handle({ t: 'controller', nick: null });
    expect(p.robot.bSend.disabled).toBe(true);
    expect(p.robot.lController.textContent).toBe('No Controller');
    expect(p.console.text()).toContain('New controller of the robot : bob\n');
    expect(p.console.text()).toContain('The robot is free\n');
    p.robot.bSend.click();
    expect(sent.filter((m) => m.t === 'command')).toEqual([]);
  });

  it('Control Robot and Send command send their messages', () => {
    const { p, sent } = page();
    p.handle(welcome);
    p.robot.bControl.click();
    expect(sent.at(-1)).toEqual({ t: 'takeControl' });
    p.handle({ t: 'controller', nick: 'ann~2' });
    p.robot.editor.value = 'x120,y150,z60;z15';
    p.robot.bSend.click();
    expect(sent.at(-1)).toEqual({ t: 'command', script: 'x120,y150,z60;z15' });
    p.robot.bControl.click();
    expect(sent.at(-1)).toEqual({ t: 'release' });
    p.robot.bClear.click();
    expect(p.robot.editor.value).toBe('');
  });

  it('formats the Robot Position line as Java', () => {
    const { p } = page();
    expect(p.robot.lRobotPosition.textContent).toBe('Robot Position :');
    p.handle({ t: 'robotState', x: 330, y: 120, z: 90, spin: -90, tilt: 0, gripperOpen: true, moving: false });
    expect(p.robot.lRobotPosition.textContent).toBe('Robot Position :Stopped x=330 y=120 z=90 s=-90° t=0° gripper open');
    expect(p.path.nodes).toEqual([{ x: 330, y: 120, z: 90 }]);
  });

  it('writes the Status line and Console lines as SimpleCommandPanel and Client', () => {
    const { p } = page();
    const st = (id: string, state: 'queued' | 'sent' | 'done' | 'error', text = '') =>
      p.handle({ t: 'commandStatus', id, state, script: 'x1,y2', text });
    st('a', 'queued');
    expect(p.robot.lCommandStatus.textContent).toBe('Status: Sending   command 1. 0 commands queued');
    st('a', 'sent');
    expect(p.robot.lCommandStatus.textContent).toBe('Status: Executing command 1. 0 commands queued');
    st('b', 'queued');
    expect(p.robot.lCommandStatus.textContent).toBe('Status: Executing command 1. 1 command  queued');
    st('a', 'done');
    st('b', 'sent');
    st('b', 'done');
    expect(p.robot.lCommandStatus.textContent).toBe('Status: Finished  command 2. 0 commands queued');
    st('c', 'queued');
    st('c', 'error', '4 2 "out of reach"');
    expect(p.console.text()).toBe(
      [
        'Sending command 1 x1,y2',
        'Sending command 2 x1,y2',
        'Command 1 completed successfully',
        'Command 2 completed successfully',
        'Sending command 3 x1,y2',
        'Command 3 had a problem :4 2 "out of reach"',
        '',
      ].join('\n'),
    );
  });
});

describe('menus', () => {
  it('has the TA200001 menu bar', () => {
    const { p } = page();
    expect(p.menuBar.labels()).toEqual(['Connection', 'Robot', 'Images', 'Workspace', 'Help']);
    expect(menuLabels(p.el, 'Connection')).toEqual(['Logout']);
    expect(menuLabels(p.el, 'Images')).toEqual(['Archive Images', 'Clear Path', 'Show Cursor', 'Show Axes', 'Show Points']);
    expect(menuLabels(p.el, 'Workspace')).toEqual(['Add Point', 'Delete Point', 'Measure point', 'Measure line']);
    expect(menuLabels(p.el, 'Help')[0]).toBe('Brief Help');
  });

  it('the Robot menu has the 5 items in order', () => {
    const { p } = page();
    expect(menuLabels(p.el, 'Robot')).toEqual([
      'Generate Random Move',
      'Comand from cursor',
      'Command from selected point',
      'Close gripper',
      'Open gripper',
    ]);
  });

  it('Robot menu items append to the command editor', () => {
    const { p } = page();
    menuItem(p.el, 'Robot', 'Comand from cursor').click();
    expect(p.robot.editor.value).toBe('x250,y250,s-90,t0,z100');
    menuItem(p.el, 'Robot', 'Close gripper').click();
    menuItem(p.el, 'Robot', 'Open gripper').click();
    expect(p.robot.editor.value).toBe('x250,y250,s-90,t0,z100gclosegopen');
    p.robot.editor.value = '';
    menuItem(p.el, 'Robot', 'Generate Random Move').click();
    expect(p.robot.editor.value).toMatch(/^x\d+,y\d+,s-?\d+,t-?\d+,z\d+,gclosed$/);
    menuItem(p.el, 'Robot', 'Command from selected point').click();
    expect(p.console.text()).toContain('No point selected\n');
  });

  it('opens a menu on click and closes it after a choice', () => {
    const { p } = page();
    const title = [...p.el.querySelectorAll('.menu-title')].find((t) => t.textContent === 'Robot') as HTMLElement;
    const list = title.parentElement!.querySelector('.menu-list') as HTMLElement;
    expect(list.hidden).toBe(true);
    title.click();
    expect(list.hidden).toBe(false);
    menuItem(p.el, 'Robot', 'Open gripper').click();
    expect(list.hidden).toBe(true);
  });

  it('Measure point and Measure line exclude each other', () => {
    const { p } = page();
    menuItem(p.el, 'Workspace', 'Measure point').click();
    expect(p.pointMeasure.enabled).toBe(true);
    expect(p.console.text()).toContain('Click on the point to measure');
    menuItem(p.el, 'Workspace', 'Measure line').click();
    expect(p.tapeMeasure.enabled).toBe(true);
    expect(p.pointMeasure.enabled).toBe(false);
    expect(menuItem(p.el, 'Workspace', 'Measure point').getAttribute('aria-checked')).toBe('false');
    expect(menuItem(p.el, 'Workspace', 'Measure line').getAttribute('aria-checked')).toBe('true');
  });

  it('Logout asks first', () => {
    const { p, logout } = page();
    menuItem(p.el, 'Connection', 'Logout').click();
    const dlg = document.querySelector('.awt-dialog')!;
    expect(dlg.textContent).toContain('Really logout?');
    ([...dlg.querySelectorAll('button')].find((b) => b.textContent === 'Yes') as HTMLButtonElement).click();
    expect(logout).toHaveBeenCalledOnce();
    expect(document.querySelector('.awt-dialog')).toBeNull();
  });

  describe('Admin menu', () => {
    const adminTitle = (root: HTMLElement) =>
      [...root.querySelectorAll('.menu-title')].find((t) => t.textContent === 'Admin') as HTMLElement;
    const button = (label: string) =>
      [...document.querySelectorAll('.awt-dialog button')].find((b) => b.textContent === label) as HTMLButtonElement;

    it('is hidden before welcome and for guests and registered users', () => {
      for (const level of ['guest', 'registered'] as const) {
        const { p } = page();
        expect(p.menuBar.labels()).not.toContain('Admin');
        expect(adminTitle(p.el).closest('.menu')!.hasAttribute('hidden')).toBe(true);
        p.handle({ ...welcome, level } as Out);
        expect(p.menuBar.labels(), level).toEqual(['Connection', 'Robot', 'Images', 'Workspace', 'Help']);
        expect(adminTitle(p.el).closest('.menu')!.hasAttribute('hidden')).toBe(true);
        p.dispose();
        document.body.replaceChildren();
      }
    });

    it('shows for an admin, and goes again if a later welcome is not an admin', () => {
      const { p } = page();
      p.handle({ ...welcome, level: 'admin' } as Out);
      expect(p.menuBar.labels()).toEqual(['Connection', 'Robot', 'Images', 'Workspace', 'Help', 'Admin']);
      expect(menuLabels(p.el, 'Admin')).toEqual(['Kick user…', 'Ban user…', 'Reset lab']);
      p.handle({ ...welcome, level: 'guest' } as Out);
      expect(p.menuBar.labels()).not.toContain('Admin');
    });

    it('Kick and Ban ask for a nick in a page dialog and send it; a bad nick is refused in the dialog', () => {
      const { p, sent } = page();
      const prompt = vi.fn();
      vi.stubGlobal('prompt', prompt); // never used: the dialog is in the page (CSP-safe, no window.prompt)
      p.handle({ ...welcome, level: 'admin' } as Out);
      sent.length = 0;

      menuItem(p.el, 'Admin', 'Kick user…').click();
      const field = document.querySelector('.awt-dialog input[aria-label="nickname of user"]') as HTMLInputElement;
      expect(document.querySelector('.awt-dialog')!.textContent).toContain('Kick user');
      field.value = '<script>';
      button('Kick').click();
      expect(sent).toEqual([]);
      expect(document.querySelector('.awt-dialog [role="alert"]')!.textContent).toMatch(/1 to 20/);
      field.value = '  ann~2 ';
      button('Kick').click();
      expect(sent).toEqual([{ t: 'admin', action: 'kick', nick: 'ann~2' }]);
      expect(document.querySelector('.awt-dialog')).toBeNull();

      menuItem(p.el, 'Admin', 'Ban user…').click();
      (document.querySelector('.awt-dialog input') as HTMLInputElement).value = 'bob the builder';
      button('Ban').click();
      expect(sent.at(-1)).toEqual({ t: 'admin', action: 'ban', nick: 'bob the builder' });

      menuItem(p.el, 'Admin', 'Ban user…').click();
      (document.querySelector('.awt-dialog input') as HTMLInputElement).value = 'a'.repeat(21);
      button('Ban').click();
      expect(sent).toHaveLength(2);
      button('Cancel').click();
      expect(document.querySelector('.awt-dialog')).toBeNull();
      expect(sent).toHaveLength(2);
      expect(prompt).not.toHaveBeenCalled();
    });

    it('Reset lab asks first, then sends reset; the results are console lines', () => {
      const { p, sent } = page();
      p.handle({ ...welcome, level: 'admin' } as Out);
      sent.length = 0;
      menuItem(p.el, 'Admin', 'Reset lab').click();
      expect(document.querySelector('.awt-dialog')!.textContent).toContain('Really reset the lab?');
      button('No').click();
      expect(sent).toEqual([]);
      menuItem(p.el, 'Admin', 'Reset lab').click();
      button('Yes').click();
      expect(sent).toEqual([{ t: 'admin', action: 'reset' }]);
      p.handle({ t: 'console', text: 'The lab has been reset' });
      p.handle({ t: 'error', code: 'bad-message', message: 'no such user' });
      expect(p.console.text()).toContain('The lab has been reset\nno such user\n');
    });
  });

  it('the popup robot moves are enabled only in control', () => {
    const { p, sent } = page();
    expect(p.popup.title).toBe('Robot <-> Cursor');
    expect(p.popup.items().map((i) => i.label)).toEqual([
      'align cursor with robot',
      'set cursor to home',
      'set cursor to point',
      'move robot to cursor',
      'move robot to point',
      'open gripper',
      'close gripper',
    ]);
    const move = p.popup.item('move robot to cursor');
    expect(move.enabled).toBe(false);
    p.handle(welcome);
    p.handle({ t: 'controller', nick: 'ann~2' });
    expect(move.enabled).toBe(true);
    move.el.click();
    expect(sent.at(-1)).toEqual({ t: 'command', script: 'x250,y250,s-90,t0,z100' });
    p.popup.item('close gripper').el.click();
    expect(sent.at(-1)).toEqual({ t: 'command', script: 'gclosed' });
    p.handle({ t: 'robotState', x: 10, y: 20, z: 30, spin: 5, tilt: 6, gripperOpen: false, moving: true });
    p.popup.item('align cursor with robot').el.click();
    expect(p.cursorPose()).toEqual({ x: 10, y: 20, z: 30, spin: 5, tilt: 6 });
    p.popup.item('set cursor to home').el.click();
    expect(p.cursorPose()).toEqual(HOME);
  });
});

describe('review fixes (control page)', () => {
  it('a new welcome resets the command list, so a reused gateway id is a new command', () => {
    const { p } = page();
    p.handle(welcome);
    p.handle({ t: 'commandStatus', id: '1', state: 'queued', script: 'x1', text: '' });
    p.handle({ t: 'commandStatus', id: '1', state: 'sent', script: 'x1', text: '' });
    p.handle(welcome); // the lab restarted; the gateway numbers from 1 again
    p.handle({ t: 'commandStatus', id: '1', state: 'queued', script: 'x2', text: '' });
    expect(p.console.text()).toContain('Sending command 2 x2\n');
    expect(p.robot.lCommandStatus.textContent).toBe('Status: Sending   command 2. 0 commands queued');
    p.handle({ t: 'commandStatus', id: '1', state: 'done', script: 'x2', text: '' });
    expect(p.robot.lCommandStatus.textContent).toBe('Status: Finished  command 2. 0 commands queued');
  });

  it('a frame decoded after dispose is closed, not drawn', async () => {
    let done: (b: ImageBitmap) => void = () => undefined;
    const close = vi.fn();
    const p = new ControlPage({ send: vi.fn(), logout: vi.fn(), openHelp: vi.fn(), openHistory: vi.fn(), decode: () => new Promise((r) => { done = r; }) });
    p.handle(welcome);
    const shown = p.image({ t: 'image', camera: 'Colour', seq: 1, width: 4, height: 3, calibration: [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 1] }, new Blob([]));
    p.dispose();
    done({ width: 4, height: 3, close } as unknown as ImageBitmap);
    await shown;
    expect(close).toHaveBeenCalledOnce();
    expect(p.cameras.cameraMatrix('Colour')).toBeNull();
  });
});

describe('cameras', () => {
  it('makes a button and canvas per camera and selects on press', () => {
    const { p, sent } = page();
    p.handle(welcome);
    const buttons = [...p.el.querySelectorAll('.camera-buttons button')].map((b) => b.textContent);
    expect(buttons).toEqual(['Colour', 'YCam', 'ZCam']);
    expect(p.el.querySelectorAll('canvas.camera-canvas')).toHaveLength(3);
    expect(sent).toContainEqual({ t: 'selectCamera', camera: 'Colour' });
    (p.el.querySelectorAll('.camera-buttons button')[1] as HTMLButtonElement).click();
    expect(sent.at(-1)).toEqual({ t: 'selectCamera', camera: 'YCam' });
    expect(p.selectedCamera()).toBe('YCam');
    const labels = [...p.el.querySelectorAll('.camera-card:not([hidden]) .camera-controls .awt-label')].map((l) => l.textContent);
    expect(labels).toEqual(['on', 'width', 'height', 'zoom', 'quality', 'fixed']);
  });

  it('fills the settings row and sends Change Settings limited as Java', () => {
    const { p, sent } = page();
    p.handle(welcome);
    p.handle({ t: 'cameraSettings', camera: 'Colour', on: true, width: 400, height: 300, zoom: 1, quality: 20, fixed: true });
    expect(p.console.text()).toContain('New settings for camera Colour\n');
    const card = p.cameras.view('Colour')!;
    expect(card.controls.widthField.el.value).toBe('400');
    expect(card.controls.zoomField.el.value).toBe('1.0');
    card.controls.qualityField.el.value = '90';
    card.controls.changeSettings.click();
    expect(sent.at(-1)).toEqual({ t: 'cameraSettings', camera: 'Colour', on: true, width: 400, height: 300, zoom: 1, quality: 50, fixed: true });
    card.controls.shutter.click();
    expect(sent.at(-1)).toEqual({ t: 'newImage', camera: 'Colour' });
  });

  it('right-click opens the popup and never the browser menu, even when it lands on the popup', () => {
    const { p } = page();
    p.handle(welcome);
    const wrap = p.el.querySelector('.camera-card:not([hidden]) .canvas-wrap') as HTMLElement;
    wrap.dispatchEvent(new MouseEvent('pointerdown', { button: 2, clientX: 20, clientY: 30, bubbles: true, cancelable: true }));
    expect(p.popup.visible).toBe(true);
    // Windows fires contextmenu on release, at the same point: by then the popup is under the pointer
    for (const target of [p.popup.el, p.popup.el.querySelector('.menu-item') as HTMLElement, wrap]) {
      const e = new MouseEvent('contextmenu', { bubbles: true, cancelable: true });
      target.dispatchEvent(e);
      expect(e.defaultPrevented).toBe(true);
    }
  });

  it('Ctrl-click (the Mac popup trigger) opens the popup and does not reach the canvas', () => {
    const { p } = page();
    p.handle(welcome);
    const wrap = p.el.querySelector('.camera-card:not([hidden]) .canvas-wrap') as HTMLElement;
    const canvas = wrap.querySelector('canvas') as HTMLCanvasElement;
    const reached = vi.fn();
    canvas.addEventListener('pointerdown', reached);
    canvas.dispatchEvent(new MouseEvent('pointerdown', { button: 0, ctrlKey: true, clientX: 5, clientY: 5, bubbles: true, cancelable: true }));
    expect(p.popup.visible).toBe(true);
    expect(reached).not.toHaveBeenCalled();
  });

  it('shows a decoded frame and reports the display matrix', async () => {
    const decode = vi.fn(async () => ({ width: 4, height: 3, close() {} }) as unknown as ImageBitmap);
    const p = new ControlPage({ send: vi.fn(), logout: vi.fn(), openHelp: vi.fn(), openHistory: vi.fn(), decode });
    p.handle(welcome);
    expect(p.cameras.cameraMatrix('Colour')).toBeNull();
    const cal = [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 1];
    await p.image({ t: 'image', camera: 'Colour', seq: 1, width: 4, height: 3, calibration: cal }, new Blob([]));
    expect(decode).toHaveBeenCalledOnce();
    expect(p.cameras.cameraMatrix('Colour')).toHaveLength(12);
  });
});

describe('login page', () => {
  it('requires a nickname and shows the applet help text', () => {
    const onGuest = vi.fn();
    const l = new LoginPage({ nick: '', signInAvailable: false, dev: false, onGuest, onSignIn: vi.fn(), onDev: vi.fn() });
    document.body.appendChild(l.el);
    expect((l.el.querySelector('.login-help') as HTMLTextAreaElement).value).toBe(HELP_TEXT);
    const guest = [...l.el.querySelectorAll('button')].find((b) => b.textContent === 'Guest')!;
    guest.click();
    expect(onGuest).not.toHaveBeenCalled();
    expect(l.console.text()).toContain('Please type a nickname');
    l.nicknameField.value = '<script>';
    guest.click();
    expect(onGuest).not.toHaveBeenCalled();
    l.nicknameField.value = ' Ann B. ';
    guest.click();
    expect(onGuest).toHaveBeenCalledWith('Ann B.');
    const signIn = [...l.el.querySelectorAll('button')].find((b) => b.textContent === 'Sign in')!;
    expect(signIn.disabled).toBe(true);
    expect(l.el.querySelector('a.history-link')!.getAttribute('href')).toBe('#/history');
    expect(l.el.textContent).toContain('public');
  });

  it('offers the dev sign-in in dev builds', () => {
    const onDev = vi.fn();
    const l = new LoginPage({ nick: 'dee', signInAvailable: true, dev: true, onGuest: vi.fn(), onSignIn: vi.fn(), onDev });
    (l.el.querySelector('input[aria-label="admin"]') as HTMLInputElement).checked = true;
    [...l.el.querySelectorAll('button')].find((b) => b.textContent === 'Dev sign in')!.click();
    expect(onDev).toHaveBeenCalledWith('dee', true);
  });
});

describe('waking page', () => {
  it('shows elapsed seconds, the cap and errors with Retry', () => {
    const t = 10_000;
    const retry = vi.fn();
    const back = vi.fn();
    const w = new WakingPage(retry, back, () => t);
    w.show({ kind: 'starting', since: 7_000, step: 'computer' });
    expect(w.heading.textContent).toBe(WAKING);
    expect(w.detail.textContent).toBe('3 s');
    w.show({ kind: 'capped' });
    expect(w.heading.textContent).toBe(CAPPED);
    expect(w.retry.hidden).toBe(true);
    w.show({ kind: 'error', message: 'Wake refused (HTTP 500)' });
    expect(w.detail.textContent).toBe('Wake refused (HTTP 500)');
    expect(w.retry.hidden).toBe(false);
    w.retry.click();
    expect(retry).toHaveBeenCalledOnce();
    expect(w.back.hidden).toBe(false);
    w.back.click();
    expect(back).toHaveBeenCalledOnce();
    w.show({ kind: 'starting', since: t, step: 'computer' });
    expect(w.back.hidden).toBe(true);
    w.stopTimer();
  });

  const stepClasses = (w: WakingPage) => [...w.steps.children].map((li) => li.className);
  const waitBox = (w: WakingPage) => w.el.querySelector('.waking-wait') as HTMLElement;

  it('sets expectations, ticks off the steps and fills the bar while waking', () => {
    vi.useFakeTimers();
    try {
      let t = 0;
      const w = new WakingPage(vi.fn(), vi.fn(), () => t);
      w.checking();
      expect(waitBox(w).hidden).toBe(true);

      w.show({ kind: 'starting', since: 0, step: 'computer' });
      expect(waitBox(w).hidden).toBe(false);
      expect(w.expect.textContent).toBe(EXPECT);
      expect(stepClasses(w)).toEqual(['active', 'todo', 'todo']);
      expect(w.steps.children[0]!.getAttribute('aria-current')).toBe('step');
      expect(w.el.querySelector('.waking-arm')).not.toBeNull();

      t = 45_000;
      vi.advanceTimersByTime(1000);
      expect(w.detail.textContent).toBe('45 s');
      const bar = w.el.querySelector('[role="progressbar"]')!;
      expect(bar.getAttribute('aria-valuenow')).toBe(String(Math.round(progressAt(45) * 100)));

      // a new step keeps the clock: since is the wake's start
      w.show({ kind: 'starting', since: 0, step: 'robot' });
      expect(stepClasses(w)).toEqual(['done', 'active', 'todo']);
      expect(w.detail.textContent).toBe('45 s');

      t = 160_000;
      vi.advanceTimersByTime(1000);
      expect(w.detail.textContent).toBe(`160 s · ${SLOW}`);

      w.show({ kind: 'running' });
      expect(w.heading.textContent).toBe('Connecting…');
      expect(stepClasses(w)).toEqual(['done', 'done', 'active']);
      expect(bar.getAttribute('aria-valuenow')).toBe('100');

      w.show({ kind: 'error', message: 'The robot did not wake up in time' });
      expect(waitBox(w).hidden).toBe(true);
    } finally {
      vi.useRealTimers();
    }
  });

  it('a lab that was already up connects without the wait panel', () => {
    const w = new WakingPage(vi.fn(), vi.fn());
    w.checking();
    w.show({ kind: 'running' });
    expect(w.heading.textContent).toBe('Connecting…');
    expect(waitBox(w).hidden).toBe(true);
  });

  it('rotates the facts while waking, and stops when the wait ends', () => {
    vi.useFakeTimers();
    try {
      const w = new WakingPage(vi.fn(), vi.fn());
      const fact = w.el.querySelector('.waking-fact')!;
      w.show({ kind: 'starting', since: Date.now(), step: 'computer' });
      expect(fact.textContent).toBe(FACTS[0]!.text);
      vi.advanceTimersByTime(FACT_MS);
      expect(fact.textContent).toBe(FACTS[1]!.text);
      w.show({ kind: 'starting', since: Date.now(), step: 'robot' }); // a new step does not restart them
      expect(fact.textContent).toBe(FACTS[1]!.text);
      w.show({ kind: 'capped' });
      vi.advanceTimersByTime(FACT_MS * 3);
      expect(fact.textContent).toBe(FACTS[1]!.text);
      expect(vi.getTimerCount()).toBe(0);
    } finally {
      vi.useRealTimers();
    }
  });

  it('paces the bar on a typical wake and never fills it before the end', () => {
    expect(progressAt(0)).toBe(0);
    expect(progressAt(45)).toBeCloseTo(0.425);
    expect(progressAt(90)).toBeCloseTo(0.85);
    for (const t of [120, 200, 300]) {
      expect(progressAt(t)).toBeGreaterThan(progressAt(t - 30));
      expect(progressAt(t)).toBeLessThan(0.98);
    }
    expect(progressAt(3600)).toBeLessThanOrEqual(0.98);
    expect(progressAt(-5)).toBe(0);
  });

  it('keeps every fact short, in plain text, with links opening in a new tab', () => {
    expect(FACTS.length).toBeGreaterThanOrEqual(6);
    for (const f of FACTS) {
      expect(f.text.length).toBeLessThanOrEqual(140);
      expect(f.text).not.toMatch(/[<>]/);
    }
    vi.useFakeTimers();
    try {
      const w = new WakingPage(vi.fn(), vi.fn());
      w.show({ kind: 'starting', since: Date.now(), step: 'computer' });
      const thesisAt = FACTS.findIndex((f) => f.link?.href === DALTON_THESIS);
      expect(thesisAt).toBeGreaterThanOrEqual(0);
      vi.advanceTimersByTime(FACT_MS * thesisAt);
      const a = w.el.querySelector('.waking-fact a') as HTMLAnchorElement;
      expect(a.href).toBe(DALTON_THESIS);
      expect(a.target).toBe('_blank');
      expect(a.rel).toBe('noopener');
      const source = w.el.querySelector('.waking-source') as HTMLAnchorElement;
      expect(source.href).toBe(WIKIPEDIA);
      w.stopTimer();
    } finally {
      vi.useRealTimers();
    }
  });
});
