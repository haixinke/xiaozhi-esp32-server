/**
 * NFC 生产管理纯状态逻辑模块
 * 所有函数均无副作用，可在 Node.js 环境中直接测试。
 */

/**
 * 根据批次后端状态返回可用操作按钮
 * 后端仅允许 DRAFT 批次启动 Scheme 任务（PdcNfcSchemeJobServiceImpl.start）。
 * SCHEME_GENERATING 期间页面提供的是"查看进度"入口，不是再次生成。
 * @param {string} status - 批次状态
 * @returns {{ generateScheme: boolean, createWriteJob: boolean, stockIn: boolean }}
 */
export function batchActions(status) {
  return {
    generateScheme: status === 'DRAFT',
    createWriteJob: status === 'READY_FOR_WRITE',
    stockIn: false  // 始终从资产页面管理入库
  }
}

/**
 * 返回商品类型的 modelId 显示文本
 * @param {{ modelId?: string|null }|null|undefined} productType
 * @returns {string}
 */
export function modelIdLabel(productType) {
  if (!productType?.modelId) return '待微信审核配置'
  return productType.modelId
}

/**
 * 返回 Element UI el-tag 的 type 属性
 * @param {string} status
 * @returns {string}
 */
export function statusBadgeType(status) {
  const map = {
    'CREATED': 'info',
    'SCHEME_GENERATING': 'warning',
    'READY_FOR_WRITE': 'success',
    'WRITING': 'warning',
    'STOCKED': '',
    'COMPLETED': 'success',
    'CANCELLED': 'danger',
    'PENDING': 'info',
    'RUNNING': 'warning',
    'SUCCEEDED': 'success',
    'FAILED': 'danger',
    'EXPORTED': '',
    'RESULT_IMPORTED': 'warning',
    'SCHEME_GENERATED': 'warning',
    'WRITTEN': '',
    'VERIFIED': 'success',
    'IN_STOCK': '',
    'ACTIVE': 'success',
    'CLAIMED': '',
    'DISABLED': 'danger',
    'SCRAPPED': 'danger'
  }
  return map[status] ?? 'info'
}

/**
 * 返回状态的中文显示文本
 * @param {string} status
 * @returns {string}
 */
export function statusLabel(status) {
  const map = {
    'DRAFT': '草稿',
    'CREATED': '已创建',
    'SCHEME_GENERATING': 'Scheme生成中',
    'READY_FOR_WRITE': '就绪',
    'WRITING': '写卡中',
    'READY_FOR_STOCK': '待入库',
    'STOCKED': '已入库',
    'COMPLETED': '已完成',
    'CLOSED': '已关闭',
    'CANCELLED': '已取消',
    'PENDING': '等待中',
    'RUNNING': '运行中',
    'SUCCEEDED': '成功',
    'FAILED': '失败',
    'PARTIAL_SUCCESS': '部分成功',
    'EXPORTED': '已导出',
    'RESULT_IMPORTED': '已导入',
    'SCHEME_GENERATED': 'Scheme已生成',
    'WRITTEN': '已写入',
    'VERIFIED': '已验证',
    'IN_STOCK': '已入库',
    'ACTIVE': '已激活',
    'CLAIMED': '已领取',
    'DISABLED': '已禁用',
    'SCRAPPED': '已报废',
    // 伪状态：扫码查询的资产不在库 / 后端返回了映射外状态
    'NOT_FOUND': '未找到',
    'UNKNOWN': '未知状态'
  }
  // 未命中映射时原样返回，暴露后端新增枚举的漏配而非吞掉
  return map[status] ?? status
}

/**
 * 返回后台操作类型的中文显示文本。
 * 取值来源两类：PdcNfcAdminOperationType 后台管理操作 +
 * 手动写卡链路的审计动作（PdcNfcManualWriteServiceImpl.logOperation：
 * SCHEME_REVEAL / PdcNfcManualMarkAction 各值 / TOUCH_VERIFY）。
 * @param {string} operationType
 * @returns {string}
 */
