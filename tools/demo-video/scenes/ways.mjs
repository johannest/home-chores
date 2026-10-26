// "Three ways to run it" — own phones, division styles, the shared device, a child without a
// phone. The script is docs/DEMO-VIDEOS-PLAN-2026-10-26.md, video 1. Three screens: Alex's
// phone, Sam's phone and the kitchen tablet. The home exists before the camera rolls, with
// the history the story needs: last week's chores make Mia the chore master, and Alex has
// done one chore three times today, so the fairness lock is on the board at the first frame.
// The one field typed on camera is Sam's nickname; the admin PIN goes in as dots, and neither
// a caption nor a line says it.

const DAY = 24 * 60 * 60 * 1000;

/** Last week's Monday at 10:00 plus some days and hours, as the backup wants it. */
function lastWeek(days, hours) {
  const monday = new Date();
  monday.setHours(10, 0, 0, 0);
  monday.setDate(monday.getDate() - ((monday.getDay() + 6) % 7) - 7);
  return new Date(monday.getTime() + days * DAY + hours * 3600 * 1000).toISOString();
}

/** The home as the video finds it, built through the app itself and a restored backup. */
export async function setup(d) {
  const { page, BASE, tap, type, settle, tab, openSection, dialogButton } = d;

  // Alex creates The Nest; the home code and the admin PIN come off the created dialog.
  await page.goto(BASE);
  await page.waitForSelector('vaadin-text-field');
  await type(page.locator('vaadin-text-field').nth(0).locator('input'), 'The Nest');
  await type(page.locator('vaadin-text-field').nth(1).locator('input'), 'Alex');
  await tap(page.getByRole('checkbox', { name: 'I agree' }));
  await tap(page.locator('vaadin-button', { hasText: 'Create home' }));
  await page.waitForSelector('vaadin-dialog-overlay');
  const [code, pin] = await page.locator('.pin-box').allTextContents();
  await tap(page.locator('vaadin-button', { hasText: "Let's go" }));
  await page.waitForSelector('.task-card');

  // History through a backup: export the fresh home, add Mia and last week, restore.
  await tap(tab('Admin'));
  await openSection('Backup');
  const href = await page.locator('a[download]', { hasText: 'Download backup' }).getAttribute('href');
  const backup = JSON.parse(await page.evaluate(async (href) => (await fetch(href)).text(), href));
  const alex = backup.members[0];
  const mia = { id: alex.id + 1, name: 'Mia', color: '#f59e0b', admin: false,
    joinedAt: lastWeek(-14, 0), avatar: 'panda', trophiesSeen: null };
  alex.avatar = 'owl';
  alex.trophiesSeen = null;   // whatever Alex has earned was seen long ago: no surprise dialog
  backup.members.push(mia);
  backup.home.approveJoin = false;   // Sam walks straight in on camera
  const task = (name) => backup.tasks.find((t) => t.name === name).id;
  const done = (memberId, taskId, doneAt) => ({ id: backup.completions.length + 1, taskId, memberId,
    doneAt, status: 'APPROVED', feedback: null, reviewedByMemberId: null, reviewedAt: null, note: null });
  backup.completions.push(
    done(mia.id, task('Empty dishwasher'), lastWeek(0, 8)),
    done(mia.id, task('Take out trash'), lastWeek(1, 9)),
    done(alex.id, task('Cook dinner'), lastWeek(2, 8)),
    done(mia.id, task('Make a salad'), lastWeek(3, 7)),
    done(mia.id, task('Take the dog out'), lastWeek(5, 9)),
    done(alex.id, task('Vacuum'), new Date(Date.now() - 3 * 3600 * 1000).toISOString()),
    done(alex.id, task('Vacuum'), new Date(Date.now() - 2 * 3600 * 1000).toISOString()),
    done(alex.id, task('Vacuum'), new Date(Date.now() - 1 * 3600 * 1000).toISOString()));
  await page.locator('vaadin-upload input[type=file]').setInputFiles({
    name: 'the-nest.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify(backup)) });
  await tap(dialogButton('Confirm'));

  // Restoring signs Alex out; sign back in with the PIN, so no one waits at the door.
  await page.waitForSelector('vaadin-tab');
  await tap(page.locator('vaadin-tab', { hasText: 'Join a home' }));
  await type(page.getByLabel('Home code'), code);
  await tap(page.getByRole('button', { name: "I'm already a member" }));
  await type(page.getByLabel('Your nickname in this home'), 'Alex');
  await type(page.getByLabel('Admin PIN (optional)'), pin);
  await tap(dialogButton("That's me"));
  await page.waitForSelector('.task-card');

  // The other two screens open on the join link, so the code is already filled in.
  d.sam = await d.screen('Sam');
  await d.sam.page.goto(`${BASE}/?join=${code}`);
  await d.sam.page.waitForSelector('vaadin-text-field');
  d.tablet = await d.screen('Kitchen tablet');
  await d.tablet.page.goto(`${BASE}/?join=${code}`);
  await d.tablet.page.waitForSelector('vaadin-text-field');
  d.pin = pin;
  await settle(2500);   // the restore toasts have gone by the time the camera rolls
  await page.evaluate(() => window.scrollTo(0, 0));
}

export default async function ways(d) {
  const { screens, onCamera, say, speechDone, settle, tap, type, typeSlow, tab, card, dialogButton,
    openSection, outro } = d;
  const [alex] = screens;
  const { sam, tablet } = d;
  /** A silent beat after the line has ended, where the picture deserves one. */
  const beat = async (ms) => { await speechDone(); await settle(ms); };

  // 1 · Everyone's phone. The board as a family finds it, fairness lock included.
  onCamera(alex);
  await say('FlashChores fits the way your family already works.', 'Three ways to run it', { lead: 1600 });
  await beat(700);
  await say("Last week's top helper wears the medal.", "🥇 Last week's best", { lead: 1500 });
  await beat(800);
  await say("Three in a row? It's someone else's turn.", 'You ×3 🔒');
  await card('Vacuum').scrollIntoViewIfNeeded();
  await tap(card('Vacuum').locator('.complete-area'));
  await d.page.waitForSelector('vaadin-dialog-overlay');
  await tap(d.page.locator('vaadin-button', { hasText: 'Yes, I did it' }));
  await beat(900);
  await d.page.evaluate(() => window.scrollTo({ top: 0, behavior: 'smooth' }));
  await say('Everyone on their own phone? Share one code.', "1 · Everyone's phone");
  await tap(d.page.locator('.invite-menu vaadin-menu-bar-button').first());
  await beat(700);
  await d.page.keyboard.press('Escape');

  onCamera(sam);
  await say('A nickname is all a phone needs.', 'No accounts');
  await typeSlow(d.page.getByRole('textbox', { name: 'Your name' }), 'Sam');
  await tap(d.page.getByRole('checkbox', { name: 'I agree' }));
  await tap(d.page.locator('vaadin-button', { hasText: 'Join home' }));
  await d.page.waitForSelector('.task-card');
  await say('Sam does the dishes…', '👇', { lead: 1000 });
  await tap(card('Empty dishwasher').locator('.complete-area'));
  await d.page.waitForSelector('vaadin-dialog-overlay');
  await tap(d.page.locator('vaadin-button', { hasText: 'Yes, I did it' }));
  await settle(900);
  await tap(dialogButton(/^Done/));

  onCamera(alex);
  await say("…and it's on Alex's phone before the sink is dry.", 'Live on every phone 📲', { lead: 900 });
  await tap(d.page.locator('.done-today vaadin-details-summary'));
  await beat(1500);

  // 2 · Who does what. The division style, and whether it is a rule or a nudge.
  await say('Prefer turns? Switch the board to rotating.', '2 · Who does what');
  await tap(tab('Admin'));
  await settle(400);
  await openSection('Chore rules');
  const style = d.page.locator('vaadin-select', { hasText: 'Chore division style' });
  await style.scrollIntoViewIfNeeded();
  await tap(style.locator('vaadin-select-value-button'));
  await settle(400);
  await tap(d.page.getByRole('option', { name: 'Rotating' }));
  await settle(500);
  await say('Enforce it, or keep it as a nudge.', 'Enforced · or suggested');
  const enforced = d.page.locator('vaadin-switch', { hasText: 'Rotating mode' });
  await enforced.scrollIntoViewIfNeeded();
  await tap(enforced);
  await settle(700);
  await tap(tab('Chores'));
  await d.page.waitForSelector('.task-card.mine');
  await d.page.locator('.task-card.mine').first().scrollIntoViewIfNeeded();
  await say('Each member gets one chore a day, rotating.', '⭐ Your turn');
  await beat(1200);

  // 3 · The kitchen tablet. Paired with the PIN, which stays dots.
  onCamera(tablet);
  await say('No phone for everyone? Pair the kitchen tablet.', '3 · The kitchen tablet');
  await tap(d.page.getByRole('button', { name: 'Set up a shared family device' }));
  const pinField = d.page.getByRole('dialog').getByLabel('Admin PIN');
  await pinField.waitFor();
  await settle(500);
  await pinField.evaluate((input) => { input.type = 'password'; });
  await type(pinField, d.pin);
  await settle(900);
  await tap(dialogButton('Pair this device'));
  await d.page.waitForSelector('.device-badge');
  await say('It shows the board for the whole family.', 'One board for all', { lead: 2400 });
  await beat(800);
  await say('Tap a chore, tap your face.', 'Tap your face');
  await tap(card('Take out trash').locator('.complete-area'));
  await d.page.waitForSelector('.who-chip');
  await beat(600);   // the face picker is the moment: let it sit
  await say('It counts for that person, streaks and all.', 'Counts for them 🎉');
  await tap(d.page.locator('.who-chip', { hasText: 'Mia' }));
  await beat(1300);
  await tap(dialogButton(/^Done/));

  // A child without a phone: a chip on the board, chores logged by a parent.
  onCamera(alex);
  await say('A child with no phone still gets a chip…', 'No phone yet');
  await tap(tab('Admin'));
  await settle(400);
  await openSection('Members');
  const add = d.page.getByRole('button', { name: 'Add a member without a phone' });
  await add.scrollIntoViewIfNeeded();
  await tap(add);
  await type(d.page.getByRole('dialog').getByLabel('Name'), 'Lu');
  await tap(d.page.getByRole('dialog').getByRole('checkbox'));
  await tap(dialogButton('Add'));
  await settle(500);
  await say('…and a parent logs their chores.', 'Log a chore for someone');
  await openSection('Log a chore for someone');
  const who = d.page.locator('vaadin-select', { hasText: 'Who did it' });
  await who.scrollIntoViewIfNeeded();
  await tap(who.locator('vaadin-select-value-button'));
  await settle(300);
  await tap(d.page.getByRole('option', { name: 'Lu' }));
  await tap(d.page.locator('vaadin-select', { hasText: 'Which chore' }).locator('vaadin-select-value-button'));
  await settle(300);
  await tap(d.page.getByRole('option', { name: 'Water plants' }));
  await tap(d.page.getByRole('button', { name: 'Log it' }));
  await settle(2600);

  await outro('One home.<br>However your family runs.', 'One home. However your family runs.');
}
