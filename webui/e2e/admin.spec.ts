// Journey: an admin (dev sign-in with the admin box) and a guest. Only the admin has the
// Admin menu. The admin moves the arm away from its start, kicks the guest (who lands
// back on the login page and stays out), then resets the lab: the arm is back home.

import { expect, test } from '@playwright/test';
import {
  consoleText, devLogin, dist, expectConsole, guestLogin, menu, newUser, release, robotState, runScript, setScript, shot,
  takeControl, waitIdle,
} from './helpers';

const HOME = { x: 250, y: 250, z: 100 };

test('admin: the Admin menu kicks a guest and resets the lab', async ({ browser }) => {
  const boss = await newUser(browser);
  const guest = await newUser(browser);
  try {
    const nickBoss = await devLogin(boss.page, 'boss', true);
    const nickGuest = await guestLogin(guest.page, 'victim');
    await expect(boss.page.locator('.user-list')).toContainText(nickGuest);

    // the menu is the admin's alone
    await expect(boss.page.locator('.menu-title', { hasText: /^Admin$/ })).toBeVisible();
    await expect(guest.page.locator('.menu-title', { hasText: /^Admin$/ })).toBeHidden();

    // move the arm away from its start
    await takeControl(boss.page, nickBoss);
    await setScript(boss.page, 'x330,y180,z140');
    const away = await runScript(boss.page);
    expect(dist(away, HOME)).toBeGreaterThan(50);
    await release(boss.page);

    // Kick user...: an in-page dialog (a bad nick is refused there), then the guest is gone
    await menu(boss.page, 'Admin', 'Kick user…');
    const dialog = boss.page.getByRole('dialog', { name: 'Kick user' });
    await expect(dialog).toBeVisible();
    await dialog.getByLabel('nickname of user').fill('<bad>');
    await dialog.getByRole('button', { name: 'Kick', exact: true }).click();
    await expect(dialog.getByRole('alert')).toContainText('1 to 20');
    await dialog.getByLabel('nickname of user').fill(nickGuest);
    await dialog.getByRole('button', { name: 'Kick', exact: true }).click();
    await expect(dialog).toBeHidden();
    await expectConsole(boss.page, `Kicked ${nickGuest}`);
    await expect(boss.page.locator('.user-list')).not.toContainText(nickGuest);
    // the guest is told why and is back on the login page, not reconnected
    await expect(guest.page.getByRole('button', { name: 'Continue as guest', exact: true })).toBeVisible();
    await expect(guest.page.locator('textarea.login-console')).toHaveValue(/removed by an administrator/);
    await guest.page.waitForTimeout(3_000);
    await expect(boss.page.locator('.user-list')).not.toContainText(nickGuest);
    await shot(boss.page, 'admin-1-kicked');

    // Reset lab: asks first, then the arm is back at its start
    await menu(boss.page, 'Admin', 'Reset lab');
    const confirm = boss.page.getByRole('dialog', { name: 'Confirmation' });
    await expect(confirm).toContainText('Really reset the lab?');
    await confirm.getByRole('button', { name: 'Yes' }).click();
    await expectConsole(boss.page, 'The lab has been reset');
    await expect.poll(async () => {
      const s = await robotState(boss.page);
      return s && !s.moving ? dist(s, HOME) : Infinity;
    }, { timeout: 30_000 }).toBeLessThan(1);
    await waitIdle(boss.page);
    expect(await consoleText(boss.page).inputValue()).not.toMatch(/did not reset/);
    await shot(boss.page, 'admin-2-reset');
  } finally {
    await boss.context.close();
    await guest.context.close();
  }
});
