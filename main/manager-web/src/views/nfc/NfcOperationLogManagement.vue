<template>
  <div class="nfc-log-page">
    <HeaderBar />
    <div class="main-wrapper">
      <div class="content-panel">
        <div class="content-area">
          <el-card class="page-card" shadow="never">
            <div class="operation-header">
              <h2 class="page-title">NFC 操作日志</h2>
            </div>

            <!-- 筛选栏 -->
            <div class="filter-bar">
              <el-select
                v-model="filters.objectType"
                placeholder="对象类型"
                clearable
                class="filter-item"
              >
                <el-option
                  v-for="opt in objectTypeOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                ></el-option>
              </el-select>
              <el-select
                v-model="filters.operationType"
                placeholder="操作类型"
                clearable
                class="filter-item"
              >
                <el-option
                  v-for="opt in operationTypeOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
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

            <div class="table-scroll">
              <el-table
                v-loading="loading"
                :data="tableData"
                border
                stripe
                style="width: 100%;"
                :header-cell-style="{ background: '#f5f7fa' }"
              >
              <el-table-column type="expand">
                <template slot-scope="{ row }">
                  <div class="expand-detail">
                    <el-row :gutter="20">
                      <el-col :span="12">
                        <h4 class="detail-title">变更前状态</h4>
                        <pre class="detail-json">{{ formatJson(row.beforeStatus) }}</pre>
                      </el-col>
                      <el-col :span="12">
                        <h4 class="detail-title">变更后状态</h4>
                        <pre class="detail-json">{{ formatJson(row.afterStatus) }}</pre>
                      </el-col>
                    </el-row>
                    <el-divider></el-divider>
                    <div v-if="row.detailJson" class="detail-extra">
                      <h4 class="detail-title">操作详情</h4>
                      <pre class="detail-json">{{ formatJson(row.detailJson) }}</pre>
                    </div>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="操作时间" min-width="160" align="center">
                <template slot-scope="{ row }">{{ formatTime(row.operateTime) }}</template>
              </el-table-column>
              <el-table-column prop="objectType" label="对象类型" min-width="120" align="center">
                <template slot-scope="{ row }">{{ objectTypeLabel(row.objectType) }}</template>
              </el-table-column>
              <el-table-column prop="objectId" label="对象 ID" min-width="120" align="center"></el-table-column>
              <el-table-column prop="operationType" label="操作类型" min-width="120" align="center">
                <template slot-scope="{ row }">{{ operationTypeLabel(row.operationType) }}</template>
              </el-table-column>
              <el-table-column prop="operatorId" label="操作人" min-width="100" align="center"></el-table-column>
              <el-table-column label="操作详情" min-width="280" align="center">
                <template slot-scope="{ row }">
                  <span class="detail-preview">{{ truncateDetail(row.detailJson) }}</span>
                </template>
              </el-table-column>
              </el-table>
            </div>

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
  </div>
</template>

<script>
import Api from '@/apis/api'
import HeaderBar from '@/components/HeaderBar.vue'
import { formatDate, operationTypeLabel, operationTypeOptions } from '@/utils/pdcNfcState.mjs'

export default {
  name: 'NfcOperationLogManagement',
  components: { HeaderBar },
  data() {
    return {
      loading: false,
      tableData: [],
      total: 0,
      currentPage: 1,
      pageSize: 20,
      filters: {
        objectType: '',
        operationType: '',
        dateRange: null
      },
      activeFilters: {},
      // 操作类型筛选选项与列显示共用 pdcNfcState 的同一映射，文案不漂移
      operationTypeOptions: operationTypeOptions(),
      objectTypeOptions: [
        { value: 'PRODUCT_TYPE', label: '商品类型' },
        { value: 'BATCH', label: '批次' },
        { value: 'SCHEME_JOB', label: 'Scheme 任务' },
        { value: 'WRITE_JOB', label: '写卡任务' },
        { value: 'ASSET', label: '资产' }
      ]
    }
  },
  created() {
    this.fetchLogs()
  },
  methods: {
    formatTime: formatDate,
    operationTypeLabel,
    objectTypeLabel(type) {
      const found = this.objectTypeOptions.find(o => o.value === type)
      return found ? found.label : (type || '-')
    },
    formatJson(value) {
      if (!value) return '-'
      if (typeof value === 'string') {
        try {
          return JSON.stringify(JSON.parse(value), null, 2)
        } catch {
          return value
        }
      }
      try {
        return JSON.stringify(value, null, 2)
      } catch {
        return String(value)
      }
    },
    truncateDetail(value) {
      if (!value) return '-'
      const str = typeof value === 'string' ? value : JSON.stringify(value)
      if (str.length <= 60) return str
      return str.substring(0, 57) + '...'
    },
    buildParams() {
      const params = {
        page: this.currentPage,
        limit: this.pageSize
      }
      const f = this.activeFilters
      if (f.objectType) params.objectType = f.objectType
      if (f.operationType) params.operationType = f.operationType
      if (f.dateRange && f.dateRange.length === 2) {
        params.startDate = f.dateRange[0]
        params.endDate = f.dateRange[1]
      }
      return params
    },
    fetchLogs() {
      this.loading = true
      Api.pdcNfc.listLogs(this.buildParams(), (res) => {
        this.loading = false
        if (res.data && res.data.code === 0) {
          const data = res.data.data
          this.tableData = data?.list || data || []
          this.total = data?.total || this.tableData.length
        } else {
          this.$message.error(res.data?.msg || '获取操作日志失败')
        }
      })
    },
    handleSearch() {
      this.activeFilters = { ...this.filters }
      this.currentPage = 1
      this.fetchLogs()
    },
    handleResetFilters() {
      this.filters = {
        objectType: '',
        operationType: '',
        dateRange: null
      }
      this.activeFilters = {}
      this.currentPage = 1
      this.fetchLogs()
    },
    handlePageChange(page) {
      this.currentPage = page
      this.fetchLogs()
    },
    handleSizeChange(size) {
      this.pageSize = size
      this.currentPage = 1
      this.fetchLogs()
    }
  }
}
</script>

<style lang="scss" scoped>
.nfc-log-page {
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
  width: 180px;
}

.filter-item-date {
  width: 300px;
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

/* 表格区域占满卡片剩余空间并内部滚动：祖先链全是定高 + overflow:hidden，
   不加这一层时超屏行会被直接裁掉且无滚动条 */
.table-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
}

.expand-detail {
  padding: 10px 20px;
}

.detail-title {
  font-size: 14px;
  font-weight: 500;
  margin: 0 0 8px;
  color: #303133;
}

.detail-json {
  background: #f5f7fa;
  border-radius: 4px;
  padding: 10px;
  font-size: 12px;
  font-family: monospace;
  color: #606266;
  max-height: 200px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}

.detail-extra {
  margin-top: 10px;
}

.detail-preview {
  font-size: 12px;
  color: #909399;
  font-family: monospace;
}
</style>
