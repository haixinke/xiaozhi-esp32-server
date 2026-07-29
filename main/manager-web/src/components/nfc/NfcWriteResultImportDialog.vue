<template>
  <el-dialog
    title="导入写卡结果"
    :visible.sync="dialogVisible"
    width="550px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form label-width="100px" label-position="right">
      <el-form-item label="写卡任务">
        <span>{{ jobId || '-' }}</span>
      </el-form-item>

      <el-form-item label="结果文件">
        <el-upload
          ref="upload"
          action="#"
          :auto-upload="false"
          :limit="1"
          :accept="'.csv'"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
          :file-list="fileList"
        >
          <el-button size="small" type="primary">选择 CSV 文件</el-button>
          <div slot="tip" class="el-upload__tip">仅支持 .csv 格式的写卡结果文件</div>
        </el-upload>
      </el-form-item>

      <el-form-item v-if="importResult" label="导入结果">
        <div class="import-result">
          <el-tag type="success">已验证: {{ importResult.verifiedCount || 0 }}</el-tag>
          <el-tag type="warning" class="ml-8">已写入: {{ importResult.writtenCount || 0 }}</el-tag>
          <el-tag type="danger" class="ml-8">失败: {{ importResult.failureCount || 0 }}</el-tag>
        </div>
      </el-form-item>
    </el-form>

    <template slot="footer">
      <el-button @click="handleClose">关 闭</el-button>
      <el-button
        type="primary"
        :loading="importing"
        :disabled="!selectedFile"
        @click="handleImport"
      >
        开始导入
      </el-button>
    </template>
  </el-dialog>
</template>

<script>
import Api from '@/apis/api'

export default {
  name: 'NfcWriteResultImportDialog',
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
      importing: false,
      selectedFile: null,
      fileList: [],
      importResult: null
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
      // Fallback UUID v4
      return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = Math.random() * 16 | 0
        const v = c === 'x' ? r : (r & 0x3 | 0x8)
        return v.toString(16)
      })
    },
    handleFileChange(file) {
      const name = file.name || ''
      if (!name.toLowerCase().endsWith('.csv')) {
        this.$message.error('请选择 .csv 格式的文件')
        this.$refs.upload.clearFiles()
        this.selectedFile = null
        return
      }
      this.selectedFile = file.raw
      this.importResult = null
    },
    handleFileRemove() {
      this.selectedFile = null
      this.importResult = null
    },
    handleImport() {
      if (!this.selectedFile) {
        this.$message.warning('请先选择结果文件')
        return
      }
      if (!this.jobId) {
        this.$message.error('缺少写卡任务 ID')
        return
      }
      this.importing = true
      const requestId = this.generateUUID()
      Api.pdcNfc.importWriteResult(this.jobId, this.selectedFile, requestId, (res) => {
        this.importing = false
        if (res.data && res.data.code === 0) {
          this.importResult = res.data.data || {}
          this.$message.success('导入成功')
          this.$emit('imported', this.importResult)
        } else {
          this.$message.error(res.data?.msg || '导入失败')
        }
      })
    },
    handleClose() {
      this.dialogVisible = false
      this.selectedFile = null
      this.fileList = []
      this.importResult = null
      this.$nextTick(() => {
        if (this.$refs.upload) {
          this.$refs.upload.clearFiles()
        }
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.import-result {
  display: flex;
  align-items: center;
}

.ml-8 {
  margin-left: 8px;
}
</style>
