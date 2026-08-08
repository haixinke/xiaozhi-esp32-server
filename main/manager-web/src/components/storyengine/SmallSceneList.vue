<template>
  <div class="story-panel">
    <div class="panel-header">
      <div class="panel-title">
        <span class="title-accent"></span>
        <h3>小场景</h3>
        <span v-if="bigSceneName" class="panel-parent">所属大场景：{{ bigSceneName }}</span>
        <span class="panel-count">共 {{ list.length }} 个</span>
      </div>
      <div class="panel-actions">
        <CustomButton icon="el-icon-back" size="small" @click="$emit('back')">返回大场景</CustomButton>
        <template v-if="weightEditMode">
          <CustomButton size="small" @click="cancelWeightEdit">取消</CustomButton>
          <CustomButton
            icon="el-icon-check"
            type="confirm"
            size="small"
            :loading="weightSaving"
            @click="saveWeights"
          >保存权重</CustomButton>
        </template>
        <template v-else>
          <CustomButton
            icon="el-icon-edit-outline"
            size="small"
            :disabled="list.length === 0"
            @click="startWeightEdit"
          >编辑权重</CustomButton>
          <CustomButton icon="el-icon-plus" type="add" size="small" @click="openCreate">
            新增小场景
          </CustomButton>
        </template>
      </div>
    </div>

    <!-- 超额告警 -->
    <div v-if="overLimitPeriods.length > 0" class="over-limit-alert">
      <i class="el-icon-warning"></i>
      <span>
        以下时段权重合计已超出 100%：{{ overLimitPeriods.map(p => `${p.label} ${p.sum}%`).join('、') }}，请调整后再保存。
      </span>
    </div>

    <el-table
      :data="list"
      v-loading="loading"
      element-loading-text="加载中"
      element-loading-spinner="el-icon-loading"
      class="story-table"
      row-key="id"
      show-summary
      :summary-method="getSummaries"
    >
      <el-table-column prop="name" label="名称" min-width="180" align="left" show-overflow-tooltip>
        <template slot-scope="scope">
          <span class="cell-name">{{ scope.row.name }}</span>
        </template>
      </el-table-column>
      <el-table-column
        v-for="period in periods"
        :key="period.key"
        :prop="period.key"
        :label="period.label"
        width="118"
        align="center"
      >
        <template slot="header">
          <div class="period-header">
            <span>{{ period.label }}</span>
            <span class="period-range">{{ period.range }}</span>
          </div>
        </template>
        <template slot-scope="scope">
          <el-input-number
            v-if="weightEditMode"
            :value="scope.row[period.key]"
            :min="0"
            :max="100"
            size="mini"
            controls-position="right"
            class="weight-input"
            @input="(val) => updateRowWeight(scope.row, period.key, val)"
          />
          <span v-else class="weight-value">{{ scope.row[period.key] || 0 }}%</span>
        </template>
      </el-table-column>
      <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
      <el-table-column label="状态" width="90" align="center">
        <template slot-scope="scope">
          <el-switch
            :value="scope.row.status === 1"
            :disabled="weightEditMode || !!scope.row.statusUpdating"
            @change="(val) => handleStatusChange(scope.row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="210" align="center">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            :disabled="weightEditMode"
            @click="$emit('manage-actions', scope.row)"
          >管理动作</el-button>
          <el-button size="mini" type="text" :disabled="weightEditMode" @click="openEdit(scope.row)">
            编辑
          </el-button>
          <el-button
            size="mini"
            type="text"
            class="danger-link"
            :disabled="weightEditMode"
            @click="handleDelete(scope.row)"
          >删除</el-button>
        </template>
      </el-table-column>
      <template slot="empty">
        <div class="empty-tip">该大场景下暂无小场景，点击右上角「新增小场景」开始配置</div>
      </template>
    </el-table>

    <CustomDialog
      :title="dialogTitle"
      :visible.sync="dialogVisible"
      width="640px"
      :confirm-loading="saving"
      @confirm="handleSubmit"
      @cancel="dialogVisible = false"
    >
      <el-form ref="sceneForm" :model="form" :rules="rules" label-width="80px" @submit.native.prevent>
        <el-form-item label="名称" prop="name">
          <el-input
            v-model="form.name"
            maxlength="100"
            show-word-limit
            placeholder="如：卧室、北京-故宫、快餐厅"
          />
        </el-form-item>
        <el-form-item label="时段权重">
          <div class="weight-grid" v-loading="summaryLoading">
            <div v-for="period in periods" :key="period.key" class="weight-cell">
              <div class="weight-cell-label">
                {{ period.label }}
                <span class="weight-cell-range">{{ period.range }}</span>
              </div>
              <el-input-number
                v-model="form[period.key]"
                :min="0"
                :max="100"
                controls-position="right"
                class="weight-cell-input"
              />
              <div class="weight-cell-hint" :class="{ 'is-over': remainingOf(period) < 0 }">
                其他小场景已占 {{ occupiedOf(period) }}%，剩余可分配
                <strong>{{ remainingOf(period) }}%</strong>
              </div>
            </div>
          </div>
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
  </div>
