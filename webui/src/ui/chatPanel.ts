// Port of mom/gui/ChatPanel: "Message" field over the received text, with the
// Users list on the right. Everything is plain text (TextArea values and textContent).

import { appendText, h } from './dom';

export class ChatPanel {
  readonly el: HTMLElement;
  readonly sendField: HTMLInputElement;
  readonly receiveArea: HTMLTextAreaElement;
  readonly userList: HTMLElement;

  constructor(private readonly onSend: (text: string) => void) {
    this.sendField = h('input', { type: 'text', class: 'awt-text chat-send', 'aria-label': 'Message', autocomplete: 'off' });
    this.sendField.readOnly = true; // editable once the channel is joined
    this.receiveArea = h('textarea', { class: 'chat-log', readonly: true, spellcheck: 'false', 'aria-label': 'Chat' });
    this.userList = h('div', { class: 'awt-list user-list', role: 'listbox', 'aria-label': 'Users' });
    this.sendField.addEventListener('keydown', (e) => {
      if (e.key !== 'Enter' || this.sendField.readOnly) return;
      e.preventDefault();
      const text = this.sendField.value;
      this.sendField.select(); // as the applet: the text stays, selected, ready to be typed over
      if (text.trim()) this.onSend(text);
    });
    this.el = h(
      'div',
      { class: 'chat-panel' },
      h(
        'div',
        { class: 'chat-main' },
        h('div', { class: 'chat-send-row' }, h('label', { class: 'awt-label chat-message-label', text: 'Message ' }), this.sendField),
        this.receiveArea,
      ),
      h('div', { class: 'chat-users' }, h('div', { class: 'awt-label users-label', text: 'Users' }), this.userList),
    );
  }

  /** ChatPanel.setChannel: the field becomes editable when the chat channel is joined. */
  setJoined(b: boolean): void {
    this.sendField.readOnly = !b;
  }

  setMaxLength(n: number): void {
    if (n > 0) this.sendField.maxLength = n;
  }

  /** ChatPanel.message: "sender: text". */
  message(nick: string, text: string): void {
    appendText(this.receiveArea, `${nick}: ${text}\n`);
  }

  setUsers(nicks: string[]): void {
    this.userList.replaceChildren(...nicks.map((n) => h('div', { class: 'awt-list-item', role: 'option', text: n })));
  }

  users(): string[] {
    return [...this.userList.children].map((c) => c.textContent ?? '');
  }
}
