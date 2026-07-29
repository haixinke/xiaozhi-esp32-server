<template>
  <div class="nfc-placeholder">
    <el-card>
      <div slot="header">
        <span>{{ $t('nfc.title') }}</span>
      </div>
      <div class="placeholder-body">
        <i class="el-icon-cpu"></i>
        <p>{{ $t('nfc.comingSoon') }}</p>
      </div>
    </el-card>
  </div>
</template>

<script>
export default {
  name: 'NfcPlaceholder'
}
</script>

<style lang="scss" scoped>
.nfc-placeholder {
  padding: 20px;
}

.placeholder-body {
  text-align: center;
  padding: 60px 0;
  color: #909399;

  i {
    font-size: 48px;
    margin-bottom: 16px;
    display: block;
  }
}
</style>
