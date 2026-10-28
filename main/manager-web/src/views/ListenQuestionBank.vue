<template>
  <div class="welcome">
    <HeaderBar />
    <div class="main-wrapper">
      <div class="content-panel">
        <div class="content-area">
          <el-card class="params-card" shadow="never">
            <div class="operation-header">
              <h2 class="page-title">题库运营</h2>
            </div>
            <!-- 大类 Tab：从字典 LISTEN_CATEGORY 实时读取并翻译名称，字典管理里改名后同步生效 -->
            <el-tabs v-model="activeCategory" class="category-tabs" @tab-click="handleCategoryChange">
              <el-tab-pane
                v-for="item in categoryOptions"
                :key="item.key"
                :label="item.name"
                :name="item.key"
              />
            </el-tabs>
            <CustomTable
              ref="groupTable"
              :data="groupList"
              :columns="tableColumns"
              :loading="loading"
              :show-selection="false"
              :show-operations="true"
              operations-label="操作"
              :total="total"
              :current-page="currentPage"
              :page-size="pageSize"
              :page-size-options="pageSizeOptions"
              @size-change="handlePageSizeChange"
              @page-change="goToPage"
            >
              <template slot="operations" slot-scope="scope">
                <el-button type="text" size="small" @click="handleView(scope.row)">查看题目</el-button>
                <el-button type="text" size="small" @click="handleEdit(scope.row)">编辑</el-button>
                <el-button type="text" size="small" class="danger-btn" @click="handleDeleteGroup(scope.row)">删除</el-button>
              </template>
            </CustomTable>
          </el-card>
        </div>
      </div>
    </div>

    <!-- 组内题目只读查看：题干 + A-E 选项 -->
    <el-dialog
      :title="detailTitle"
      :visible.sync="detailVisible"
      width="720px"
      :close-on-click-modal="false"
    >
      <div v-loading="detailLoading">
        <div v-for="(question, index) in detailQuestions" :key="question.id" class="question-block">
          <div class="question-text">{{ index + 1 }}. {{ question.text }}</div>
          <div class="option-list">
            <div v-for="option in question.options" :key="option.key" class="option-item">
              <span class="option-key">{{ option.key }}</span>
              <span>{{ option.text }}</span>
            </div>
          </div>
        </div>
        <el-empty v-if="!detailLoading && detailQuestions.length === 0" description="该题组暂无题目" />
      </div>
      <span slot="footer">
        <el-button size="small" @click="detailVisible = false">关闭</el-button>
      </span>
    </el-dialog>

    <!-- 题组编辑弹窗：组名 + 组内全部题目（题干 + 各 5 选项），整组一次性提交 -->
    <el-dialog
      title="编辑题组"
      :visible.sync="editVisible"
      width="820px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
    >
      <div v-loading="editLoading">
        <div class="edit-group-name">
          <span class="edit-label">题组名称</span>
          <el-input v-model="editForm.name" maxlength="50" placeholder="请输入题组名称" style="width: 320px" />
        </div>
        <div v-for="(question, index) in editForm.questions" :key="question.id || `new-${index}`" class="question-block">
          <div class="edit-question-header">
            <span class="edit-label">题目 {{ index + 1 }}</span>
            <el-button
              type="text"
              size="small"
              class="danger-btn"
              :disabled="editForm.questions.length <= 1"
              @click="handleRemoveEditQuestion(index)"
            >删除本题</el-button>
          </div>
          <el-input
            v-model="question.text"
            maxlength="200"
            placeholder="请输入题干"
            class="edit-question-text"
          />
          <div class="edit-option-list">
            <div v-for="(option, optionIndex) in question.options" :key="optionIndex" class="edit-option-item">
              <span class="option-key">{{ option.key }}</span>
              <el-input v-model="option.text" maxlength="100" placeholder="选项文本" />
            </div>
          </div>
        </div>
        <el-button
          v-if="editForm.questions.length < 5"
          type="text"
          size="small"
          icon="el-icon-plus"
          @click="handleAddEditQuestion"
        >添加题目（最多 5 题）</el-button>
      </div>
      <span slot="footer">
        <el-button size="small" @click="editVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="editSaving" @click="handleSaveEdit">保存</el-button>
      </span>
    </el-dialog>
    <el-footer><VersionFooter/></el-footer>
  </div>
</template>

<script>
import Api from "@/apis/api";
import HeaderBar from "@/components/HeaderBar.vue";
import VersionFooter from "@/components/VersionFooter.vue";
import CustomTable from "@/components/CustomTable.vue";

