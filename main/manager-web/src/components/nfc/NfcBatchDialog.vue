<template>
  <el-dialog
    title="创建批次"
    :visible.sync="dialogVisible"
    width="600px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form
      ref="batchForm"
      :model="form"
      :rules="rules"
      label-width="100px"
      label-position="right"
    >
      <el-form-item label="批次号" prop="batchNo">
        <el-input
          v-model="form.batchNo"
          placeholder="全局唯一，资产编号将以此为前缀"
          maxlength="64"
        ></el-input>
      </el-form-item>

      <el-form-item label="商品类型" prop="productTypeId">
        <el-select
          v-model="form.productTypeId"
          placeholder="请选择商品类型"
          filterable
          style="width: 100%;"
          :loading="productTypeLoading"
        >
          <el-option
            v-for="item in productTypeOptions"
            :key="item.id"
            :label="`${item.typeCode} - ${item.typeName}`"
            :value="item.id"
          ></el-option>
        </el-select>
      </el-form-item>

      <el-form-item label="SKU 编码" prop="skuCode">
        <el-input
          v-model="form.skuCode"
          placeholder="请输入 SKU 编码"
          maxlength="64"
        ></el-input>
      </el-form-item>

      <el-form-item label="原型" prop="prototype">
        <el-select
          v-model="form.prototype"
          placeholder="请选择原型"
          style="width: 100%;"
          :loading="prototypeLoading"
        >
          <el-option
            v-for="item in prototypeOptions"
            :key="item.key"
            :label="item.name"
            :value="item.key"
          ></el-option>
        </el-select>
      </el-form-item>

      <el-form-item label="计划数量" prop="plannedQuantity">
        <el-input-number
          v-model="form.plannedQuantity"
          :min="1"
          :max="10000"
          :step="1"
          controls-position="right"
        ></el-input-number>
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

    <template slot="footer">
      <el-button @click="handleClose">取 消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">确 定</el-button>
    </template>
  </el-dialog>
</template>

<script>
import Api from '@/apis/api'

export default {
  name: 'NfcBatchDialog',
  props: {
    visible: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      dialogVisible: this.visible,
      saving: false,
      productTypeLoading: false,
      productTypeOptions: [],
      prototypeLoading: false,
      prototypeOptions: [],
      form: {
        batchNo: '',
        productTypeId: '',
        skuCode: '',
        prototype: '',
        plannedQuantity: 1,
        remark: ''
      },
      rules: {
        batchNo: [
          { required: true, message: '请输入批次号', trigger: 'blur' },
          { max: 64, message: '批次号长度不能超过 64 个字符', trigger: 'blur' }
        ],
        productTypeId: [
          { required: true, message: '请选择商品类型', trigger: 'change' }
        ],
        skuCode: [
          { required: true, message: '请输入 SKU 编码', trigger: 'blur' },
          { max: 64, message: 'SKU 编码长度不能超过 64 个字符', trigger: 'blur' }
        ],
        prototype: [
          { required: true, message: '请选择原型', trigger: 'change' }
        ],
        plannedQuantity: [
          { required: true, message: '请输入计划数量', trigger: 'blur' },
          { type: 'number', min: 1, max: 10000, message: '数量范围 1-10000', trigger: 'blur' }
        ]
      }
    }
  },
  watch: {
    visible(val) {
      this.dialogVisible = val
      if (val) {
        this.fetchProductTypes()
        this.fetchPrototypes()
      }
    },
    dialogVisible(val) {
      this.$emit('update:visible', val)
    }
  },
  methods: {
    fetchProductTypes() {
      this.productTypeLoading = true
      Api.pdcNfc.listProductTypes({ page: 1, limit: 200 }, (res) => {
        this.productTypeLoading = false
        if (res.data && res.data.code === 0) {
          this.productTypeOptions = res.data.data?.list || res.data.data || []
        } else {
          this.$message.error(res.data?.msg || '获取商品类型列表失败')
        }
      })
    },
    // 原型选项来自字典 EGG_PET_PROTOTYPE，dict_value 为中文且与后端校验值一致
    // 拉取失败时不做本地兜底：宁可下拉为空暴露异常，也不能用可能过期的硬编码值生产错误资产
    fetchPrototypes() {
      this.prototypeLoading = true
      Api.dict.getDictDataByType('EGG_PET_PROTOTYPE')
        .then((data) => {
          this.prototypeLoading = false
          this.prototypeOptions = Array.isArray(data) ? data : (data.data || [])
        })
        .catch(() => {
          this.prototypeLoading = false
          this.prototypeOptions = []
          this.$message.error('获取原型字典失败，请稍后重试')
        })
    },
    handleSubmit() {
      this.$refs.batchForm.validate((valid) => {
        if (!valid) return
        this.saving = true
        Api.pdcNfc.createBatch(this.form, (res) => {
          this.saving = false
          if (res.data && res.data.code === 0) {
            this.$message.success('批次创建成功')
            this.$emit('created')
            this.handleClose()
          } else {
            this.$message.error(res.data?.msg || '批次创建失败')
          }
        })
      })
    },
    handleClose() {
      this.dialogVisible = false
      this.saving = false
      this.$nextTick(() => {
        if (this.$refs.batchForm) {
          this.$refs.batchForm.resetFields()
        }
        this.form = {
          batchNo: '',
          productTypeId: '',
          skuCode: '',
          prototype: '',
          plannedQuantity: 1,
          remark: ''
        }
      })
    }
  }
}
</script>
