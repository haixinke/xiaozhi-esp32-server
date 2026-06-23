const assert = require('assert');
const VoiceCallManager = require('./voice-call-manager');

(function () {
  const mgr = new VoiceCallManager.VoiceCallManager();
  assert.strictEqual(mgr.getState().state, VoiceCallManager.STATE_IDLE);

  mgr.startCall();
  assert.strictEqual(mgr.getState().state, VoiceCallManager.STATE_CALLING);

  mgr.connect();
  assert.strictEqual(mgr.getState().state, VoiceCallManager.STATE_CONNECTED);
  assert.ok(mgr.getState().startTime > 0);

  mgr.toggleMute();
  assert.strictEqual(mgr.getState().isMuted, true);
  mgr.toggleMute();
  assert.strictEqual(mgr.getState().isMuted, false);

  mgr.hangup();
  assert.strictEqual(mgr.getState().state, VoiceCallManager.STATE_ENDED);

  console.log('voice-call-manager.test.js: ALL PASS');
})();

(function () {
  const mgr = new VoiceCallManager.VoiceCallManager();
  let received = null;
  mgr.onStateChange((state) => { received = state; });

  mgr.startCall();
  assert.strictEqual(received.state, VoiceCallManager.STATE_CALLING);
  mgr.connect();
  assert.strictEqual(received.state, VoiceCallManager.STATE_CONNECTED);
  mgr.hangup();
  assert.strictEqual(received.state, VoiceCallManager.STATE_ENDED);

  console.log('state subscription test: PASS');
})();

(function () {
  const mgr = new VoiceCallManager.VoiceCallManager();
  let restarted = false;
  mgr.setOnRecordRestart(() => { restarted = true; });

  mgr.startCall();
  mgr.connect();

  assert.strictEqual(mgr.getState().durationSeconds, 0);
  const remaining = mgr.getState().recordRestartAt - Date.now();
  assert.ok(remaining > 9 * 60 * 1000 && remaining <= 10 * 60 * 1000, 'recordRestartAt out of range');

  mgr._triggerRecordRestartForTest();
  assert.strictEqual(restarted, true);

  mgr.hangup();
  mgr.destroy();
  console.log('duration/restart test: PASS');
})();
