<template>
  <el-dialog
    title="写卡任务"
    :visible.sync="dialogVisible"
    width="650px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div v-loading="loading" class="write-job-detail">
      <el-descriptions :column="2" border size="medium">
        <el-descriptions-item label="任务ID">{{ jobDetail.id || '-' }}</el-descriptions-item>
        <el-descriptions-item label="批次号">{{ jobDetail.batchNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="badgeType(jobDetail.status)">{{ statusText(jobDetail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="计划数量">{{ jobDetail.plannedQuantity || '-' }}</el-descriptions-item>
        <el-descriptions-item label="已导出">{{ jobDetail.exportedCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="已写入">{{ jobDetail.writtenCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="已验证">{{ jobDetail.verifiedCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="失败数">{{ jobDetail.failureCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="创建时间" :span="2">{{ formatTime(jobDetail.createTime) }}</el-descriptions-item>
      </el-descriptions>

      <div v-if="jobDetail.status === 'EXPORTED' || jobDetail.status === 'RESULT_IMPORTED'" class="job-actions">
        <el-button type="primary" icon="el-icon-download" :loading="downloading" @click="handleDownload">
          下载写卡数据
        </el-button>
        <el-button type="warning" icon="el-icon-upload2" @click="showImportDialog = true">
          导入写卡结果
        </el-button>
      </div>
    </div>

    <template slot="footer">
      <el-button @click="handleClose">关 闭</el-button>
    </template>

    <NfcWriteResultImportDialog
      :visible.sync="showImportDialog"
      :job-id="jobDetail.id || jobId"
      @imported="onImported"
    />
  </el-dialog>
</template>

<script>
import Api from '@/apis/api'
import { statusBadgeType, statusLabel, formatDate } from '@/utils/pdcNfcState.mjs'
import NfcWriteResultImportDialog from './NfcWriteResultImportDialog.vue'

export default {
  name: 'NfcWriteJobDialog',
  components: { NfcWriteResultImportDialog },
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    jobId: {
      type: [String, Number],
      default: ''
    }
  },
  data() {
    return {
      dialogVisible: this.visible,
      loading: false,
      downloading: false,
      showImportDialog: false,
      jobDetail: {},
      pollTimer: null
    }
  },
  watch: {
    visible(val) {
      this.dialogVisible = val
      if (val && this.jobId) {
        this.fetchJobDetail()
        this.startPolling()
      }
    },
    dialogVisible(val) {
      this.$emit('update:visible', val)
      if (!val) {
        this.stopPolling()
      }
    },
    jobId(val) {
      if (this.dialogVisible && val) {
        this.fetchJobDetail()
      }
    }
  },
  beforeDestroy() {
    this.stopPolling()
  },
  methods: {
    badgeType: statusBadgeType,
    statusText: statusLabel,
    formatTime: formatDate,
    fetchJobDetail() {
      if (!this.jobId) return
      this.loading = true
      Api.pdcNfc.getWriteJob(this.jobId, (res) => {
        this.loading = false
        if (res.data && res.data.code === 0) {
          this.jobDetail = res.data.data || {}
          // Stop polling if terminal state
          if (['EXPORTED', 'RESULT_IMPORTED', 'FAILED', 'CANCELLED'].includes(this.jobDetail.status)) {
            this.stopPolling()
          }
        } else {
          this.$message.error(res.data?.msg || '获取写卡任务详情失败')
        }
      })
    },
    startPolling() {
      this.stopPolling()
      this.pollTimer = setInterval(() => {
        this.fetchJobDetail()
      }, 3000)
    },
    stopPolling() {
      if (this.pollTimer) {
        clearInterval(this.pollTimer)
        this.pollTimer = null
      }
    },
    handleDownload() {
      if (!this.jobDetail.id) {
        this.$message.warning('任务信息不完整')
        return
      }
      this.downloading = true
      Api.pdcNfc.downloadWriteJob(this.jobDetail.id, (res) => {
        this.downloading = false
        // The blob response comes back as res.data (already a Blob when responseType is blob)
        const blob = res.data instanceof Blob ? res.data : new Blob([res.data])
        const url = window.URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.download = `write_job_${this.jobDetail.id}.csv`
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        window.URL.revokeObjectURL(url)
        this.$message.success('下载已开始')
      })
    },
    onImported() {
      this.fetchJobDetail()
      this.$emit('refresh')
    },
    handleClose() {
      this.dialogVisible = false
      this.jobDetail = {}
    }
  }
}
</script>

<style lang="scss" scoped>
.write-job-detail {
  min-height: 120px;
}

.job-actions {
  margin-top: 20px;
  display: flex;
  gap: 12px;
}
</style>
