<template>
  <div class="nfc-asset-page">
    <HeaderBar />
    <div class="main-wrapper">
      <div class="content-panel">
        <div class="content-area">
          <el-card class="page-card" shadow="never">
            <div class="operation-header">
              <h2 class="page-title">NFC 资产管理</h2>
            </div>

            <!-- 筛选栏 -->
            <div class="filter-bar">
              <el-input
                v-model="filters.assetNo"
                placeholder="资产编号"
                clearable
                class="filter-item"
                @keyup.enter.native="handleSearch"
              />
              <el-input
                v-model="filters.wechatSn"
                placeholder="微信 SN"
                clearable
                class="filter-item"
                @keyup.enter.native="handleSearch"
              />
              <el-input
                v-model="filters.batchId"
                placeholder="批次 ID"
                clearable
                class="filter-item"
                @keyup.enter.native="handleSearch"
              />
              <el-select
                v-model="filters.status"
                placeholder="状态"
                clearable
                class="filter-item"
              >
                <el-option
                  v-for="s in statusOptions"
                  :key="s.value"
                  :label="s.label"
                  :value="s.value"
                ></el-option>
              </el-select>
              <el-input
                v-model="filters.skuCode"
                placeholder="SKU 编码"
                clearable
                class="filter-item"
                @keyup.enter.native="handleSearch"
              />
              <el-select
                v-model="filters.prototype"
                placeholder="原型"
                clearable
                class="filter-item"
                :loading="prototypeLoading"
              >
                <el-option
                  v-for="item in prototypeOptions"
                  :key="item.key"
                  :label="item.name"
                  :value="item.key"
                ></el-option>
              </el-select>
              <el-date-picker
                v-model="filters.dateRange"
                type="daterange"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                value-format="yyyy-MM-dd"
                class="filter-item-date"
              />
              <el-button icon="el-icon-search" type="primary" @click="handleSearch">搜索</el-button>
              <el-button icon="el-icon-refresh" @click="handleResetFilters">重置</el-button>
            </div>

            <!-- 批量操作 -->
            <div class="bulk-actions">
              <el-button
                size="small"
                type="success"
                icon="el-icon-box"
                :disabled="selectedIds.length === 0"
                @click="openStockInDialog"
              >批量入库</el-button>
              <el-button
                size="small"
                type="primary"
                icon="el-icon-open"
                :disabled="selectedIds.length === 0"
                @click="handleBulkActivate"
              >批量激活</el-button>
              <el-button
                size="small"
                type="warning"
                icon="el-icon-turn-off"
                :disabled="selectedIds.length === 0"
                @click="handleBulkDisable"
              >批量禁用</el-button>
              <el-button
                size="small"
                type="danger"
                icon="el-icon-delete"
                :disabled="selectedIds.length === 0"
                @click="handleBulkScrap"
              >批量报废</el-button>
              <span v-if="selectedIds.length > 0" class="selected-count">
                已选 {{ selectedIds.length }} 项
              </span>
            </div>

            <el-table
              v-loading="loading"
              :data="tableData"
              border
              stripe
              style="width: 100%;"
              :header-cell-style="{ background: '#f5f7fa' }"
              @row-click="handleRowClick"
              @selection-change="handleSelectionChange"
            >
              <el-table-column type="selection" width="45" align="center"></el-table-column>
              <el-table-column prop="assetNo" label="资产编号" min-width="160" align="center"></el-table-column>
              <el-table-column prop="batchNo" label="批次号" min-width="140" align="center"></el-table-column>
              <el-table-column prop="itemNo" label="物料序号" min-width="90" align="center"></el-table-column>
              <el-table-column prop="skuCode" label="SKU" min-width="120" align="center"></el-table-column>
              <el-table-column prop="prototype" label="原型" min-width="80" align="center">
                <template slot-scope="{ row }">{{ row.prototype || '-' }}</template>
              </el-table-column>
              <el-table-column prop="wechatSn" label="微信 SN" min-width="140" align="center">
                <template slot-scope="{ row }">{{ row.wechatSn || '-' }}</template>
              </el-table-column>
              <el-table-column label="状态" min-width="100" align="center">
                <template slot-scope="{ row }">
                  <el-tag :type="badgeType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="Scheme SHA256" min-width="140" align="center">
                <template slot-scope="{ row }">
                  <span class="hash-text">{{ truncateHash(row.schemeSha256) }}</span>
                </template>
              </el-table-column>
            </el-table>

            <div class="pagination-wrapper">
              <el-pagination
                background
                layout="total, sizes, prev, pager, next, jumper"
                :total="total"
                :current-page="currentPage"
                :page-size="pageSize"
                :page-sizes="[20, 50, 100]"
                @current-change="handlePageChange"
                @size-change="handleSizeChange"
              ></el-pagination>
            </div>
          </el-card>
        </div>
      </div>
    </div>

    <!-- 资产详情对话框 -->
    <NfcAssetDetailDialog
      :visible.sync="detailDialogVisible"
      :asset-id="currentAssetId"
    />

    <!-- 批量入库对话框 -->
    <NfcStockInDialog
      :visible.sync="stockInDialogVisible"
      :asset-ids="selectedIds"
      @stocked="onStockInComplete"
    />
  </div>
