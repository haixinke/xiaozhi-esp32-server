import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  batchActions,
  modelIdLabel,
  statusBadgeType,
  statusLabel,
  operationTypeLabel,
  capabilityModeLabel,
  manualWriteStatusLabel,
  manualWriteStatusTagType,
  formatDate
} from '../src/utils/pdcNfcState.mjs'

describe('batchActions', () => {
  it('buttons follow backend batch state', () => {
    assert.deepEqual(batchActions('DRAFT'), { generateScheme: true, createWriteJob: false, stockIn: false })
    assert.equal(batchActions('READY_FOR_WRITE').createWriteJob, true)
  })

  it('only DRAFT enables generateScheme (backend rejects other states)', () => {
    for (const status of ['DRAFT']) {
      assert.equal(batchActions(status).generateScheme, true)
    }
    for (const status of ['SCHEME_GENERATING', 'READY_FOR_WRITE', 'COMPLETED', 'CANCELLED']) {
      assert.equal(batchActions(status).generateScheme, false)
    }
  })

  it('READY_FOR_WRITE enables createWriteJob but not generateScheme', () => {
    const actions = batchActions('READY_FOR_WRITE')
    assert.equal(actions.generateScheme, false)
    assert.equal(actions.createWriteJob, true)
    assert.equal(actions.stockIn, false)
  })

  it('SCHEME_GENERATING disables both actions (page shows progress entry instead)', () => {
    const actions = batchActions('SCHEME_GENERATING')
    assert.equal(actions.generateScheme, false)
    assert.equal(actions.createWriteJob, false)
  })

  it('stockIn is always false regardless of status', () => {
    for (const status of ['CREATED', 'READY_FOR_WRITE', 'STOCKED', 'COMPLETED', 'CANCELLED']) {
      assert.equal(batchActions(status).stockIn, false)
    }
  })

  it('unknown status disables all actions except stockIn which is always false', () => {
    const actions = batchActions('UNKNOWN_STATUS')
    assert.equal(actions.generateScheme, false)
    assert.equal(actions.createWriteJob, false)
    assert.equal(actions.stockIn, false)
  })
})

describe('modelIdLabel', () => {
  it('blank model id renders pending review', () => {
    assert.equal(modelIdLabel({ modelId: null }), '待微信审核配置')
  })

  it('configured model id returns value', () => {
    assert.equal(modelIdLabel({ modelId: 'MODEL_001' }), 'MODEL_001')
  })

  it('empty string model id renders pending review', () => {
    assert.equal(modelIdLabel({ modelId: '' }), '待微信审核配置')
  })

  it('null productType renders pending review', () => {
    assert.equal(modelIdLabel(null), '待微信审核配置')
  })

  it('undefined productType renders pending review', () => {
    assert.equal(modelIdLabel(undefined), '待微信审核配置')
  })

  it('productType without modelId field renders pending review', () => {
    assert.equal(modelIdLabel({ typeCode: 'TC001' }), '待微信审核配置')
  })
})

describe('statusBadgeType', () => {
  it('maps known statuses to Element UI tag types', () => {
    assert.equal(statusBadgeType('CREATED'), 'info')
    assert.equal(statusBadgeType('SCHEME_GENERATING'), 'warning')
    assert.equal(statusBadgeType('READY_FOR_WRITE'), 'success')
    assert.equal(statusBadgeType('COMPLETED'), 'success')
    assert.equal(statusBadgeType('CANCELLED'), 'danger')
    assert.equal(statusBadgeType('RUNNING'), 'warning')
    assert.equal(statusBadgeType('SUCCEEDED'), 'success')
    assert.equal(statusBadgeType('FAILED'), 'danger')
    assert.equal(statusBadgeType('ACTIVE'), 'success')
    assert.equal(statusBadgeType('DISABLED'), 'danger')
    assert.equal(statusBadgeType('SCRAPPED'), 'danger')
  })

  it('empty string is a valid type (default tag style)', () => {
    assert.equal(statusBadgeType('STOCKED'), '')
    assert.equal(statusBadgeType('EXPORTED'), '')
    assert.equal(statusBadgeType('IN_STOCK'), '')
    assert.equal(statusBadgeType('WRITTEN'), '')
    assert.equal(statusBadgeType('CLAIMED'), '')
  })

  it('unknown status defaults to info', () => {
    assert.equal(statusBadgeType('UNKNOWN'), 'info')
    assert.equal(statusBadgeType(''), 'info')
    assert.equal(statusBadgeType(undefined), 'info')
  })
})

