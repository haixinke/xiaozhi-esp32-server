/* eslint-disable test/no-import-node-test -- zero-dependency API regression gate */
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const feedbackApiSource = await readFile(new URL("./feedback.js", import.meta.url), "utf8");

function sourceBetween(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  assert.notEqual(start, -1, `missing source marker: ${startMarker}`);
  const end = endMarker ? source.indexOf(endMarker, start) : source.length;
  assert.notEqual(end, -1, `missing source marker: ${endMarker}`);
  return source.slice(start, end);
}

test("feedback page query hits the admin page endpoint with filters", () => {
  const source = sourceBetween(feedbackApiSource, "getFeedbackPage(params", "// 查询反馈详情");

  assert.match(source, /\/admin\/feedback\/page\?\$\{queryParams\}/);
  assert.match(source, /\.method\('GET'\)/);
  assert.match(source, /status: params\.status/);
  assert.match(source, /type: params\.type \|\| ''/);
});

test("feedback handle sends PUT to the update endpoint without delete support", () => {
  const source = sourceBetween(feedbackApiSource, "handleFeedback(data", null);

  assert.match(source, /\/admin\/feedback\/update/);
  assert.match(source, /\.method\('PUT'\)/);
  assert.doesNotMatch(feedbackApiSource, /method\('DELETE'\)|\/delete/);
});