</template>

<script>
import Api from '@/apis/api'
import HeaderBar from '@/components/HeaderBar.vue'
import NfcAssetDetailDialog from '@/components/nfc/NfcAssetDetailDialog.vue'
import NfcStockInDialog from '@/components/nfc/NfcStockInDialog.vue'
import { statusBadgeType, statusLabel, presentAsset } from '@/utils/pdcNfcState.mjs'

export default {
  name: 'NfcAssetManagement',
  components: { HeaderBar, NfcAssetDetailDialog, NfcStockInDialog },
  data() {
    return {
      loading: false,
      tableData: [],
      total: 0,
      currentPage: 1,
      pageSize: 20,
      // 筛选
      filters: {
        assetNo: '',
        wechatSn: '',
        batchId: '',
        status: '',
        skuCode: '',
        prototype: '',
        dateRange: null
      },
      activeFilters: {},
      // 选择
      selectedRows: [],
      // 详情对话框
      detailDialogVisible: false,
      currentAssetId: '',
      // 入库对话框
      stockInDialogVisible: false,
      // 状态选项
      statusOptions: [
        { value: 'SCHEME_GENERATED', label: 'Scheme已生成' },
        { value: 'WRITTEN', label: '已写入' },
        { value: 'VERIFIED', label: '已验证' },
        { value: 'IN_STOCK', label: '已入库' },
        { value: 'ACTIVE', label: '已激活' },
        { value: 'CLAIMED', label: '已领取' },
        { value: 'DISABLED', label: '已禁用' },
        { value: 'SCRAPPED', label: '已报废' }
      ],
      // 原型选项（来自字典 EGG_PET_PROTOTYPE）
      prototypeLoading: false,
      prototypeOptions: []
    }
  },
  computed: {
    selectedIds() {
      return this.selectedRows.map(r => r.id)
    }
  },
  created() {
    this.fetchAssets()
    this.fetchPrototypes()
  },
  methods: {
    badgeType: statusBadgeType,
    statusText: statusLabel,
    // 原型筛选选项来自字典 EGG_PET_PROTOTYPE，与创建批次共用同一数据源
    // 筛选值需与后端 prototype 字段精确匹配，拼音值会导致查询恒为空结果
    fetchPrototypes() {
      this.prototypeLoading = true
      Api.dict.getDictDataByType('EGG_PET_PROTOTYPE')
        .then((data) => {
          this.prototypeLoading = false
          this.prototypeOptions = Array.isArray(data) ? data : (data.data || [])
        })
        .catch(() => {
          this.prototypeLoading = false
          this.prototypeOptions = []
        })
    },
    truncateHash(hash) {
      if (!hash) return '-'
      if (hash.length <= 16) return hash
      return hash.substring(0, 12) + '…'
    },
    buildParams() {
      const params = {
        page: this.currentPage,
        limit: this.pageSize
      }
      const f = this.activeFilters
      if (f.assetNo) params.assetNo = f.assetNo
      if (f.wechatSn) params.wechatSn = f.wechatSn
      if (f.batchId) params.batchId = f.batchId
      if (f.status) params.status = f.status
      if (f.skuCode) params.skuCode = f.skuCode
      if (f.prototype) params.prototype = f.prototype
      if (f.dateRange && f.dateRange.length === 2) {
        params.startDate = f.dateRange[0]
        params.endDate = f.dateRange[1]
      }
      return params
    },
    fetchAssets() {
      this.loading = true
      Api.pdcNfc.listAssets(this.buildParams(), (res) => {
        this.loading = false
        if (res.data && res.data.code === 0) {
          const data = res.data.data
          const list = data?.list || data || []
          // 仅保留安全字段用于展示，敏感数据不进入 Vue data
          this.tableData = list.map(a => presentAsset(a))
          this.total = data?.total || this.tableData.length
        } else {
          this.$message.error(res.data?.msg || '获取资产列表失败')
        }
      })
    },
    handleSearch() {
      this.activeFilters = { ...this.filters }
      this.currentPage = 1
      this.fetchAssets()
    },
    handleResetFilters() {
      this.filters = {
        assetNo: '',
        wechatSn: '',
        batchId: '',
        status: '',
        skuCode: '',
        prototype: '',
        dateRange: null
      }
      this.activeFilters = {}
      this.currentPage = 1
      this.fetchAssets()
    },
    handlePageChange(page) {
      this.currentPage = page
      this.fetchAssets()
    },
    handleSizeChange(size) {
      this.pageSize = size
      this.currentPage = 1
      this.fetchAssets()
    },
    handleSelectionChange(rows) {
      this.selectedRows = rows
    },
    handleRowClick(row) {
      this.currentAssetId = row.id
      this.detailDialogVisible = true
    },
    openStockInDialog() {
      if (this.selectedIds.length === 0) return
      this.stockInDialogVisible = true
    },
    onStockInComplete() {
      this.stockInDialogVisible = false
      this.fetchAssets()
    },
    generateUUID() {
      if (typeof crypto !== 'undefined' && crypto.randomUUID) {
        return crypto.randomUUID()
      }
      return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = Math.random() * 16 | 0
        const v = c === 'x' ? r : (r & 0x3 | 0x8)
        return v.toString(16)
      })
    },
    handleBulkActivate() {
      const ids = this.selectedIds
      this.$confirm(`确认激活选中的 ${ids.length} 项资产吗？`, '批量激活', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        const payload = {
          assetIds: ids,
          requestId: this.generateUUID()
        }
        Api.pdcNfc.activate(payload, (res) => {
          if (res.data && res.data.code === 0) {
            const result = res.data.data || {}
            this.$message.success(`激活完成：处理 ${result.processedCount ?? ids.length} 项，成功 ${result.successCount ?? 0} 项`)
            this.fetchAssets()
          } else {
            this.$message.error(res.data?.msg || '激活失败')
          }
        })
      }).catch(() => {})
    },
    handleBulkDisable() {
      const ids = this.selectedIds
      this.$confirm(`确认禁用选中的 ${ids.length} 项资产吗？此操作不可逆。`, '批量禁用', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        const payload = {
          assetIds: ids,
          requestId: this.generateUUID()
        }
        Api.pdcNfc.disable(payload, (res) => {
          if (res.data && res.data.code === 0) {
            const result = res.data.data || {}
            this.$message.success(`禁用完成：处理 ${result.processedCount ?? ids.length} 项，成功 ${result.successCount ?? 0} 项`)
            this.fetchAssets()
          } else {
            this.$message.error(res.data?.msg || '禁用失败')
          }
        })
      }).catch(() => {})
    },
    handleBulkScrap() {
      const ids = this.selectedIds
      this.$confirm(`确认报废选中的 ${ids.length} 项资产吗？此操作不可逆。`, '批量报废', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'error'
      }).then(() => {
        const payload = {
          assetIds: ids,
          requestId: this.generateUUID()
        }
        Api.pdcNfc.scrap(payload, (res) => {
          if (res.data && res.data.code === 0) {
            const result = res.data.data || {}
            this.$message.success(`报废完成：处理 ${result.processedCount ?? ids.length} 项，成功 ${result.successCount ?? 0} 项`)
            this.fetchAssets()
          } else {
            this.$message.error(res.data?.msg || '报废失败')
          }
        })
      }).catch(() => {})
    }
  }
}
</script>

