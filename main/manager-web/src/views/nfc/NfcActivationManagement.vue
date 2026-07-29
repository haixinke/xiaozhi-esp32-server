<template>
  <div class="nfc-activation-page">
    <HeaderBar />
    <div class="main-wrapper">
      <div class="content-panel">
        <div class="content-area">
          <el-card class="page-card" shadow="never">
            <div class="operation-header">
              <h2 class="page-title">扫码出库激活</h2>
            </div>

            <el-alert
              title="操作说明"
              type="info"
              :closable="false"
              show-icon
              class="info-alert"
            >
              <template>
                扫码或输入资产编号后按回车添加。系统会自动查询资产状态，仅状态为"已入库(IN_STOCK)"的资产可激活。最多可扫 500 条。
              </template>
            </el-alert>

            <!-- 扫码组件 -->
            <NfcActivationScanner
              ref="scanner"
              :asset-status-map="assetStatusMap"
              @scanned="handleScanned"
              @cleared="handleCleared"
            />

            <!-- 激活表单 -->
            <div class="activation-form">
              <el-divider content-position="left">激活信息</el-divider>
              <el-form
                ref="activateForm"
                :model="activateForm"
                :rules="activateRules"
                label-width="120px"
                label-position="right"
                class="activate-form"
              >
                <el-form-item label="激活单号" prop="businessNo">
                  <el-input
                    v-model="activateForm.businessNo"
                    placeholder="请输入激活业务单号"
                    maxlength="64"
                    style="width: 320px;"
                  ></el-input>
                </el-form-item>
              </el-form>
            </div>

            <!-- 操作按钮 -->
            <div class="action-bar">
              <el-button
                type="primary"
                icon="el-icon-open"
                :disabled="validCount === 0 || !activateForm.businessNo"
                @click="handleActivate"
              >激活 {{ validCount }} 项资产</el-button>
            </div>

            <!-- 激活结果 -->
            <el-dialog
              title="激活结果"
              :visible.sync="resultDialogVisible"
              width="480px"
              :close-on-click-modal="false"
            >
              <el-result
                :icon="resultIcon"
                :title="resultTitle"
                :subTitle="resultSubTitle"
              >
              </el-result>
              <div v-if="activateResult" class="result-detail">
                <el-descriptions :column="2" border size="medium">
                  <el-descriptions-item label="处理数量">{{ activateResult.processedCount ?? 0 }}</el-descriptions-item>
                  <el-descriptions-item label="成功数量">{{ activateResult.successCount ?? 0 }}</el-descriptions-item>
                </el-descriptions>
              </div>
              <template slot="footer">
                <el-button @click="resultDialogVisible = false">关 闭</el-button>
                <el-button type="primary" @click="handleAfterResult">完成</el-button>
              </template>
            </el-dialog>
          </el-card>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import Api from '@/apis/api'
import HeaderBar from '@/components/HeaderBar.vue'
import NfcActivationScanner from '@/components/nfc/NfcActivationScanner.vue'

export default {
  name: 'NfcActivationManagement',
  components: { HeaderBar, NfcActivationScanner },
  data() {
    return {
      // assetNo -> status 映射
      assetStatusMap: {},
      activateForm: {
        businessNo: ''
      },
      activateRules: {
        businessNo: [
          { required: true, message: '请输入激活业务单号', trigger: 'blur' },
          { max: 64, message: '单号长度不能超过 64 个字符', trigger: 'blur' }
        ]
      },
      // 激活结果
      resultDialogVisible: false,
      activateResult: null
    }
  },
  computed: {
    validCount() {
      const scanner = this.$refs.scanner
      if (!scanner) return 0
      return scanner.getValidCodes().length
    },
    resultIcon() {
      if (this.activateResult && this.activateResult.successCount === 0) return 'error'
      if (this.activateResult && this.activateResult.successCount < (this.activateResult.processedCount || 0)) return 'warning'
      return 'success'
    },
    resultTitle() {
      if (this.activateResult && this.activateResult.successCount === 0) return '激活失败'
      if (this.activateResult && this.activateResult.successCount < (this.activateResult.processedCount || 0)) return '部分成功'
      return '激活成功'
    },
    resultSubTitle() {
      if (!this.activateResult) return ''
      return `处理 ${this.activateResult.processedCount ?? 0} 项，成功 ${this.activateResult.successCount ?? 0} 项`
    }
  },
  methods: {
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
    // 扫码后查询资产状态
    handleScanned(assetNo) {
      // 通过 listAssets 查询单个资产的状态
      Api.pdcNfc.listAssets({ assetNo: assetNo, page: 1, limit: 1 }, (res) => {
        if (res.data && res.data.code === 0) {
          const data = res.data.data
          const list = data?.list || data || []
          if (list.length > 0) {
            const asset = list[0]
            // 使用 $set 确保 Vue 响应式更新
            this.$set(this.assetStatusMap, assetNo, asset.status || 'UNKNOWN')
            if (asset.status !== 'IN_STOCK') {
              this.$message.warning(`资产 ${assetNo} 状态为 ${asset.status || '未知'}，不可激活`)
            }
          } else {
            this.$set(this.assetStatusMap, assetNo, 'NOT_FOUND')
            this.$message.warning(`未找到资产编号 ${assetNo}`)
          }
        }
      })
    },
    handleCleared() {
      this.assetStatusMap = {}
    },
    handleActivate() {
      const scanner = this.$refs.scanner
      if (!scanner) return
      const validCodes = scanner.getValidCodes()
      if (validCodes.length === 0) {
        this.$message.warning('没有可激活的资产')
        return
      }
      this.$refs.activateForm.validate((valid) => {
        if (!valid) return
        this.$confirm(
          `确认激活 ${validCodes.length} 项资产吗？激活单号：${this.activateForm.businessNo}`,
          '确认激活',
          {
            confirmButtonText: '确定激活',
            cancelButtonText: '取消',
            type: 'warning'
          }
        ).then(() => {
          this.doActivate(validCodes)
        }).catch(() => {})
      })
    },
    doActivate(assetNos) {
      const payload = {
        assetNos: assetNos,
        businessNo: this.activateForm.businessNo,
        requestId: this.generateUUID()
      }
      Api.pdcNfc.activate(payload, (res) => {
        if (res.data && res.data.code === 0) {
          this.activateResult = res.data.data || {}
          this.resultDialogVisible = true
        } else {
          this.$message.error(res.data?.msg || '激活失败')
        }
      })
    },
    handleAfterResult() {
      this.resultDialogVisible = false
      // 重置扫码器和表单
      if (this.$refs.scanner) {
        this.$refs.scanner.reset()
      }
      this.assetStatusMap = {}
      this.activateForm.businessNo = ''
      this.activateResult = null
    }
  }
}
</script>

<style lang="scss" scoped>
.nfc-activation-page {
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

.info-alert {
  margin-bottom: 20px;
}

.activation-form {
  margin-top: 10px;
}

.action-bar {
  margin-top: 20px;
  display: flex;
  gap: 12px;
}

.result-detail {
  margin-top: 16px;
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
</style>
