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
              <el-table-column label="统计" min-width="120" align="center">
                <template slot-scope="{ row }">
                  <span class="stat-item">资产: {{ row.assetCount ?? 0 }}</span>
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
          <el-descriptions-item label="任务ID">{{ schemeJobDetail.jobId || '-' }}</el-descriptions-item>
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
          <el-descriptions-item label="已处理">{{ schemeProcessedCount }} / {{ schemeJobDetail.totalCount ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="错误信息" v-if="schemeJobDetail.lastError">
            <span class="error-text">{{ schemeJobDetail.lastError }}</span>
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

    <!-- 创建写卡任务对话框：选择写卡模式（ADR 0003，创建后不可变更） -->
    <el-dialog
      title="创建写卡任务"
      :visible.sync="createJobDialogVisible"
      width="460px"
      :close-on-click-modal="false"
    >
      <div v-if="createJobTargetRow" class="create-mode-dialog">
        <p class="create-mode-batch">批次：{{ createJobTargetRow.batchNo }}</p>
        <el-radio-group v-model="createJobMode">
          <el-radio label="MANUAL" class="create-mode-option">
            手动模式（小批量验证）
            <div class="create-mode-hint">手机 NFC App 逐张写卡，触碰自验证，验证通过后锁卡再入库</div>
          </el-radio>
          <!-- 工厂 CSV 模式暂不可用：验证阶段仅支持手动模式，恢复时去掉 disabled 即可（后端能力保留） -->
          <el-radio label="FACTORY_CSV" class="create-mode-option" disabled>
            工厂 CSV 模式
            <div class="create-mode-hint">量产：导出 CSV 给工厂设备批量写卡，回传结果导入（暂不可用，验证阶段仅支持手动模式）</div>
          </el-radio>
        </el-radio-group>
      </div>
      <span slot="footer">
        <el-button size="small" @click="createJobDialogVisible = false">取消</el-button>
        <el-button size="small" type="primary" @click="confirmCreateWriteJob">创建</el-button>
      </span>
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
      currentWriteJobId: '',
      // 创建写卡任务对话框：默认手动模式（当前小批量验证阶段手动为主路径，ADR 0003）
      createJobDialogVisible: false,
      createJobTargetRow: null,
      createJobMode: 'MANUAL'
    }
  },
  computed: {
    // 后端 PdcNfcSchemeProgressVO 只有 successCount/failureCount，已处理数前端相加
    schemeProcessedCount() {
      const success = this.schemeJobDetail.successCount || 0
      const failure = this.schemeJobDetail.failureCount || 0
      return success + failure
    },
    schemeProgressPercent() {
      const total = this.schemeJobDetail.totalCount || 0
      if (total === 0) return 0
      return Math.min(100, Math.round((this.schemeProcessedCount / total) * 100))
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
            this.openSchemeProgress(row)
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
      // progress 接口按批次查询（/scheme/progress/{batchId}）
      this.fetchSchemeProgress(row.id)
      this.startSchemePolling(row.id)
    },
    fetchSchemeProgress(batchId) {
      if (!batchId) return
      this.schemeLoading = true
      Api.pdcNfc.schemeJobProgress(batchId, (res) => {
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
    startSchemePolling(batchId) {
      this.stopSchemePolling()
      this.schemePollTimer = setInterval(() => {
        this.fetchSchemeProgress(batchId)
      }, 2000)
    },
    stopSchemePolling() {
      if (this.schemePollTimer) {
        clearInterval(this.schemePollTimer)
        this.schemePollTimer = null
      }
    },
    handleRetryScheme() {
      const batchId = this.currentBatch?.id
      if (!batchId) return
      // retry 接口按批次（/scheme/retry/{batchId}），重试后按批次轮询新任务
      Api.pdcNfc.retrySchemeJob(batchId, (res) => {
        if (res.data && res.data.code === 0) {
          this.$message.success('重试已触发')
          this.fetchSchemeProgress(batchId)
          this.startSchemePolling(batchId)
        } else {
          this.$message.error(res.data?.msg || '重试失败')
        }
      })
    },
    handleCancelScheme() {
      const jobId = this.schemeJobDetail.jobId
      if (!jobId) return
      const batchId = this.currentBatch?.id
      this.$confirm('确认取消该 Scheme 任务吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        // cancel 接口按任务（/scheme/cancel/{jobId}）
        Api.pdcNfc.cancelSchemeJob(jobId, (res) => {
          if (res.data && res.data.code === 0) {
            this.$message.success('任务已取消')
            this.stopSchemePolling()
            if (batchId) this.fetchSchemeProgress(batchId)
            this.fetchBatches()
          } else {
            this.$message.error(res.data?.msg || '取消失败')
          }
        })
      }).catch(() => {})
    },
    handleCreateWriteJob(row) {
      this.createJobTargetRow = row
      this.createJobMode = 'MANUAL'
      this.createJobDialogVisible = true
    },
    confirmCreateWriteJob() {
      const row = this.createJobTargetRow
      if (!row) return
      Api.pdcNfc.createWriteJob(row.id, this.createJobMode, (res) => {
        if (res.data && res.data.code === 0) {
          this.createJobDialogVisible = false
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
