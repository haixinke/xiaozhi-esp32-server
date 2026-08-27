<template>
  <div class="nfc-write-page">
    <HeaderBar />
    <div class="main-wrapper">
      <div class="content-panel">
        <div class="content-area">
          <el-card class="page-card" shadow="never">
            <div class="operation-header">
              <h2 class="page-title">NFC 写卡任务</h2>
              <div class="right-operations">
                <el-select
                  v-model="statusFilter"
                  placeholder="状态筛选"
                  clearable
                  class="filter-select"
                  @change="applyFilter"
                >
                  <el-option label="全部" value=""></el-option>
                  <el-option label="已创建" value="CREATED"></el-option>
                  <el-option label="已导出" value="EXPORTED"></el-option>
                  <el-option label="已导入结果" value="RESULT_IMPORTED"></el-option>
                  <el-option label="已完成" value="COMPLETED"></el-option>
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
              v-loading="loading"
              :data="filteredData"
              border
              stripe
              style="width: 100%;"
              :header-cell-style="{ background: '#f5f7fa' }"
              row-key="id"
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
              <el-table-column label="写卡任务" min-width="300" align="center">
                <template slot-scope="{ row }">
                  <template v-if="row._writeJob">
                    <div class="write-cell">
                      <div class="write-status-row">
                        <el-tag
                          :type="badgeType(row._writeJob.status)"
                          size="mini"
                        >{{ statusText(row._writeJob.status) }}</el-tag>
                        <span class="write-job-no">{{ row._writeJob.jobNo }}</span>
                      </div>
                      <el-progress
                        :percentage="calcPercent(row._writeJob)"
                        :status="progressStatus(row._writeJob)"
                        :stroke-width="14"
                        class="write-bar"
                      ></el-progress>
                      <span class="write-counts">
                        总 {{ row._writeJob.totalCount || 0 }}
                        | 成功 {{ row._writeJob.successCount || 0 }}
                        <template v-if="row._writeJob.failureCount">
                          | <span class="fail-count">失败 {{ row._writeJob.failureCount }}</span>
                        </template>
                      </span>
                      <span v-if="row._writeJob.exportedAt" class="write-export-time">
                        导出: {{ formatDate(row._writeJob.exportedAt) }}
                      </span>
                    </div>
                  </template>
                  <span v-else class="no-write">—</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="280" align="center" fixed="right">
                <template slot-scope="{ row }">
                  <el-button
                    v-if="canCreate(row)"
                    size="mini"
                    type="text"
                    :loading="row._creating"
                    @click="handleCreate(row)"
                  >创建任务</el-button>
                  <el-button
                    v-if="canDownload(row)"
                    size="mini"
                    type="text"
                    icon="el-icon-download"
                    :loading="row._downloading"
                    @click="handleDownload(row)"
                  >下载CSV</el-button>
                  <el-button
                    v-if="canImport(row)"
                    size="mini"
                    type="text"
                    icon="el-icon-upload2"
                    @click="handleImport(row)"
                  >导入结果</el-button>
                  <el-button
                    v-if="canManualWrite(row)"
                    size="mini"
                    type="text"
                    icon="el-icon-mobile-phone"
                    @click="goManualWrite(row)"
                  >手动写卡</el-button>
                  <el-button
                    v-if="canCancel(row)"
                    size="mini"
                    type="text"
                    class="danger-text"
                    @click="handleCancelJob(row)"
                  >取消</el-button>
                  <el-button
                    v-if="hasJob(row)"
                    size="mini"
                    type="text"
                    @click="toggleDetail(row)"
                  >{{ row._showDetail ? '收起' : '详情' }}</el-button>
                </template>
              </el-table-column>

              <!-- 展开行：详细信息 -->
              <el-table-column type="expand">
                <template slot-scope="{ row }">
                  <div v-if="row._showDetail && row._writeJob" class="expand-detail">
                    <el-descriptions :column="2" border size="small">
                      <el-descriptions-item label="任务ID">{{ row._writeJob.id }}</el-descriptions-item>
                      <el-descriptions-item label="任务编号">{{ row._writeJob.jobNo }}</el-descriptions-item>
                      <el-descriptions-item label="批次ID">{{ row._writeJob.batchId }}</el-descriptions-item>
                      <el-descriptions-item label="状态">
                        <el-tag :type="badgeType(row._writeJob.status)" size="mini">
                          {{ statusText(row._writeJob.status) }}
                        </el-tag>
                      </el-descriptions-item>
                      <el-descriptions-item label="CSV格式版本">{{ row._writeJob.formatVersion || '-' }}</el-descriptions-item>
                      <el-descriptions-item label="CSV行数">{{ row._writeJob.rowCount || 0 }}</el-descriptions-item>
                      <el-descriptions-item label="总数">{{ row._writeJob.totalCount }}</el-descriptions-item>
                      <el-descriptions-item label="成功">{{ row._writeJob.successCount || 0 }}</el-descriptions-item>
                      <el-descriptions-item label="失败">{{ row._writeJob.failureCount || 0 }}</el-descriptions-item>
                      <el-descriptions-item label="文件SHA-256" :span="2">
                        <span class="sha-text">{{ row._writeJob.fileSha256 || '-' }}</span>
                      </el-descriptions-item>
                      <el-descriptions-item v-if="row._writeJob.exportedAt" label="导出时间">
                        {{ formatDate(row._writeJob.exportedAt) }}
                      </el-descriptions-item>
                      <el-descriptions-item label="创建时间">
                        {{ formatDate(row._writeJob.createdAt) }}
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

    <!-- 导入结果对话框 -->
    <NfcWriteResultImportDialog
      :visible.sync="showImportDialog"
      :job-id="importJobId"
      @imported="onImported"
    />

    <!-- 创建任务对话框：选择写卡模式（ADR 0003，创建后不可变更） -->
    <el-dialog
      title="创建写卡任务"
      :visible.sync="createDialogVisible"
      width="460px"
      :close-on-click-modal="false"
    >
      <div v-if="createTargetRow" class="create-mode-dialog">
        <p class="create-mode-batch">批次：{{ createTargetRow.batchNo }}</p>
        <el-radio-group v-model="createMode">
          <el-radio label="FACTORY_CSV" class="create-mode-option">
            工厂 CSV 模式
            <div class="create-mode-hint">量产：导出 CSV 给工厂设备批量写卡，回传结果导入</div>
          </el-radio>
          <el-radio label="MANUAL" class="create-mode-option">
            手动模式（小批量验证）
            <div class="create-mode-hint">手机 NFC App 逐张写卡，触碰自验证，验证通过后锁卡再入库</div>
          </el-radio>
        </el-radio-group>
      </div>
      <span slot="footer">
        <el-button size="small" @click="createDialogVisible = false">取消</el-button>
        <el-button size="small" type="primary" @click="confirmCreate">创建</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import Api from '@/apis/api'
