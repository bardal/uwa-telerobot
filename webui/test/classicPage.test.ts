// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Camera, triangulate, type Vec3 } from '../src/geometry/camera';
import type { In, Out } from '../src/net/protocol';
import { stateAsGBHString } from '../src/pathscript';
import { ClassicPage, HEADINGS, RATE_RETRY_MS, TIMEOUTS } from '../src/ui/classicPage';

const CAMS = ['Colour', 'YCam', 'ZCam'];
const welcome = (nick: string): Out => ({
  t: 'welcome', nick, level: 'guest', cameras: CAMS, commit: 'c', limits: { script: 400, waypoints: 30, chat: 300 },
});
const state = (o: Partial<Extract<Out, { t: 'robotState' }>> = {}): Out => ({
  t: 'robotState', x: 250, y: 250, z: 100, spin: -90, tilt: 0, gripperOpen: false, moving: false, ...o,
});

/** A pinhole camera at c: P = [M | -M c] with an invertible M. */
function pinhole(m: number[], c: Vec3): number[] {
  const t = [0, 1, 2].map((r) => -(m[3 * r]! * c.x + m[3 * r + 1]! * c.y + m[3 * r + 2]! * c.z));
  return [m[0]!, m[1]!, m[2]!, t[0]!, m[3]!, m[4]!, m[5]!, t[1]!, m[6]!, m[7]!, m[8]!, t[2]!];
}
const CAL: Record<string, number[]> = {
  Colour: pinhole([400, 0, 200, 0, -400, 150, 0.2, 0.3, 1], { x: -300, y: -600, z: 400 }),
  YCam: pinhole([380, 40, 210, 10, -390, 140, 0.25, 0.1, 1], { x: 900, y: -500, z: 500 }),
  ZCam: pinhole([350, 0, 200, 0, -350, 150, 0, 0, 1], { x: 250, y: 250, z: 1200 }),
};

function setup() {
  const sent: In[] = [];
  let n = 0;
  const urls = { create: vi.fn(() => `blob:${++n}`), revoke: vi.fn() };
  const deps = { send: (m: In) => void sent.push(m), showApplet: vi.fn(), openHistory: vi.fn(), logout: vi.fn(), objectUrl: urls };
  const page = new ClassicPage(deps);
  document.body.appendChild(page.el);
  return { page, sent, deps, urls };
}

/** Answers every selectCamera with a 400x300 photo of that camera. */
function photographer(page: ClassicPage, sent: In[]) {
  let seen = 0;
  let seq = 0;
  return () => {
    for (; seen < sent.length; seen++) {
      const m = sent[seen]!;
      if (m.t === 'selectCamera') {
        page.image({ t: 'image', camera: m.camera, seq: ++seq, width: 400, height: 300, calibration: CAL[m.camera]! }, new Blob(['jpeg']));
      }
    }
  };
}

const flush = async () => {
  for (let i = 0; i < 20; i++) await Promise.resolve();
};

beforeEach(() => {
  vi.useFakeTimers();
  document.body.replaceChildren();
});
afterEach(() => vi.useRealTimers());

describe('triangulate', () => {
  it('recovers a point from its images in two calibrated cameras', () => {
    const w = { x: 120, y: 340, z: 60 };
    const a = new Camera(CAL.Colour!);
    const b = new Camera(CAL.YCam!);
    const p = triangulate(a.uv2line(a.xyz2uv(w)), b.uv2line(b.xyz2uv(w)))!;
    expect(p.x).toBeCloseTo(w.x, 6);
    expect(p.y).toBeCloseTo(w.y, 6);
    expect(p.z).toBeCloseTo(w.z, 6);
  });

  it('gives up on parallel rays', () => {
    const r = { a: { x: 0, y: 0, z: 0 }, b: { x: 1, y: 0, z: 0 } };
    expect(triangulate(r, { a: { x: 0, y: 1, z: 0 }, b: { x: 2, y: 1, z: 0 } })).toBeNull();
  });
});

