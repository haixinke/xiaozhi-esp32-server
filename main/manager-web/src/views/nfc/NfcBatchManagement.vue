<template>
  <div class="nfc-batch-page">
    <HeaderBar />
    <div class="main-wrapper">
      <div class="content-panel">
        <div class="content-area">
          <el-card class="page-card" shadow="never">
            <div class="operation-header">
              <h2 class="page-title">NFC 批次管理</h2>
              <div class="right-operations">
                <el-input
                  v-model="searchKeyword"
                  placeholder="搜索批次号/SKU"
                  class="search-input"
                  clearable
                  @keyup.enter.native="handleSearch"
                />
                <el-button icon="el-icon-search" type="primary" @click="handleSearch">搜索</el-button>
                <el-button icon="el-icon-plus" type="success" @click="batchDialogVisible = true">创建批次</el-button>
              </div>
            </div>

            <el-table
              v-loading="loading"
              :data="tableData"
              border
              stripe
              style="width: 100%;"
              :header-cell-style="{ background: '#f5f7fa' }"
            >
              <el-table-column prop="batchNo" label="批次号" min-width="160" align="center"></el-table-column>
              <el-table-column label="商品类型" min-width="160" align="center">
                <template slot-scope="{ row }">
                  {{ row.productTypeName || row.typeCode || row.productTypeId || '-' }}
                </template>
              </el-table-column>
              <el-table-column prop="skuCode" label="SKU 编码" min-width="140" align="center"></el-table-column>
              <el-table-column prop="prototype" label="原型" min-width="90" align="center">
                <template slot-scope="{ row }">
                  {{ row.prototype || '-' }}
                </template>
              </el-table-column>
              <el-table-column prop="plannedQuantity" label="计划数量" min-width="100" align="center"></el-table-column>
              <el-table-column label="状态" min-width="120" align="center">
                <template slot-scope="{ row }">
                  <el-tag :type="badgeType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="统计" min-width="180" align="center">
                <template slot-scope="{ row }">
                  <span class="stat-item">Scheme: {{ row.schemeGeneratedCount ?? 0 }}</span>
                  <el-divider direction="vertical"></el-divider>
                  <span class="stat-item">已写入: {{ row.writtenCount ?? 0 }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="320" align="center" fixed="right">
                <template slot-scope="{ row }">
                  <el-button
                    v-if="actions(row.status).generateScheme"
                    size="mini"
                    type="text"
                    @click="handleGenerateScheme(row)"
                  >生成Scheme</el-button>
                  <el-button
                    v-if="row.schemeJobId && row.status === 'SCHEME_GENERATING'"
                    size="mini"
                    type="text"
                    @click="openSchemeProgress(row)"
                  >查看Scheme进度</el-button>
                  <el-button
                    v-if="actions(row.status).createWriteJob"
                    size="mini"
                    type="text"
                    @click="handleCreateWriteJob(row)"
                  >创建写卡任务</el-button>
                  <el-button
                    v-if="row.writeJobId && (row.writeJobStatus === 'EXPORTED' || row.writeJobStatus === 'RESULT_IMPORTED')"
                    size="mini"
                    type="text"
                    @click="openWriteJobDialog(row)"
                  >写卡任务</el-button>
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

    <!-- 创建批次对话框 -->
    <NfcBatchDialog
      :visible.sync="batchDialogVisible"
      @created="onBatchCreated"
    />

    <!-- Scheme 进度对话框 -->
    <el-dialog
      title="Scheme 生成进度"
      :visible.sync="schemeDialogVisible"
      width="500px"
      :close-on-click-modal="false"
      @close="stopSchemePolling"
    >
      <div v-loading="schemeLoading" class="scheme-progress">
        <el-descriptions :column="1" border size="medium">
          <el-descriptions-item label="批次号">{{ currentBatch?.batchNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="任务ID">{{ schemeJobDetail.id || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="badgeType(schemeJobDetail.status)" size="small">
              {{ statusText(schemeJobDetail.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="进度">
            <el-progress
              :percentage="schemeProgressPercent"
              :status="schemeProgressStatus"
            ></el-progress>
          </el-descriptions-item>
          <el-descriptions-item label="已处理">{{ schemeJobDetail.processedCount ?? 0 }} / {{ schemeJobDetail.totalCount ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="错误信息" v-if="schemeJobDetail.errorMessage">
            <span class="error-text">{{ schemeJobDetail.errorMessage }}</span>
          </el-descriptions-item>
        </el-descriptions>
        <div v-if="schemeJobDetail.status === 'FAILED'" class="scheme-actions">
          <el-button type="warning" @click="handleRetryScheme">重试</el-button>
          <el-button type="danger" @click="handleCancelScheme">取消</el-button>
        </div>
      </div>
      <template slot="footer">
        <el-button @click="schemeDialogVisible = false">关 闭</el-button>
      </template>
    </el-dialog>

    <!-- 写卡任务对话框 -->
    <NfcWriteJobDialog
      :visible.sync="writeJobDialogVisible"
      :job-id="currentWriteJobId"
      @refresh="fetchBatches"
    />
  </div>
</template>

<script>
import Api from '@/apis/api'
import HeaderBar from '@/components/HeaderBar.vue'
import NfcBatchDialog from '@/components/nfc/NfcBatchDialog.vue'
import NfcWriteJobDialog from '@/components/nfc/NfcWriteJobDialog.vue'
import { batchActions, statusBadgeType, statusLabel } from '@/utils/pdcNfcState.mjs'

export default {
  name: 'NfcBatchManagement',
  components: { HeaderBar, NfcBatchDialog, NfcWriteJobDialog },
  data() {
    return {
      loading: false,
      tableData: [],
      total: 0,
      currentPage: 1,
      pageSize: 10,
      searchKeyword: '',
      activeSearchKeyword: '',
      // Batch dialog
      batchDialogVisible: false,
      // Scheme progress
      schemeDialogVisible: false,
      schemeLoading: false,
      currentBatch: null,
      schemeJobDetail: {},
      schemePollTimer: null,
      // Write job dialog
      writeJobDialogVisible: false,
      currentWriteJobId: ''
    }
  },
  computed: {
    schemeProgressPercent() {
      const total = this.schemeJobDetail.totalCount || 0
      const processed = this.schemeJobDetail.processedCount || 0
      if (total === 0) return 0
      return Math.min(100, Math.round((processed / total) * 100))
    },
    schemeProgressStatus() {
      const s = this.schemeJobDetail.status
      if (s === 'SUCCEEDED') return 'success'
      if (s === 'FAILED') return 'exception'
      return null
    }
  },
  created() {
    this.fetchBatches()
  },
  beforeDestroy() {
    this.stopSchemePolling()
  },
  methods: {
    actions: batchActions,
    badgeType: statusBadgeType,
    statusText: statusLabel,
    fetchBatches() {
      this.loading = true
      const params = {
        page: this.currentPage,
        limit: this.pageSize
      }
      if (this.activeSearchKeyword) {
        params.keyword = this.activeSearchKeyword
      }
      Api.pdcNfc.listBatches(params, (res) => {
        this.loading = false
        if (res.data && res.data.code === 0) {
          const data = res.data.data
          this.tableData = data?.list || data || []
          this.total = data?.total || this.tableData.length
        } else {
          this.$message.error(res.data?.msg || '获取批次列表失败')
        }
      })
    },
    handleSearch() {
      this.activeSearchKeyword = this.searchKeyword
      this.currentPage = 1
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
    },
    onBatchCreated() {
      this.currentPage = 1
      this.fetchBatches()
    },
    handleGenerateScheme(row) {
      this.$confirm('确认为该批次生成 Scheme 吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'info'
      }).then(() => {
        Api.pdcNfc.startSchemeJob(row.id, (res) => {
          if (res.data && res.data.code === 0) {
            this.$message.success('Scheme 任务已启动')
            this.fetchBatches()
            // If the response includes job info, open progress dialog
            const job = res.data.data
            if (job && job.id) {
              this.openSchemeProgressWithJob(row, job)
            }
          } else {
            this.$message.error(res.data?.msg || '启动 Scheme 任务失败')
          }
        })
      }).catch(() => {})
    },
    openSchemeProgress(row) {
      this.currentBatch = row
      this.schemeJobDetail = {}
      this.schemeDialogVisible = true
      if (row.schemeJobId) {
        this.fetchSchemeProgress(row.schemeJobId)
        this.startSchemePolling(row.schemeJobId)
      }
    },
    openSchemeProgressWithJob(row, job) {
      this.currentBatch = row
      this.schemeJobDetail = job || {}
      this.schemeDialogVisible = true
      if (job && job.id) {
        this.startSchemePolling(job.id)
      }
    },
    fetchSchemeProgress(jobId) {
      if (!jobId) return
      this.schemeLoading = true
      Api.pdcNfc.schemeJobProgress(jobId, (res) => {
        this.schemeLoading = false
        if (res.data && res.data.code === 0) {
          this.schemeJobDetail = res.data.data || {}
          // Stop polling on terminal states
          if (['SUCCEEDED', 'FAILED', 'CANCELLED'].includes(this.schemeJobDetail.status)) {
            this.stopSchemePolling()
            this.fetchBatches()
          }
        }
      })
    },
    startSchemePolling(jobId) {
      this.stopSchemePolling()
      this.schemePollTimer = setInterval(() => {
        this.fetchSchemeProgress(jobId)
      }, 2000)
    },
    stopSchemePolling() {
      if (this.schemePollTimer) {
        clearInterval(this.schemePollTimer)
        this.schemePollTimer = null
      }
    },
    handleRetryScheme() {
      const jobId = this.schemeJobDetail.id
      if (!jobId) return
      Api.pdcNfc.retrySchemeJob(jobId, (res) => {
        if (res.data && res.data.code === 0) {
          this.$message.success('重试已触发')
          this.fetchSchemeProgress(jobId)
          this.startSchemePolling(jobId)
        } else {
          this.$message.error(res.data?.msg || '重试失败')
        }
      })
    },
    handleCancelScheme() {
      const jobId = this.schemeJobDetail.id
      if (!jobId) return
      this.$confirm('确认取消该 Scheme 任务吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        Api.pdcNfc.cancelSchemeJob(jobId, (res) => {
          if (res.data && res.data.code === 0) {
            this.$message.success('任务已取消')
            this.stopSchemePolling()
            this.fetchSchemeProgress(jobId)
            this.fetchBatches()
          } else {
            this.$message.error(res.data?.msg || '取消失败')
          }
        })
      }).catch(() => {})
    },
    handleCreateWriteJob(row) {
      this.$confirm('确认为该批次创建写卡任务吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'info'
      }).then(() => {
        Api.pdcNfc.createWriteJob(row.id, (res) => {
          if (res.data && res.data.code === 0) {
            this.$message.success('写卡任务已创建')
            this.fetchBatches()
            const job = res.data.data
            if (job && job.id) {
              this.currentWriteJobId = job.id
              this.writeJobDialogVisible = true
            }
          } else {
            this.$message.error(res.data?.msg || '创建写卡任务失败')
          }
        })
      }).catch(() => {})
    },
    openWriteJobDialog(row) {
      this.currentWriteJobId = row.writeJobId
      this.writeJobDialogVisible = true
    }
  }
}
</script>

<style lang="scss" scoped>
.nfc-batch-page {
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

.search-input {
  width: 220px;
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

.stat-item {
  font-size: 13px;
  color: #606266;
}

.scheme-progress {
  min-height: 120px;
}

.scheme-actions {
  margin-top: 16px;
  display: flex;
  gap: 12px;
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
</style>
