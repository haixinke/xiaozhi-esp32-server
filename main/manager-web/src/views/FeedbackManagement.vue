<template>
  <div class="welcome">
    <HeaderBar />
    <div class="main-wrapper">
      <div class="content-panel">
        <div class="content-area">
          <el-card class="params-card" shadow="never">
            <div class="operation-header">
              <h2 class="page-title">用户反馈</h2>
              <div class="right-operations">
                <el-select v-model="filterStatus" placeholder="处理状态" clearable class="filter-select" @change="handleSearch">
                  <el-option label="未处理" :value="0" />
                  <el-option label="已处理" :value="1" />
                </el-select>
                <el-select v-model="filterType" placeholder="诉求类型" clearable filterable class="filter-select" @change="handleSearch">
                  <el-option v-for="item in typeOptions" :key="item.key" :label="item.name" :value="item.key" />
                </el-select>
                <CustomButton icon="el-icon-search" type="confirm" size="small" @click="handleSearch">
                  搜索
                </CustomButton>
              </div>
            </div>
            <CustomTable
              ref="feedbackTable"
              :data="feedbackList"
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
              <template slot="type" slot-scope="scope">
                <span>{{ typeLabel(scope.row.type) }}</span>
              </template>
              <template slot="status" slot-scope="scope">
                <el-tag :type="scope.row.status === 1 ? 'success' : 'warning'" size="mini">
                  {{ scope.row.status === 1 ? '已处理' : '未处理' }}
                </el-tag>
              </template>
              <template slot="operations" slot-scope="scope">
                <el-button size="mini" type="text" @click="handleView(scope.row)">
                  详情 / 处理
                </el-button>
              </template>
            </CustomTable>
          </el-card>
        </div>
      </div>
    </div>

    <el-dialog
      title="反馈详情"
      :visible.sync="detailVisible"
      width="560px"
      :close-on-click-modal="false"
    >
      <div v-if="detail" class="feedback-detail">
        <div class="detail-row"><span class="detail-label">受理编号</span><span>{{ detail.receiptNumber }}</span></div>
        <div class="detail-row"><span class="detail-label">诉求类型</span><span>{{ typeLabel(detail.type) }}</span></div>
        <div class="detail-row"><span class="detail-label">提交用户</span><span>{{ detail.userId }}</span></div>
        <div class="detail-row"><span class="detail-label">提交时间</span><span>{{ detail.createDate }}</span></div>
        <div class="detail-row detail-row--top"><span class="detail-label">反馈内容</span><span class="detail-content">{{ detail.content }}</span></div>
        <el-form label-position="top" class="handle-form">
          <el-form-item label="处理状态">
            <el-radio-group v-model="handleForm.status">
              <el-radio :label="0">未处理</el-radio>
              <el-radio :label="1">已处理</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="处理备注">
            <el-input
              v-model="handleForm.remark"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              placeholder="记录处理结论，仅运营可见，不会回传给用户"
            />
          </el-form-item>
        </el-form>
      </div>
      <span slot="footer">
        <el-button size="small" @click="detailVisible = false">取消</el-button>
        <el-button size="small" type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </span>
    </el-dialog>
    <el-footer><VersionFooter/></el-footer>
  </div>
</template>

<script>
import Api from "@/apis/api";
import HeaderBar from "@/components/HeaderBar.vue";
import VersionFooter from "@/components/VersionFooter.vue";
import CustomButton from "@/components/CustomButton.vue";
import CustomTable from "@/components/CustomTable.vue";

export default {
  components: { HeaderBar, VersionFooter, CustomButton, CustomTable },
  data() {
    return {
      feedbackList: [],
      typeOptions: [],
      filterStatus: '',
      filterType: '',
      currentPage: 1,
      loading: false,
      pageSize: 10,
      pageSizeOptions: [10, 20, 50, 100],
      total: 0,
      detailVisible: false,
      detail: null,
      saving: false,
      handleForm: { status: 0, remark: '' },
      tableColumns: []
    };
  },
  created() {
    this.tableColumns = [
      { prop: 'receiptNumber', label: '受理编号', align: 'center' },
      { prop: 'type', label: '诉求类型', align: 'center', slot: 'type' },
      { prop: 'content', label: '反馈内容', align: 'center' },
      { prop: 'status', label: '处理状态', align: 'center', slot: 'status' },
      { prop: 'createDate', label: '提交时间', align: 'center' }
    ];
    this.fetchTypeOptions();
    this.fetchFeedbackList();
  },
  methods: {
    // 诉求类型选项来自字典 EGG_FEEDBACK_TYPE，与小程序共用同一数据源
    fetchTypeOptions() {
      Api.dict.getDictDataByType('EGG_FEEDBACK_TYPE')
        .then((data) => {
          this.typeOptions = Array.isArray(data) ? data : (data.data || []);
        })
        .catch(() => {
          this.typeOptions = [];
        });
    },

    typeLabel(type) {
      const hit = this.typeOptions.find(item => item.key === type);
      return hit ? hit.name : type;
    },

    fetchFeedbackList() {
      this.loading = true;
      Api.feedback.getFeedbackPage({
        status: this.filterStatus,
        type: this.filterType,
        page: this.currentPage,
        limit: this.pageSize
      }, ({ data }) => {
        this.loading = false;
        if (data.code === 0) {
          this.feedbackList = data.data.list || [];
          this.total = data.data.total || 0;
        } else {
          this.$message.error({
            message: data.msg || '获取用户反馈列表失败',
            showClose: true
          });
        }
      });
    },

    handleSearch() {
      this.currentPage = 1;
      this.fetchFeedbackList();
    },

    handlePageSizeChange(val) {
      this.pageSize = val;
      this.currentPage = 1;
      this.fetchFeedbackList();
    },

    goToPage(page) {
      if (page !== this.currentPage) {
        this.currentPage = page;
        this.fetchFeedbackList();
      }
    },

    handleView(row) {
      Api.feedback.getFeedbackDetail(row.id, ({ data }) => {
        if (data.code === 0) {
          this.detail = data.data;
          this.handleForm = { status: data.data.status, remark: data.data.remark || '' };
          this.detailVisible = true;
        } else {
          this.$message.error(data.msg || '获取反馈详情失败');
        }
      });
    },

    handleSave() {
      this.saving = true;
      Api.feedback.handleFeedback({
        id: this.detail.id,
        status: this.handleForm.status,
        remark: this.handleForm.remark
      }, ({ data }) => {
        this.saving = false;
        if (data.code === 0) {
          this.$message.success('保存成功');
          this.detailVisible = false;
          this.fetchFeedbackList();
        } else {
          this.$message.error(data.msg || '保存失败');
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
  padding: 0 0 16px 0;
}

.page-title {
  font-weight: 500;
  font-size: 24px;
  margin: 0;
}

.right-operations {
  display: flex;
  gap: 10px;
  margin-left: auto;
}

.filter-select {
  width: 160px;
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

:deep(.el-table .el-button--text) {
  color: #7079aa;
}

:deep(.el-table .el-button--text:hover) {
  color: #5a64b5;
}

.feedback-detail {
  .detail-row {
    display: flex;
    margin-bottom: 12px;
    font-size: 14px;
    color: #333;
  }

  .detail-row--top {
    align-items: flex-start;
  }

  .detail-label {
    flex: none;
    width: 72px;
    color: #999;
  }

  .detail-content {
    flex: 1;
    white-space: pre-wrap;
    word-break: break-all;
  }

  .handle-form {
    margin-top: 8px;
    border-top: 1px solid #f0f0f0;
    padding-top: 12px;
  }
}
</style>
