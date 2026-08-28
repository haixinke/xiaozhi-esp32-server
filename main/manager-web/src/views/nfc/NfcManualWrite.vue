<template>
  <div class="manual-write-page">
    <HeaderBar />
    <div class="main-wrapper">
      <div class="content-area">
        <div class="page-header">
          <el-button size="mini" icon="el-icon-back" @click="$router.push('/pdc-nfc/write')">返回写卡任务</el-button>
          <span class="page-title">手动写卡</span>
          <el-button size="mini" icon="el-icon-refresh" :loading="loading" @click="fetchAssets">刷新</el-button>
        </div>

        <div class="usage-hint">
          逐张操作：复制 Scheme → NFC Tools 写入（Android 加写 AAR: com.tencent.mm）→ 点「已写入」→ 手机触碰标签自验证 → 验证通过后锁卡
        </div>

        <div v-loading="loading" class="asset-list">
          <div v-for="asset in assets" :key="asset.assetId" class="asset-card">
            <div class="asset-head">
              <span class="asset-no">{{ asset.assetNo }}</span>
              <el-tag size="mini" :type="statusTagType(asset)">{{ statusText(asset) }}</el-tag>
            </div>
            <div class="asset-meta">SN: {{ asset.wechatSn }} ｜ 原型: {{ asset.prototype }}</div>
            <div class="asset-flags">
              <span v-if="asset.verifySource">验证: {{ asset.verifySource === 'TOUCH' ? '触碰自验证' : '人工确认' }}</span>
              <span v-if="asset.lockedAt">已锁卡{{ asset.lockVerifiedAt ? '（复验通过）' : '（待复验）' }}</span>
            </div>
            <div class="asset-actions">
              <template v-if="asset.status === 'SCHEME_GENERATED'">
                <el-button size="mini" type="primary" @click="copyScheme(asset)">复制 Scheme</el-button>
                <el-button size="mini" type="success" :disabled="!asset._schemeCopied" @click="mark(asset, 'MARK_WRITTEN')">已写入</el-button>
              </template>
              <template v-else-if="asset.status === 'WRITTEN'">
                <el-button size="mini" type="primary" @click="copyScheme(asset)">复制 Scheme</el-button>
                <el-button size="mini" type="warning" @click="mark(asset, 'MARK_WRITE_FAILED')">写坏了</el-button>
                <el-button size="mini" type="success" @click="mark(asset, 'MARK_VERIFIED')">验证通过</el-button>
              </template>
              <template v-else-if="asset.status === 'VERIFIED' && !asset.lockedAt">
                <el-button size="mini" type="danger" @click="confirmLock(asset)">已锁卡</el-button>
              </template>
              <span v-else-if="asset.status === 'VERIFIED' && asset.lockedAt" class="done-text">
                {{ asset.lockVerifiedAt ? '可入库' : '请再触碰一次完成锁后复验' }}
              </span>
            </div>
          </div>
          <el-empty v-if="!loading && assets.length === 0" description="任务内没有资产" />
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import Api from '@/apis/api'
import HeaderBar from '@/components/HeaderBar.vue'
import { manualWriteStatusLabel, manualWriteStatusTagType } from '@/utils/pdcNfcState.mjs'

/**
 * 手动写卡页（ADR 0003）：小批量验证阶段用手机 NFC App 逐张写卡。
 * 移动端可用布局；Scheme 明文按需单条获取（后端记审计），复制后不持久展示。
 */
