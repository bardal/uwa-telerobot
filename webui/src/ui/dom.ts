// Tiny DOM construction helpers: text goes in through textContent only, never HTML.

type Attrs = Record<string, string | number | boolean | undefined>;
type Child = Node | string | null | undefined | false;

export function h<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  attrs: Attrs = {},
  ...children: Child[]
): HTMLElementTagNameMap[K] {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (v === undefined || v === false) continue;
    if (k === 'class') el.className = String(v);
    else if (k === 'text') el.textContent = String(v);
    else el.setAttribute(k, v === true ? '' : String(v));
  }
  for (const c of children) {
    if (c === null || c === undefined || c === false) continue;
    el.appendChild(typeof c === 'string' ? document.createTextNode(c) : c);
  }
  return el;
}

/** An AWT-style button. */
export function button(label: string, onClick: () => void, cls = ''): HTMLButtonElement {
  const b = h('button', { type: 'button', class: `awt-button ${cls}`.trim(), text: label });
  b.addEventListener('click', () => {
    if (!b.disabled) onClick();
  });
  return b;
}

/** A Java BorderPanel: a black titled border around its content. */
export function borderPanel(title: string, cls: string, ...children: Child[]): HTMLFieldSetElement {
  return h('fieldset', { class: `border-panel ${cls}`.trim() }, h('legend', { text: title }), ...children);
}

/** TextArea.append(text): append to a read-only text area and keep the end in view. */
export function appendText(ta: HTMLTextAreaElement, text: string): void {
  ta.value += text;
  ta.scrollTop = ta.scrollHeight;
}
