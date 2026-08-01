import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  batchActions,
  modelIdLabel,
  statusBadgeType,
  statusLabel,
  formatDate
} from '../src/utils/pdcNfcState.mjs'

/**
 * Production workflow tests - simulates the full NFC production lifecycle
 * using only the pure state logic (no DOM required).
 */

describe('NFC production workflow - button enable/disable logic', () => {
  it('newly created batch shows no production actions', () => {
    const actions = batchActions('CREATED')
    assert.equal(actions.generateScheme, false)
    assert.equal(actions.createWriteJob, false)
    assert.equal(actions.stockIn, false)
  })

  it('DRAFT batch can generate scheme', () => {
    const actions = batchActions('DRAFT')
    assert.equal(actions.generateScheme, true)
    assert.equal(actions.createWriteJob, false)
  })

  it('READY_FOR_WRITE batch can create write job but not generate scheme', () => {
    const actions = batchActions('READY_FOR_WRITE')
    assert.equal(actions.generateScheme, false)
    assert.equal(actions.createWriteJob, true)
  })

  it('SCHEME_GENERATING batch shows progress entry instead of actions', () => {
    const actions = batchActions('SCHEME_GENERATING')
    assert.equal(actions.generateScheme, false)
    assert.equal(actions.createWriteJob, false)
  })

  it('STOCKED batch has no production actions', () => {
    const actions = batchActions('STOCKED')
    assert.equal(actions.generateScheme, false)
    assert.equal(actions.createWriteJob, false)
  })

  it('COMPLETED batch has no production actions', () => {
    const actions = batchActions('COMPLETED')
    assert.equal(actions.generateScheme, false)
    assert.equal(actions.createWriteJob, false)
  })

  it('CANCELLED batch has no production actions', () => {
    const actions = batchActions('CANCELLED')
    assert.equal(actions.generateScheme, false)
    assert.equal(actions.createWriteJob, false)
  })
})