export default {
  name: 'NfcManualWrite',
  components: { HeaderBar },
  data() {
    return {
      jobId: this.$route.params.jobId,
      assets: [],
      loading: false
    }
  },
  created() {
    this.fetchAssets()
  },
  methods: {
    fetchAssets() {
      this.loading = true
      Api.pdcNfc.getManualWriteAssets(this.jobId, (res) => {
        this.loading = false
        if (res.data && res.data.code === 0) {
          this.assets = res.data.data || []
        } else {
          this.$message.error(res.data?.msg || '获取资产列表失败')
        }
      })
    },
    copyScheme(asset) {
      // Scheme 按需单条解密（后端记审计），但剪贴板要求用户手势同步调用。
      // 解法：点击瞬间构造含 Promise 的 ClipboardItem 交给剪贴板，
      // 响应到达后由剪贴板异步取值，规避手势失效导致的 NotAllowedError。
      const schemePromise = new Promise((resolve, reject) => {
        Api.pdcNfc.revealManualScheme(this.jobId, asset.assetId, (res) => {
          if (res.data && res.data.code === 0 && res.data.data && res.data.data.scheme) {
            resolve(res.data.data.scheme)
          } else {
            reject(new Error((res.data && res.data.msg) || '获取 Scheme 失败'))
          }
        })
      })
      // clipboard API 仅在安全上下文（HTTPS 或 localhost）可用；
      // 手机经 http://局域网IP 访问时不可用，直接走手动复制兜底
      const canAsyncCopy = window.isSecureContext && navigator.clipboard && window.ClipboardItem
      if (canAsyncCopy) {
        const item = new ClipboardItem({
          'text/plain': schemePromise.then(s => new Blob([s], { type: 'text/plain' }))
        })
        navigator.clipboard.write([item])
          .then(() => {
            this.$set(asset, '_schemeCopied', true)
            this.$message.success('Scheme 已复制，请到 NFC 工具写入')
          })
          .catch(() => {
            // 失败可能是权限拒绝，也可能是解密请求失败，按有无 Scheme 分流
            schemePromise
              .then(scheme => this.showSchemeForManualCopy(asset, scheme))
              .catch(err => this.$message.error(err.message))
          })
      } else {
        schemePromise
          .then(scheme => this.showSchemeForManualCopy(asset, scheme))
          .catch(err => this.$message.error(err.message))
      }
    },
    showSchemeForManualCopy(asset, scheme) {
      this.$prompt('自动复制不可用，请长按全选复制', 'Scheme', {
        inputValue: scheme,
        inputType: 'textarea',
        showCancelButton: false,
        closeOnClickModal: false
      }).then(() => {
        this.$set(asset, '_schemeCopied', true)
      }).catch(() => {
        // 弹窗关闭即视为已看到 Scheme，解锁「已写入」按钮
        this.$set(asset, '_schemeCopied', true)
      })
    },
    mark(asset, action) {
      Api.pdcNfc.markManualAsset(this.jobId, asset.assetId, action, (res) => {
        if (res.data && res.data.code === 0) {
          const idx = this.assets.findIndex(a => a.assetId === asset.assetId)
          if (idx >= 0 && res.data.data) {
            this.$set(this.assets, idx, res.data.data)
          }
          this.$message.success('已标记')
        } else {
          this.$message.error(res.data?.msg || '标记失败')
          this.fetchAssets()
        }
      })
    },
    confirmLock(asset) {
      this.$confirm('锁卡不可逆，锁错即废卡。确认已用手机 NFC 工具完成锁卡？', '锁卡确认', {
        confirmButtonText: '已锁卡，确认',
        cancelButtonText: '再想想',
        type: 'warning'
      }).then(() => {
        this.mark(asset, 'MARK_LOCKED')
      }).catch(() => {})
    },
    statusText(asset) {
      return manualWriteStatusLabel(asset.status)
    },
    statusTagType(asset) {
      return manualWriteStatusTagType(asset.status)
    }
  }
}
</script>

<style lang="scss" scoped>
.manual-write-page {
  min-height: 100vh;
  background: #f5f7fa;
}

.main-wrapper {
  padding: 12px;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  flex: 1;
  text-align: center;
}

.usage-hint {
  font-size: 12px;
  color: #909399;
  background: #fff;
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 12px;
  line-height: 1.6;
}

.asset-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.asset-card {
  background: #fff;
  border-radius: 8px;
  padding: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}

.asset-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.asset-no {
  font-weight: 600;
  font-family: monospace;
}

.asset-meta {
  font-size: 12px;
  color: #909399;
  margin: 6px 0;
  word-break: break-all;
}

.asset-flags {
  font-size: 12px;
  color: #67c23a;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.asset-actions {
  margin-top: 10px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.done-text {
  font-size: 12px;
  color: #67c23a;
  line-height: 28px;
}
</style>
