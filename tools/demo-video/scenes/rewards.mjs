// "Diamonds, streaks and trophies" — earn, keep, spend, for a parent deciding whether
// incentives are for them. The script is docs/DEMO-VIDEOS-PLAN-2026-10-26.md, video 3. The
// camera opens on a home that already exists, with Alex three approved chores in, so that the
// fourth tap says "Just 1 more to 🌱 Sprout" and the fifth unlocks the trophy inside the video.
// The `setup` export does that off camera: the recorder's first cut begins after it.

/** Off camera: the home, its rules and rewards, and three chores done. */
export async function setup(d) {
  const { page, BASE, settle, tab, card, dialogButton, openSection } = d;
  await page.goto(BASE);
  await page.waitForSelector('vaadin-text-field');
  await page.locator('vaadin-text-field').nth(0).locator('input').fill('The Nest');
  await page.locator('vaadin-text-field').nth(1).locator('input').fill('Alex');
  await page.getByRole('checkbox', { name: 'I agree' }).click();
  await page.locator('vaadin-button', { hasText: 'Create home' }).click();
  await page.waitForSelector('vaadin-dialog-overlay');
  await page.locator('vaadin-button', { hasText: "Let's go" }).click();
  await page.waitForSelector('.task-card');

  await tab('Admin').click();
  // No "did you do this?" question: the taps on camera go straight to the celebration.
  await openSection('Chore rules');
  await page.getByText('Ask "did you do this?"').click();
  await settle(400);
  // One chore worth 💎 5 — the pill on the card is the opening shot.
  await openSection('Chores');
  await page.locator('.list-row', { hasText: 'Cook dinner' }).locator('vaadin-menu-bar').click();
  await page.getByRole('menuitem', { name: 'Edit chore' }).click();
  const credits = page.getByLabel('Reward credits');
  await credits.waitFor();
  await credits.fill('5');
  await page.getByRole('dialog').getByRole('button', { name: 'Save' }).click();
  await settle(500);
  // 🌱 Sprout pays 7, so the fourth tap's "Just 1 more" names a reward and the fifth pays it.
  await openSection('Rewards (credits)');
  const sprout = page.getByLabel('💎 reward for Sprout');
  await sprout.fill('7');
  await sprout.press('Tab');
  await settle(500);
  // Folded again, so the Admin tab opens on camera the way it does for anyone.
  await page.evaluate(() => {
    for (const s of document.querySelectorAll('vaadin-details.admin-section')) s.opened = false;
  });
  await settle(400);

  await tab('Chores').click();
  for (const name of ['Empty dishwasher', 'Take out trash', 'Vacuum']) {
    await card(name).locator('.complete-area').click();
    await dialogButton(/^Done/).click();
    await page.waitForSelector('vaadin-dialog-overlay', { state: 'detached' });
    await settle(300);
  }
  await page.evaluate(() => window.scrollTo(0, 0));
  await settle(800);
}

export default async function rewards(d) {
  const { page, say, speechDone, settle, tap, type, typeSlow, pointAt, tab, card, dialogButton,
    openSection, outro } = d;
  // A payoff is looked at in silence for a moment after its line ends, before the hands move on.
  const beat = async (ms) => { await speechDone(); await settle(ms); };
  await settle(2000);

  // Earn: the pill, the tap, and how near the next trophy is.
  const pill = card('Cook dinner').locator('.card-credit');
  await say('Some chores are worth more. Admins say how much.', '💎 5 on the card', { lead: 1200 });
  await pointAt(pill, 1500);
  await say('Do it, and the diamonds are yours.', '+5 💎');
  await tap(card('Cook dinner').locator('.complete-area'));
  await page.waitForSelector('.trophy-near');
  await settle(600);
  await say('And the app tells you when something bigger is close.', 'Just 1 more…', { lead: 600 });
  await pointAt(page.locator('.trophy-near'), 1800);
  await beat(700);
  await tap(dialogButton(/^Done/));
  await settle(300);

  // The admin's side: a spree bonus added, and what each trophy pays.
  await say('Every day for a week? A spree bonus.', '7 days → 💎');
  await tap(tab('Admin'));
  await settle(400);
  await openSection('Rewards (credits)');
  await type(page.getByLabel('Days'), '7');
  await type(page.getByLabel('Credits', { exact: true }), '10');
  await tap(page.locator('vaadin-button', { hasText: 'Add tier' }));
  await settle(700);
  await say('Trophies at 5, 10, 25 chores and up — each can pay.', '🌱 🧹 🥉 🥈 🏆 🏅 🎓 🦄', { lead: 500 });
  await pointAt(page.getByLabel('💎 reward for Sprout'), 1800);

  // Keep: the fifth chore unlocks 🌱 Sprout, the shelf keeps it, the chip wears it.
  await say('Five chores in: your first trophy.', 'New trophy: Sprout!');
  await tap(tab('Chores'));
  await settle(400);
  await tap(card('Fill dishwasher').locator('.complete-area'));
  await page.waitForSelector('.trophy-shine');
  await beat(2600);
  await say('Trophies are kept, not just celebrated.', 'Your shelf 🏆', { lead: 500 });
  await tap(dialogButton('See my trophies'));
  await page.waitForSelector('.trophy-shelf');
  await beat(1000);
  await say('Eight rungs, from five chores to a thousand.', '5 → 1000 chores');
  await pointAt(page.locator('.trophy-tile').last(), 1800);
  await beat(600);
  await say('Your best one sits on your chip, for all to see.', 'On the leaderboard');
  await tap(tab('Chores'));
  await page.waitForSelector('.trophy-badge');
  await pointAt(page.locator('.trophy-badge').first(), 1600);
  await beat(600);

  // A mis-tap taken back, diamonds and all: the balance on the chip drops again.
  await say('Mis-tap? Undo, and the diamonds go back too.', 'Undo takes it back');
  await tap(card('Cook dinner').locator('.complete-area'));
  await page.waitForSelector('vaadin-dialog-overlay');
  await settle(900);
  await tap(dialogButton(/Oops/));
  await beat(1500);

  // Spend: a redemption, and the ledger on Stats.
  await say('Then spend them on something real.', '🍿 Movie night');
  await tap(tab('Admin'));
  await settle(400);
  await openSection('Rewards (credits)');
  await tap(page.locator('vaadin-button', { hasText: 'Redeem' }).first());
  const amount = page.getByRole('dialog').getByLabel('Amount');
  await amount.waitFor();
  await say('Pick an amount, and say what it was for.', 'Amount · note');
  await amount.fill('10');
  await typeSlow(page.getByRole('dialog').getByLabel('Note'), 'Movie night');
  await settle(400);
  await tap(page.getByRole('dialog').getByRole('button', { name: 'Redeem' }));
  await settle(1000);
  await say("Earned, redeemed, and what's left — all on Stats.", 'Earned · redeemed · balance');
  await tap(tab('Stats'));
  const ledger = page.locator('.stat-tiles.three');
  await ledger.waitFor();
  await ledger.scrollIntoViewIfNeeded();
  await beat(2000);

  // The stance: a reward is optional; the ladder is not.
  await say("Don't want incentives? Leave it empty. Trophies still count.", 'Optional, always');
  await tap(tab('Admin'));
  await settle(400);
  await openSection('Rewards (credits)');
  await pointAt(page.getByLabel('💎 reward for Helper'), 1800);
  await beat(1000);

  await outro('Earn them. Keep them.<br>Spend them together.',
    'Earn them. Keep them. Spend them together.');
}