describe('statusLabel', () => {
  it('maps known statuses to Chinese labels', () => {
    assert.equal(statusLabel('CREATED'), '已创建')
    assert.equal(statusLabel('READY_FOR_WRITE'), '就绪')
    assert.equal(statusLabel('COMPLETED'), '已完成')
    assert.equal(statusLabel('CANCELLED'), '已取消')
    assert.equal(statusLabel('RUNNING'), '运行中')
    assert.equal(statusLabel('SUCCEEDED'), '成功')
    assert.equal(statusLabel('FAILED'), '失败')
    assert.equal(statusLabel('EXPORTED'), '已导出')
    assert.equal(statusLabel('RESULT_IMPORTED'), '已导入')
    assert.equal(statusLabel('ACTIVE'), '已激活')
    assert.equal(statusLabel('DISABLED'), '已禁用')
    assert.equal(statusLabel('SCRAPPED'), '已报废')
  })

  it('maps remaining batch and scheme statuses to Chinese labels', () => {
    assert.equal(statusLabel('DRAFT'), '草稿')
    assert.equal(statusLabel('READY_FOR_STOCK'), '待入库')
    assert.equal(statusLabel('CLOSED'), '已关闭')
    assert.equal(statusLabel('PARTIAL_SUCCESS'), '部分成功')
  })

  it('maps pseudo statuses from asset scanning to Chinese labels', () => {
    assert.equal(statusLabel('NOT_FOUND'), '未找到')
    assert.equal(statusLabel('UNKNOWN'), '未知状态')
  })

  it('unknown status returns the raw status string', () => {
    assert.equal(statusLabel('UNKNOWN_STATUS'), 'UNKNOWN_STATUS')
  })
})

describe('operationTypeLabel', () => {
  it('maps admin operation types to Chinese labels', () => {
    assert.equal(operationTypeLabel('WRITE_RESULT_IMPORT'), '写卡结果导入')
    assert.equal(operationTypeLabel('STOCK_IN'), '入库')
    assert.equal(operationTypeLabel('ACTIVATE'), '激活')
    assert.equal(operationTypeLabel('DISABLE'), '禁用')
    assert.equal(operationTypeLabel('SCRAP'), '报废')
  })

  it('unknown operation type returns the raw string', () => {
    assert.equal(operationTypeLabel('SOME_NEW_OP'), 'SOME_NEW_OP')
  })
})

describe('capabilityModeLabel', () => {
  it('maps known capability mode to Chinese label', () => {
    assert.equal(capabilityModeLabel('ONE_DEVICE_ONE_CODE'), '一机一码')
  })

  it('unknown capability mode returns the raw string', () => {
    assert.equal(capabilityModeLabel('NEW_MODE'), 'NEW_MODE')
  })
})

describe('manualWriteStatusLabel', () => {
  it('maps manual write statuses to operator-facing Chinese labels', () => {
    assert.equal(manualWriteStatusLabel('SCHEME_GENERATED'), '待写入')
    assert.equal(manualWriteStatusLabel('WRITTEN'), '已写入待验证')
    assert.equal(manualWriteStatusLabel('VERIFIED'), '已验证')
  })

  it('unknown status returns the raw string', () => {
    assert.equal(manualWriteStatusLabel('IN_STOCK'), 'IN_STOCK')
  })
})

describe('manualWriteStatusTagType', () => {
  it('maps manual write statuses to el-tag types', () => {
    assert.equal(manualWriteStatusTagType('SCHEME_GENERATED'), 'info')
    assert.equal(manualWriteStatusTagType('WRITTEN'), 'warning')
    assert.equal(manualWriteStatusTagType('VERIFIED'), 'success')
    assert.equal(manualWriteStatusTagType('OTHER'), 'info')
  })
})

describe('formatDate', () => {
  it('returns dash for null or undefined', () => {
    assert.equal(formatDate(null), '-')
    assert.equal(formatDate(undefined), '-')
  })

  it('returns dash for empty string', () => {
    assert.equal(formatDate(''), '-')
  })

  it('returns dash for invalid date string', () => {
    assert.equal(formatDate('invalid-date'), '-')
  })

  it('formats a valid ISO date string', () => {
    const result = formatDate('2026-07-30T10:00:00Z')
    assert.ok(result !== '-')
    assert.ok(result.length > 0)
  })

  it('formats a timestamp number', () => {
    const result = formatDate(1785480000000)
    assert.ok(result !== '-')
    assert.ok(result.length > 0)
  })
})
