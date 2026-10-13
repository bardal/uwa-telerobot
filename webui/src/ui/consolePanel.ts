// gui/awt/TextConsole: a read-only text area that TheConsole.println appends to.

import { appendText, h } from './dom';

export class ConsolePanel {
  readonly el: HTMLTextAreaElement;

  constructor(rows = 8, cls = 'console') {
    this.el = h('textarea', { class: `text-console ${cls}`, rows, readonly: true, spellcheck: 'false', 'aria-label': 'Console' });
  }

  println(text: string): void {
    appendText(this.el, `${text}\n`);
  }

  text(): string {
    return this.el.value;
  }

  clear(): void {
    this.el.value = '';
  }
}