import HeaderBar from '@/components/HeaderBar.vue'
import NfcWriteResultImportDialog from '@/components/nfc/NfcWriteResultImportDialog.vue'
import { statusBadgeType, statusLabel, formatDate } from '@/utils/pdcNfcState.mjs'

/** 写卡任务终态 */
const TERMINAL = ['RESULT_IMPORTED', 'COMPLETED', 'CANCELLED']
const POLL_INTERVAL = 3000

export default {
  name: 'NfcWriteJobManagement',
  components: { HeaderBar, NfcWriteResultImportDialog },
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
      pollJobIds: new Set(),
      showImportDialog: false,
      importJobId: '',
      createDialogVisible: false,
      createMode: 'FACTORY_CSV',
      createTargetRow: null
    }
  },
  computed: {
    filteredData() {
      let data = this.tableData
      if (this.statusFilter) {
        data = data.filter(row => {
          const job = row._writeJob
          return job && job.status === this.statusFilter
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

    calcPercent(job) {
      if (!job || !job.totalCount) return 0
      return Math.min(100, Math.round(((job.successCount || 0) / job.totalCount) * 100))
    },
    progressStatus(job) {
      if (!job) return null
      if (job.status === 'COMPLETED' || job.status === 'RESULT_IMPORTED') return 'success'
      if (job.status === 'CANCELLED') return 'exception'
      return null
    },

    // ==================== 按钮可见性 ====================

    canCreate(row) {
      // 批次 READY_FOR_WRITE 且尚无写卡任务
      return row.status === 'READY_FOR_WRITE' && !row._writeJob
    },
    isManualJob(row) {
      // 手动写卡模式（ADR 0003）：不走 CSV 下载/导入通道
      return row._writeJob && row._writeJob.mode === 'MANUAL'
    },
    canDownload(row) {
      const job = row._writeJob
      return job && !this.isManualJob(row) && (job.status === 'CREATED' || job.status === 'EXPORTED')
    },
    canImport(row) {
      const job = row._writeJob
      return job && !this.isManualJob(row) && job.status === 'EXPORTED'
    },
    canManualWrite(row) {
      const job = row._writeJob
      return job && this.isManualJob(row) && job.status === 'CREATED'
    },
    canCancel(row) {
      const job = row._writeJob
      return job && (job.status === 'CREATED' || job.status === 'EXPORTED')
    },
    hasJob(row) {
      return !!row._writeJob
    },
    toggleDetail(row) {
      this.$set(row, '_showDetail', !row._showDetail)
    },
    goManualWrite(row) {
      const job = row._writeJob
      if (job && job.id) {
        this.$router.push(`/pdc-nfc/manual-write/${job.id}`)
      }
    },

    // ==================== 数据加载 ====================

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
            _writeJob: null,
            _showDetail: false,
            _creating: false,
            _downloading: false
          }))
          this.total = data?.total || list.length
          this.loadAllWriteJobs()
        } else {
          this.$message.error(res.data?.msg || '获取批次列表失败')
        }
      })
    },

    loadAllWriteJobs() {
      // 只有非 DRAFT/CREATED 的批次可能有写卡任务
      const noWriteStatuses = new Set(['DRAFT', 'CREATED'])
      this.tableData.forEach(row => {
        if (!noWriteStatuses.has(row.status) && row.writeJobId) {
          this.fetchWriteJob(row.id, row.writeJobId, false)
        }
      })
      this.refreshPolling()
    },

    fetchWriteJob(batchRowId, jobId, showError = false) {
      Api.pdcNfc.getWriteJob(jobId, (res) => {
        if (res.data && res.data.code === 0 && res.data.data) {
          const row = this.tableData.find(r => r.id === batchRowId)
          if (row) {
            this.$set(row, '_writeJob', res.data.data)
          }
          this.refreshPolling()
        }
        // 不存在时忽略
      })
    },

    // ==================== 轮询 ====================

    refreshPolling() {
      const activeJobIds = new Set()
      this.tableData.forEach(row => {
        const job = row._writeJob
        if (job && !TERMINAL.includes(job.status)) {
          activeJobIds.add(job.id)
        }
      })

      this.pollJobIds = activeJobIds

      if (activeJobIds.size > 0 && !this.pollTimer) {
        this.pollTimer = setInterval(() => {
          this.pollActiveJobs()
        }, POLL_INTERVAL)
      } else if (activeJobIds.size === 0 && this.pollTimer) {
        this.stopAllPolling()
      }
    },

    pollActiveJobs() {
      this.tableData.forEach(row => {
        const job = row._writeJob
        if (job && !TERMINAL.includes(job.status)) {
          Api.pdcNfc.getWriteJob(job.id, (res) => {
            if (res.data && res.data.code === 0 && res.data.data) {
              this.$set(row, '_writeJob', res.data.data)
              if (TERMINAL.includes(res.data.data.status)) {
                this.refreshPolling()
              }
            }
          })
        }
      })
    },

    stopAllPolling() {
      if (this.pollTimer) {
        clearInterval(this.pollTimer)
        this.pollTimer = null
      }
    },

    // ==================== 操作 ====================

    handleCreate(row) {
      this.createTargetRow = row
      this.createMode = 'FACTORY_CSV'
      this.createDialogVisible = true
    },

    confirmCreate() {
      const row = this.createTargetRow
      if (!row) return
      this.createDialogVisible = false
      this.$set(row, '_creating', true)
      Api.pdcNfc.createWriteJob(row.id, this.createMode, (res) => {
        this.$set(row, '_creating', false)
        if (res.data && res.data.code === 0) {
          this.$message.success('写卡任务已创建')
          const job = res.data.data
          if (job) {
            this.$set(row, '_writeJob', job)
          }
          this.refreshPolling()
        } else {
          this.$message.error(res.data?.msg || '创建写卡任务失败')
        }
      })
    },

    handleDownload(row) {
      const job = row._writeJob
      if (!job || !job.id) {
        this.$message.warning('任务信息不完整')
        return
      }
      this.$set(row, '_downloading', true)
      Api.pdcNfc.downloadWriteJob(job.id, (res) => {
        this.$set(row, '_downloading', false)
        const blob = res.data instanceof Blob ? res.data : new Blob([res.data])
        const url = window.URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.download = `write_${job.jobNo || job.id}.csv`
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        window.URL.revokeObjectURL(url)
        this.$message.success('下载已开始')
        // 下载后刷新任务状态（可能变为 EXPORTED）
        this.fetchWriteJob(row.id, job.id)
      })
    },

    handleImport(row) {
      const job = row._writeJob
      if (!job || !job.id) {
        this.$message.warning('任务信息不完整')
        return
      }
      this.importJobId = job.id
      this.showImportDialog = true
    },

    onImported() {
      // 导入成功后刷新所有写卡任务状态
      this.tableData.forEach(row => {
        if (row._writeJob) {
          this.fetchWriteJob(row.id, row._writeJob.id)
        }
      })
    },

    handleCancelJob(row) {
      const job = row._writeJob
      if (!job || !job.id) return
      this.$confirm(`确认取消批次「${row.batchNo}」的写卡任务吗？`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        Api.pdcNfc.cancelWriteJob(job.id, (res) => {
          if (res.data && res.data.code === 0) {
            this.$message.success('任务已取消')
            this.fetchWriteJob(row.id, job.id)
          } else {
            this.$message.error(res.data?.msg || '取消失败')
          }
        })
      }).catch(() => {})
    },

    // ==================== 搜索/分页 ====================

    handleSearch() {
      this.activeSearchKeyword = this.searchKeyword
      this.currentPage = 1
      this.fetchBatches()
    },
    applyFilter() {
      // 前端过滤
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
.nfc-write-page {
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

.write-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.write-status-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.write-job-no {
  font-size: 11px;
  color: #909399;
}

.write-bar {
  width: 200px;
}

.write-counts {
  font-size: 12px;
  color: #606266;
}

.write-export-time {
  font-size: 11px;
  color: #909399;
}

.fail-count {
  color: #f56c6c;
}

.no-write {
  color: #c0c4cc;
}

.expand-detail {
  padding: 12px 20px;
}

.sha-text {
  font-family: monospace;
  font-size: 11px;
  color: #606266;
  word-break: break-all;
}

:deep(.el-table .el-button--text) {
  color: #7079aa;
}

:deep(.el-table .el-button--text:hover) {
  color: #5a64b5;
}

.danger-text {
  color: #f56c6c !important;
}

.create-mode-batch {
  margin: 0 0 12px;
  color: #606266;
}

.create-mode-option {
  display: block;
  margin-bottom: 14px;
  white-space: normal;
}

.create-mode-hint {
  font-size: 12px;
  color: #909399;
  margin-left: 24px;
  line-height: 1.5;
}
</style>