<style lang="scss" scoped>
.nfc-asset-page {
  min-width: 900px;
  min-height: 506px;
  height: 100vh;
  display: flex;
  position: relative;
  flex-direction: column;
  background: #eff4ff;
  background-size: cover;
  overflow: hidden;
}

.main-wrapper {
  height: calc(100vh - 63px - 35px);
  padding: 20px 22px 0;
  position: relative;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

.operation-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0 16px 0;
}

.page-title {
  font-weight: 500;
  font-size: 24px;
  margin: 0;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding-bottom: 14px;
}

.filter-item {
  width: 160px;
}

.filter-item-date {
  width: 300px;
}

.bulk-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 14px;
}

.selected-count {
  color: #409eff;
  font-size: 13px;
  margin-left: 8px;
}

.content-panel {
  display: flex;
  overflow: hidden;
  height: 100%;
  border-radius: 15px;
  background: transparent;
  border: 1px solid #fff;
}

.content-area {
  flex: 1;
  height: 100%;
  min-width: 600px;
  overflow: auto;
  background-color: white;
  display: flex;
  flex-direction: column;
}

.page-card {
  background: white;
  flex: 1;
  display: flex;
  flex-direction: column;
  border: none;
  box-shadow: none;
  overflow: hidden;

  ::v-deep .el-card__body {
    padding: 14px 20px;
    display: flex;
    flex-direction: column;
    flex: 1;
    overflow: hidden;
  }
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
}

.hash-text {
  font-family: monospace;
  font-size: 12px;
  color: #606266;
}

:deep(.el-table .el-button--text) {
  color: #7079aa;
}

:deep(.el-table .el-button--text:hover) {
  color: #5a64b5;
}

:deep(.el-table__row) {
  cursor: pointer;
}
</style>
