import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { presentAsset } from '../src/utils/pdcNfcState.mjs'

describe('presentAsset', () => {
  it('drops secrets', () => {
    const view = presentAsset({
      assetNo: 'A1', claimRef: 'secret', schemeCiphertext: 'cipher',
      schemeSha256: 'sha', tagUid: 'uid123'
    })
    assert.ok(view.assetNo)
    assert.ok(view.schemeSha256)
    assert.equal(view.claimRef, undefined)
    assert.equal(view.schemeCiphertext, undefined)
    assert.equal(view.tagUid, undefined)
  })

  it('handles null', () => {
    assert.equal(presentAsset(null), null)
  })

  it('handles undefined', () => {
    assert.equal(presentAsset(undefined), null)
  })

  it('preserves allowed fields', () => {
    const view = presentAsset({
      id: 42,
      assetNo: 'A001',
      batchNo: 'B001',
      itemNo: 1,
      skuCode: 'SKU01',
      prototype: '锦鲤',
      wechatSn: 'SN123',
      status: 'IN_STOCK',
      schemeSha256: 'abc123',
      stockBusinessNo: 'BN001',
      activationBusinessNo: 'ABN001',
      claimedAt: '2026-07-30T10:00:00Z',
      stockedAt: '2026-07-29T10:00:00Z',
      activatedAt: null
    })
    assert.equal(view.id, 42)
    assert.equal(view.assetNo, 'A001')
    assert.equal(view.batchNo, 'B001')
    assert.equal(view.itemNo, 1)
    assert.equal(view.skuCode, 'SKU01')
    assert.equal(view.prototype, '锦鲤')
    assert.equal(view.wechatSn, 'SN123')
    assert.equal(view.status, 'IN_STOCK')
    assert.equal(view.schemeSha256, 'abc123')
    assert.equal(view.stockBusinessNo, 'BN001')
    assert.equal(view.activationBusinessNo, 'ABN001')
    assert.equal(view.claimedAt, '2026-07-30T10:00:00Z')
    assert.equal(view.stockedAt, '2026-07-29T10:00:00Z')
    assert.equal(view.activatedAt, null)
  })

  it('drops claimHash and scheme URL fields', () => {
    const view = presentAsset({
      assetNo: 'A1',
      claimHash: 'hashvalue',
      schemeUrl: 'https://example.com/scheme',
      schemeCiphertext: 'encrypted'
    })
    assert.ok(view.assetNo)
    assert.equal(view.claimHash, undefined)
    assert.equal(view.schemeUrl, undefined)
    assert.equal(view.schemeCiphertext, undefined)
  })

  it('preserves statusTimeline when present', () => {
    const timeline = [
      { status: 'VERIFIED', time: '2026-07-28T10:00:00Z' },
      { status: 'IN_STOCK', time: '2026-07-29T10:00:00Z' }
    ]
    const view = presentAsset({
      assetNo: 'A1',
      statusTimeline: timeline
    })
    assert.deepEqual(view.statusTimeline, timeline)
  })

  it('omits statusTimeline when absent', () => {
    const view = presentAsset({ assetNo: 'A1' })
    assert.equal(view.statusTimeline, undefined)
  })

  it('returns empty object for empty input', () => {
    const view = presentAsset({})
    assert.deepEqual(view, {})
  })
})
