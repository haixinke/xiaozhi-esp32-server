<template>
  <div class="welcome">
    <HeaderBar />
    <div class="main-wrapper">
      <div class="content-panel">
        <div class="content-area">
          <el-card class="params-card" shadow="never">
            <div class="operation-header">
              <h2 class="page-title">小纸条管理</h2>
              <div class="header-actions">
                <!-- 完整日期精确查询；清空后回到全量列表 -->
                <el-date-picker
                  v-model="filterDate"
                  type="date"
                  value-format="yyyy-MM-dd"
                  placeholder="按日期查询"
                  size="small"
                  :clearable="true"
                  @change="handleDateChange"
                />
                <el-button type="primary" size="small" @click="handleCreate">新增小纸条</el-button>
              </div>
            </div>
            <CustomTable
              ref="noteTable"
              :data="noteList"
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
              <!-- 列表展示正文摘要（截断），完整正文在编辑弹窗中展示 -->
              <template slot="content" slot-scope="scope">
                <span>{{ summarize(scope.row.content) }}</span>
              </template>
              <template slot="solarTerm" slot-scope="scope">
                <span>{{ scope.row.solarTerm || '—' }}</span>
              </template>
              <template slot="operations" slot-scope="scope">
                <el-button type="text" size="small" @click="handleEdit(scope.row)">编辑</el-button>
                <el-button type="text" size="small" class="danger-btn" @click="handleDelete(scope.row)">删除</el-button>
              </template>
            </CustomTable>
          </el-card>
        </div>
      </div>
    </div>

    <!-- 新增与编辑共用表单：编辑时回填完整正文 -->
    <el-dialog
      :title="formId ? '编辑小纸条' : '新增小纸条'"
      :visible.sync="formVisible"
      width="560px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
    >
      <el-form label-width="70px" label-position="left">
        <el-form-item label="日期" required>
          <el-date-picker
            v-model="form.noteDate"
            type="date"
            value-format="yyyy-MM-dd"
            placeholder="选择日期"
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="50" show-word-limit placeholder="最多 50 字" />
        </el-form-item>
        <el-form-item label="正文" required>
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="6"
            maxlength="200"
            show-word-limit
            placeholder="最多 200 字，内部换行会保留"
          />
        </el-form-item>
        <el-form-item label="节气">
          <el-input v-model="form.solarTerm" maxlength="50" show-word-limit placeholder="可选，任意文案，最多 50 字" />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button size="small" @click="formVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="saving" @click="handleSave">保存</el-button>
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
      noteList: [],
      currentPage: 1,
      loading: false,
      pageSize: 10,
      pageSizeOptions: [10, 20, 50, 100],
      total: 0,
      tableColumns: [],
      // 完整日期精确过滤（yyyy-MM-dd），空值查全部
      filterDate: "",
      // 请求序号：分页/过滤快速切换时丢弃过期响应，避免旧结果覆盖新查询
      requestSequence: 0,
      // 新增/编辑共用表单；formId 为 null 表示新增
      formVisible: false,
      saving: false,
      formId: null,
      form: { noteDate: "", title: "", content: "", solarTerm: "" }
    };
  },
  created() {
    this.tableColumns = [
      { prop: 'noteDate', label: '日期', align: 'center' },
      { prop: 'title', label: '标题', align: 'center' },
      { prop: 'solarTerm', label: '节气', align: 'center' },
      { prop: 'content', label: '正文', align: 'center' },
      { prop: 'updateDate', label: '更新时间', align: 'center' }
    ];
    this.fetchNoteList();
  },
  methods: {
    fetchNoteList() {
      const requestId = ++this.requestSequence;
      this.loading = true;
      Api.dailyNote.getPage({
        noteDate: this.filterDate || undefined,
        page: this.currentPage,
        limit: this.pageSize
      }, ({ data }) => {
        // 过期响应直接丢弃
        if (requestId !== this.requestSequence) {
          return;
        }
        this.loading = false;
        if (data.code === 0) {
          this.noteList = data.data.list || [];
          this.total = data.data.total || 0;
        } else {
          this.noteList = [];
          this.total = 0;
          this.$message.error({
            message: data.msg || '获取小纸条列表失败',
            showClose: true
          });
        }
      });
    },

    handleDateChange() {
      this.currentPage = 1;
      this.fetchNoteList();
    },

    handlePageSizeChange(val) {
      this.pageSize = val;
      this.currentPage = 1;
      this.fetchNoteList();
    },

    goToPage(page) {
      if (page !== this.currentPage) {
        this.currentPage = page;
        this.fetchNoteList();
      }
    },

    // 正文摘要：单行预览，超长截断；完整正文在编辑弹窗中展示
    summarize(content) {
      if (!content) {
        return '';
      }
      const oneLine = content.replace(/\s+/g, ' ').trim();
      return oneLine.length > 30 ? oneLine.slice(0, 30) + '…' : oneLine;
    },

    handleCreate() {
      this.formId = null;
      this.form = { noteDate: "", title: "", content: "", solarTerm: "" };
      this.formVisible = true;
    },

    // 编辑：列表行已含完整正文（后端列表与详情共用同一 VO），直接回填
    handleEdit(row) {
      this.formId = row.id;
      this.form = {
        noteDate: row.noteDate,
        title: row.title,
        content: row.content,
        solarTerm: row.solarTerm || ""
      };
      this.formVisible = true;
    },

    // 保存前校验：日期必填且完整，标题/正文 trim 后非空（长度上限由输入框 maxlength 与后端共同兜底）
    validateForm() {
      if (!this.form.noteDate) {
        this.$message.error('请选择日期');
        return false;
      }
      if (!this.form.title || !this.form.title.trim()) {
        this.$message.error('请输入标题');
        return false;
      }
      if (!this.form.content || !this.form.content.trim()) {
        this.$message.error('请输入正文');
        return false;
      }
      return true;
    },

    handleSave() {
      if (!this.validateForm()) {
        return;
      }
      this.saving = true;
      const payload = {
        noteDate: this.form.noteDate,
        title: this.form.title,
        content: this.form.content,
        solarTerm: this.form.solarTerm || null
      };
      const done = () => {
        this.saving = false;
      };
      const onResult = ({ data }) => {
        done();
        if (data.code === 0) {
          this.$message.success('保存成功');
          this.formVisible = false;
          this.fetchNoteList();
        } else {
          // 重复日期（10386）等错误由后端 i18n 消息原样提示
          this.$message.error({
            message: data.msg || '保存失败',
            showClose: true
          });
        }
      };
      if (this.formId) {
        Api.dailyNote.update(this.formId, payload, onResult, done);
      } else {
        Api.dailyNote.create(payload, onResult, done);
      }
    },

    // 物理删除：确认文案展示日期与标题，并明示物理删除不可恢复
    handleDelete(row) {
      this.$confirm(
        `${row.noteDate} 的小纸条「${row.title}」将被物理删除，不可恢复。确认删除？`,
        '删除确认',
        { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
      ).then(() => {
        Api.dailyNote.remove(row.id, ({ data }) => {
          if (data.code === 0) {
            this.$message.success('小纸条已删除');
            this.fetchNoteList();
          } else {
            this.$message.error(data.msg || '删除小纸条失败');
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

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
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

.danger-btn {
  color: #f56c6c;
}
</style>
