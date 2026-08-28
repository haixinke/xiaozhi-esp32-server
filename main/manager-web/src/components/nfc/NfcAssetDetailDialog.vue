<template>
  <el-dialog
    title="资产详情"
    :visible.sync="dialogVisible"
    width="720px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div v-loading="loading" class="asset-detail">
      <el-descriptions :column="2" border size="medium">
        <el-descriptions-item label="资产编号">{{ safeView.assetNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="批次号">{{ safeView.batchNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="物料序号">{{ safeView.itemNo ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="SKU 编码">{{ safeView.skuCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="原型">{{ safeView.prototype || '-' }}</el-descriptions-item>
        <el-descriptions-item label="微信 SN">{{ safeView.wechatSn || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="badgeType(safeView.status)" size="small">{{ statusText(safeView.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="Scheme SHA256">
          <span class="hash-text">{{ truncateHash(safeView.schemeSha256) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="入库单号">{{ safeView.stockBusinessNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="激活单号">{{ safeView.activationBusinessNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="入库时间">{{ formatTime(safeView.stockedAt) }}</el-descriptions-item>
        <el-descriptions-item label="激活时间">{{ formatTime(safeView.activatedAt) }}</el-descriptions-item>
        <el-descriptions-item label="领取时间" :span="2">{{ formatTime(safeView.claimedAt) }}</el-descriptions-item>
      </el-descriptions>

      <!-- 状态时间线 -->
      <div v-if="safeView.statusTimeline && safeView.statusTimeline.length" class="timeline-section">
        <h4 class="section-title">状态时间线</h4>
        <el-timeline>
          <el-timeline-item
            v-for="(item, idx) in safeView.statusTimeline"
            :key="idx"
            :timestamp="formatTime(item.time)"
            :type="badgeType(item.status)"
          >
            <el-tag :type="badgeType(item.status)" size="small">{{ statusText(item.status) }}</el-tag>
          </el-timeline-item>
        </el-timeline>
      </div>

      <!-- 操作日志子表 -->
      <div class="logs-section">
        <h4 class="section-title">操作日志</h4>
        <el-table
          v-loading="logsLoading"
          :data="logsData"
          border
          stripe
          size="small"
          style="width: 100%;"
          :header-cell-style="{ background: '#f5f7fa' }"
        >
          <el-table-column prop="operateTime" label="操作时间" min-width="160" align="center">
            <template slot-scope="{ row }">{{ formatTime(row.operateTime) }}</template>
          </el-table-column>
          <el-table-column prop="operationType" label="操作类型" min-width="120" align="center">
            <template slot-scope="{ row }">{{ operationTypeLabel(row.operationType) }}</template>
          </el-table-column>
          <el-table-column prop="operatorId" label="操作人" min-width="100" align="center"></el-table-column>
        </el-table>
        <div v-if="logsTotal > logsPageSize" class="logs-pagination">
          <el-pagination
            background
            small
            layout="prev, pager, next"
            :total="logsTotal"
            :current-page="logsPage"
            :page-size="logsPageSize"
            @current-change="handleLogsPageChange"
          ></el-pagination>
        </div>
      </div>
    </div>

    <template slot="footer">
      <el-button @click="handleClose">关 闭</el-button>
    </template>
  </el-dialog>
</template>

<script>
import Api from '@/apis/api'
import { statusBadgeType, statusLabel, formatDate, presentAsset, operationTypeLabel } from '@/utils/pdcNfcState.mjs'

export default {
  name: 'NfcAssetDetailDialog',
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    assetId: {
      type: [String, Number],
      default: ''
    }
  },
  data() {
    return {
      dialogVisible: this.visible,
      loading: false,
      rawAsset: null,
      // logs
      logsLoading: false,
      logsData: [],
      logsTotal: 0,
      logsPage: 1,
      logsPageSize: 5
    }
  },
  computed: {
    // 安全视图：仅暴露白名单字段，敏感数据永远不会进入 DOM
    safeView() {
      return presentAsset(this.rawAsset) || {}
    }
  },
  watch: {
    visible(val) {
      this.dialogVisible = val
      if (val && this.assetId) {
        this.fetchDetail()
      }
    },
    dialogVisible(val) {
      this.$emit('update:visible', val)
    },
    assetId(val) {
      if (this.dialogVisible && val) {
        this.fetchDetail()
      }
    }
  },
  methods: {
    badgeType: statusBadgeType,
    statusText: statusLabel,
    operationTypeLabel,
    formatTime: formatDate,
    truncateHash(hash) {
      if (!hash) return '-'
      if (hash.length <= 16) return hash
      return hash.substring(0, 12) + '…'
    },
    fetchDetail() {
      if (!this.assetId) return
      this.loading = true
      Api.pdcNfc.assetDetail(this.assetId, (res) => {
        this.loading = false
        if (res.data && res.data.code === 0) {
          this.rawAsset = res.data.data || {}
        } else {
          this.$message.error(res.data?.msg || '获取资产详情失败')
        }
      })
      this.fetchLogs()
    },
    fetchLogs() {
      if (!this.assetId) return
      this.logsLoading = true
      const params = {
        page: this.logsPage,
        limit: this.logsPageSize
      }
      Api.pdcNfc.listLogsByObject('ASSET', this.assetId, params, (res) => {
        this.logsLoading = false
        if (res.data && res.data.code === 0) {
          const data = res.data.data
          this.logsData = data?.list || data || []
          this.logsTotal = data?.total || this.logsData.length
        }
      })
    },
    handleLogsPageChange(page) {
      this.logsPage = page
      this.fetchLogs()
    },
    handleClose() {
      this.dialogVisible = false
      this.rawAsset = null
      this.logsData = []
      this.logsTotal = 0
      this.logsPage = 1
    }
  }
}
</script>

<style lang="scss" scoped>
.asset-detail {
  min-height: 120px;
}

.hash-text {
  font-family: monospace;
  font-size: 13px;
  word-break: break-all;
}

.section-title {
  font-size: 15px;
  font-weight: 500;
  margin: 20px 0 12px;
  color: #303133;
}

.timeline-section {
  margin-top: 8px;
}

.logs-section {
  margin-top: 8px;
}

.logs-pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: 10px;
}
</style>
