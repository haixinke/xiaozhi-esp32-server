<template>
  <div class="invite-page">
    <HeaderBar />
    <main class="invite-workspace">
      <div class="invite-toolbar">
        <h1>{{ $t('enterpriseInvite.title') }}</h1>
        <div class="toolbar-actions">
          <label for="enterprise-invite-status">{{ $t('enterpriseInvite.status') }}</label>
          <el-select id="enterprise-invite-status" v-model="filterStatus"
                     :placeholder="$t('common.all')" size="small" @change="filterInvites">
            <el-option :label="$t('common.all')" :value="null" />
            <el-option :label="$t('enterpriseInvite.active')" :value="1" />
            <el-option :label="$t('enterpriseInvite.disabled')" :value="0" />
          </el-select>
          <CustomButton icon="el-icon-refresh" size="small" @click="loadInvites">
            {{ $t('paramManagement.refresh') }}
          </CustomButton>
          <CustomButton icon="el-icon-plus" size="small" type="add" @click="editInvite()">
            {{ $t('common.add') }}
          </CustomButton>
        </div>
      </div>
      <CustomTable
        :data="invites"
        :columns="inviteColumns"
        :loading="loading"
        :loading-text="$t('common.loading')"
        :show-selection="false"
        show-operations
        :operations-width="352"
        :operations-label="$t('common.action')"
        :total="total"
        :current-page="page"
        :page-size="limit"
        @page-change="changePage"
        @size-change="changeLimit"
      >
        <template #status="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="mini">
            {{ $t(row.status === 1 ? 'enterpriseInvite.active' : 'enterpriseInvite.disabled') }}
          </el-tag>
        </template>
        <template #expireTime="{ row }">
          {{ row.expireTime || $t('enterpriseInvite.noExpiry') }}
        </template>
        <template #operations="{ row }">
          <div class="row-actions">
            <CustomButton size="mini" @click="editInvite(row)">{{ $t('common.edit') }}</CustomButton>
            <CustomButton size="mini" @click="showUsage(row)">{{ $t('enterpriseInvite.usage') }}</CustomButton>
            <CustomButton size="mini" icon="el-icon-download" :loading="downloadingId === row.id"
                          :disabled="downloadingId !== null" @click="downloadQrCode(row.id)">
              {{ $t('enterpriseInvite.download') }}
            </CustomButton>
            <CustomButton size="mini" @click="statusInvite = row">
              {{ $t(row.status === 1 ? 'enterpriseInvite.disable' : 'enterpriseInvite.enable') }}
            </CustomButton>
          </div>
        </template>
      </CustomTable>
    </main>
    <EnterpriseInviteEditor :visible.sync="editorVisible" :invite="editingInvite" @saved="loadInvites" />
    <CustomDialog :visible="Boolean(statusInvite)" :title="$t('enterpriseInvite.status')"
                  :cancel-text="$t('common.cancel')" :confirm-text="$t('common.confirm')"
                  :confirm-loading="statusSaving" width="480px" @close="statusInvite = null" @confirm="changeStatus">
      <p v-if="statusInvite">
        {{ $t(statusInvite.status === 1 ? 'enterpriseInvite.disableConfirm' : 'enterpriseInvite.enableConfirm',
          { name: statusInvite.name }) }}
      </p>
    </CustomDialog>
    <CustomDialog :visible.sync="usageVisible" :title="$t('enterpriseInvite.usage')"
                  :footer="false" width="680px">
      <p v-if="usageInvite" class="usage-name">{{ usageInvite.name }}</p>
      <div class="usage-table">
        <CustomTable
          :data="usageRecords"
          :columns="usageColumns"
          :loading="usageLoading"
          :loading-text="$t('common.loading')"
          :show-selection="false"
          :total="usageTotal"
          :current-page="usagePage"
          :page-size="usageLimit"
          @page-change="changeUsagePage"
          @size-change="changeUsageLimit"
        />
      </div>
    </CustomDialog>
    <el-footer><VersionFooter /></el-footer>
  </div>
</template>

<script>
import Api from '@/apis/api';
import CustomButton from '@/components/CustomButton.vue';
import CustomDialog from '@/components/CustomDialog.vue';
import CustomTable from '@/components/CustomTable.vue';
import HeaderBar from '@/components/HeaderBar.vue';
import VersionFooter from '@/components/VersionFooter.vue';
import EnterpriseInviteEditor from '@/components/invite/EnterpriseInviteEditor.vue';

