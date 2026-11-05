/* Run with NODE_PATH pointing to a temporary miniprogram-automator installation. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const automator = require('miniprogram-automator');
const { REFERENCES } = require('../miniprogram/drawing/utils/references');
const PROJECT = path.resolve(__dirname, '..');
const OUTPUT = process.env.DRAWING_E2E_OUTPUT || '/private/tmp/egg-drawing-e2e';
const ROUTE = 'drawing/pages/draw/draw';
const METHODS = ['getStorageSync', 'setStorageSync', 'removeStorageSync', 'request', 'showModal'];
const pause = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds));

async function until(check, message, timeout = 12000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    if (await check()) return;
    await pause(100);
  }
  throw new Error(message);
}
async function tap(page, selector) {
  const element = await page.$(selector);
  assert.ok(element, `Missing ${selector}`);
  await element.tap();
}
async function counters(mp) {
  return mp.evaluate(() => ({ ...getApp().__drawingE2E.counts }));
}
async function stroke(canvas, coordinates, duration = 0) {
  const point = (x, y) => ({ identifier: 0, clientX: x, clientY: y, pageX: x, pageY: y });
  const start = point(...coordinates[0]);
  await canvas.touchstart({ touches: [start], changedTouches: [start] });
  for (const coordinate of coordinates.slice(1)) {
    if (duration) await pause(duration / (coordinates.length - 1));
    const next = point(...coordinate);
    await canvas.touchmove({ touches: [next], changedTouches: [next] });
  }
  await canvas.touchend({ touches: [], changedTouches: [point(...coordinates.at(-1))] });
}
async function installFixture(mp) {
  await mp.evaluate(() => {
    const pet = { id: 'drawing-fixture-pet', ownerId: 'drawing-fixture-user',
      name: '画画测试', hatchStatus: 'HATCHED', prototype: '锦鲤',
      tasks: { nicknameDone: true }, shell: { color: '#EDE78E', pattern: '星星' },
      preferences: { wishes: [], lessons: [] },
      deviceId: 'drawing-fixture-device', createdAt: Date.now() - 86400000,
      nickname: '画画测试', expectedHatchTime: new Date(Date.now() - 86400000).toISOString() };
    const session = { token: 'drawing-fixture-only', userId: 'drawing-fixture-user',
      openid: 'drawing-fixture-only', expire: 7200, issuedAt: Date.now(), hasPhone: true };
    getApp().__drawingE2E = { pet, cost: 0, cancelModal: false, originalGlobalData: { ...getApp().globalData },
      counts: { config: 0, start: 0, complete: 0, modal: 0, writes: 0 }, submissions: [], startBodies: [],
      storage: { eggbaby_auth_token: session.token, eggbaby_auth_user_id: session.userId,
        eggbaby_auth_openid: session.openid, eggbaby_auth_expire: session.expire,
        eggbaby_auth_issued_at: session.issuedAt, eggbaby_auth_has_phone: true,
        eggbaby_mvp_user_v1: { id: session.userId }, eggbaby_mvp_pet_v1: pet,
        eggbaby_mvp_pets_v1: { [pet.id]: pet }, eggbaby_active_pet_v1: pet.id } };
    getApp().applySession(session);
    getApp().globalData.authReady = Promise.resolve(session);
    getApp().globalData.welcomeCompleted = true;
  });
  await mp.mockWxMethod('getStorageSync', key => getApp().__drawingE2E.storage[key]);
  await mp.mockWxMethod('setStorageSync', () => { getApp().__drawingE2E.counts.writes += 1; });
  await mp.mockWxMethod('removeStorageSync', () => {});
  await mp.mockWxMethod('showModal', () => {
    const fixture = getApp().__drawingE2E;
    fixture.counts.modal += 1;
    return { confirm: !fixture.cancelModal, cancel: fixture.cancelModal };
  });
  await mp.mockWxMethod('request', options => {
    const fixture = getApp().__drawingE2E;
    const url = options.url;
    let data = [];
    if (url.includes('/app/drawing/config')) {
      fixture.counts.config += 1;
      data = { earnStars: fixture.cost > 0 ? 0 : 8, consumeStars: fixture.cost, dailyLimit: 3,
        remainingPlays: 3, rewardAvailable: true };
    } else if (url.includes('/app/drawing/start')) {
      fixture.counts.start += 1;
      fixture.startBodies.push(options.data);
      data = { sessionId: `drawing-fixture-${fixture.counts.start}`, referenceId: 'short_pencil' };
    } else if (/\/app\/drawing\/[^/]+\/complete/.test(url)) {
      fixture.counts.complete += 1;
      fixture.submissions.push({ url, body: options.data || null });
      data = { sessionId: 'drawing-fixture-1', awardedStars: 5,
        rewardStatus: 'NEW_REWARD', balance: 43, completedAt: new Date().toISOString() };
    } else if (url.includes('/pet/list')) data = [fixture.pet];
    else if (url.includes('/story-state')) data = { bigSceneName: '在家', smallSceneName: '卧室', imageUrl: '', caption: '' };
    else if (url.includes('/star/balance')) data = { balance: 38 };
    else if (url.includes('/app/game/list')) data = [{ id: 81, gameType: 'DRAW_TOGETHER',
      gameTypeName: '一起画', earnStars: 8, consumeStars: 0 }];
    else if (url.includes('/daily-note/today')) data = { title: '一起画', content: '来画一画',
      game: { gameType: 'DRAW_TOGETHER', gameTypeName: '一起画' } };
    else if (url.includes('/daily-note/open')) data = { awarded: false, amount: 0 };
    else if (url.includes('/wechat/profile')) data = { nickname: '画画测试' };
    return { statusCode: 200, data: { code: 0, data } };
  });
}

async function main() {
  fs.mkdirSync(OUTPUT, { recursive: true });
  const mp = await automator.launch({ projectPath: PROJECT,
    cliPath: '/Applications/wechatwebdevtools.app/Contents/MacOS/cli', timeout: 60000 });
  const checks = [];
  try {
    console.log('Connected; installing isolated fixture');
    await installFixture(mp);
    console.log('Fixture ready; opening activity');
    if (process.env.DRAWING_E2E_CASE !== 'paid') {
      const activity = await mp.reLaunch('/pages/iaa-activities/iaa-activities');
      await until(async () => (await activity.$$('.activities-row')).length === 1, 'Activity did not load');
      await tap(activity, '.activities-row');
      await until(async () => (await mp.currentPage()).path === ROUTE, 'Activity route failed');
      const draw = await mp.currentPage();
      console.log('Drawing route entered; waiting for preview');
      await until(async () => await draw.data('peekVisible'), 'Automatic reference preview failed');
      assert.equal(await draw.data('canvasReady'), true);
      assert.equal(await draw.data('looksRemaining'), 3);
      const previewAt = Date.now();
      await pause(350);
      await mp.screenshot({ path: path.join(OUTPUT, 'reference-preview.png') });
      await pause(1000);
      assert.equal(await draw.data('peekVisible'), true, 'Preview closed before five seconds');
      await until(async () => !(await draw.data('peekVisible')), 'Preview did not auto close', 7000);
      assert.ok(Date.now() - previewAt >= 4000, 'Automatic preview too short');
      checks.push('activity tap, compiled Canvas 2D, automatic five-second preview');
      console.log('Preview passed; checking 82 PNG assets');

      const assetResults = await mp.evaluate(function (sources) {
        return Promise.all(sources.map(src => new Promise(resolve => wx.getImageInfo({ src,
          success: image => resolve({ src, ok: image.width > 0 && image.height > 0 }),
          fail: () => resolve({ src, ok: false }) }))));
      }, REFERENCES.flatMap(item => [item.line, item.color]));
      assert.equal(REFERENCES.length, 41);
      assert.deepEqual(assetResults.filter(item => !item.ok), [], 'PNG asset failed to decode');
      checks.push('all 41 references / 82 PNGs decoded by wx.getImageInfo');
      console.log('Assets passed; testing manual peeks');
      const previewCanvas = await draw.$('#drawingCanvas');
      const previewOffset = await previewCanvas.offset();
      await stroke(previewCanvas, [[previewOffset.left + 40, previewOffset.top + 50],
        [previewOffset.left + 180, previewOffset.top + 150]]);
      assert.equal(await draw.data('hasContent'), true);

      for (let index = 0; index < 3; index += 1) {
        await tap(draw, '.look-pill');
        assert.equal(await draw.data('looksRemaining'), 2 - index);
        assert.equal(await draw.data('peekVisible'), true);
        if (index === 0) {
          await pause(350);
          await mp.screenshot({ path: path.join(OUTPUT, 'peek-with-existing-stroke.png') });
          await mp.navigateTo('/pages/iaa-activities/iaa-activities');
          await pause(800);
          await mp.navigateBack();
          assert.equal(await draw.data('peekVisible'), true, 'Hidden preview did not resume');
          assert.equal((await counters(mp)).start, 1, 'Resume started another session');
        }
        await tap(draw, '.peek');
        await until(async () => !(await draw.data('peekVisible')), 'Preview dismiss failed');
      }
      await tap(draw, '.look-pill');
      assert.equal(await draw.data('peekVisible'), false, 'Fourth peek allowed');
      checks.push('three manual peeks, exhausted button, hide/resume retains session');
      console.log('Peeks passed; testing drawing tools');
      await tap(draw, '.action:nth-child(2)');
      assert.equal(await draw.data('hasContent'), false);

      await tap(draw, '.finish');
      assert.equal((await counters(mp)).complete, 0, 'Blank canvas submitted');
      await tap(draw, '[data-tool="eraser"]');
      assert.equal(await draw.data('tool'), 'eraser');
      await tap(draw, '[data-size="22"]');
      assert.equal(await draw.data('eraserWidth'), 22);
      await tap(draw, '[data-tool="sticker"]');
      await tap(draw, '[data-sticker="heart"]');
      assert.equal(await draw.data('sticker'), 'heart');
      const canvas = await draw.$('#drawingCanvas');
      const offset = await canvas.offset();
      const size = await canvas.size();
      const xy = (x, y) => [offset.left + Number(size.width) * x, offset.top + Number(size.height) * y];
      await stroke(canvas, [xy(0.4, 0.4)]);
      assert.equal(await draw.data('hasContent'), true, 'Sticker gesture did not paint');
      await tap(draw, '.finish');
      assert.equal((await counters(mp)).complete, 0, 'Sticker-only canvas submitted');
      await tap(draw, '.action');
      assert.equal(await draw.data('hasContent'), false, 'Undo failed');
      await tap(draw, '[data-tool="brush"]');
      await tap(draw, '.color-trigger');
      await tap(draw, '[data-color="#D98652"]');
      assert.equal(await draw.data('color'), '#D98652');
      await stroke(canvas, [xy(0.3, 0.3), xy(0.7, 0.7)]);
      await tap(draw, '.action:nth-child(2)');
      assert.equal(await draw.data('hasContent'), false, 'Clear failed');
      await tap(draw, '.finish');
      assert.equal((await counters(mp)).complete, 0, 'Cleared canvas submitted');
      checks.push('real tool taps, sticker gesture, brush color, undo, clear, accidental completion blocked');
      console.log('Tool and accidental-tap checks passed; drawing valid strokes');

      for (let index = 0; index < 5; index += 1) {
        const y = 0.2 + index * 0.13;
        await stroke(canvas, [xy(0.15, y), xy(0.35, y + 0.04), xy(0.6, y - 0.03), xy(0.85, y)], 1200);
        console.log(`Brush stroke ${index + 1}/5 complete`);
      }
      console.log('Capturing board');
      await mp.screenshot({ path: path.join(OUTPUT, 'drawing-board.png') });
      console.log('Submitting session completion after local validation');
      await tap(draw, '.finish');
      await until(async () => Boolean(await draw.data('result')), 'Valid gesture drawing did not complete');
      assert.equal((await counters(mp)).complete, 1);
      assert.equal(await draw.data('result.awardedStars'), 5, 'UI must show actual awarded amount, not configured eight');
      assert.match(await (await draw.$('.result-award')).text(), /\+5/);
      const submission = await mp.evaluate(() => getApp().__drawingE2E.submissions[0]);
      assert.deepEqual(Object.keys(submission).sort(), ['body', 'url']);
      assert.match(submission.url, /\/app\/drawing\/drawing-fixture-1\/complete$/);
      assert.equal(submission.body, null, 'Completion must not upload strokes, coordinates or timestamps');
      await pause(500);
      await mp.screenshot({ path: path.join(OUTPUT, 'reward-result.png') });
      await tap(draw, '.result-ok');
      await until(async () => (await mp.currentPage()).path === activity.path, 'Result OK did not return');
      assert.equal((await counters(mp)).complete, 1);
      checks.push('five real brush gestures pass local validation; session completion sent once without body, coordinates or timestamps; actual +5 reward; OK returns');

    }
    const startsBeforePaid = (await counters(mp)).start;
    await mp.evaluate(() => { getApp().__drawingE2E.cost = 3; getApp().__drawingE2E.cancelModal = true; });
    const modalsBeforeStart = (await counters(mp)).modal;
    console.log('Opening drawing fixture with configured completion cost');
    // The home view in this devtools session does not complete its native render callbacks.
    // Keep its optional WXML journey explicit; the default drawing journey is independent.
    if (process.env.DRAWING_E2E_LETTER === '1') {
      console.log('Reward passed; opening home for letter');
      await mp.evaluate(() => { wx.reLaunch({ url: '/pages/home/home' }); });
      await until(async () => (await mp.currentPage()).path === 'pages/home/home', 'Home route failed');
      const home = await mp.currentPage();
      console.log('Home opened; preparing letter');
      await until(async () => !(await home.data('petRestoreLoading')), 'Home pet not ready');
      await until(async () => Boolean(await home.$('.story-chat-button--envelope')), 'Letter envelope did not render');
      await tap(home, '.story-chat-button--envelope');
      await until(async () => Boolean(await home.$('.daily-note-letter__game')), 'Letter did not render');
      console.log('Tapping letter invitation');
      await tap(home, '.daily-note-letter__game');
      await until(async () => (await mp.currentPage()).path === ROUTE, 'Letter route failed');
    } else {
      await mp.evaluate(() => { wx.reLaunch({ url: '/drawing/pages/draw/draw?source=activity' }); });
      await until(async () => (await mp.currentPage()).path === ROUTE, 'Drawing route failed');
    }
    const paid = await mp.currentPage();
    await until(async () => Boolean(await paid.data('peekVisible')), 'Drawing did not prepare reference');
    assert.equal((await counters(mp)).start, startsBeforePaid + 1);
    assert.equal(await paid.data('started'), true);
    assert.equal((await counters(mp)).modal, modalsBeforeStart, 'Start must not confirm a snapshot price');
    const startBody = await mp.evaluate(() => getApp().__drawingE2E.startBodies.at(-1));
    assert.deepEqual(Object.keys(startBody).sort(), ['petId', 'requestId']);
    await mp.screenshot({ path: path.join(OUTPUT, 'configured-cost-start.png') });
    checks.push('configured completion cost does not prompt or submit any amount at start');
    if (process.env.DRAWING_E2E_LETTER === '1') checks.push('real letter invitation tap shares drawing route');
    assert.equal(await paid.data('canvasReady'), true);
    await tap(paid, '.peek');
    const paidCanvas = await paid.$('#drawingCanvas');
    const paidOffset = await paidCanvas.offset();
    await stroke(paidCanvas, [[paidOffset.left + 30, paidOffset.top + 30], [paidOffset.left + 80, paidOffset.top + 80]]);
    assert.equal(await paid.data('hasContent'), true, 'Canvas did not accept touch after start');
    checks.push('configured completion cost starts once and accepts real canvas touch');
    const report = { checks, skipped: process.env.DRAWING_E2E_LETTER === '1' ? [] :
      ['Letter WXML invitation: devtools home render/callback limitation; opt in with DRAWING_E2E_LETTER=1.'], counters: await counters(mp), artifacts: OUTPUT,
      limitations: ['Mocked API/storage; no real backend billing, device rendering, background OS or audio validation.',
        'Hide/show uses real page navigation; configured-cost flow opens the registered drawing route directly.'] };
    fs.writeFileSync(path.join(OUTPUT, 'report.json'), JSON.stringify(report, null, 2));
    console.log(JSON.stringify(report, null, 2));
  } catch (error) {
    const current = await mp.currentPage().catch(() => null);
    if (current) {
      console.error(`Current route: ${current.path}`);
      console.error(JSON.stringify(await mp.evaluate(() => { const d = getCurrentPages().pop().data; return {
        authChecked: d.authChecked, petRestoreLoading: d.petRestoreLoading, stage: d.stage,
        hasPet: Boolean(d.pet), storyAtHome: d.storyAtHome, hasDailyNote: Boolean(d.dailyNote),
        dailyNoteVisible: d.dailyNoteVisible, hasLetterSnapshot: Boolean(d.dailyNoteSnapshot) }; })));
      await mp.screenshot({ path: path.join(OUTPUT, 'failure-page.png') }).catch(() => {});
    }
    throw error;
  } finally {
    // Stop page timers before restoring boundaries; never read, print or write real auth/storage.
    const cleanupErrors = [];
    await mp.evaluate(() => {
      const page = getCurrentPages().pop();
      if (page && page.onHide) page.onHide();
      const app = getApp();
      if (app.__drawingE2E) app.globalData = app.__drawingE2E.originalGlobalData;
    }).catch(() => { cleanupErrors.push('application state'); });
    for (const name of METHODS) {
      await mp.restoreWxMethod(name).catch(() => { cleanupErrors.push(name); });
    }
    await mp.evaluate(() => { delete getApp().__drawingE2E; }).catch(() => { cleanupErrors.push('fixture memory'); });
    await mp.disconnect();
    if (cleanupErrors.length) throw new Error(`E2E cleanup failed: ${cleanupErrors.join(', ')}`);
  }
}
main().catch(error => { console.error(error.stack); process.exitCode = 1; });
