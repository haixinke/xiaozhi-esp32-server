<template>
  <div class="story-panel">
    <div class="panel-header">
      <div class="panel-title">
        <span class="title-accent"></span>
        <h3>大场景</h3>
        <span class="panel-count">共 {{ list.length }} 个</span>
      </div>
      <div class="panel-actions">
        <CustomButton icon="el-icon-upload2" type="default" size="small" @click="openImport">
          导入图片文案
        </CustomButton>
        <CustomButton icon="el-icon-plus" type="add" size="small" @click="openCreate">
          新增大场景
        </CustomButton>
      </div>
    </div>

    <el-table
      :data="list"
      v-loading="loading"
      element-loading-text="加载中"
      element-loading-spinner="el-icon-loading"
      class="story-table"
      row-key="id"
    >
      <el-table-column prop="name" label="名称" min-width="220" align="left" show-overflow-tooltip>
        <template slot-scope="scope">
          <span class="cell-name">{{ scope.row.name }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="sortOrder" label="排序" width="100" align="center" />
      <el-table-column label="状态" width="120" align="center">
        <template slot-scope="scope">
          <el-switch
            :value="scope.row.status === 1"
            :disabled="!!scope.row.statusUpdating"
            @change="(val) => handleStatusChange(scope.row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="250" align="center">
        <template slot-scope="scope">
          <el-button size="mini" type="text" @click="$emit('manage-small-scenes', scope.row)">
            管理小场景
          </el-button>
          <el-button size="mini" type="text" @click="openEdit(scope.row)">编辑</el-button>
          <el-button size="mini" type="text" class="danger-link" @click="handleDelete(scope.row)">
            删除
          </el-button>
        </template>
      </el-table-column>
      <template slot="empty">
        <div class="empty-tip">暂无大场景，点击右上角「新增大场景」开始配置</div>
      </template>
    </el-table>

    <CustomDialog
      :title="dialogTitle"
      :visible.sync="dialogVisible"
      width="500px"
      :confirm-loading="saving"
      @confirm="handleSubmit"
      @cancel="dialogVisible = false"
    >
      <el-form ref="sceneForm" :model="form" :rules="rules" label-width="80px" @submit.native.prevent>
        <el-form-item label="名称" prop="name">
          <el-input
            v-model="form.name"
            maxlength="64"
            show-word-limit
            placeholder="如：在家、旅行、上学、打工"
          />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" controls-position="right" />
          <span class="form-hint">数值越小越靠前</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="禁用"
          />
        </el-form-item>
      </el-form>
    </CustomDialog>

    <CustomDialog
      title="导入图片文案"
      :visible.sync="importVisible"
      width="560px"
      confirm-text="开始导入"
      :confirm-loading="importing"
      @confirm="handleImport"
      @cancel="closeImport"
    >
      <div class="template-download">
        <a :href="templateUrl" download="图片文案模版.xlsx">
          <i class="el-icon-download"></i> 下载文案模版
        </a>
      </div>
      <el-upload
        ref="captionsUpload"
        action="#"
        drag
        :auto-upload="false"
        :limit="1"
        :on-change="handleImportFileChange"
        :on-remove="handleImportFileRemove"
        accept=".xlsx"
      >
        <i class="el-icon-upload"></i>
        <div class="el-upload__text">将 Excel 文件拖到此处，或<em>点击选择</em></div>
        <div class="el-upload__tip" slot="tip">
          仅支持 .xlsx，表头须为：大场景 | 小场景 | 动作 | 时段 | 宠物类型 | 图片文案。
          名称需与故事引擎中的命名完全一致，对不上的行不会更新；「在家/卧室」中带窗户标签的图片不更新。
        </div>
      </el-upload>
      <div v-if="importResult" class="import-result">
        <div class="import-result-summary">
          已更新 {{ importResult.updatedImages }} 张图片，跳过 {{ importResult.skippedRows }} 行
        </div>
        <ul v-if="importResult.skippedDetails && importResult.skippedDetails.length" class="import-result-details">
          <li v-for="(detail, index) in importResult.skippedDetails" :key="index">{{ detail }}</li>
        </ul>
      </div>
    </CustomDialog>
  </div>
</template>

<script>
import Api from "@/apis/api";
import CustomButton from "@/components/CustomButton.vue";
import CustomDialog from "@/components/CustomDialog.vue";

export default {
  name: "BigSceneList",
  components: { CustomButton, CustomDialog },
  data() {
    return {
      list: [],
      loading: false,
      saving: false,
      dialogVisible: false,
      dialogTitle: "新增大场景",
      importVisible: false,
      importing: false,
      importFile: null,
      importResult: null,
      form: {
        id: null,
        name: "",
        sortOrder: 0,
        status: 1
      },
      rules: {
        name: [{ required: true, message: "请输入大场景名称", trigger: "blur" }]
      }
    };
  },
  created() {
    this.fetchList();
  },
  computed: {
    templateUrl() {
      const base = process.env.BASE_URL || "/";
      return base + "templates/" + encodeURIComponent("图片文案模版.xlsx");
    }
  },
  methods: {
    fetchList() {
      this.loading = true;
      Api.storyEngine.getBigSceneList(({ data }) => {
        this.loading = false;
        if (data.code === 0) {
          this.list = (data.data || []).map(item => ({ ...item, statusUpdating: false }));
        } else {
          this.$message.error({ message: data.msg || "获取大场景列表失败", showClose: true });
        }
      });
    },
    resetForm() {
      this.form = { id: null, name: "", sortOrder: 0, status: 1 };
      if (this.$refs.sceneForm) {
        this.$refs.sceneForm.clearValidate();
      }
    },
    openCreate() {
      this.dialogTitle = "新增大场景";
      this.resetForm();
      this.dialogVisible = true;
    },
    openImport() {
      this.importFile = null;
      this.importResult = null;
      this.importVisible = true;
      this.$nextTick(() => {
        if (this.$refs.captionsUpload) {
          this.$refs.captionsUpload.clearFiles();
        }
      });
    },
    closeImport() {
      this.importVisible = false;
      this.importFile = null;
      this.importResult = null;
    },
    handleImportFileChange(file) {
      const name = (file.name || "").toLowerCase();
      if (!name.endsWith(".xlsx")) {
        this.$refs.captionsUpload.clearFiles();
        this.importFile = null;
        this.$message.error({ message: "仅支持 .xlsx 格式的 Excel 文件", showClose: true });
        return;
      }
      this.importFile = file.raw;
      this.importResult = null;
    },
    handleImportFileRemove() {
      this.importFile = null;
    },
    handleImport() {
      if (!this.importFile) {
        this.$message.warning({ message: "请先选择文案 Excel 文件", showClose: true });
        return;
      }
      this.importing = true;
      const formData = new FormData();
      formData.append("file", this.importFile);
      Api.storyEngine.importActionImageCaptions(
        formData,
        ({ data }) => {
          this.importing = false;
          if (data.code === 0) {
            this.importResult = data.data || { updatedImages: 0, skippedRows: 0, skippedDetails: [] };
            this.$message.success({ message: "导入完成", showClose: true });
          } else {
            this.$message.error({ message: data.msg || "导入失败", showClose: true });
          }
        },
        ({ data }) => {
          this.importing = false;
          this.$message.error({ message: (data && data.msg) || "导入失败", showClose: true });
        }
      );
    },
    openEdit(row) {
      this.dialogTitle = "编辑大场景";
      this.form = {
        id: row.id,
        name: row.name,
        sortOrder: row.sortOrder == null ? 0 : row.sortOrder,
        status: row.status == null ? 1 : row.status
      };
      this.dialogVisible = true;
    },
    handleSubmit() {
      this.$refs.sceneForm.validate(valid => {
        if (!valid) {
          return;
        }
        this.saving = true;
        const payload = {
          id: this.form.id,
          name: this.form.name.trim(),
          sortOrder: this.form.sortOrder,
          status: this.form.status
        };
        const isEdit = !!payload.id;
        const onSuccess = ({ data }) => {
          this.saving = false;
          if (data.code === 0) {
            this.dialogVisible = false;
            this.fetchList();
            this.$message.success({
              message: isEdit ? "修改成功" : "新增成功",
              showClose: true
            });
          } else {
            this.$message.error({ message: data.msg || "保存失败", showClose: true });
          }
        };
        const onFail = ({ data }) => {
          this.saving = false;
          this.$message.error({ message: (data && data.msg) || "保存失败", showClose: true });
        };
        if (isEdit) {
          Api.storyEngine.updateBigScene(payload, onSuccess, onFail);
        } else {
          Api.storyEngine.saveBigScene(payload, onSuccess, onFail);
        }
      });
    },
    handleStatusChange(row, checked) {
      const nextStatus = checked ? 1 : 0;
      this.$set(row, "statusUpdating", true);
      const payload = {
        id: row.id,
        name: row.name,
        sortOrder: row.sortOrder,
        status: nextStatus
      };
      Api.storyEngine.updateBigScene(
        payload,
        ({ data }) => {
          this.$set(row, "statusUpdating", false);
          if (data.code === 0) {
            this.$set(row, "status", nextStatus);
            this.$message.success({
              message: nextStatus === 1 ? "已启用" : "已禁用",
              showClose: true
            });
          } else {
            this.$message.error({ message: data.msg || "状态更新失败", showClose: true });
          }
        },
        ({ data }) => {
          this.$set(row, "statusUpdating", false);
          this.$message.error({ message: (data && data.msg) || "状态更新失败", showClose: true });
        }
      );
    },
    handleDelete(row) {
      this.$confirm(
        `删除大场景「${row.name}」将级联删除其下所有小场景、动作与图片，是否继续？`,
        "警告",
        {
          confirmButtonText: "确定删除",
          cancelButtonText: "取消",
          type: "warning"
        }
      )
        .then(() => {
          Api.storyEngine.deleteBigScene(
            row.id,
            ({ data }) => {
              if (data.code === 0) {
                this.fetchList();
                this.$message.success({ message: "删除成功", showClose: true });
              } else {
                this.$message.error({ message: data.msg || "删除失败", showClose: true });
              }
            },
            ({ data }) => {
              this.$message.error({ message: (data && data.msg) || "删除失败", showClose: true });
            }
          );
        })
        .catch(() => {});
    }
  }
};
</script>

<style lang="scss" scoped>
.story-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 10px;

  .title-accent {
    width: 4px;
    height: 18px;
    border-radius: 2px;
    background: linear-gradient(to bottom, #4a7cfd, #8154fc);
  }

  h3 {
    margin: 0;
    font-size: 18px;
    font-weight: 500;
    color: #342f45;
  }
}

.panel-count {
  font-size: 12px;
  color: #a3a8c3;
  background: #f2f5fd;
  border-radius: 10px;
  padding: 2px 10px;
}

.panel-actions {
  display: flex;
  gap: 10px;
}

.story-table {
  border: 1px solid #eef3fd;
  border-radius: 6px;
  box-shadow: 0 2px 12px rgba(74, 124, 253, 0.08);
}

.cell-name {
  font-weight: 500;
  color: #342f45;
}

.empty-tip {
  padding: 30px 0;
  color: #a3a8c3;
  font-size: 13px;
}

.form-hint {
  margin-left: 10px;
  font-size: 12px;
  color: #a3a8c3;
}

.import-result {
  margin-top: 16px;
  padding: 12px;
  border-radius: 8px;
  background: #f5f7fd;
}

.template-download {
  margin-bottom: 12px;

  a {
    font-size: 13px;
    color: #5a64b5;
    text-decoration: none;

    &:hover {
      color: #7079aa;
      text-decoration: underline;
    }
  }
}

.import-result-summary {
  font-size: 13px;
  font-weight: 500;
  color: #342f45;
}

.import-result-details {
  margin: 8px 0 0;
  padding-left: 18px;
  max-height: 180px;
  overflow-y: auto;
  font-size: 12px;
  color: #a3a8c3;
  line-height: 1.8;
}

:deep(.el-table__header th) {
  color: #342f45;
  background: #edf2fc !important;
}

:deep(.el-table .el-button--text) {
  color: #7079aa;

  &:hover {
    color: #5a64b5;
  }
}

:deep(.el-table .el-button--text.danger-link) {
  color: #ff6b6b;

  &:hover {
    color: #f04b4b;
  }
}
</style>