export function operationTypeLabel(operationType) {
  const map = {
    'WRITE_RESULT_IMPORT': '写卡结果导入',
    'STOCK_IN': '入库',
    'ACTIVATE': '激活',
    'DISABLE': '禁用',
    'SCRAP': '报废',
    // 手动写卡模式（ADR 0003）审计动作
    'SCHEME_REVEAL': '查看 Scheme',
    'MARK_WRITTEN': '标记已写入',
    'MARK_WRITE_FAILED': '标记写入失败',
    'MARK_VERIFIED': '标记验证通过',
    'MARK_LOCKED': '标记已锁卡',
    'TOUCH_VERIFY': '触碰自验证'
  }
  return map[operationType] ?? operationType
}

/**
 * 返回商品类型能力模式的中文显示文本（capabilityMode 为普通字符串，非枚举）
 * @param {string} capabilityMode
 * @returns {string}
 */
export function capabilityModeLabel(capabilityMode) {
  const map = {
    'ONE_DEVICE_ONE_CODE': '一机一码'
  }
  return map[capabilityMode] ?? capabilityMode
}

/**
 * 手动写卡页的资产状态中文文案（语境化措辞，与全局 statusLabel 不同：
 * 操作员视角关注"下一步做什么"）
 * @param {string} status
 * @returns {string}
 */
export function manualWriteStatusLabel(status) {
  const map = {
    'SCHEME_GENERATED': '待写入',
    'WRITTEN': '已写入待验证',
    'VERIFIED': '已验证'
  }
  return map[status] ?? status
}

/**
 * 手动写卡页的资产状态 el-tag type
 * @param {string} status
 * @returns {string}
 */
export function manualWriteStatusTagType(status) {
  const map = {
    'SCHEME_GENERATED': 'info',
    'WRITTEN': 'warning',
    'VERIFIED': 'success'
  }
  return map[status] ?? 'info'
}

/**
 * 格式化日期字符串用于展示
 * @param {string|number|Date|null|undefined} dateStr
 * @returns {string}
 */
export function formatDate(dateStr) {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  if (Number.isNaN(d.getTime())) return '-'
  return d.toLocaleString('zh-CN')
}

// ==================== 扫码状态管理 ====================

/**
 * 创建一个空的扫码状态对象
 * @returns {{ codes: string[], maxCodes: number }}
 */
export function emptyScannerState() {
  return { codes: [], maxCodes: 500 }
}

/**
 * 接受一次扫码输入，自动去空格、去重、封顶
 * @param {{ codes: string[], maxCodes: number }} state
 * @param {string} rawInput
 * @returns {{ codes: string[], maxCodes: number }}
 */
export function acceptScan(state, rawInput) {
  const code = (rawInput || '').trim()
  if (!code) return state
  if (state.codes.includes(code)) return state  // 去重
  if (state.codes.length >= state.maxCodes) return state  // 封顶 500
  return { ...state, codes: [...state.codes, code] }
}

/**
 * 从扫码列表中移除一个编码
 * @param {{ codes: string[], maxCodes: number }} state
 * @param {string} code
 * @returns {{ codes: string[], maxCodes: number }}
 */
export function removeScan(state, code) {
  return { ...state, codes: state.codes.filter(c => c !== code) }
}

/**
 * 清空扫码列表
 * @param {{ codes: string[], maxCodes: number }} state
 * @returns {{ codes: string[], maxCodes: number }}
 */
export function clearScanner(state) {
  return { ...state, codes: [] }
}

// ==================== 安全资产展示 ====================

/**
 * 将资产对象转换为安全展示视图（不含任何敏感字段）
 * 敏感字段 claimRef、claimHash、schemeCiphertext、scheme URL、tagUid 等永远不会出现。
 * @param {object|null|undefined} asset
 * @returns {object|null}
 */
export function presentAsset(asset) {
  if (!asset) return null
  const allowed = ['id', 'assetNo', 'batchNo', 'itemNo', 'skuCode', 'prototype',
    'wechatSn', 'status', 'schemeSha256', 'stockBusinessNo',
    'activationBusinessNo', 'claimedAt', 'stockedAt', 'activatedAt']
  const result = {}
  for (const key of allowed) {
    if (asset[key] !== undefined) result[key] = asset[key]
  }
  // 状态时间线（非敏感）
  if (asset.statusTimeline) result.statusTimeline = asset.statusTimeline
  return result
}
