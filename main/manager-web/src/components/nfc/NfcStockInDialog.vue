<template>
  <el-dialog
    title="资产批量入库"
    :visible.sync="dialogVisible"
    width="600px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <!-- 步骤 1：填写表单 -->
    <div v-if="step === 'form'" class="stock-in-form">
      <el-form
        ref="stockInForm"
        :model="form"
        :rules="rules"
        label-width="110px"
        label-position="right"
      >
        <el-form-item label="入库资产">
          <div class="asset-summary">
            <el-tag type="info">共 {{ assetIds.length }} 项</el-tag>
            <span v-if="assetIds.length === 0" class="text-muted">请在资产列表中勾选需要入库的资产</span>
          </div>
        </el-form-item>

        <el-form-item label="入库单号" prop="businessNo">
          <el-input
            v-model="form.businessNo"
            placeholder="请输入入库业务单号"
            maxlength="64"
          ></el-input>
        </el-form-item>

        <el-form-item label="备注" prop="remark">
          <el-input
            type="textarea"
            v-model="form.remark"
            :rows="3"
            placeholder="请输入备注（可选）"
            maxlength="200"
            show-word-limit
          ></el-input>
        </el-form-item>
      </el-form>
    </div>

    <!-- 步骤 2：确认 -->
    <div v-if="step === 'confirm'" class="confirm-section">
      <el-alert title="请确认入库信息" type="info" :closable="false" show-icon></el-alert>
      <el-descriptions :column="1" border size="medium" class="confirm-desc">
        <el-descriptions-item label="入库资产数">{{ assetIds.length }} 项</el-descriptions-item>
        <el-descriptions-item label="入库单号">{{ form.businessNo }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ form.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="请求 ID">{{ form.requestId }}</el-descriptions-item>
      </el-descriptions>
    </div>

    <!-- 步骤 3：结果 -->
    <div v-if="step === 'result'" class="result-section">
      <el-result
        :icon="resultIcon"
        :title="resultTitle"
        :subTitle="resultSubTitle"
      >
        <template slot="extra">
          <el-button type="primary" @click="handleClose">完 成</el-button>
        </template>
      </el-result>
      <div v-if="result" class="result-detail">
        <el-descriptions :column="2" border size="medium">
          <el-descriptions-item label="处理数量">{{ result.processedCount ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="成功数量">{{ result.successCount ?? 0 }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </div>

    <template slot="footer">
      <template v-if="step === 'form'">
        <el-button @click="handleClose">取 消</el-button>
        <el-button
          type="primary"
          :disabled="assetIds.length === 0"
          @click="goConfirm"
        >下一步</el-button>
      </template>
      <template v-if="step === 'confirm'">
        <el-button @click="step = 'form'">上一步</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确认入库</el-button>
      </template>
    </template>
  </el-dialog>
</template>

<script>
import Api from '@/apis/api'

export default {
  name: 'NfcStockInDialog',
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    assetIds: {
      type: Array,
      default: () => []
    }
  },
  data() {
    return {
      dialogVisible: this.visible,
      step: 'form',
      submitting: false,
      result: null,
      form: {
        businessNo: '',
        remark: '',
        requestId: ''
      },
      rules: {
        businessNo: [
          { required: true, message: '请输入入库业务单号', trigger: 'blur' },
          { max: 64, message: '单号长度不能超过 64 个字符', trigger: 'blur' }
        ]
      }
    }
  },
  computed: {
    resultIcon() {
      if (this.result && this.result.successCount === 0) return 'error'
      if (this.result && this.result.successCount < (this.result.processedCount || 0)) return 'warning'
      return 'success'
    },
    resultTitle() {
      if (this.result && this.result.successCount === 0) return '入库失败'
      if (this.result && this.result.successCount < (this.result.processedCount || 0)) return '部分成功'
      return '入库成功'
    },
    resultSubTitle() {
      if (!this.result) return ''
      return `处理 ${this.result.processedCount ?? 0} 项，成功 ${this.result.successCount ?? 0} 项`
    }
  },
  watch: {
    visible(val) {
      this.dialogVisible = val
    },
    dialogVisible(val) {
      this.$emit('update:visible', val)
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
    goConfirm() {
      this.$refs.stockInForm.validate((valid) => {
        if (!valid) return
        this.form.requestId = this.generateUUID()
        this.step = 'confirm'
      })
    },
    handleSubmit() {
      this.submitting = true
      const payload = {
        assetIds: [...this.assetIds],
        businessNo: this.form.businessNo,
        requestId: this.form.requestId
      }
      if (this.form.remark) payload.remark = this.form.remark
      Api.pdcNfc.stockIn(payload, (res) => {
        this.submitting = false
        if (res.data && res.data.code === 0) {
          this.result = res.data.data || {}
          this.step = 'result'
          this.$emit('stocked', this.result)
        } else {
          this.$message.error(res.data?.msg || '入库失败')
        }
      })
    },
    handleClose() {
      this.dialogVisible = false
      this.step = 'form'
      this.result = null
      this.form = { businessNo: '', remark: '', requestId: '' }
      this.$nextTick(() => {
        if (this.$refs.stockInForm) {
          this.$refs.stockInForm.resetFields()
        }
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.asset-summary {
  display: flex;
  align-items: center;
  gap: 8px;
}

.text-muted {
  color: #c0c4cc;
  font-size: 13px;
}

.confirm-section {
  .confirm-desc {
    margin-top: 16px;
  }
}

.result-detail {
  margin-top: 16px;
}
</style>