export default {
  components: { HeaderBar, VersionFooter, CustomTable },
  data() {
    return {
      // 大类 Tab 数据源：字典 LISTEN_CATEGORY 小类（key=dict_value, name=dict_label）
      categoryOptions: [],
      activeCategory: "",
      groupList: [],
      currentPage: 1,
      loading: false,
      pageSize: 10,
      pageSizeOptions: [10, 20, 50, 100],
      total: 0,
      tableColumns: [],
      // 请求序号：切换 Tab/分页快速点击时，丢弃过期响应，避免旧结果覆盖新查询
      requestSequence: 0,
      detailVisible: false,
      detailLoading: false,
      detailTitle: "",
      detailQuestions: [],
      // 编辑弹窗：整组表单（组名 + 组内全部题目），整组一次性提交
      editVisible: false,
      editLoading: false,
      editSaving: false,
      editGroupId: null,
      editForm: { name: "", questions: [] }
    };
  },
  created() {
    this.tableColumns = [
      { prop: 'name', label: '题组名称', align: 'center' },
      { prop: 'questionCount', label: '题数', align: 'center' },
      { prop: 'sortOrder', label: '排序', align: 'center' },
      { prop: 'updateDate', label: '更新时间', align: 'center' }
    ];
    this.fetchCategories();
  },
  methods: {
    // 大类从字典接口实时读取；拿到列表后默认选中第一个并加载其题组
    fetchCategories() {
      Api.dict.getDictDataByType('LISTEN_CATEGORY').then(({ data }) => {
        this.categoryOptions = data || [];
        if (this.categoryOptions.length > 0) {
          this.activeCategory = this.categoryOptions[0].key;
          this.fetchGroupList();
        }
      }).catch((err) => {
        this.$message.error(err.message || '获取题目大类失败');
      });
    },

    handleCategoryChange() {
      this.currentPage = 1;
      this.fetchGroupList();
    },

    fetchGroupList() {
      if (!this.activeCategory) {
        return;
      }
      const requestId = ++this.requestSequence;
      this.loading = true;
      Api.listenQuestion.getGroupPage({
        categoryCode: this.activeCategory,
        page: this.currentPage,
        limit: this.pageSize
      }, ({ data }) => {
        // 过期响应直接丢弃
        if (requestId !== this.requestSequence) {
          return;
        }
        this.loading = false;
        if (data.code === 0) {
          this.groupList = data.data.list || [];
          this.total = data.data.total || 0;
        } else {
          this.groupList = [];
          this.total = 0;
          this.$message.error({
            message: data.msg || '获取题组列表失败',
            showClose: true
          });
        }
      });
    },

    handlePageSizeChange(val) {
      this.pageSize = val;
      this.currentPage = 1;
      this.fetchGroupList();
    },

    goToPage(page) {
      if (page !== this.currentPage) {
        this.currentPage = page;
        this.fetchGroupList();
      }
    },

    // 只读查看：组详情接口返回组内全部题目与解析后的选项
    handleView(row) {
      this.detailTitle = `题组：${row.name}`;
      this.detailQuestions = [];
      this.detailLoading = true;
      this.detailVisible = true;
      Api.listenQuestion.getGroupDetail(row.id, ({ data }) => {
        this.detailLoading = false;
        if (data.code === 0) {
          this.detailQuestions = (data.data && data.data.questions) || [];
        } else {
          this.$message.error(data.msg || '获取题组详情失败');
        }
      });
    },

    // 打开编辑弹窗：复用组详情接口取回全量题目，深拷贝避免编辑直接改动只读态数据
    handleEdit(row) {
      this.editGroupId = row.id;
      this.editForm = { name: row.name, questions: [] };
      this.editLoading = true;
      this.editVisible = true;
      Api.listenQuestion.getGroupDetail(row.id, ({ data }) => {
        this.editLoading = false;
        if (data.code !== 0) {
          this.$message.error(data.msg || '获取题组详情失败');
          return;
        }
        const questions = ((data.data && data.data.questions) || []).map((question, index) => ({
          id: question.id,
          text: question.text,
          type: question.type,
          sortOrder: index + 1,
          options: (question.options || []).map((option) => ({ key: option.key, text: option.text }))
        }));
        this.editForm = { name: data.data.name, questions };
      });
    },

    // 选项键固定 A-E，按当前下标生成
    buildOptionKeys() {
      return ['A', 'B', 'C', 'D', 'E'];
    },

    // 新增一道空题（带默认 5 个空选项），追加在末尾
    handleAddEditQuestion() {
      this.editForm.questions.push({
        id: null,
        text: '',
        type: 'SINGLE',
        sortOrder: this.editForm.questions.length + 1,
        options: this.buildOptionKeys().map((key) => ({ key, text: '' }))
      });
    },

    // 从编辑表单移除一道题；若该题已有 id，需经确认后单题物理删除（不可恢复）
    handleRemoveEditQuestion(index) {
      const question = this.editForm.questions[index];
      if (question.id) {
        this.$confirm(
          `题目「${question.text || `第 ${index + 1} 题`}」将被物理删除，不可恢复。确认删除？`,
          '删除确认',
          { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
        ).then(() => {
          Api.listenQuestion.deleteQuestion(question.id, ({ data }) => {
            if (data.code === 0) {
              this.editForm.questions.splice(index, 1);
              this.$message.success('题目已删除');
            } else {
              this.$message.error(data.msg || '删除题目失败');
            }
          });
        }).catch(() => {});
        return;
      }
      // 未落库的新题直接移除
      this.editForm.questions.splice(index, 1);
    },

    // 保存前校验：组名非空、至少一题、题干与选项文本均非空、题干不重复（前端先行提示，后端判重兜底）
    validateEditForm() {
      if (!this.editForm.name || !this.editForm.name.trim()) {
        this.$message.error('请输入题组名称');
        return false;
      }
      if (this.editForm.questions.length === 0) {
        this.$message.error('题组至少包含一道题目');
        return false;
      }
      const texts = new Set();
      for (let i = 0; i < this.editForm.questions.length; i++) {
        const question = this.editForm.questions[i];
        if (!question.text || !question.text.trim()) {
          this.$message.error(`第 ${i + 1} 题的题干不能为空`);
          return false;
        }
        const normalized = question.text.trim();
        if (texts.has(normalized)) {
          this.$message.error(`题干「${normalized}」在组内重复`);
          return false;
        }
        texts.add(normalized);
        for (let j = 0; j < question.options.length; j++) {
          if (!question.options[j].text || !question.options[j].text.trim()) {
            this.$message.error(`第 ${i + 1} 题的选项 ${question.options[j].key} 不能为空`);
            return false;
          }
        }
      }
      return true;
    },

    // 整组一次性提交：sortOrder 按当前展示顺序重排；带 id 的保留 id（后端更新保 id），无 id 的新增
    handleSaveEdit() {
      if (!this.validateEditForm()) {
        return;
      }
      this.editSaving = true;
      const payload = {
        name: this.editForm.name.trim(),
        questions: this.editForm.questions.map((question, index) => ({
          id: question.id,
          text: question.text.trim(),
          type: question.type,
          sortOrder: index + 1,
          options: question.options.map((option) => ({ key: option.key, text: option.text.trim() }))
        }))
      };
      Api.listenQuestion.updateGroup(this.editGroupId, payload, ({ data }) => {
        this.editSaving = false;
        if (data.code === 0) {
          this.$message.success('保存成功');
          this.editVisible = false;
          this.fetchGroupList();
        } else {
          this.$message.error(data.msg || '保存题组失败');
        }
      }, () => {
        this.editSaving = false;
      });
    },

    // 整组物理删除：二次确认且文案明示不可恢复
    handleDeleteGroup(row) {
      this.$confirm(
        `题组「${row.name}」及其全部 ${row.questionCount} 道题目将被物理删除，不可恢复。确认删除？`,
        '删除确认',
        { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
      ).then(() => {
        Api.listenQuestion.deleteGroup(row.id, ({ data }) => {
          if (data.code === 0) {
            this.$message.success('题组已删除');
            this.fetchGroupList();
          } else {
            this.$message.error(data.msg || '删除题组失败');
          }
        });
      }).catch(() => {});
    }
  }
};
</script>

<style lang="scss" scoped>
.welcome {
  min-width: 900px;
  min-height: 506px;
  height: 100vh;
  display: flex;
  position: relative;
  flex-direction: column;
  background-size: cover;
  background: #eff4ff;
  -webkit-background-size: cover;
  -o-background-size: cover;
  overflow: hidden;
}

.main-wrapper {
  height: calc(100vh - 63px - 35px);
  padding: 20px 22px 0;
  position: relative;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

.operation-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0 8px 0;
}

.page-title {
  font-weight: 500;
  font-size: 24px;
  margin: 0;
}

.category-tabs {
  ::v-deep .el-tabs__header {
    margin-bottom: 12px;
  }
}

.content-panel {
  display: flex;
  overflow: hidden;
  height: 100%;
  border-radius: 15px;
  background: transparent;
  border: 1px solid #fff;
}

.content-area {
  flex: 1;
  height: 100%;
  min-width: 600px;
  overflow: auto;
  background-color: white;
  display: flex;
  flex-direction: column;
}

.params-card {
  background: white;
  flex: 1;
  display: flex;
  flex-direction: column;
  border: none;
  box-shadow: none;
  overflow: hidden;

  ::v-deep .el-card__body {
    padding: 14px 20px;
    display: flex;
    flex-direction: column;
    flex: 1;
    overflow: hidden;
  }
}

.question-block {
  padding: 12px 0;
  border-bottom: 1px solid #ebeef5;

  &:last-child {
    border-bottom: none;
  }
}

.question-text {
  font-weight: 500;
  margin-bottom: 8px;
}

.option-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-left: 16px;
}

.option-item {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.option-key {
  font-weight: 600;
  color: #5778ff;
}

.danger-btn {
  color: #f56c6c;
}

.edit-group-name {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 0 12px;
}

.edit-label {
  font-weight: 500;
  white-space: nowrap;
}

.edit-question-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.edit-question-text {
  margin: 6px 0 10px;
}

.edit-option-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-left: 16px;
}

.edit-option-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