export default {
  name: 'EnterpriseInviteManagement',
  components: { HeaderBar, VersionFooter, CustomButton, CustomDialog, CustomTable, EnterpriseInviteEditor },
  data() {
    return {
      invites: [], loading: false, page: 1, limit: 10, total: 0, filterStatus: null, listRequest: 0,
      editingInvite: null, editorVisible: false,
      statusInvite: null, statusSaving: false, downloadingId: null,
      usageInvite: null, usageVisible: false, usageRecords: [], usageLoading: false,
      usagePage: 1, usageLimit: 10, usageTotal: 0, usageRequest: 0
    };
  },
  computed: {
    inviteColumns() {
      return [
        { prop: 'name', label: this.$t('enterpriseInvite.companyOrCampaign'), minWidth: 148, align: 'left' },
        { prop: 'code', label: this.$t('enterpriseInvite.code'), minWidth: 85 },
        { prop: 'quota', label: this.$t('enterpriseInvite.quota'), minWidth: 72 },
        { prop: 'usedCount', label: this.$t('enterpriseInvite.used'), minWidth: 72 },
        { prop: 'remaining', label: this.$t('enterpriseInvite.remaining'), minWidth: 72 },
        { prop: 'status', label: this.$t('enterpriseInvite.status'), minWidth: 85 },
        { prop: 'expireTime', label: this.$t('enterpriseInvite.expires'), minWidth: 158 },
        { prop: 'remark', label: this.$t('enterpriseInvite.remark'), minWidth: 110, align: 'left' }
      ];
    },
    usageColumns() {
      return [
        { prop: 'inviteeUserId', label: this.$t('enterpriseInvite.invitee') },
        { prop: 'createDate', label: this.$t('enterpriseInvite.usedAt') }
      ];
    }
  },
  created() {
    this.loadInvites();
  },
  methods: {
    async loadInvites() {
      const request = ++this.listRequest;
      this.loading = true;
      try {
        const { list, total } = await Api.enterpriseInvite.list({
          page: this.page, limit: this.limit, status: this.filterStatus
        });
        if (request === this.listRequest) {
          this.invites = list;
          this.total = total;
        }
      } catch (error) {
        if (request === this.listRequest) this.showError(error);
      } finally {
        if (request === this.listRequest) this.loading = false;
      }
    },
    changePage(page) {
      if (this.page === page) return;
      this.page = page;
      this.loadInvites();
    },
    changeLimit(limit) {
      this.limit = limit;
      this.filterInvites();
    },
    filterInvites() {
      this.page = 1;
      this.loadInvites();
    },
    editInvite(row = null) {
      this.editingInvite = row;
      this.editorVisible = true;
    },
    async changeStatus() {
      if (!this.statusInvite || this.statusSaving) return;
      const statusInvite = this.statusInvite;
      const { id, status } = statusInvite;
      this.statusSaving = true;
      try {
        await Api.enterpriseInvite.update({ id, status: status === 1 ? 0 : 1 });
        if (this.statusInvite === statusInvite) this.statusInvite = null;
        this.$message.success(this.$t('common.updateSuccess'));
        this.loadInvites();
      } catch (error) {
        this.showError(error);
      } finally {
        this.statusSaving = false;
      }
    },
    showUsage(row) {
      this.usageInvite = row;
      this.usageRecords = [];
      this.usageTotal = 0;
      this.usagePage = 1;
      this.usageVisible = true;
      this.loadUsage();
    },
    changeUsagePage(page) {
      if (this.usagePage === page) return;
      this.usagePage = page;
      this.loadUsage();
    },
    changeUsageLimit(limit) {
      this.usageLimit = limit;
      this.usagePage = 1;
      this.loadUsage();
    },
    async loadUsage() {
      const request = ++this.usageRequest;
      this.usageLoading = true;
      try {
        const { list, total } = await Api.enterpriseInvite.usage(this.usageInvite.id, {
          page: this.usagePage, limit: this.usageLimit
        });
        if (request === this.usageRequest) {
          this.usageRecords = list;
          this.usageTotal = total;
        }
      } catch (error) {
        if (request === this.usageRequest && this.usageVisible) this.showError(error);
      } finally {
        if (request === this.usageRequest) this.usageLoading = false;
      }
    },
    async downloadQrCode(id) {
      if (this.downloadingId !== null) return;
      this.downloadingId = id;
      try {
        const blob = await Api.enterpriseInvite.downloadQrCode(id);
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `invite-${id}.png`;
        document.body.appendChild(link);
        link.click();
        link.remove();
        setTimeout(() => URL.revokeObjectURL(url), 0);
      } catch (error) {
        this.showError(error, 'enterpriseInvite.downloadFailed');
      } finally {
        this.downloadingId = null;
      }
    },
    showError(error, fallback = 'message.error') {
      this.$message.error(error?.data?.msg || this.$t(fallback));
    }
  }
};
</script>

<style lang="scss" scoped>
.invite-page {
  min-width: 900px;
  min-height: 506px;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #eff4ff;
}

.invite-workspace {
  flex: 1;
  min-height: 0;
  margin: 20px;
  padding: 22px;
  display: flex;
  flex-direction: column;
  border-radius: 15px;
  background: #fafcfe;
}

.invite-toolbar, .toolbar-actions, .row-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.invite-toolbar {
  justify-content: space-between;
  margin-bottom: 16px;

  h1 {
    color: #3d4566;
    font-size: 24px;
    font-weight: 600;
    margin: 0;
  }
}

.toolbar-actions label {
  color: #606266;
  font-size: 14px;
}

.row-actions {
  justify-content: center;
  flex-wrap: wrap;

  ::v-deep .el-button + .el-button {
    margin-left: 0;
  }
}

.usage-name {
  color: #3d4566;
  margin: 0 0 16px;
  overflow-wrap: anywhere;
}

.usage-table {
  height: 350px;
  display: flex;
}
</style>
