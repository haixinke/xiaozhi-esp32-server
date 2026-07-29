/**
 * NFC 生产管理纯状态逻辑模块
 * 所有函数均无副作用，可在 Node.js 环境中直接测试。
 */

/**
 * 根据批次后端状态返回可用操作按钮
 * @param {string} status - 批次状态
 * @returns {{ generateScheme: boolean, createWriteJob: boolean, stockIn: boolean }}
 */
export function batchActions(status) {
  return {
    generateScheme: status === 'READY_FOR_WRITE' || status === 'SCHEME_GENERATING',
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
    'CREATED': '已创建',
    'SCHEME_GENERATING': 'Scheme生成中',
    'READY_FOR_WRITE': '就绪',
    'STOCKED': '已入库',
    'COMPLETED': '已完成',
    'CANCELLED': '已取消',
    'PENDING': '等待中',
    'RUNNING': '运行中',
    'SUCCEEDED': '成功',
    'FAILED': '失败',
    'EXPORTED': '已导出',
    'RESULT_IMPORTED': '已导入',
    'SCHEME_GENERATED': 'Scheme已生成',
    'WRITTEN': '已写入',
    'VERIFIED': '已验证',
    'IN_STOCK': '已入库',
    'ACTIVE': '已激活',
    'CLAIMED': '已领取',
    'DISABLED': '已禁用',
    'SCRAPPED': '已报废'
  }
  return map[status] ?? status
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
