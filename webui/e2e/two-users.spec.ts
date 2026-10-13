// Journey 2: a guest (A) and a registered user (B) in two browser contexts. Chat goes
// both ways; B's Control Robot takes control from A at once; when A closes its tab
// while in control, the robot is free for B straight away.

import { expect, test } from '@playwright/test';
import { controller, devLogin, guestLogin, newUser, release, shot, takeControl } from './helpers';

const AT_ONCE_MS = 3000;

test('two users: chat both ways, B takes control from A, A closing its tab frees the robot', async ({ browser }) => {
  const a = await newUser(browser);
  const b = await newUser(browser);
  try {
    const nickA = await guestLogin(a.page, 'alice');
    const nickB = await devLogin(b.page, 'bob');
    for (const p of [a.page, b.page]) {
      await expect(p.locator('.user-list')).toContainText(nickA);
      await expect(p.locator('.user-list')).toContainText(nickB);
    }

    // chat both ways
    const chatA = a.page.getByLabel('Message');
    const chatB = b.page.getByLabel('Message');
    await chatA.fill('hello from A');
    await chatA.press('Enter');
    await expect(b.page.getByLabel('Chat', { exact: true })).toHaveValue(new RegExp(`${nickA}: hello from A`));
    await chatB.fill('hi A, B here');
    await chatB.press('Enter');
    await expect(a.page.getByLabel('Chat', { exact: true })).toHaveValue(new RegExp(`${nickB}: hi A, B here`));
    await expect(a.page.getByLabel('Chat', { exact: true })).toHaveValue(new RegExp(`${nickA}: hello from A`));
    await shot(a.page, 'two-users-1-chat-A');
    await shot(b.page, 'two-users-1-chat-B');

    // A takes control; B's Control Robot takes it from A at once
    await takeControl(a.page, nickA);
    await expect.poll(() => controller(b.page)).toBe(nickA);
    const t0 = Date.now();
    await b.page.getByRole('button', { name: 'Control Robot' }).click();
    await expect.poll(() => controller(b.page), { timeout: AT_ONCE_MS }).toBe(nickB);
    await expect.poll(() => controller(a.page), { timeout: AT_ONCE_MS }).toBe(nickB);
    expect(Date.now() - t0).toBeLessThan(AT_ONCE_MS);
    await expect(a.page.getByRole('button', { name: 'Control Robot' })).toBeVisible();
    await expect(a.page.getByRole('button', { name: 'Send command' })).toBeDisabled();
    await expect(b.page.getByRole('button', { name: 'Send command' })).toBeEnabled();
    await shot(b.page, 'two-users-2-B-took-control');

    // A takes control back, then closes its tab while in control: the robot is free at once
    await release(b.page);
    await takeControl(a.page, nickA);
    await expect.poll(() => controller(b.page)).toBe(nickA);
    const t1 = Date.now();
    await a.page.close();
    await expect.poll(() => controller(b.page), { timeout: AT_ONCE_MS }).toBeNull();
    await expect(b.page.locator('.controller-line')).toHaveText('No Controller');
    await expect(b.page.locator('.user-list')).not.toContainText(nickA);
    await b.page.getByRole('button', { name: 'Control Robot' }).click();
    await expect.poll(() => controller(b.page), { timeout: AT_ONCE_MS }).toBe(nickB);
    expect(Date.now() - t1).toBeLessThan(2 * AT_ONCE_MS);
    await shot(b.page, 'two-users-3-A-closed-B-in-control');

    await release(b.page);
  } finally {
    await a.context.close();
    await b.context.close();
  }
});
