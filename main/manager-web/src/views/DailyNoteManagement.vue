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
                <!-- JSON 批量导入：格式说明与示例在弹窗内展示 -->
                <el-button size="small" @click="importDialogVisible = true">导入 JSON</el-button>
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
    <!-- JSON 导入弹窗：格式说明、可复制示例、上传、结果统计与逐行错误明细 -->
    <el-dialog
      title="导入小纸条 JSON"
      :visible.sync="importDialogVisible"
      width="640px"
      :close-on-click-modal="false"
      @closed="resetImportState"
    >
      <div class="import-format">
        <p class="import-format-title">文件格式</p>
        <ul class="import-format-rules">
          <li>UTF-8 编码的 <code>.json</code> 文件，最大 1 MB，最多 1000 条</li>
          <li>顶层为非空数组，每项仅允许 <code>date</code>、<code>title</code>、<code>content</code>、可选 <code>solarTerm</code></li>
          <li><code>date</code> 严格 <code>yyyy-MM-dd</code>；正文换行在 JSON 中使用 <code>\n</code> 转义</li>
          <li>文件内相同日期整批拒绝；库内同日期整项覆盖（保留原 ID 与创建时间），其余日期新增</li>
        </ul>
        <div class="import-example-header">
          <span class="import-format-title">示例（可直接复制）</span>
          <el-button type="text" size="small" @click="copyImportExample">复制</el-button>
        </div>
        <pre class="import-example">{{ importExample }}</pre>
      </div>
      <el-upload
        action="#"
        :show-file-list="false"
        accept=".json,application/json"
        :http-request="uploadImportFile"
        :before-upload="beforeImportUpload"
      >
        <el-button type="primary" size="small" :loading="importing">选择文件并导入</el-button>
      </el-upload>
      <!-- 成功：新增数与覆盖数 -->
      <el-alert
        v-if="importResult"
        class="import-result"
        type="success"
        :closable="false"
        :title="`导入成功：新增 ${importResult.addedCount} 条，覆盖 ${importResult.overwrittenCount} 条`"
      />
      <!-- 失败：逐行错误明细（行号从 1 开始，与 JSON 数组顺序一致） -->
      <div v-if="importErrors.length > 0" class="import-errors">
        <el-alert type="error" :closable="false" title="导入失败，请按以下明细修正后重传（本次整批零写入）" />
        <div class="import-error-list">
          <div v-for="(err, index) in importErrors" :key="index" class="import-error-item">
            第 {{ err.row }} 行{{ err.field ? ` · ${err.field}` : '' }}：{{ err.reason }}
          </div>
        </div>
      </div>
      <span slot="footer">
        <el-button size="small" @click="importDialogVisible = false">关闭</el-button>
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
      form: { noteDate: "", title: "", content: "", solarTerm: "" },
      // JSON 导入弹窗状态：importResult 为成功统计，importErrors 为逐行错误明细
      importDialogVisible: false,
      importing: false,
      importResult: null,
      importErrors: []
    };
  },
  computed: {
    // 可直接复制的两条示例：第一条含节气及两行正文，第二条 solarTerm 为 null
    importExample() {
      return JSON.stringify([
        {
          date: "2026-10-29",
          title: "今日小纸条",
          content: "今天也辛苦了。\n记得早点休息。",
          solarTerm: "霜降"
        },
        {
          date: "2026-10-30",
          title: "给自己的一句话",
          content: "慢慢来，也很好。",
          solarTerm: null
        }
      ], null, 2);
    }
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

    // 导入前置校验：只收 JSON 文件
    beforeImportUpload(file) {
      const isJson = file.type === 'application/json' || /\.json$/i.test(file.name);
      if (!isJson) {
        this.$message.error('仅支持 .json 文件');
        return false;
      }
      if (file.size > 1024 * 1024) {
        this.$message.error('文件大小不能超过 1 MB');
        return false;
      }
      return true;
    },

    // el-upload 自定义上传：经 RequestService 走统一鉴权（Bearer token）
    uploadImportFile(option) {
      const formData = new FormData();
      formData.append('file', option.file);
      this.importing = true;
      this.importResult = null;
      this.importErrors = [];
      Api.dailyNote.importNotes(formData, ({ data }) => {
        this.importing = false;
        if (data.code === 0) {
          this.importResult = data.data;
          this.fetchNoteList();
        } else if (data.code === 10394 && Array.isArray(data.data)) {
          // 条目级校验失败：逐行错误明细列表展示，整批零写入
          this.importErrors = data.data;
        } else {
          this.$message.error({
            message: data.msg || '导入失败',
            showClose: true
          });
        }
      }, () => {
        this.importing = false;
        this.$message.error('导入失败，请稍后重试');
      });
    },

    copyImportExample() {
      navigator.clipboard.writeText(this.importExample).then(() => {
        this.$message.success('示例已复制');
      }).catch(() => {
        this.$message.error('复制失败，请手动选择文本复制');
      });
    },

    resetImportState() {
      this.importing = false;
      this.importResult = null;
      this.importErrors = [];
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

/* JSON 导入弹窗 */
.import-format {
  margin-bottom: 14px;
}

.import-format-title {
  font-weight: 600;
  margin: 0 0 6px 0;
  font-size: 13px;
}

.import-format-rules {
  margin: 0 0 10px 0;
  padding-left: 18px;
  font-size: 12px;
  color: #606266;
  line-height: 1.8;
}

.import-format-rules code {
  background: #f4f4f5;
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 12px;
}

.import-example-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.import-example {
  background: #f4f4f5;
  border-radius: 4px;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.6;
  overflow-x: auto;
  margin: 4px 0 0 0;
  white-space: pre;
}

.import-result {
  margin-top: 14px;
}

.import-errors {
  margin-top: 14px;
}

.import-error-list {
  margin-top: 8px;
  max-height: 200px;
  overflow-y: auto;
  border: 1px solid #fde2e2;
  border-radius: 4px;
  padding: 8px 12px;
}

.import-error-item {
  font-size: 12px;
  color: #f56c6c;
  line-height: 1.8;
}
</style>
