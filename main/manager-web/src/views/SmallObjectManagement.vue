<template>
  <div class="welcome">
    <HeaderBar />
    <div class="main-wrapper">
      <div class="content-panel">
        <div class="content-area">
          <el-card class="params-card" shadow="never">
            <div class="operation-header">
              <h2 class="page-title">小物件管理</h2>
              <div class="right-operations">
                <CustomButton icon="el-icon-plus" type="confirm" size="small" @click="handleAdd">
                  新增小物件
                </CustomButton>
              </div>
            </div>
            <CustomTable
              ref="smallObjectTable"
              :data="objectList"
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
              <template slot="imageUrl" slot-scope="scope">
                <img v-if="scope.row.imageUrl" :src="scope.row.imageUrl" class="object-image" alt="" />
                <span v-else>-</span>
              </template>
              <template slot="objectTypeName" slot-scope="scope">
                <span>{{ scope.row.objectTypeName }}</span>
              </template>
              <template slot="status" slot-scope="scope">
                <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'" size="mini">
                  {{ scope.row.status === 1 ? '启用' : '停用' }}
                </el-tag>
              </template>
              <template slot="operations" slot-scope="scope">
                <el-button size="mini" type="text" class="delete-btn" @click="handleDelete(scope.row)">
                  删除
                </el-button>
              </template>
            </CustomTable>
          </el-card>
        </div>
      </div>
    </div>

    <!-- 新增小物件弹窗（本片只实现新增；编辑回填与改类型由后续票实现） -->
    <el-dialog
      title="新增小物件"
      :visible.sync="dialogVisible"
      width="620px"
      :close-on-click-modal="false"
    >
      <el-form label-position="left" label-width="110px" class="edit-form">
        <el-form-item label="物件类型" required>
          <!-- 数据源是 object-types 接口而非字典全量：字典里混有固件类型、手机区号等非物件类型。
               已被占用的类型不出现在下拉里，前端分页视角看不到跨页占用，由后端 used 标记决定 -->
          <el-select v-model="editForm.objectType" placeholder="请选择物件类型" class="form-control">
            <el-option
              v-for="item in availableTypeOptions"
              :key="item.key"
              :label="item.name"
              :value="item.key"
            />
          </el-select>
          <span v-if="typeOptions.length && !availableTypeOptions.length" class="form-tip">
            所有物件类型都已被占用，请先在字典管理中新增小类
          </span>
        </el-form-item>
        <el-form-item label="物件图片" required>
          <el-upload
            class="icon-uploader"
            action="#"
            :http-request="uploadImage"
            :show-file-list="false"
            :before-upload="beforeImageUpload"
            accept="image/png,image/jpeg,image/webp"
          >
            <img v-if="editForm.imageUrl" :src="editForm.imageUrl" class="preview-image" alt="" />
            <el-button v-else size="small" type="primary">上传图片</el-button>
          </el-upload>
          <span class="form-tip">支持 png/jpeg/webp，大小不超过 2MB</span>
        </el-form-item>
        <el-form-item label="物件故事" required>
          <!-- 纯文本多行输入，maxlength + show-word-limit 提供字数统计；后端仍完整校验 -->
          <el-input
            v-model="editForm.story"
            type="textarea"
            :rows="6"
            maxlength="2000"
            show-word-limit
            placeholder="填写物件背后的故事，最多 2000 字"
            class="form-control"
          />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button size="small" @click="dialogVisible = false">取消</el-button>
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
      objectList: [],
      currentPage: 1,
      loading: false,
      pageSize: 10,
      pageSizeOptions: [10, 20, 50, 100],
      total: 0,
      tableColumns: [],
      // 请求序号：分页/页大小快速切换时，丢弃过期响应，避免旧结果覆盖新查询
      requestSequence: 0,
      // 全部字典小类（含 used 标记），下拉再按 used 过滤出未占用的
      typeOptions: [],
      dialogVisible: false,
      saving: false,
      editForm: this.emptyForm()
    };
  },
  computed: {
    // 新增时只列出尚未被占用的类型；占用判断来自后端，不靠前端分页数据推算
    availableTypeOptions() {
      return this.typeOptions.filter(item => !item.used);
    }
  },
  created() {
    this.tableColumns = [
      { prop: 'imageUrl', label: '图片', align: 'center', slot: 'imageUrl' },
      { prop: 'objectTypeName', label: '类型名称', align: 'center', slot: 'objectTypeName' },
      { prop: 'status', label: '状态', align: 'center', slot: 'status' },
      { prop: 'updateDate', label: '更新时间', align: 'center' }
    ];
    this.fetchObjectList();
  },
  methods: {
    fetchObjectList() {
      const requestId = ++this.requestSequence;
      this.loading = true;
      Api.smallObject.getSmallObjectPage({
        page: this.currentPage,
        limit: this.pageSize
      }, ({ data }) => {
        // 过期响应直接丢弃
        if (requestId !== this.requestSequence) {
          return;
        }
        this.loading = false;
        if (data.code === 0) {
          this.objectList = data.data.list || [];
          this.total = data.data.total || 0;
        } else {
          this.objectList = [];
          this.total = 0;
          this.$message.error({
            message: data.msg || '获取小物件列表失败',
            showClose: true
          });
        }
      });
    },

    handlePageSizeChange(val) {
      this.pageSize = val;
      this.currentPage = 1;
      this.fetchObjectList();
    },

    goToPage(page) {
      if (page !== this.currentPage) {
        this.currentPage = page;
        this.fetchObjectList();
      }
    },

    // 删除为软删且不提供恢复入口，需二次确认；成功后刷新列表让记录消失
    handleDelete(row) {
      this.$confirm('确定删除该小物件吗？', '提示', { type: 'warning' })
        .then(() => {
          Api.smallObject.deleteSmallObject(row.id, ({ data }) => {
            if (data.code === 0) {
              this.$message.success('删除成功');
              this.fetchObjectList();
            } else {
              this.$message.error(data.msg || '删除失败');
            }
          });
        })
        .catch(() => {});
    },

    emptyForm() {
      return {
        id: null,
        objectType: '',
        imageUrl: '',
        story: ''
      };
    },

    // 类型下拉数据源：object-types 接口（含占用标记），调用字典全量会混入非物件类型
    fetchObjectTypes(callback) {
      Api.smallObject.getObjectTypes(({ data }) => {
        if (data.code === 0) {
          this.typeOptions = data.data || [];
        } else {
          this.typeOptions = [];
          this.$message.error(data.msg || '获取物件类型失败');
        }
        if (callback) {
          callback();
        }
      });
    },

    handleAdd() {
      this.editForm = this.emptyForm();
      // 每次打开都重新拉取，避免刚被别人占用的类型仍出现在下拉里
      this.fetchObjectTypes(() => {
        this.dialogVisible = true;
      });
    },

    // el-upload 自定义上传：经 RequestService 走统一鉴权（Bearer token），场景为 small-object
    uploadImage(option) {
      const formData = new FormData();
      formData.append('file', option.file);
      Api.smallObject.uploadImage(formData, ({ data }) => {
        if (data.code === 0) {
          this.editForm.imageUrl = data.data;
        } else {
          this.$message.error(data.msg || '上传失败');
        }
      }, () => {
        this.$message.error('上传失败');
      });
    },

    beforeImageUpload(file) {
      // 前端预校验只为体验，后端 UploadScene 白名单仍是安全边界
      const allowedTypes = ['image/png', 'image/jpeg', 'image/webp'];
      if (!allowedTypes.includes(file.type)) {
        this.$message.error('仅支持 png/jpeg/webp 格式');
        return false;
      }
      if (file.size > 2 * 1024 * 1024) {
        this.$message.error('文件大小不能超过 2MB');
        return false;
      }
      return true;
    },

    handleSave() {
      // 提交前提示；后端仍完整校验（前端校验是体验，不是安全边界）
      if (!this.editForm.objectType) {
        this.$message.warning('请选择物件类型');
        return;
      }
      if (!this.editForm.imageUrl) {
        this.$message.warning('请上传物件图片');
        return;
      }
      if (!this.editForm.story || !this.editForm.story.trim()) {
        this.$message.warning('请填写物件故事');
        return;
      }
      if (this.editForm.story.length > 2000) {
        this.$message.warning('物件故事最多 2000 字');
        return;
      }

      this.saving = true;
      Api.smallObject.saveSmallObject({
        objectType: this.editForm.objectType,
        imageUrl: this.editForm.imageUrl,
        story: this.editForm.story
      }, ({ data }) => {
        this.saving = false;
        if (data.code === 0) {
          this.$message.success('新增成功');
          this.dialogVisible = false;
          this.fetchObjectList();
        } else {
          this.$message.error(data.msg || '保存失败');
        }
      }, ({ data }) => {
        // 后端业务拒绝走 fail 分支（HTTP 200 + code != 0），不经过 success 回调；
        // 这里必须复位 saving，否则 el-button 的 loading 会让保存按钮永久禁用，无法改后重试
        this.saving = false;
        this.$message.error((data && data.msg) || '保存失败');
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

.object-image {
  width: 32px;
  height: 32px;
  object-fit: contain;
}

.delete-btn {
  color: #f56c6c !important;
}

.preview-image {
  width: 120px;
  height: 120px;
  object-fit: contain;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}

.form-tip {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}
</style>