// The applet's small modal dialogs: gui/awt/ConfirmDialog, model/AddPointDialog
// and model/DeletePointDialog.

import { button, h } from './dom';

function modal(title: string, ...rows: HTMLElement[]): { el: HTMLElement; close: () => void } {
  const box = h('div', { class: 'awt-dialog', role: 'dialog', 'aria-modal': 'true', 'aria-label': title }, h('div', { class: 'dialog-title', text: title }), ...rows);
  const el = h('div', { class: 'dialog-backdrop' }, box);
  const close = () => {
    document.removeEventListener('keydown', onKey);
    el.remove();
  };
  const onKey = (e: KeyboardEvent) => {
    if (e.key === 'Escape') close();
  };
  document.addEventListener('keydown', onKey);
  document.body.appendChild(el);
  return { el, close };
}

/** ConfirmDialog(parent, message, handler): Yes / No. */
export function confirmDialog(message: string, yes: () => void, no: () => void = () => undefined): HTMLElement {
  let d: { el: HTMLElement; close: () => void } | null = null;
  const bYes = button('Yes', () => { d?.close(); yes(); });
  const bNo = button('No', () => { d?.close(); no(); });
  d = modal('Confirmation', h('div', { class: 'dialog-row' }, h('span', { class: 'awt-label', text: message }), bYes, bNo));
  bYes.focus();
  return d.el;
}

/** A nickname as the gateway accepts it, or as it de-duplicates one ("ann~2"). */
export const TARGET_NICK = /^[A-Za-z0-9 _.-]{1,20}(~\d{1,4})?$/;

/**
 * Asks for the nick of a user to act on (the Admin menu's Kick and Ban). An invalid nick keeps the
 * dialog open with a note; OK with a valid one closes it and calls {@code ok} with the trimmed nick.
 */
export function nickDialog(title: string, okLabel: string, ok: (nick: string) => void): HTMLElement {
  const nickF = h('input', { type: 'text', class: 'awt-text', size: 20, maxlength: 25, 'aria-label': 'nickname of user' });
  const note = h('span', { class: 'awt-label dialog-note', role: 'alert' });
  let d: { el: HTMLElement; close: () => void } | null = null;
  const submit = () => {
    const nick = nickF.value.trim();
    if (!TARGET_NICK.test(nick)) {
      note.textContent = 'A nickname is 1 to 20 letters, digits, spaces, _ . or -';
      nickF.focus();
      return;
    }
    d?.close();
    ok(nick);
  };
  const bOk = button(okLabel, submit);
  const bCancel = button('Cancel', () => d?.close());
  nickF.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') submit();
  });
  d = modal(
    title,
    h('div', { class: 'dialog-row' }, h('span', { class: 'awt-label', text: 'nickname' }), nickF, bOk, bCancel),
    h('div', { class: 'dialog-row' }, note),
  );
  nickF.focus();
  return d.el;
}

function intField(value: string, label: string, size = 5): HTMLInputElement {
  return h('input', { type: 'text', class: 'awt-text', size, value, 'aria-label': label, inputmode: 'numeric' });
}

const intValue = (f: HTMLInputElement): number => {
  const v = Number.parseInt(f.value, 10);
  return Number.isFinite(v) ? v : 0;
};

/** AddPointDialog: x, y, z and the name of the point (default "point"). */
export function addPointDialog(
  add: (p: { x: number; y: number; z: number; name: string }) => void,
  println: (s: string) => void,
): HTMLElement {
  const xf = intField('0', 'x');
  const yf = intField('0', 'y');
  const zf = intField('0', 'z');
  const nameF = h('input', { type: 'text', class: 'awt-text', value: 'point', 'aria-label': 'name of point' });
  let d: { el: HTMLElement; close: () => void } | null = null;
  const bAdd = button('Add', () => {
    d?.close();
    const name = nameF.value.trim();
    if (nameF.value.length > 0 && name) add({ x: intValue(xf), y: intValue(yf), z: intValue(zf), name });
    else println('Add point failed - no name specified');
  });
  const bCancel = button('Cancel', () => d?.close());
  d = modal(
    'Add Point',
    h('div', { class: 'dialog-row' }, h('span', { class: 'awt-label', text: 'x' }), xf, h('span', { class: 'awt-label', text: 'y' }), yf, h('span', { class: 'awt-label', text: 'z' }), zf),
    h('div', { class: 'dialog-row' }, h('span', { class: 'awt-label', text: 'name of point' }), nameF, bAdd, bCancel),
  );
  xf.focus();
  return d.el;
}

/** DeletePointDialog: the name of the point to delete. */
export function deletePointDialog(del: (name: string) => void, println: (s: string) => void): HTMLElement {
  const nameF = h('input', { type: 'text', class: 'awt-text', size: 20, 'aria-label': 'name of point' });
  let d: { el: HTMLElement; close: () => void } | null = null;
  const bDelete = button('Delete', () => {
    d?.close();
    const name = nameF.value.trim();
    if (nameF.value.length > 0 && name) del(name);
    else println('Delete point failed - no name specified');
  });
  const bCancel = button('Cancel', () => d?.close());
  d = modal('Delete Point', h('div', { class: 'dialog-row' }, h('span', { class: 'awt-label', text: 'name of point' }), nameF, bDelete, bCancel));
  nameF.focus();
  return d.el;
}
