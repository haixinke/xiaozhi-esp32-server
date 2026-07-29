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
              :data="tableData"
              border
              stripe
              style="width: 100%;"
              :header-cell-style="{ background: '#f5f7fa' }"
            >
              <el-table-column prop="typeCode" label="类型编码" min-width="140" align="center"></el-table-column>
              <el-table-column prop="typeName" label="类型名称" min-width="160" align="center"></el-table-column>
              <el-table-column prop="prototype" label="原型" min-width="100" align="center">
                <template slot-scope="{ row }">
                  {{ row.prototype || '-' }}
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
                    v{{ row.latestEvidence.version }}
                    <span class="evidence-time">{{ formatDate(row.latestEvidence.releaseTime) }}</span>
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
        <el-form-item label="商品类型">
          <span>{{ currentProductType?.typeCode }} - {{ currentProductType?.typeName }}</span>
        </el-form-item>
        <el-form-item label="版本号" prop="version">
          <el-input v-model="evidenceForm.version" placeholder="请输入版本号" maxlength="32"></el-input>
        </el-form-item>
        <el-form-item label="发布时间" prop="releaseTime">
          <el-date-picker
            v-model="evidenceForm.releaseTime"
            type="datetime"
            placeholder="选择发布时间"
            value-format="yyyy-MM-dd HH:mm:ss"
            style="width: 100%;"
          ></el-date-picker>
        </el-form-item>
        <el-form-item label="证据内容" prop="evidence">
          <el-input
            type="textarea"
            v-model="evidenceForm.evidence"
            :rows="4"
            placeholder="请输入证据内容"
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

export default {
  name: 'NfcProductTypeManagement',
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
      // Evidence dialog
      evidenceDialogVisible: false,
      evidenceSaving: false,
      currentProductType: null,
      evidenceForm: {
        version: '',
        releaseTime: '',
        evidence: ''
      },
      evidenceRules: {
        version: [{ required: true, message: '请输入版本号', trigger: 'blur' }],
        releaseTime: [{ required: true, message: '请选择发布时间', trigger: 'change' }],
        evidence: [{ required: true, message: '请输入证据内容', trigger: 'blur' }]
      }
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
      const params = {
        page: this.currentPage,
        limit: this.pageSize
      }
      if (this.activeSearchKeyword) {
        params.keyword = this.activeSearchKeyword
      }
      Api.pdcNfc.listProductTypes(params, (res) => {
        this.loading = false
        if (res.data && res.data.code === 0) {
          const data = res.data.data
          this.tableData = data?.list || data || []
          this.total = data?.total || this.tableData.length
        } else {
          this.$message.error(res.data?.msg || '获取商品类型列表失败')
        }
      })
    },
    handleSearch() {
      this.activeSearchKeyword = this.searchKeyword
      this.currentPage = 1
      this.fetchProductTypes()
    },
    handlePageChange(page) {
      this.currentPage = page
      this.fetchProductTypes()
    },
    handleSizeChange(size) {
      this.pageSize = size
      this.currentPage = 1
      this.fetchProductTypes()
    },
    openEvidenceDialog(row) {
      this.currentProductType = row
      this.evidenceForm = { version: '', releaseTime: '', evidence: '' }
      this.evidenceDialogVisible = true
    },
    submitEvidence() {
      this.$refs.evidenceForm.validate((valid) => {
        if (!valid) return
        this.evidenceSaving = true
        const productTypeId = this.currentProductType?.id
        Api.pdcNfc.registerProductTypeEvidence(productTypeId, this.evidenceForm, (res) => {
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

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
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