</template>

<script>
import Api from "@/apis/api";
import CustomButton from "@/components/CustomButton.vue";
import CustomDialog from "@/components/CustomDialog.vue";

// 四个时段与后端权重字段、合计字段的映射
const PERIODS = [
  { key: "weightNight", label: "深夜", range: "00:00~05:59", totalKey: "totalNight" },
  { key: "weightMorning", label: "上午", range: "06:00~11:59", totalKey: "totalMorning" },
  { key: "weightAfternoon", label: "下午", range: "12:00~17:59", totalKey: "totalAfternoon" },
  { key: "weightEvening", label: "傍晚", range: "18:00~23:59", totalKey: "totalEvening" }
];

export default {
  name: "SmallSceneList",
  components: { CustomButton, CustomDialog },
  props: {
    bigSceneId: {
      type: String,
      required: true
    },
    bigSceneName: {
      type: String,
      default: ""
    }
  },
  data() {
    return {
      periods: PERIODS,
      list: [],
      loading: false,
      saving: false,
      // 权重批量编辑
      weightEditMode: false,
      weightSaving: false,
      weightSnapshot: [],
      // 弹窗
      dialogVisible: false,
      dialogTitle: "新增小场景",
      editingOriginal: null,
      summaryLoading: false,
      summaryTotals: {},
      form: this.buildEmptyForm(),
      rules: {
        name: [{ required: true, message: "请输入小场景名称", trigger: "blur" }]
      }
    };
  },
  computed: {
    // 各时段实时合计（编辑权重时随输入变化）
    periodSums() {
      const sums = {};
      PERIODS.forEach(period => {
        sums[period.key] = this.list.reduce(
          (total, row) => total + (Number(row[period.key]) || 0),
          0
        );
      });
      return sums;
    },
    overLimitPeriods() {
      return PERIODS.filter(period => this.periodSums[period.key] > 100).map(period => ({
        label: period.label,
        sum: this.periodSums[period.key]
      }));
    }
  },
  watch: {
    bigSceneId() {
      this.weightEditMode = false;
      this.fetchList();
    }
  },
  created() {
    this.fetchList();
  },
  methods: {
    buildEmptyForm() {
      return {
        id: null,
        name: "",
        weightNight: 0,
        weightMorning: 0,
        weightAfternoon: 0,
        weightEvening: 0,
        sortOrder: 0,
        status: 1
      };
    },
    fetchList() {
      if (!this.bigSceneId) {
        return;
      }
      this.loading = true;
      Api.storyEngine.getSmallSceneList(this.bigSceneId, ({ data }) => {
        this.loading = false;
        if (data.code === 0) {
          this.list = (data.data || []).map(item => ({
            ...item,
            weightNight: item.weightNight || 0,
            weightMorning: item.weightMorning || 0,
            weightAfternoon: item.weightAfternoon || 0,
            weightEvening: item.weightEvening || 0,
            statusUpdating: false
          }));
        } else {
          this.$message.error({ message: data.msg || "获取小场景列表失败", showClose: true });
        }
      });
    },
    // ==================== 合计行 ====================
    getSummaries({ columns }) {
      const h = this.$createElement;
      return columns.map((column, index) => {
        if (index === 0) {
          return h("span", { style: { fontWeight: 600, color: "#342f45" } }, "合计");
        }
        const period = PERIODS.find(item => item.key === column.property);
        if (!period) {
          return "";
        }
        const sum = this.periodSums[period.key];
        const over = sum > 100;
        return h(
          "span",
          {
            style: over
              ? { color: "#f56c6c", fontWeight: 700 }
              : { color: "#342f45", fontWeight: 600 }
          },
          `${sum}%`
        );
      });
    },
    // ==================== 权重批量编辑 ====================
    startWeightEdit() {
      this.weightSnapshot = this.list.map(row => ({
        id: row.id,
        weightNight: row.weightNight,
        weightMorning: row.weightMorning,
        weightAfternoon: row.weightAfternoon,
        weightEvening: row.weightEvening
      }));
      this.weightEditMode = true;
    },
    updateRowWeight(row, key, value) {
      this.$set(row, key, value == null ? 0 : value);
    },
    cancelWeightEdit() {
      this.weightSnapshot.forEach(snapshot => {
        const row = this.list.find(item => item.id === snapshot.id);
        if (row) {
          PERIODS.forEach(period => {
            this.$set(row, period.key, snapshot[period.key]);
          });
        }
      });
      this.weightSnapshot = [];
      this.weightEditMode = false;
    },
    saveWeights() {
      if (this.overLimitPeriods.length > 0) {
        this.$message.warning({
          message: "存在时段权重合计超过 100%，请先调整",
          showClose: true
        });
        return;
      }
      const items = this.list.map(row => ({
        id: row.id,
        weightNight: row.weightNight || 0,
        weightMorning: row.weightMorning || 0,
        weightAfternoon: row.weightAfternoon || 0,
        weightEvening: row.weightEvening || 0
      }));
      if (items.length === 0) {
        return;
      }
      this.weightSaving = true;
      Api.storyEngine.batchUpdateWeights(
        { items },
        ({ data }) => {
          this.weightSaving = false;
          if (data.code === 0) {
            this.weightEditMode = false;
            this.weightSnapshot = [];
            this.fetchList();
            this.$message.success({ message: "权重保存成功", showClose: true });
          } else {
            this.$message.error({ message: data.msg || "权重保存失败", showClose: true });
          }
        },
        ({ data }) => {
          this.weightSaving = false;
          this.$message.error({ message: (data && data.msg) || "权重保存失败", showClose: true });
        }
      );
    },
    // ==================== 新增/编辑 ====================
    fetchWeightSummary() {
      this.summaryLoading = true;
      Api.storyEngine.getWeightSummary(({ data }) => {
        this.summaryLoading = false;
        if (data.code === 0) {
          this.summaryTotals = data.data || {};
        } else {
          // 汇总接口失败时退化为本地合计，避免弹窗无提示可用
          this.summaryTotals = {
            totalNight: this.periodSums.weightNight,
            totalMorning: this.periodSums.weightMorning,
            totalAfternoon: this.periodSums.weightAfternoon,
            totalEvening: this.periodSums.weightEvening
          };
        }
      });
    },
    // 除当前编辑对象外，其他小场景已占用的权重
    occupiedOf(period) {
      const total = Number(this.summaryTotals[period.totalKey]) || 0;
      const own = this.editingOriginal ? Number(this.editingOriginal[period.key]) || 0 : 0;
      return Math.max(total - own, 0);
    },
    remainingOf(period) {
      return 100 - this.occupiedOf(period) - (Number(this.form[period.key]) || 0);
    },
    openCreate() {
      this.dialogTitle = "新增小场景";
      this.editingOriginal = null;
      this.form = this.buildEmptyForm();
      if (this.$refs.sceneForm) {
        this.$refs.sceneForm.clearValidate();
      }
      this.dialogVisible = true;
      this.fetchWeightSummary();
    },
    openEdit(row) {
      this.dialogTitle = "编辑小场景";
      this.editingOriginal = { ...row };
      this.form = {
        id: row.id,
        name: row.name,
        weightNight: row.weightNight || 0,
        weightMorning: row.weightMorning || 0,
        weightAfternoon: row.weightAfternoon || 0,
        weightEvening: row.weightEvening || 0,
        sortOrder: row.sortOrder == null ? 0 : row.sortOrder,
        status: row.status == null ? 1 : row.status
      };
      this.dialogVisible = true;
      this.fetchWeightSummary();
    },
    handleSubmit() {
      this.$refs.sceneForm.validate(valid => {
        if (!valid) {
          return;
        }
        const overPeriod = PERIODS.find(period => this.remainingOf(period) < 0);
        if (overPeriod) {
          this.$message.warning({
            message: `${overPeriod.label}时段权重合计将超过 100%，请调整`,
            showClose: true
          });
          return;
        }
        this.saving = true;
        const payload = {
          id: this.form.id,
          bigSceneId: this.bigSceneId,
          name: this.form.name.trim(),
          weightNight: this.form.weightNight || 0,
          weightMorning: this.form.weightMorning || 0,
          weightAfternoon: this.form.weightAfternoon || 0,
          weightEvening: this.form.weightEvening || 0,
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
          Api.storyEngine.updateSmallScene(payload, onSuccess, onFail);
        } else {
          Api.storyEngine.saveSmallScene(payload, onSuccess, onFail);
        }
      });
    },
    handleStatusChange(row, checked) {
      const nextStatus = checked ? 1 : 0;
      this.$set(row, "statusUpdating", true);
      const payload = {
        id: row.id,
        bigSceneId: this.bigSceneId,
        name: row.name,
        weightNight: row.weightNight || 0,
        weightMorning: row.weightMorning || 0,
        weightAfternoon: row.weightAfternoon || 0,
        weightEvening: row.weightEvening || 0,
        sortOrder: row.sortOrder,
        status: nextStatus
      };
      Api.storyEngine.updateSmallScene(
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
        `删除小场景「${row.name}」将级联删除其下所有动作与图片，是否继续？`,
        "警告",
        {
          confirmButtonText: "确定删除",
          cancelButtonText: "取消",
          type: "warning"
        }
      )
        .then(() => {
          Api.storyEngine.deleteSmallScene(
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

.panel-parent {
  font-size: 12px;
  color: #7079aa;
}

.panel-actions {
  display: flex;
  gap: 10px;
}

.over-limit-alert {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 6px;
  background: #fef0f0;
  border: 1px solid #fbc4c4;
  color: #f56c6c;
  font-size: 13px;
  font-weight: 600;
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

.period-header {
  display: flex;
  flex-direction: column;
  line-height: 1.3;

  .period-range {
    font-size: 11px;
    font-weight: 400;
    color: #a3a8c3;
  }
}

.weight-value {
  color: #5a6080;
}

.weight-input {
  width: 100%;
}

.empty-tip {
  padding: 30px 0;
  color: #a3a8c3;
  font-size: 13px;
}

.weight-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
}

.weight-cell {
  padding: 12px;
  border: 1px solid #eef3fd;
  border-radius: 8px;
  background: #fafbff;

  .weight-cell-label {
    font-size: 13px;
    color: #342f45;
    font-weight: 500;
    margin-bottom: 8px;
  }

  .weight-cell-range {
    margin-left: 6px;
    font-size: 11px;
    font-weight: 400;
    color: #a3a8c3;
  }

  .weight-cell-input {
    width: 100%;
  }

  .weight-cell-hint {
    margin-top: 8px;
    font-size: 12px;
    line-height: 1.4;
    color: #a3a8c3;

    strong {
      color: #4a7cfd;
    }

    &.is-over {
      color: #f56c6c;

      strong {
        color: #f56c6c;
      }
    }
  }
}

.form-hint {
  margin-left: 10px;
  font-size: 12px;
  color: #a3a8c3;
}

:deep(.el-table__header th) {
  color: #342f45;
  background: #edf2fc !important;
}

:deep(.el-table__footer td) {
  background: #f7f9ff !important;
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

:deep(.el-table .el-button--text.is-disabled) {
  color: #c8cde4;
}
</style>
