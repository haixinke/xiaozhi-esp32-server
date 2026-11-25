<template>
  <CustomDialog :visible="visible" :title="$t(invite ? 'common.edit' : 'common.add')"
                :cancel-text="$t('common.cancel')" :confirm-text="$t('button.save')"
                :confirm-loading="saving" width="520px" @update:visible="$emit('update:visible', $event)"
                @confirm="save">
    <el-form ref="form" :model="form" :rules="rules" label-position="top">
      <el-form-item prop="name" :label="$t('enterpriseInvite.companyOrCampaign')">
        <el-input v-model.trim="form.name" maxlength="100" show-word-limit />
      </el-form-item>
      <el-form-item prop="quota" :label="$t('enterpriseInvite.quota')">
        <el-input-number v-model="form.quota" :min="minimumQuota" :max="2147483647" :precision="0" />
        <p v-if="invite" class="field-help">{{ $t('enterpriseInvite.quotaIncreaseOnly') }}</p>
      </el-form-item>
      <el-form-item :label="$t('enterpriseInvite.expires')">
        <el-date-picker v-model="form.expireTime" type="datetime" value-format="yyyy-MM-dd HH:mm:ss"
                        :placeholder="$t('enterpriseInvite.noExpiry')" />
        <p class="field-help">{{ $t('enterpriseInvite.timeZone') }}</p>
      </el-form-item>
      <el-form-item :label="$t('enterpriseInvite.remark')">
        <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="255" show-word-limit />
      </el-form-item>
    </el-form>
  </CustomDialog>
</template>

<script>
import Api from '@/apis/api';
import CustomDialog from '@/components/CustomDialog.vue';

function editableFields(invite) {
  return {
    name: invite?.name || '',
    quota: invite?.quota || 1,
    expireTime: invite?.expireTime || null,
    remark: invite?.remark || ''
  };
}

export default {
  name: 'EnterpriseInviteEditor',
  components: { CustomDialog },
  props: {
    visible: Boolean,
    invite: { type: Object, default: null }
  },
  data() {
    return { form: editableFields(this.invite), saving: false };
  },
  computed: {
    minimumQuota() {
      return this.invite?.quota || 1;
    },
    rules() {
      return {
        name: [{ required: true, whitespace: true, message: this.$t('enterpriseInvite.requiredName'), trigger: 'blur' }],
        quota: [{ type: 'integer', required: true, min: this.minimumQuota, max: 2147483647,
          message: this.$t('enterpriseInvite.invalidQuota'), trigger: 'change' }]
      };
    }
  },
  watch: {
    visible(open) {
      if (open) this.form = editableFields(this.invite);
    }
  },
  methods: {
    save() {
      if (this.saving) return;
      this.$refs.form.validate(async (valid) => {
        if (!valid || this.saving) return;
        const invite = this.invite;
        const form = this.form;
        this.saving = true;
        try {
          const fields = { ...form, expireTime: form.expireTime || null };
          if (invite) {
            const original = editableFields(invite);
            const changes = Object.fromEntries(Object.entries(fields).filter(([key, value]) => value !== original[key]));
            if (Object.keys(changes).length) await Api.enterpriseInvite.update({ id: invite.id, ...changes });
          } else {
            await Api.enterpriseInvite.create(fields);
          }
          this.$message.success(this.$t(invite ? 'common.updateSuccess' : 'common.addSuccess'));
          if (this.form === form) this.$emit('update:visible', false);
          this.$emit('saved');
        } catch (error) {
          this.$message.error(error?.data?.msg || this.$t('enterpriseInvite.saveUnconfirmed'));
        } finally {
          this.saving = false;
        }
      });
    }
  }
};
</script>

<style scoped>
.field-help {
  color: #606266;
  font-size: 12px;
  line-height: 1.5;
  margin: 6px 0 0;
}
</style>
