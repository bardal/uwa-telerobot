// AWT menus in the DOM: the shared item list used by the menu bar's drop-downs,
// and PopupMenu, the canvas right-click menu (TA200001.ThePopUpMenu).

import { h } from './dom';

export type MenuItemSpec =
  | { kind: 'item'; label: string; action: () => void; enabled?: boolean }
  | { kind: 'check'; label: string; checked: boolean; onChange: (checked: boolean) => void }
  | { kind: 'separator' };

export const item = (label: string, action: () => void, enabled = true): MenuItemSpec => ({ kind: 'item', label, action, enabled });
export const check = (label: string, checked: boolean, onChange: (c: boolean) => void): MenuItemSpec => ({
  kind: 'check',
  label,
  checked,
  onChange,
});
export const separator: MenuItemSpec = { kind: 'separator' };

/** A MenuItem / CheckboxMenuItem in the DOM. */
export class MenuItemHandle {
  private _enabled = true;
  private _state = false;

  constructor(
    readonly el: HTMLElement,
    readonly spec: Exclude<MenuItemSpec, { kind: 'separator' }>,
  ) {
    if (spec.kind === 'item') this.setEnabled(spec.enabled ?? true);
    else this.setState(spec.checked);
  }

  get label(): string {
    return this.spec.label;
  }

  get enabled(): boolean {
    return this._enabled;
  }

  setEnabled(b: boolean): void {
    this._enabled = b;
    this.el.classList.toggle('disabled', !b);
    this.el.setAttribute('aria-disabled', String(!b));
  }

  /** CheckboxMenuItem.getState(). */
  get state(): boolean {
    return this._state;
  }

  /** CheckboxMenuItem.setState(): changes the tick without firing the listener. */
  setState(b: boolean): void {
    this._state = b;
    if (this.spec.kind === 'check') this.el.setAttribute('aria-checked', String(b));
  }

  /** What a click does. */
  activate(): void {
    if (!this._enabled) return;
    if (this.spec.kind === 'item') this.spec.action();
    else {
      this.setState(!this._state);
      this.spec.onChange(this._state);
    }
  }
}

/** Builds a vertical menu list; `done` runs after an item is chosen (to close the menu). */
export function buildMenuList(items: MenuItemSpec[], done: () => void): { el: HTMLElement; handles: MenuItemHandle[] } {
  const el = h('div', { class: 'menu-list', role: 'menu' });
  const handles: MenuItemHandle[] = [];
  for (const spec of items) {
    if (spec.kind === 'separator') {
      el.appendChild(h('div', { class: 'menu-separator', role: 'separator' }));
      continue;
    }
    const row = h(
      'div',
      { class: `menu-item ${spec.kind === 'check' ? 'menu-check' : ''}`.trim(), role: spec.kind === 'check' ? 'menuitemcheckbox' : 'menuitem' },
      h('span', { class: 'menu-tick', 'aria-hidden': 'true' }),
      h('span', { class: 'menu-label', text: spec.label }),
    );
    const handle = new MenuItemHandle(row, spec);
    row.addEventListener('click', (e) => {
      e.stopPropagation();
      if (!handle.enabled) return;
      done();
      handle.activate();
    });
    handles.push(handle);
    el.appendChild(row);
  }
  return { el, handles };
}

/** java.awt.PopupMenu: shown at a point, closed by a choice, a click elsewhere or Escape. */
export class PopupMenu {
  readonly el: HTMLElement;
  private readonly handles: MenuItemHandle[];

  constructor(
    readonly title: string,
    items: MenuItemSpec[],
  ) {
    const list = buildMenuList(items, () => this.hide());
    this.handles = list.handles;
    this.el = list.el;
    this.el.classList.add('popup-menu');
    this.el.setAttribute('aria-label', title);
    this.el.hidden = true;
    document.addEventListener('pointerdown', this.onDocDown);
    document.addEventListener('keydown', this.onKey);
  }

  private readonly onDocDown = (e: Event): void => {
    if (!this.el.hidden && !this.el.contains(e.target as Node)) this.hide();
  };

  private readonly onKey = (e: KeyboardEvent): void => {
    if (e.key === 'Escape') this.hide();
  };

  dispose(): void {
    document.removeEventListener('pointerdown', this.onDocDown);
    document.removeEventListener('keydown', this.onKey);
    this.el.remove();
  }

  item(label: string): MenuItemHandle {
    const it = this.handles.find((x) => x.label === label);
    if (!it) throw new Error(`no popup item ${label}`);
    return it;
  }

  items(): MenuItemHandle[] {
    return [...this.handles];
  }

  /** Show with the top-left corner at viewport point (x, y), kept on screen. */
  show(x: number, y: number): void {
    this.el.hidden = false;
    const w = this.el.offsetWidth;
    const hgt = this.el.offsetHeight;
    const vw = globalThis.innerWidth || 0;
    const vh = globalThis.innerHeight || 0;
    this.el.style.left = `${Math.max(0, vw && x + w > vw ? vw - w : x)}px`;
    this.el.style.top = `${Math.max(0, vh && y + hgt > vh ? vh - hgt : y)}px`;
  }

  hide(): void {
    this.el.hidden = true;
  }

  get visible(): boolean {
    return !this.el.hidden;
  }
}
