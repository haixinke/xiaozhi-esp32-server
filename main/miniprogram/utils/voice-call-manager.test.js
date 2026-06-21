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

  mgr.toggleSpeaker();
  assert.strictEqual(mgr.getState().isSpeakerOn, false);
  mgr.toggleSpeaker();
  assert.strictEqual(mgr.getState().isSpeakerOn, true);

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