describe('NFC production workflow - status badge mapping', () => {
  const batchStatuses = ['CREATED', 'SCHEME_GENERATING', 'READY_FOR_WRITE', 'STOCKED', 'COMPLETED', 'CANCELLED']
  const jobStatuses = ['PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'EXPORTED', 'RESULT_IMPORTED']
  const assetStatuses = ['SCHEME_GENERATED', 'WRITTEN', 'VERIFIED', 'IN_STOCK', 'ACTIVE', 'CLAIMED', 'DISABLED', 'SCRAPPED']

  it('all batch statuses have valid badge types', () => {
    for (const s of batchStatuses) {
      const type = statusBadgeType(s)
      assert.ok(typeof type === 'string', `batch status ${s} should return a string badge type`)
    }
  })

  it('all job statuses have valid badge types', () => {
    for (const s of jobStatuses) {
      const type = statusBadgeType(s)
      assert.ok(typeof type === 'string', `job status ${s} should return a string badge type`)
    }
  })

  it('all asset statuses have valid badge types', () => {
    for (const s of assetStatuses) {
      const type = statusBadgeType(s)
      assert.ok(typeof type === 'string', `asset status ${s} should return a string badge type`)
    }
  })

  it('error/danger statuses map to danger badge', () => {
    assert.equal(statusBadgeType('CANCELLED'), 'danger')
    assert.equal(statusBadgeType('FAILED'), 'danger')
    assert.equal(statusBadgeType('DISABLED'), 'danger')
    assert.equal(statusBadgeType('SCRAPPED'), 'danger')
  })

  it('success statuses map to success badge', () => {
    assert.equal(statusBadgeType('READY_FOR_WRITE'), 'success')
    assert.equal(statusBadgeType('COMPLETED'), 'success')
    assert.equal(statusBadgeType('SUCCEEDED'), 'success')
    assert.equal(statusBadgeType('VERIFIED'), 'success')
    assert.equal(statusBadgeType('ACTIVE'), 'success')
  })

  it('in-progress statuses map to warning badge', () => {
    assert.equal(statusBadgeType('SCHEME_GENERATING'), 'warning')
    assert.equal(statusBadgeType('RUNNING'), 'warning')
    assert.equal(statusBadgeType('RESULT_IMPORTED'), 'warning')
    assert.equal(statusBadgeType('SCHEME_GENERATED'), 'warning')
  })
})

describe('NFC production workflow - status label mapping', () => {
  it('all batch statuses have Chinese labels', () => {
    const batchStatuses = ['CREATED', 'SCHEME_GENERATING', 'READY_FOR_WRITE', 'STOCKED', 'COMPLETED', 'CANCELLED']
    for (const s of batchStatuses) {
      const label = statusLabel(s)
      assert.ok(label !== s, `status ${s} should have a translated label, not the raw value`)
      assert.ok(label.length > 0, `status ${s} label should not be empty`)
    }
  })

  it('all job statuses have Chinese labels', () => {
    const jobStatuses = ['PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'EXPORTED', 'RESULT_IMPORTED']
    for (const s of jobStatuses) {
      const label = statusLabel(s)
      assert.ok(label !== s, `status ${s} should have a translated label, not the raw value`)
      assert.ok(label.length > 0, `status ${s} label should not be empty`)
    }
  })

  it('all asset statuses have Chinese labels', () => {
    const assetStatuses = ['SCHEME_GENERATED', 'WRITTEN', 'VERIFIED', 'IN_STOCK', 'ACTIVE', 'CLAIMED', 'DISABLED', 'SCRAPPED']
    for (const s of assetStatuses) {
      const label = statusLabel(s)
      assert.ok(label !== s, `status ${s} should have a translated label, not the raw value`)
      assert.ok(label.length > 0, `status ${s} label should not be empty`)
    }
  })

  it('unknown status falls back to the raw string', () => {
    assert.equal(statusLabel('NOT_A_REAL_STATUS'), 'NOT_A_REAL_STATUS')
  })
})

describe('NFC production workflow - modelId display', () => {
  it('product type without modelId shows pending message', () => {
    assert.equal(modelIdLabel({ typeCode: 'TC001', typeName: 'Test Product', modelId: null }), '待微信审核配置')
  })

  it('product type with modelId shows the configured value', () => {
    assert.equal(modelIdLabel({ typeCode: 'TC001', typeName: 'Test Product', modelId: 'mfg_abc123' }), 'mfg_abc123')
  })

  it('handles missing product type object gracefully', () => {
    assert.equal(modelIdLabel(undefined), '待微信审核配置')
    assert.equal(modelIdLabel(null), '待微信审核配置')
    assert.equal(modelIdLabel({}), '待微信审核配置')
  })
})

describe('NFC production workflow - date formatting', () => {
  it('null and undefined dates render as dash', () => {
    assert.equal(formatDate(null), '-')
    assert.equal(formatDate(undefined), '-')
  })

  it('empty string renders as dash', () => {
    assert.equal(formatDate(''), '-')
  })

  it('invalid date renders as dash', () => {
    assert.equal(formatDate('not-a-date'), '-')
  })

  it('valid dates produce a non-dash formatted string', () => {
    assert.notEqual(formatDate('2026-07-30T10:00:00Z'), '-')
    assert.notEqual(formatDate(Date.now()), '-')
  })
})

describe('NFC production workflow - end-to-end state transitions', () => {
  it('simulates a complete batch lifecycle', () => {
    // 1. Batch created (DRAFT before scheme generation)
    let status = 'DRAFT'
    let actions = batchActions(status)
    assert.equal(actions.generateScheme, true)
    assert.equal(actions.createWriteJob, false)

    // 2. Scheme generation starts
    status = 'SCHEME_GENERATING'
    actions = batchActions(status)
    assert.equal(actions.generateScheme, false)
    assert.equal(actions.createWriteJob, false)

    // 3. Batch becomes ready for write
    status = 'READY_FOR_WRITE'
    actions = batchActions(status)
    assert.equal(actions.generateScheme, false)
    assert.equal(actions.createWriteJob, true)

    // 4. Batch completed
    status = 'COMPLETED'
    actions = batchActions(status)
    assert.equal(actions.generateScheme, false)
    assert.equal(actions.createWriteJob, false)
  })

  it('simulates scheme job status progression', () => {
    const progression = ['PENDING', 'RUNNING', 'SUCCEEDED']
    const badgeTypes = progression.map(s => statusBadgeType(s))
    assert.equal(badgeTypes[0], 'info')      // PENDING
    assert.equal(badgeTypes[1], 'warning')   // RUNNING
    assert.equal(badgeTypes[2], 'success')   // SUCCEEDED

    const labels = progression.map(s => statusLabel(s))
    assert.equal(labels[0], '等待中')
    assert.equal(labels[1], '运行中')
    assert.equal(labels[2], '成功')
  })

  it('simulates write job status progression', () => {
    const progression = ['PENDING', 'RUNNING', 'EXPORTED', 'RESULT_IMPORTED']
    const badgeTypes = progression.map(s => statusBadgeType(s))
    assert.equal(badgeTypes[0], 'info')      // PENDING
    assert.equal(badgeTypes[1], 'warning')   // RUNNING
    assert.equal(badgeTypes[2], '')           // EXPORTED (default)
    assert.equal(badgeTypes[3], 'warning')   // RESULT_IMPORTED

    const labels = progression.map(s => statusLabel(s))
    assert.equal(labels[0], '等待中')
    assert.equal(labels[1], '运行中')
    assert.equal(labels[2], '已导出')
    assert.equal(labels[3], '已导入')
  })

  it('simulates asset status progression', () => {
    const progression = ['SCHEME_GENERATED', 'WRITTEN', 'VERIFIED', 'IN_STOCK', 'ACTIVE']
    const badgeTypes = progression.map(s => statusBadgeType(s))
    assert.equal(badgeTypes[0], 'warning')   // SCHEME_GENERATED
    assert.equal(badgeTypes[1], '')           // WRITTEN (default)
    assert.equal(badgeTypes[2], 'success')   // VERIFIED
    assert.equal(badgeTypes[3], '')           // IN_STOCK (default)
    assert.equal(badgeTypes[4], 'success')   // ACTIVE

    const labels = progression.map(s => statusLabel(s))
    assert.equal(labels[0], 'Scheme已生成')
    assert.equal(labels[1], '已写入')
    assert.equal(labels[2], '已验证')
    assert.equal(labels[3], '已入库')
    assert.equal(labels[4], '已激活')
  })
})
