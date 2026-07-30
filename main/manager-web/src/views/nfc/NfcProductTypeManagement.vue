<template>
  <div class="nfc-product-type-page">
    <HeaderBar />
    <div class="main-wrapper">
      <div class="content-panel">
        <div class="content-area">
          <el-card class="page-card" shadow="never">
            <div class="operation-header">
              <h2 class="page-title">NFC 商品类型</h2>
              <div class="right-operations">
                <el-input
                  v-model="searchKeyword"
                  placeholder="搜索类型编码/名称"
                  class="search-input"
                  clearable
                  @keyup.enter.native="handleSearch"
                />
                <el-button icon="el-icon-search" type="primary" @click="handleSearch">搜索</el-button>
              </div>
            </div>

            <el-table
              v-loading="loading"
              :data="filteredData"
              border
              stripe
              style="width: 100%;"
              :header-cell-style="{ background: '#f5f7fa' }"
            >
              <el-table-column prop="typeCode" label="类型编码" min-width="140" align="center"></el-table-column>
              <el-table-column prop="typeName" label="类型名称" min-width="160" align="center"></el-table-column>
              <el-table-column prop="capabilityMode" label="能力模式" min-width="100" align="center">
                <template slot-scope="{ row }">
                  {{ row.capabilityMode || '-' }}
                </template>
              </el-table-column>
              <el-table-column label="Model ID" min-width="180" align="center">
                <template slot-scope="{ row }">
                  {{ modelIdLabel(row) }}
                </template>
              </el-table-column>
              <el-table-column label="发布就绪" min-width="100" align="center">
                <template slot-scope="{ row }">
                  <el-tag :type="row.releaseReady ? 'success' : 'info'" size="small">
                    {{ row.releaseReady ? '已就绪' : '未就绪' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="最新证据" min-width="200" align="center">
                <template slot-scope="{ row }">
                  <span v-if="row.latestEvidence">
                    {{ row.latestEvidence.releaseVersion }}
                    <span class="evidence-time">{{ formatDate(row.latestEvidence.publishedAt) }}</span>
                  </span>
                  <span v-else class="text-muted">-</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="140" align="center" fixed="right">
                <template slot-scope="{ row }">
                  <el-button size="mini" type="text" @click="openEvidenceDialog(row)">
                    登记发布证据
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </div>
      </div>
    </div>

    <!-- 登记发布证据对话框 -->
    <el-dialog
      title="登记发布证据"
      :visible.sync="evidenceDialogVisible"
      width="520px"
      :close-on-click-modal="false"
      @close="resetEvidenceForm"
    >
      <el-form
        ref="evidenceForm"
        :model="evidenceForm"
        :rules="evidenceRules"
        label-width="100px"
        label-position="right"
      >
        <el-form-item label="发布版本" prop="releaseVersion">
          <el-input v-model="evidenceForm.releaseVersion" placeholder="请输入当前领取页发布版本"></el-input>
        </el-form-item>
        <el-form-item label="发布时间" prop="publishedAt">
          <el-input v-model="evidenceForm.publishedAt" placeholder="例如 2026-07-30T10:00:00+08:00"></el-input>
        </el-form-item>
        <el-form-item label="冒烟证据" prop="smokeEvidence">
          <el-input
            type="textarea"
            v-model="evidenceForm.smokeEvidence"
            :rows="4"
            placeholder="请输入冒烟验证证据"
            maxlength="500"
            show-word-limit
          ></el-input>
        </el-form-item>
      </el-form>
      <template slot="footer">
        <el-button @click="evidenceDialogVisible = false">取 消</el-button>
        <el-button type="primary" :loading="evidenceSaving" @click="submitEvidence">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import Api from '@/apis/api'
import HeaderBar from '@/components/HeaderBar.vue'
import { modelIdLabel, formatDate } from '@/utils/pdcNfcState.mjs'
import { buildReleaseEvidencePayload } from '@/utils/pdcNfcReleaseEvidence.mjs'

export default {
  name: 'NfcProductTypeManagement',
  components: { HeaderBar },
  data() {
    return {
      loading: false,
      tableData: [],
      searchKeyword: '',
      // Evidence dialog
      evidenceDialogVisible: false,
      evidenceSaving: false,
      currentProductType: null,
      evidenceForm: {
        releaseVersion: '',
        publishedAt: '',
        smokeEvidence: ''
      },
      evidenceRules: {
        releaseVersion: [{ required: true, message: '请输入发布版本', trigger: 'blur' }],
        publishedAt: [{ required: true, message: '请输入发布时间', trigger: 'blur' }],
        smokeEvidence: [{ required: true, message: '请输入冒烟验证证据', trigger: 'blur' }]
      }
    }
  },
  computed: {
    filteredData() {
      if (!this.searchKeyword) return this.tableData
      const kw = this.searchKeyword.toLowerCase()
      return this.tableData.filter(row =>
        (row.typeCode && row.typeCode.toLowerCase().includes(kw)) ||
        (row.typeName && row.typeName.toLowerCase().includes(kw))
      )
    }
  },
  created() {
    this.fetchProductTypes()
  },
  methods: {
    modelIdLabel,
    formatDate,
    fetchProductTypes() {
      this.loading = true
      Api.pdcNfc.listProductTypes({}, (res) => {
        this.loading = false
        if (res.data && res.data.code === 0) {
          this.tableData = res.data.data || []
        } else {
          this.$message.error(res.data?.msg || '获取商品类型列表失败')
        }
      })
    },
    handleSearch() {
      // 前端过滤，无需重新请求
    },
    openEvidenceDialog(row) {
      this.currentProductType = row
      this.evidenceForm = { releaseVersion: '', publishedAt: '', smokeEvidence: '' }
      this.evidenceDialogVisible = true
    },
    submitEvidence() {
      this.$refs.evidenceForm.validate((valid) => {
        if (!valid) return
        this.evidenceSaving = true
        const payload = buildReleaseEvidencePayload(this.evidenceForm)
        Api.pdcNfc.registerReleaseEvidence(payload, (res) => {
          this.evidenceSaving = false
          if (res.data && res.data.code === 0) {
            this.$message.success('发布证据登记成功')
            this.evidenceDialogVisible = false
            this.fetchProductTypes()
          } else {
            this.$message.error(res.data?.msg || '发布证据登记失败')
          }
        })
      })
    },
    resetEvidenceForm() {
      this.currentProductType = null
      this.$nextTick(() => {
        if (this.$refs.evidenceForm) {
          this.$refs.evidenceForm.resetFields()
        }
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.nfc-product-type-page {
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
  width: 240px;
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

.evidence-time {
  color: #909399;
  font-size: 12px;
  margin-left: 6px;
}

.text-muted {
  color: #c0c4cc;
}
</style>
