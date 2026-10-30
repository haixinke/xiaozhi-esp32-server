<template>
  <div class="welcome">
    <HeaderBar />
    <div class="main-wrapper">
      <div class="content-panel">
        <div class="content-area">
          <el-card class="params-card" shadow="never">
            <div class="operation-header">
              <h2 class="page-title">游戏管理</h2>
              <div class="right-operations">
                <CustomButton icon="el-icon-plus" type="confirm" size="small" @click="handleAdd">
                  新增游戏
                </CustomButton>
              </div>
            </div>
            <CustomTable
              ref="gameTable"
              :data="gameList"
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
              <template slot="gameType" slot-scope="scope">
                <span>{{ gameTypeLabel(scope.row.gameType) }}</span>
              </template>
              <template slot="icon" slot-scope="scope">
                <img v-if="scope.row.icon" :src="scope.row.icon" class="game-icon" alt="" />
                <span v-else>-</span>
              </template>
              <template slot="entrances" slot-scope="scope">
                <span>{{ entrancesLabel(scope.row.entrances) }}</span>
              </template>
              <template slot="status" slot-scope="scope">
                <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'" size="mini">
                  {{ scope.row.status === 1 ? '启用' : '停用' }}
                </el-tag>
              </template>
              <template slot="operations" slot-scope="scope">
                <el-button size="mini" type="text" @click="handleEdit(scope.row)">
                  编辑
                </el-button>
                <el-button size="mini" type="text" @click="handleToggleStatus(scope.row)">
                  {{ scope.row.status === 1 ? '停用' : '启用' }}
                </el-button>
                <el-button size="mini" type="text" class="delete-btn" @click="handleDelete(scope.row)">
                  删除
                </el-button>
              </template>
            </CustomTable>
          </el-card>
        </div>
      </div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <CustomDialog
      :title="editForm.id ? '编辑游戏' : '新增游戏'"
      :visible.sync="dialogVisible"
      width="680px"
      :destroy-on-close="false"
      :confirm-loading="saving"
      confirm-text="保存"
      @confirm="handleSave"
    >
      <el-form label-position="top" class="edit-form">
        <div class="form-section">
          <div class="section-heading">基本信息</div>
          <div class="form-grid">
        <el-form-item label="游戏类型" required>
          <el-select v-model="editForm.gameType" placeholder="请选择游戏类型" class="form-control">
            <el-option v-for="item in gameTypeOptions" :key="item.key" :label="item.name" :value="item.key" />
          </el-select>
        </el-form-item>
        <el-form-item label="游戏入口" required>
          <el-checkbox-group v-model="editForm.entranceList">
            <el-checkbox label="NOTE">小纸条</el-checkbox>
            <el-checkbox label="ACTIVITY">活动</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
          </div>
        </div>
        <div class="form-section">
          <div class="section-heading">游戏规则</div>
          <div class="form-grid">
        <el-form-item label="赚取星星数">
          <el-input-number v-model="editForm.earnStars" :min="0" class="form-control" controls-position="right" />
        </el-form-item>
        <el-form-item label="消耗星星数">
          <el-input-number v-model="editForm.consumeStars" :min="0" class="form-control" controls-position="right" />
        </el-form-item>
        <el-form-item label="每日次数上限">
          <el-input-number v-model="editForm.dailyLimit" :min="0" class="form-control" controls-position="right" />
          <span class="form-tip">0 表示不限次数</span>
        </el-form-item>
          </div>
        </div>
        <div class="form-section">
          <div class="section-heading">展示信息</div>
        <el-form-item label="游戏图标">
          <el-upload
            class="icon-uploader"
            action="#"
            :http-request="uploadIcon"
            :show-file-list="false"
            :before-upload="beforeIconUpload"
            accept="image/png,image/jpeg,image/webp,image/svg+xml"
          >
            <img v-if="editForm.icon" :src="editForm.icon" class="preview-icon" alt="" />
            <CustomButton v-else size="small">上传图标</CustomButton>
          </el-upload>
        </el-form-item>
        <el-form-item label="游戏说明">
          <el-input
            v-model="editForm.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="游戏说明，可空"
            class="form-control"
          />
        </el-form-item>
        </div>
      </el-form>
    </CustomDialog>
    <el-footer><VersionFooter/></el-footer>
  </div>
