<template>
  <div class="activation-scanner">
    <!-- 扫码输入框 -->
    <div class="scanner-input-row">
      <el-input
        ref="scanInput"
        v-model="rawInput"
        placeholder="扫码或输入资产编号后按回车添加"
        clearable
        class="scan-input"
        @keyup.enter.native="handleScan"
      >
        <i slot="prefix" class="el-input__icon el-icon-full-screen"></i>
      </el-input>
      <el-button type="primary" icon="el-icon-plus" @click="handleScan">添加</el-button>
      <el-button type="danger" plain icon="el-icon-delete" :disabled="codes.length === 0" @click="handleClear">清空</el-button>
    </div>

    <!-- 状态摘要 -->
    <div class="scanner-summary">
      <el-tag type="info" size="medium">已扫码: {{ codes.length }}</el-tag>
      <el-tag type="success" size="medium" class="ml-8">可激活: {{ validCount }}</el-tag>
      <el-tag v-if="codes.length >= maxCodes" type="danger" size="medium" class="ml-8">已达上限 {{ maxCodes }}</el-tag>
    </div>

    <!-- 已扫码列表 -->
    <el-table
      v-if="codes.length > 0"
      :data="displayCodes"
      border
      stripe
      size="small"
      style="width: 100%;"
      max-height="300"
      :header-cell-style="{ background: '#f5f7fa' }"
    >
      <el-table-column type="index" label="#" width="50" align="center"></el-table-column>
      <el-table-column prop="code" label="资产编号" min-width="200"></el-table-column>
      <el-table-column label="状态" width="120" align="center">
        <template slot-scope="{ row }">
          <el-tag v-if="row.status" :type="badgeType(row.status)" size="mini">{{ statusText(row.status) }}</el-tag>
          <el-tag v-else type="info" size="mini">待查询</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="可激活" width="80" align="center">
        <template slot-scope="{ row }">
          <i v-if="row.status === 'IN_STOCK'" class="el-icon-success valid-icon"></i>
          <i v-else class="el-icon-error invalid-icon"></i>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80" align="center">
        <template slot-scope="{ row }">
          <el-button size="mini" type="text" class="remove-btn" @click="handleRemove(row.code)">移除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div v-else class="empty-hint">
      <i class="el-icon-document"></i>
      <span>暂无扫码记录，请扫描或输入资产编号</span>
    </div>
  </div>
</template>

<script>
import { emptyScannerState, acceptScan, removeScan, clearScanner, statusBadgeType, statusLabel } from '@/utils/pdcNfcState.mjs'

export default {
  name: 'NfcActivationScanner',
  props: {
    // 外部传入的资产状态查询结果（assetNo -> status）
    assetStatusMap: {
      type: Object,
      default: () => ({})
    }
  },
  data() {
    return {
      scannerState: emptyScannerState(),
      rawInput: ''
    }
  },
  computed: {
    codes() {
      return this.scannerState.codes
    },
    maxCodes() {
      return this.scannerState.maxCodes
    },
    displayCodes() {
      return this.codes.map(code => ({
        code,
        status: this.assetStatusMap[code] || null
      }))
    },
    validCount() {
      return this.codes.filter(c => this.assetStatusMap[c] === 'IN_STOCK').length
    }
  },
  mounted() {
    this.focusInput()
  },
  methods: {
    badgeType: statusBadgeType,
    statusText: statusLabel,
    focusInput() {
      this.$nextTick(() => {
        if (this.$refs.scanInput) {
          this.$refs.scanInput.focus()
        }
      })
    },
    handleScan() {
      const input = this.rawInput
      if (!input || !input.trim()) return
      const prevState = this.scannerState
      this.scannerState = acceptScan(this.scannerState, input)
      // 如果是新编码，通知父组件查询资产状态
      if (this.scannerState.codes.length > prevState.codes.length) {
        const newCode = input.trim()
        this.$emit('scanned', newCode)
      }
      this.rawInput = ''
      this.focusInput()
    },
    handleRemove(code) {
      this.scannerState = removeScan(this.scannerState, code)
    },
    handleClear() {
      this.scannerState = clearScanner(this.scannerState)
      this.$emit('cleared')
    },
    // 供父组件调用：获取所有可激活的编码
    getValidCodes() {
      return this.codes.filter(c => this.assetStatusMap[c] === 'IN_STOCK')
    },
    // 供父组件调用：获取所有编码
    getAllCodes() {
      return [...this.codes]
    },
    // 供父组件调用：重置扫码器
    reset() {
      this.scannerState = clearScanner(this.scannerState)
      this.rawInput = ''
      this.focusInput()
    }
  }
}
</script>

<style lang="scss" scoped>
.activation-scanner {
  .scanner-input-row {
    display: flex;
    gap: 10px;
    margin-bottom: 16px;
  }

  .scan-input {
    flex: 1;
  }

  .scanner-summary {
    margin-bottom: 12px;
  }

  .ml-8 {
    margin-left: 8px;
  }

  .valid-icon {
    color: #67c23a;
    font-size: 16px;
  }

  .invalid-icon {
    color: #f56c6c;
    font-size: 16px;
  }

  .remove-btn {
    color: #f56c6c;
  }

  .empty-hint {
    text-align: center;
    padding: 40px 0;
    color: #c0c4cc;

    i {
      font-size: 32px;
      margin-right: 8px;
    }
  }
}
</style>