describe('classic (1996 forms) page', () => {
  it('shows the controller page with the robot’s pose in the form, and fetches a photo from each camera', async () => {
    const { page, sent } = setup();
    const snap = photographer(page, sent);
    page.handle(welcome('ann'));
    page.handle(state({ x: 300, y: 120, z: 90, spin: -45, tilt: 20, gripperOpen: true }));
    page.show();
    for (let i = 0; i < 3; i++) {
      snap();
      await flush();
    }
    expect(page.heading.textContent).toBe(HEADINGS.operator);
    expect(Object.fromEntries(Object.entries(page.fields).map(([k, f]) => [k, f.value]))).toEqual({ X: '300', Y: '120', Z: '90', Spin: '-45', Tilt: '20' });
    expect(page.gripper.checked).toBe(true);
    expect(sent.filter((m) => m.t === 'selectCamera').map((m) => (m as { camera: string }).camera)).toEqual(CAMS);
    const imgs = [...page.el.querySelectorAll<HTMLImageElement>('img.classic-photo')];
    expect(imgs.map((i) => i.getAttribute('src'))).toEqual(['blob:1', 'blob:2', 'blob:3']);
    expect(page.stats.textContent).toMatch(/^You have made 0 moves, and have been on since /);
  });

  it('a camera with no photo yet is asked to take one', async () => {
    const { page, sent } = setup();
    page.handle(welcome('ann'));
    page.show();
    await flush();
    expect(sent.at(-1)).toEqual({ t: 'selectCamera', camera: 'Colour' });
    vi.advanceTimersByTime(TIMEOUTS.photo);
    await flush();
    expect(sent.at(-1)).toEqual({ t: 'newImage', camera: 'Colour' });
    page.image({ t: 'image', camera: 'Colour', seq: 1, width: 400, height: 300, calibration: CAL.Colour! }, new Blob(['j']));
    await flush();
    expect(page.el.querySelector('img.classic-photo')!.getAttribute('src')).toBe('blob:1'); // shown before the others
    expect(sent.at(-1)).toEqual({ t: 'selectCamera', camera: 'YCam' });
    page.dispose();
  });

  it('a rate-limited camera request waits a second and asks again', async () => {
    const { page, sent } = setup();
    page.handle(welcome('ann'));
    page.show();
    await flush();
    expect(sent).toEqual([{ t: 'selectCamera', camera: 'Colour' }]);
    page.handle({ t: 'error', code: 'rate-limited', message: 'slow down' });
    await flush();
    vi.advanceTimersByTime(RATE_RETRY_MS);
    await flush();
    expect(sent).toEqual([{ t: 'selectCamera', camera: 'Colour' }, { t: 'selectCamera', camera: 'Colour' }]);
    page.image({ t: 'image', camera: 'Colour', seq: 1, width: 400, height: 300, calibration: CAL.Colour! }, new Blob(['j']));
    await flush();
    expect(sent.at(-1)).toEqual({ t: 'selectCamera', camera: 'YCam' });
    page.dispose();
  });

  it('a camera turned off is not refreshed', async () => {
    const { page, sent } = setup();
    const snap = photographer(page, sent);
    page.handle(welcome('ann'));
    (page.el.querySelector('input[name="YCamDontRefresh"]') as HTMLInputElement).checked = true;
    page.show();
    for (let i = 0; i < 3; i++) {
      snap();
      await flush();
    }
    expect(sent.filter((m) => m.t === 'selectCamera').map((m) => (m as { camera: string }).camera)).toEqual(['Colour', 'ZCam']);
  });

  it('Move the robot: takes control when it is free, makes one move, then fresh photos', async () => {
    const { page, sent } = setup();
    const snap = photographer(page, sent);
    page.handle(welcome('ann'));
    page.handle(state());
    page.fields.X.value = '330';
    page.fields.Y.value = '120';
    page.fields.Z.value = '90';
    page.gripper.checked = true;
    const done = page.submit();
    await flush();
    expect(sent.at(-1)).toEqual({ t: 'takeControl' });
    page.handle({ t: 'controller', nick: 'ann' });
    await flush();
    const script = stateAsGBHString({ pose: { x: 330, y: 120, z: 90, spin: -90, tilt: 0 }, gripperOpen: true });
    expect(sent.at(-1)).toEqual({ t: 'command', script });
    expect(page.moveButton.disabled).toBe(true);
    page.handle({ t: 'commandStatus', id: 'c1', state: 'done', script, text: '' });
    page.handle(state({ x: 330, y: 120, z: 90, gripperOpen: true }));
    page.handle({ t: 'archived', kind: 'move', id: 'm1', url: '/archive/x' });
    for (let i = 0; i < 4; i++) {
      await flush();
      snap();
    }
    await done;
    expect(sent.filter((m) => m.t === 'selectCamera')).toHaveLength(3);
    expect(page.moveButton.disabled).toBe(false);
    expect(page.error.textContent).toBe('');
    expect(page.stats.textContent).toMatch(/You have made 1 move,.*Your last move was made at /);
    expect(vi.getTimerCount()).toBe(0);
  });

  it('Move relative adds the form’s values to where the robot is', async () => {
    const { page, sent } = setup();
    page.handle(welcome('ann'));
    page.handle({ t: 'controller', nick: 'ann' });
    page.handle(state({ x: 200, y: 200, z: 100, spin: -90, tilt: 0 }));
    for (const [k, v] of [['X', '10'], ['Y', '-20'], ['Z', '5'], ['Spin', '0'], ['Tilt', '15']] as const) page.fields[k].value = v;
    page.relative.checked = true;
    void page.submit();
    await flush();
    expect(sent.at(-1)).toEqual({
      t: 'command',
      script: stateAsGBHString({ pose: { x: 210, y: 180, z: 105, spin: -90, tilt: 15 }, gripperOpen: false }),
    });
    vi.advanceTimersByTime(TIMEOUTS.move + TIMEOUTS.archive + 3 * TIMEOUTS.photo);
  });

  it('a value that is not a number is an error and nothing is sent', async () => {
    const { page, sent } = setup();
    page.handle(welcome('ann'));
    page.handle(state());
    page.fields.Z.value = 'up';
    await page.submit();
    expect(page.error.textContent).toBe('Z must be a number.');
    expect(sent).toEqual([]);
  });

  it('a move the robot refuses shows its reason', async () => {
    const { page, sent } = setup();
    const snap = photographer(page, sent);
    page.handle(welcome('ann'));
    page.handle({ t: 'controller', nick: 'ann' });
    page.handle(state());
    const done = page.submit();
    await flush();
    const script = (sent.at(-1) as { script: string }).script;
    page.handle({ t: 'commandStatus', id: 'c1', state: 'error', script, text: 'Out of reach' });
    await flush();
    vi.advanceTimersByTime(TIMEOUTS.archive); // no archive entry for a refused move
    for (let i = 0; i < 4; i++) {
      await flush();
      snap();
    }
    await done;
    expect(page.error.textContent).toBe('Out of reach');
  });

  it('observer page while someone else has the robot; Try to get control asks for it', async () => {
    const { page, sent } = setup();
    page.handle(welcome('ann'));
    page.handle({ t: 'controller', nick: 'bob' });
    expect(page.mode()).toBe('observer');
    expect(page.heading.textContent).toBe(HEADINGS.observer);
    expect(page.notice.textContent).toMatch(/currently being used by bob/);
    expect(page.moveButton.disabled).toBe(true);
    expect(page.controlButton.hidden).toBe(false);
    await page.submit();
    expect(sent).toEqual([]);
    page.controlButton.click();
    await flush();
    expect(sent.at(-1)).toEqual({ t: 'takeControl' });
    page.handle({ t: 'controller', nick: 'ann' });
    for (let i = 0; i < 3; i++) {
      await flush();
      vi.advanceTimersByTime(TIMEOUTS.photo); // no photos come: each camera's wait runs out in turn
    }
    await flush();
    expect(page.mode()).toBe('operator');
    expect(page.controlButton.hidden).toBe(true);
  });

  it('one image clicked asks for the other; the second triangulates and moves there', async () => {
    const { page, sent } = setup();
    const snap = photographer(page, sent);
    page.handle(welcome('ann'));
    page.handle({ t: 'controller', nick: 'ann' });
    page.handle(state({ spin: -60, tilt: 10 }));
    page.show();
    for (let i = 0; i < 3; i++) {
      snap();
      await flush();
    }
    const imgs = [...page.el.querySelectorAll<HTMLImageElement>('img.classic-photo')];
    for (const img of imgs) {
      // shown at half size: clicks are scaled back to the photo's 400x300
      img.getBoundingClientRect = () => ({ left: 10, top: 20, width: 200, height: 150, right: 210, bottom: 170, x: 10, y: 20, toJSON() {} });
    }
    const target = { x: 180, y: 260, z: 40 };
    const click = (i: number, cam: string) => {
      const uv = new Camera(CAL[cam]!).xyz2uv(target);
      imgs[i]!.dispatchEvent(new MouseEvent('click', { clientX: 10 + uv.u / 2, clientY: 20 + uv.v / 2, bubbles: true }));
    };
    click(0, 'Colour');
    expect(page.mode()).toBe('clicked');
    expect(page.notice.textContent).toMatch(/now click the other image/);
    expect(imgs[0]!.classList.contains('classic-photo-clicked')).toBe(true);
    click(2, 'ZCam');
    await flush();
    const cmd = sent.at(-1) as { t: string; script: string };
    expect(cmd.t).toBe('command');
    const m = /^x(-?\d+),y(-?\d+),s(-?\d+),t(-?\d+),z(-?\d+),gclosed$/.exec(cmd.script)!;
    expect(m.slice(1).map(Number)).toEqual([180, 260, -60, 10, 40]);
    expect(page.mode()).toBe('operator');
    vi.advanceTimersByTime(TIMEOUTS.move + TIMEOUTS.archive + 3 * TIMEOUTS.photo);
  });

  it('the links switch to the applet page, History and log out', () => {
    const { page, deps } = setup();
    const link = (text: RegExp) => [...page.el.querySelectorAll('a')].find((a) => text.test(a.textContent ?? ''))!;
    link(/applet/).click();
    link(/History/).click();
    link(/Log out/).click();
    expect(deps.showApplet).toHaveBeenCalledOnce();
    expect(deps.openHistory).toHaveBeenCalledOnce();
    expect(deps.logout).toHaveBeenCalledOnce();
  });

  it('frees its photos when disposed', async () => {
    const { page, sent, urls } = setup();
    const snap = photographer(page, sent);
    page.handle(welcome('ann'));
    page.show();
    for (let i = 0; i < 3; i++) {
      snap();
      await flush();
    }
    page.dispose();
    expect(urls.revoke.mock.calls.map((c) => c[0]).sort()).toEqual(['blob:1', 'blob:2', 'blob:3']);
  });
});