</template>

<script>
import Api from "@/apis/api";
import HeaderBar from "@/components/HeaderBar.vue";
import VersionFooter from "@/components/VersionFooter.vue";
import CustomButton from "@/components/CustomButton.vue";
import CustomTable from "@/components/CustomTable.vue";
import CustomDialog from "@/components/CustomDialog.vue";

export default {
  components: { HeaderBar, VersionFooter, CustomButton, CustomTable, CustomDialog },
  data() {
    return {
      gameList: [],
      gameTypeOptions: [],
      currentPage: 1,
      loading: false,
      pageSize: 10,
      pageSizeOptions: [10, 20, 50, 100],
      total: 0,
      dialogVisible: false,
      saving: false,
      editForm: this.emptyForm(),
      tableColumns: []
    };
  },
  created() {
    this.tableColumns = [
      { prop: 'gameType', label: '游戏类型', align: 'center', slot: 'gameType' },
      { prop: 'icon', label: '图标', align: 'center', slot: 'icon' },
      { prop: 'earnStars', label: '赚取星星', align: 'center' },
      { prop: 'consumeStars', label: '消耗星星', align: 'center' },
      { prop: 'dailyLimit', label: '每日次数', align: 'center' },
      { prop: 'entrances', label: '游戏入口', align: 'center', slot: 'entrances' },
      { prop: 'status', label: '状态', align: 'center', slot: 'status' }
    ];
    this.fetchGameTypeOptions();
    this.fetchGameList();
  },
  methods: {
    emptyForm() {
      return {
        id: null,
        gameType: '',
        earnStars: 0,
        consumeStars: 0,
        dailyLimit: 0,
        icon: '',
        description: '',
        entranceList: [],
        status: 1
      };
    },

    // 游戏类型选项来自字典 GAME_TYPE
    fetchGameTypeOptions() {
      Api.dict.getDictDataByType('GAME_TYPE')
        .then((data) => {
          this.gameTypeOptions = Array.isArray(data) ? data : (data.data || []);
        })
        .catch(() => {
          this.gameTypeOptions = [];
        });
    },

    gameTypeLabel(type) {
      const hit = this.gameTypeOptions.find(item => item.key === type);
      return hit ? hit.name : type;
    },

    entrancesLabel(entrances) {
      if (!entrances) return '-';
      const labelMap = { NOTE: '小纸条', ACTIVITY: '活动' };
      return entrances.split(',').map(e => labelMap[e.trim()] || e).join('、');
    },

    fetchGameList() {
      this.loading = true;
      Api.game.getGamePage({
        page: this.currentPage,
        limit: this.pageSize
      }, ({ data }) => {
        this.loading = false;
        if (data.code === 0) {
          this.gameList = data.data.list || [];
          this.total = data.data.total || 0;
        } else {
          this.$message.error({
            message: data.msg || '获取游戏列表失败',
            showClose: true
          });
        }
      });
    },

    handlePageSizeChange(val) {
      this.pageSize = val;
      this.currentPage = 1;
      this.fetchGameList();
    },

    goToPage(page) {
      if (page !== this.currentPage) {
        this.currentPage = page;
        this.fetchGameList();
      }
    },

    handleAdd() {
      this.editForm = this.emptyForm();
      this.dialogVisible = true;
    },

    handleEdit(row) {
      Api.game.getGameDetail(row.id, ({ data }) => {
        if (data.code === 0) {
          const d = data.data;
          this.editForm = {
            id: d.id,
            gameType: d.gameType,
            earnStars: d.earnStars,
            consumeStars: d.consumeStars,
            dailyLimit: d.dailyLimit,
            icon: d.icon || '',
            description: d.description || '',
            entranceList: d.entrances ? d.entrances.split(',').map(e => e.trim()) : [],
            status: d.status
          };
          this.dialogVisible = true;
        } else {
          this.$message.error(data.msg || '获取游戏详情失败');
        }
      });
    },

    handleToggleStatus(row) {
      const newStatus = row.status === 1 ? 0 : 1;
      Api.game.updateGame({
        id: row.id,
        gameType: row.gameType,
        earnStars: row.earnStars,
        consumeStars: row.consumeStars,
        dailyLimit: row.dailyLimit,
        icon: row.icon,
        description: row.description,
        entrances: row.entrances,
        status: newStatus
      }, ({ data }) => {
        if (data.code === 0) {
          this.$message.success(newStatus === 1 ? '已启用' : '已停用');
          this.fetchGameList();
        } else {
          this.$message.error(data.msg || '操作失败');
        }
      });
    },

    handleDelete(row) {
      this.$confirm('确定删除该游戏配置吗？', '提示', { type: 'warning' })
        .then(() => {
          Api.game.deleteGame(row.id, ({ data }) => {
            if (data.code === 0) {
              this.$message.success('删除成功');
              this.fetchGameList();
            } else {
              this.$message.error(data.msg || '删除失败');
            }
          });
        })
        .catch(() => {});
    },

    // el-upload 自定义上传：经 RequestService 走统一鉴权（Bearer token），URL 与其他接口一致取 getServiceUrl()
    uploadIcon(option) {
      // scene 只拼在 URL query 上；再放入 FormData 会与 query 合并成 "game-icon,game-icon" 导致后端多值校验失败
      const formData = new FormData();
      formData.append('file', option.file);
      Api.game.uploadImage(formData, ({ data }) => {
        if (data.code === 0) {
          this.editForm.icon = data.data;
        } else {
          this.$message.error(data.msg || '上传失败');
        }
      }, () => {
        this.$message.error('上传失败');
      });
    },

    beforeIconUpload(file) {
      const allowedTypes = ['image/png', 'image/jpeg', 'image/webp', 'image/svg+xml'];
      if (!allowedTypes.includes(file.type)) {
        this.$message.error('仅支持 png/jpeg/webp/svg 格式');
        return false;
      }
      if (file.size > 2 * 1024 * 1024) {
        this.$message.error('文件大小不能超过 2MB');
        return false;
      }
      return true;
    },

    handleSave() {
      if (!this.editForm.gameType) {
        this.$message.warning('请选择游戏类型');
        return;
      }
      if (this.editForm.entranceList.length === 0) {
        this.$message.warning('请至少选择一个游戏入口');
        return;
      }

      this.saving = true;
      const payload = {
        gameType: this.editForm.gameType,
        earnStars: this.editForm.earnStars,
        consumeStars: this.editForm.consumeStars,
        dailyLimit: this.editForm.dailyLimit,
        icon: this.editForm.icon || null,
        description: this.editForm.description || null,
        entrances: this.editForm.entranceList.join(','),
        status: this.editForm.status
      };

      const isEdit = !!this.editForm.id;
      if (isEdit) {
        payload.id = this.editForm.id;
      }

      const apiCall = isEdit ? Api.game.updateGame : Api.game.saveGame;
      apiCall.call(Api.game, payload, ({ data }) => {
        this.saving = false;
        if (data.code === 0) {
          this.$message.success(isEdit ? '更新成功' : '新增成功');
          this.dialogVisible = false;
          this.fetchGameList();
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

.delete-btn {
  color: #f56c6c !important;
}

.game-icon {
  width: 32px;
  height: 32px;
  object-fit: contain;
}

.preview-icon {
  width: 64px;
  height: 64px;
  object-fit: contain;
}

.edit-form {
  max-height: calc(100vh - 220px);
  overflow-y: auto;
  padding: 0 4px;
}

.form-section + .form-section {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #eef3fd;
}

.section-heading {
  margin-bottom: 16px;
  color: #3d4566;
  font-size: 14px;
  font-weight: 600;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 20px;
}

.edit-form ::v-deep .el-form-item {
  margin-bottom: 16px;
}

.edit-form ::v-deep .el-form-item__label {
  padding: 0 0 6px;
  line-height: 20px;
  color: #3d4566;
  font-weight: 500;
}

.edit-form ::v-deep .el-form-item__content {
  line-height: 40px;
}

.form-control,
.edit-form ::v-deep .el-input-number {
  width: 100%;
}

.edit-form ::v-deep .el-input-number .el-input__inner {
  text-align: left;
}

.form-tip {
  display: block;
  color: #818cae;
  font-size: 12px;
  line-height: 18px;
  margin-top: 4px;
}

.icon-uploader {
  min-height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
