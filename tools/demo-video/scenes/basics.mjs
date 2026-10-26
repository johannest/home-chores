// "FlashChores in a minute" — the basics, for someone who has never seen the app. The script is
// docs/DEMO-VIDEOS-PLAN-2026-10-26.md, video 2: one typed field, no gesture demos, no diamonds
// (those are the rewards video), and a board that is new, so every scene is something a family
// sees in its first ten minutes.
export default async function basics(d) {
  const { page, BASE, say, settle, tap, type, typeSlow, tab, chip, dialogButton, openSection,
    outro } = d;

  // The landing page, and the one field typed on camera.
  await page.goto(BASE);
  await page.waitForSelector('vaadin-text-field');
  await say('Meet FlashChores. Family chores, zero nagging.', 'Meet FlashChores ⚡', { lead: 900 });
  await typeSlow(page.locator('vaadin-text-field').nth(0).locator('input'), 'The Nest');
  await say('No accounts, just nicknames.', 'Just nicknames 🦸');
  await type(page.locator('vaadin-text-field').nth(1).locator('input'), 'Captain Mop');
  await tap(page.getByRole('checkbox', { name: 'I agree' }));
  await tap(page.locator('vaadin-button', { hasText: 'Create home' }));

  // The share code.
  await page.waitForSelector('vaadin-dialog-overlay');
  await say('One code invites the whole family.', 'One shared code', { lead: 1400 });
  await tap(page.locator('vaadin-button', { hasText: "Let's go" }));

  // The board, and the first tap.
  await page.waitForSelector('.task-card');
  await say('You start with everyday chores, ready to tap.', '11 starter chores', { lead: 1500 });
  await say('Did one? Tap it.', 'Tap it 👇');
  await tap(page.locator('.complete-area').first());
  await page.waitForSelector('vaadin-dialog-overlay');
  await tap(page.locator('vaadin-button', { hasText: 'Yes, I did it' }));
  await say('Confetti. Obviously.', '🎉', { lead: 1200 });
  await say('Say how it felt.', 'How was it? 😖 🙂 😍');
  await tap(page.locator('.feedback-btn').nth(2));
  await settle(500);
  await tap(dialogButton(/^Done/));
  await settle(400);

  // Booking, and the day's record.
  await say("Call dibs, so nobody does it twice.", "I'll do it 🔖");
  await tap(page.locator('.book-btn', { hasText: "I'll do it" }).first());
  await settle(900);
  await say('Everyone sees who did what today.', 'Done today ✅');
  await tap(page.locator('.done-today vaadin-details-summary'));
  await settle(1400);

  // Shared lists: one line added, one ticked.
  await tap(tab('Lists'));
  await say('Groceries, to-dos and dinners, shared too.', 'Shared lists 🛒');
  const addBox = page.getByRole('textbox', { name: 'Add an item…' });
  for (const item of ['Milk', 'Bread']) {
    await addBox.click();
    await page.keyboard.insertText(item);
    await addBox.press('Enter');
    await settle(450);
  }
  await tap(page.locator('.shared-list:not(.done) .list-item', { hasText: 'Milk' }).locator('vaadin-checkbox'));
  await settle(1100);

  // The admin adds a seasonal chore.
  await say('Admins add chores in plain language.', 'Add chore ➕');
  await tap(tab('Admin'));
  await settle(500);
  await openSection('Chores');
  await tap(page.locator('vaadin-button', { hasText: 'Add chore' }));
  // Dialog content is not a DOM descendant of vaadin-dialog-overlay in Vaadin 25 —
  // address the fields by their accessible labels instead.
  const choreName = page.getByLabel('Chore name');
  await choreName.waitFor();
  await type(choreName, 'Shovel snow');
  const choreEmoji = page.getByRole('dialog').getByRole('textbox', { name: 'Emoji' });
  await type(choreEmoji, '❄️');
  await say('Once a year, winter only.', 'Once a year · Winter ❄️');
  await tap(page.getByLabel('How often?'));
  await settle(400);
  await tap(page.getByRole('option', { name: 'Once a year' }));
  await settle(300);
  await tap(page.getByRole('checkbox', { name: 'Winter' }));
  await settle(300);
  await tap(page.getByRole('button', { name: 'Save' }));
  await settle(600);

  // The filters, with the new chore waiting for winter.
  await tap(tab('Chores'));
  await page.waitForSelector('.filter-chip');
  await say("Filters show what's due now, and what waits.", 'Due now · Off-season');
  await tap(chip('Yearly'));
  await settle(1200);
  await tap(chip('Off-season'));
  await settle(1300);
  await tap(chip('All'));

  // Stats, then out.
  await tap(tab('Stats'));
  await say('Streaks, fairness and stats, built in.', 'Today · week · month · all time', { lead: 1800 });
  await outro('Tap a chore. Keep it fair.<br>Celebrate together.',
    'Tap a chore. Keep it fair. Celebrate together.');
}
