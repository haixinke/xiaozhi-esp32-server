/* eslint-disable test/no-import-node-test -- zero-dependency source regression gate */
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { describe, it } from 'node:test'

const writeJobDialogSource = await readFile(
  new URL('../src/components/nfc/NfcWriteJobDialog.vue', import.meta.url),
  'utf8'
)
const importDialogSource = await readFile(
  new URL('../src/components/nfc/NfcWriteResultImportDialog.vue', import.meta.url),
  'utf8'
)

// 回归背景: NfcWriteResultImportDialog 嵌套在 NfcWriteJobDialog 的 el-dialog 内部。
// Element UI 嵌套弹窗若缺少 append-to-body, 内层 dialog 渲染在外层 wrapper 的
// stacking context (z-index 2001) 中, 而遮罩 v-modal 挂在 body 下 (z-index 2002),
// 导致内层弹窗被遮罩压住 —— 整页变灰且无法操作。
describe('NFC write result import dialog - nested modal stacking', () => {
  it('is nested inside NfcWriteJobDialog (context for append-to-body requirement)', () => {
    assert.match(writeJobDialogSource, /<NfcWriteResultImportDialog[\s\S]*?\/>/)
  })

  it('declares append-to-body so the nested dialog escapes the parent stacking context', () => {
    assert.match(importDialogSource, /<el-dialog[\s\S]*?append-to-body[\s\S]*?@close="handleClose"/)
  })
})
