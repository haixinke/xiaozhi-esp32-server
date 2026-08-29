<template>
  <div class="nfc-scheme-page">
    <HeaderBar />
    <div class="main-wrapper">
      <div class="content-panel">
        <div class="content-area">
          <el-card class="page-card" shadow="never">
            <div class="operation-header">
              <h2 class="page-title">NFC Scheme 任务</h2>
              <div class="right-operations">
                <el-select
                  v-model="statusFilter"
                  placeholder="状态筛选"
                  clearable
                  class="filter-select"
                  @change="applyFilter"
                >
                  <el-option label="全部" value=""></el-option>
                  <el-option label="运行中" value="RUNNING"></el-option>
                  <el-option label="等待中" value="PENDING"></el-option>
                  <el-option label="成功" value="SUCCEEDED"></el-option>
                  <el-option label="失败" value="FAILED"></el-option>
                  <el-option label="部分成功" value="PARTIAL_SUCCESS"></el-option>
                  <el-option label="已取消" value="CANCELLED"></el-option>
                </el-select>
                <el-input
                  v-model="searchKeyword"
                  placeholder="搜索批次号"
                  class="search-input"
                  clearable
                  @keyup.enter.native="handleSearch"
                />
                <el-button icon="el-icon-search" type="primary" @click="handleSearch">搜索</el-button>
                <el-button icon="el-icon-refresh" @click="handleRefresh">刷新</el-button>
              </div>
            </div>

            <el-table
              ref="batchTable"
              v-loading="loading"
              :data="filteredData"
              border
              stripe
              style="width: 100%;"
              :header-cell-style="{ background: '#f5f7fa' }"
              row-key="id"
              @expand-change="handleExpandChange"
            >
              <el-table-column prop="batchNo" label="批次号" min-width="160" align="center"></el-table-column>
              <el-table-column label="商品类型" min-width="140" align="center">
                <template slot-scope="{ row }">
                  {{ row.productTypeName || row.typeCode || '-' }}
                </template>
              </el-table-column>
              <el-table-column prop="skuCode" label="SKU" min-width="120" align="center"></el-table-column>
              <el-table-column prop="plannedQuantity" label="计划数量" min-width="90" align="center"></el-table-column>
              <el-table-column label="批次状态" min-width="110" align="center">
                <template slot-scope="{ row }">
                  <el-tag :type="badgeType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="Scheme 任务" min-width="260" align="center">
                <template slot-scope="{ row }">
                  <template v-if="row._schemeProgress">
                    <div class="scheme-cell">
                      <div class="scheme-status-row">
                        <el-tag
                          :type="badgeType(row._schemeProgress.status)"
                          size="mini"
                        >{{ statusText(row._schemeProgress.status) }}</el-tag>
                        <span class="scheme-job-no">{{ row._schemeProgress.jobNo }}</span>
                      </div>
                      <el-progress
                        :percentage="calcPercent(row._schemeProgress)"
                        :status="progressStatus(row._schemeProgress)"
                        :stroke-width="14"
                        class="scheme-bar"
                      ></el-progress>
                      <span class="scheme-counts">
                        {{ row._schemeProgress.successCount || 0 }}/{{ row._schemeProgress.totalCount || 0 }}
                        <template v-if="row._schemeProgress.failureCount">
                          <span class="fail-count">（失败 {{ row._schemeProgress.failureCount }}）</span>
                        </template>
                      </span>
                      <span v-if="row._schemeProgress.lastError" class="scheme-error">
                        {{ row._schemeProgress.lastError }}
                      </span>
                    </div>
                  </template>
                  <span v-else class="no-scheme">—</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="220" align="center" fixed="right">
                <template slot-scope="{ row }">
                  <el-button
                    v-if="canGenerate(row)"
                    size="mini"
                    type="text"
                    :loading="row._generating"
                    @click="handleGenerate(row)"
                  >生成 Scheme</el-button>
                  <el-button
                    v-if="canRetry(row)"
                    size="mini"
                    type="text"
                    class="warn-text"
                    @click="handleRetry(row)"
                  >重试</el-button>
                  <el-button
                    v-if="canCancel(row)"
                    size="mini"
                    type="text"
                    class="danger-text"
                    @click="handleCancel(row)"
                  >取消</el-button>
                  <el-button
                    v-if="hasProgress(row)"
                    size="mini"
                    type="text"
                    @click="toggleDetail(row)"
                  >{{ row._expanded ? '收起' : '详情' }}</el-button>
                </template>
              </el-table-column>

              <!-- 展开行：详细信息 -->
              <el-table-column type="expand">
                <template slot-scope="{ row }">
                  <div v-if="row._schemeProgress" class="expand-detail">
                    <el-descriptions :column="2" border size="small">
                      <el-descriptions-item label="任务ID">{{ row._schemeProgress.jobId }}</el-descriptions-item>
                      <el-descriptions-item label="任务编号">{{ row._schemeProgress.jobNo }}</el-descriptions-item>
                      <el-descriptions-item label="批次ID">{{ row._schemeProgress.batchId }}</el-descriptions-item>
                      <el-descriptions-item label="状态">
                        <el-tag :type="badgeType(row._schemeProgress.status)" size="mini">
                          {{ statusText(row._schemeProgress.status) }}
                        </el-tag>
                      </el-descriptions-item>
                      <el-descriptions-item label="总数">{{ row._schemeProgress.totalCount }}</el-descriptions-item>
                      <el-descriptions-item label="成功">{{ row._schemeProgress.successCount || 0 }}</el-descriptions-item>
                      <el-descriptions-item label="失败">{{ row._schemeProgress.failureCount || 0 }}</el-descriptions-item>
                      <el-descriptions-item label="游标位置">{{ row._schemeProgress.cursorAssetId || '-' }}</el-descriptions-item>
                      <el-descriptions-item v-if="row._schemeProgress.nextRetryAt" label="下次重试">
                        {{ formatDate(row._schemeProgress.nextRetryAt) }}
                      </el-descriptions-item>
                      <el-descriptions-item v-if="row._schemeProgress.lastError" label="最近错误" :span="2">
                        <span class="error-text">{{ row._schemeProgress.lastError }}</span>
                      </el-descriptions-item>
                    </el-descriptions>
                  </div>
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
                :page-sizes="[10, 20, 50]"
                @current-change="handlePageChange"
                @size-change="handleSizeChange"
              ></el-pagination>
            </div>
          </el-card>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import Api from '@/apis/api'
