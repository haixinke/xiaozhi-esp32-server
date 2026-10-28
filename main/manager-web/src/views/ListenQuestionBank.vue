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
              </template>
            </CustomTable>
          </el-card>
        </div>
      </div>
    </div>

    <!-- 组内题目只读查看：题干 + A-E 选项，本期不提供编辑 -->
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
      detailQuestions: []
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
</style>
