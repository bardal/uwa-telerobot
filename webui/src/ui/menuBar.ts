// java.awt.MenuBar in the DOM: titles along the top; a click opens a drop-down,
// moving to another title while one is open switches to it, and a choice, a
// click elsewhere or Escape closes it.

import { h } from './dom';
import { buildMenuList, type MenuItemHandle, type MenuItemSpec } from './popupMenu';

export interface MenuSpec {
  label: string;
  items: MenuItemSpec[];
}

interface Menu {
  label: string;
  title: HTMLElement;
  list: HTMLElement;
  handles: MenuItemHandle[];
}

export class MenuBar {
  readonly el: HTMLElement;
  private readonly menus: Menu[] = [];
  private open: Menu | null = null;

  constructor(specs: MenuSpec[]) {
    this.el = h('div', { class: 'menu-bar', role: 'menubar' });
    for (const spec of specs) {
      const title = h('div', { class: 'menu-title', role: 'menuitem', 'aria-haspopup': 'true', 'aria-expanded': 'false', text: spec.label });
      const { el: list, handles } = buildMenuList(spec.items, () => this.close());
      list.classList.add('menu-drop');
      list.setAttribute('aria-label', spec.label);
      list.hidden = true;
      const wrap = h('div', { class: 'menu' }, title, list);
      const menu: Menu = { label: spec.label, title, list, handles };
      title.addEventListener('click', (e) => {
        e.stopPropagation();
        if (this.open === menu) this.close();
        else this.show(menu);
      });
      title.addEventListener('pointerenter', () => {
        if (this.open && this.open !== menu) this.show(menu);
      });
      this.menus.push(menu);
      this.el.appendChild(wrap);
    }
    document.addEventListener('pointerdown', this.onDocDown);
    document.addEventListener('keydown', this.onKey);
  }

  private readonly onDocDown = (e: Event): void => {
    if (this.open && !this.el.contains(e.target as Node)) this.close();
  };

  private readonly onKey = (e: KeyboardEvent): void => {
    if (e.key === 'Escape') this.close();
  };

  /** Menu titles, left to right. */
  labels(): string[] {
    return this.menus.map((m) => m.label);
  }

  /** The items of a menu, in order (separators left out). */
  items(menu: string): MenuItemHandle[] {
    return [...this.menu(menu).handles];
  }

  item(menu: string, label: string): MenuItemHandle {
    const it = this.menu(menu).handles.find((x) => x.label === label);
    if (!it) throw new Error(`no menu item ${menu} > ${label}`);
    return it;
  }

  close(): void {
    if (!this.open) return;
    this.open.list.hidden = true;
    this.open.title.classList.remove('open');
    this.open.title.setAttribute('aria-expanded', 'false');
    this.open = null;
  }

  dispose(): void {
    document.removeEventListener('pointerdown', this.onDocDown);
    document.removeEventListener('keydown', this.onKey);
  }

  private show(menu: Menu): void {
    this.close();
    menu.list.hidden = false;
    menu.title.classList.add('open');
    menu.title.setAttribute('aria-expanded', 'true');
    this.open = menu;
  }

  private menu(label: string): Menu {
    const m = this.menus.find((x) => x.label === label);
    if (!m) throw new Error(`no menu ${label}`);
    return m;
  }
}