import HeaderBar from '@/components/HeaderBar.vue'
import { statusBadgeType, statusLabel, formatDate } from '@/utils/pdcNfcState.mjs'

const TERMINAL = ['SUCCEEDED', 'FAILED', 'CANCELLED', 'PARTIAL_SUCCESS']
const POLL_INTERVAL = 2000

export default {
  name: 'NfcSchemeManagement',
  components: { HeaderBar },
  data() {
    return {
      loading: false,
      tableData: [],
      total: 0,
      currentPage: 1,
      pageSize: 10,
      searchKeyword: '',
      activeSearchKeyword: '',
      statusFilter: '',
      pollTimer: null,
      pollBatchIds: new Set()
    }
  },
  computed: {
    filteredData() {
      let data = this.tableData
      if (this.statusFilter) {
        data = data.filter(row => {
          const p = row._schemeProgress
          return p && p.status === this.statusFilter
        })
      }
      return data
    }
  },
  created() {
    this.fetchBatches()
  },
  beforeDestroy() {
    this.stopAllPolling()
  },
  beforeRouteLeave(to, from, next) {
    this.stopAllPolling()
    next()
  },
  methods: {
    badgeType: statusBadgeType,
    statusText: statusLabel,
    formatDate,

    calcPercent(p) {
      if (!p || !p.totalCount) return 0
      return Math.min(100, Math.round(((p.successCount || 0) / p.totalCount) * 100))
    },
    progressStatus(p) {
      if (!p) return null
      if (p.status === 'SUCCEEDED') return 'success'
      if (p.status === 'FAILED' || p.status === 'PARTIAL_SUCCESS') return 'exception'
      return null
    },

    canGenerate(row) {
      return row.status === 'DRAFT' || row.status === 'CREATED'
    },
    canRetry(row) {
      const p = row._schemeProgress
      return p && (p.status === 'FAILED' || p.status === 'PARTIAL_SUCCESS')
    },
    canCancel(row) {
      const p = row._schemeProgress
      return p && (p.status === 'PENDING' || p.status === 'RUNNING')
    },
    hasProgress(row) {
      return !!row._schemeProgress
    },
    toggleDetail(row) {
      // 展开状态由 el-table 统一管理，按钮与原生展开箭头共用同一状态，避免两边失同步
      this.$refs.batchTable.toggleRowExpansion(row)
    },
    handleExpandChange(row, expandedRows) {
      // 原生箭头展开/收起时回写状态，保证“详情/收起”按钮文案一致
      this.$set(row, '_expanded', expandedRows.indexOf(row) !== -1)
    },

    fetchBatches() {
      this.loading = true
      const params = { page: this.currentPage, limit: this.pageSize }
      if (this.activeSearchKeyword) {
        params.keyword = this.activeSearchKeyword
      }
      Api.pdcNfc.listBatches(params, (res) => {
        this.loading = false
        if (res.data && res.data.code === 0) {
          const data = res.data.data
          const list = data?.list || data || []
          this.tableData = list.map(b => ({
            ...b,
            _schemeProgress: null,
            _expanded: false,
            _generating: false
          }))
          this.total = data?.total || list.length
          // 对有 scheme 任务的批次批量加载进度
          this.loadAllProgress()
        } else {
          this.$message.error(res.data?.msg || '获取批次列表失败')
        }
      })
    },

    loadAllProgress() {
      // 只对已启动 Scheme 流程的批次请求进度（DRAFT/CREATED 批次不可能有 scheme job）
      const noSchemeStatuses = new Set(['DRAFT', 'CREATED'])
      this.tableData.forEach(row => {
        if (!noSchemeStatuses.has(row.status)) {
          this.fetchProgressForBatch(row.id, false)
        }
      })
      this.refreshPolling()
    },

    fetchProgressForBatch(batchId, showError = false) {
      Api.pdcNfc.schemeJobProgress(batchId, (res) => {
        if (res.data && res.data.code === 0 && res.data.data) {
          const row = this.tableData.find(r => r.id === batchId)
          if (row) {
            this.$set(row, '_schemeProgress', res.data.data)
          }
          this.refreshPolling()
        }
        // code !== 0 通常意味着该批次没有 scheme job，忽略
      })
    },

    refreshPolling() {
      // 检查是否有运行中的任务
      const activeBatchIds = new Set()
      this.tableData.forEach(row => {
        const p = row._schemeProgress
        if (p && !TERMINAL.includes(p.status)) {
          activeBatchIds.add(row.id)
        }
      })

      this.pollBatchIds = activeBatchIds

      if (activeBatchIds.size > 0 && !this.pollTimer) {
        this.pollTimer = setInterval(() => {
          this.pollActiveJobs()
        }, POLL_INTERVAL)
      } else if (activeBatchIds.size === 0 && this.pollTimer) {
        this.stopAllPolling()
      }
    },

    pollActiveJobs() {
      this.pollBatchIds.forEach(batchId => {
        Api.pdcNfc.schemeJobProgress(batchId, (res) => {
          if (res.data && res.data.code === 0 && res.data.data) {
            const row = this.tableData.find(r => r.id === batchId)
            if (row) {
              this.$set(row, '_schemeProgress', res.data.data)
              // 到达终态时刷新批次列表
              if (TERMINAL.includes(res.data.data.status)) {
                this.refreshPolling()
              }
            }
          }
        })
      })
    },

    stopAllPolling() {
      if (this.pollTimer) {
        clearInterval(this.pollTimer)
        this.pollTimer = null
      }
    },

    handleGenerate(row) {
      this.$confirm(`确认为批次「${row.batchNo}」生成 Scheme 吗？`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'info'
      }).then(() => {
        this.$set(row, '_generating', true)
        Api.pdcNfc.startSchemeJob(row.id, (res) => {
          this.$set(row, '_generating', false)
          if (res.data && res.data.code === 0) {
            this.$message.success('Scheme 任务已启动')
            this.fetchProgressForBatch(row.id, true)
            this.refreshPolling()
          } else {
            this.$message.error(res.data?.msg || '启动 Scheme 任务失败')
          }
        })
      }).catch(() => {})
    },

    handleRetry(row) {
      this.$confirm(`确认重试批次「${row.batchNo}」的 Scheme 任务吗？`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        Api.pdcNfc.retrySchemeJob(row.id, (res) => {
          if (res.data && res.data.code === 0) {
            this.$message.success('重试已触发')
            this.fetchProgressForBatch(row.id, true)
            this.refreshPolling()
          } else {
            this.$message.error(res.data?.msg || '重试失败')
          }
        })
      }).catch(() => {})
    },

    handleCancel(row) {
      const p = row._schemeProgress
      if (!p || !p.jobId) return
      this.$confirm(`确认取消批次「${row.batchNo}」的 Scheme 任务吗？`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        Api.pdcNfc.cancelSchemeJob(p.jobId, (res) => {
          if (res.data && res.data.code === 0) {
            this.$message.success('任务已取消')
            this.fetchProgressForBatch(row.id, true)
          } else {
            this.$message.error(res.data?.msg || '取消失败')
          }
        })
      }).catch(() => {})
    },

    handleSearch() {
      this.activeSearchKeyword = this.searchKeyword
      this.currentPage = 1
      this.fetchBatches()
    },
    applyFilter() {
      // 前端过滤，无需请求
    },
    handleRefresh() {
      this.stopAllPolling()
      this.fetchBatches()
    },
    handlePageChange(page) {
      this.currentPage = page
      this.fetchBatches()
    },
    handleSizeChange(size) {
      this.pageSize = size
      this.currentPage = 1
      this.fetchBatches()
    }
  }
}
</script>

<style lang="scss" scoped>
.nfc-scheme-page {
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

.right-operations {
  display: flex;
  gap: 10px;
  margin-left: auto;
  align-items: center;
}

.filter-select {
  width: 150px;
}

.search-input {
  width: 200px;
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

.scheme-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.scheme-status-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.scheme-job-no {
  font-size: 11px;
  color: #909399;
}

.scheme-bar {
  width: 180px;
}

.scheme-counts {
  font-size: 12px;
  color: #606266;
}

.fail-count {
  color: #f56c6c;
}

.scheme-error {
  font-size: 11px;
  color: #f56c6c;
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.no-scheme {
  color: #c0c4cc;
}

.expand-detail {
  padding: 12px 20px;
}

.error-text {
  color: #f56c6c;
  word-break: break-all;
}

:deep(.el-table .el-button--text) {
  color: #7079aa;
}

:deep(.el-table .el-button--text:hover) {
  color: #5a64b5;
}

.warn-text {
  color: #e6a23c !important;
}

.danger-text {
  color: #f56c6c !important;
}
</style>
