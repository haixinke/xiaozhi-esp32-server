<template>
  <div class="story-panel">
    <div class="panel-header">
      <div class="panel-title">
        <span class="title-accent"></span>
        <h3>动作</h3>
        <span v-if="smallSceneName" class="panel-parent">所属小场景：{{ smallSceneName }}</span>
        <span class="panel-count">共 {{ list.length }} 个</span>
      </div>
      <div class="panel-actions">
        <CustomButton icon="el-icon-back" size="small" @click="$emit('back')">返回小场景</CustomButton>
        <CustomButton icon="el-icon-plus" type="add" size="small" @click="openCreate">
          新增动作
        </CustomButton>
      </div>
    </div>

    <el-table
      ref="actionTable"
      :data="list"
      v-loading="loading"
      element-loading-text="加载中"
      element-loading-spinner="el-icon-loading"
      class="story-table"
      row-key="id"
      :expand-row-keys="expandedKeys"
      @expand-change="handleExpandChange"
    >
      <el-table-column type="expand" width="42">
        <template slot-scope="scope">
          <div class="image-manager">
            <div v-if="tagOptions(scope.row).length > 0" class="tag-filter">
              <span class="tag-filter-label">标签筛选</span>
              <el-select v-model="tagFilter" size="mini" placeholder="全部" class="tag-filter-select">
                <el-option label="全部" value=""></el-option>
                <el-option v-for="tag in tagOptions(scope.row)" :key="tag" :label="tag" :value="tag"></el-option>
              </el-select>
            </div>
            <el-tabs v-model="activePrototype" class="prototype-tabs">
              <el-tab-pane
                v-for="prototype in petPrototypes"
                :key="prototype"
                :label="`${prototype}（${countByPrototype(scope.row, prototype)}）`"
                :name="prototype"
              >
                <div class="time-columns">
                  <div v-for="timeOfDay in timeOfDays" :key="timeOfDay" class="time-column">
                    <div class="time-column-header">
                      <span class="time-label">
                        <i :class="timeIcon(timeOfDay)"></i>
                        {{ timeOfDay }}
                      </span>
                      <div class="time-header-right">
                        <span class="time-count">
                          {{ imagesOf(scope.row, prototype, timeOfDay).length }} 张
                        </span>
                        <el-button
                          size="mini"
                          type="text"
                          icon="el-icon-upload2"
                          @click="openUpload(scope.row, prototype, timeOfDay)"
                        >上传</el-button>
                      </div>
                    </div>
                    <div class="image-grid">
                      <div
                        v-for="image in imagesOf(scope.row, prototype, timeOfDay)"
                        :key="image.id"
                        class="image-card"
                      >
                        <el-image
                          class="image-thumb"
                          :src="thumbUrl(image.imageUrl)"
                          :preview-src-list="[image.imageUrl]"
                          fit="cover"
                        >
                          <div slot="error" class="image-error">
                            <i class="el-icon-picture-outline"></i>
                          </div>
                        </el-image>
                        <el-popover
                          placement="top"
                          width="260"
                          trigger="manual"
                          :value="captionEditingId === image.id"
                        >
                          <div class="caption-editor">
                            <el-input
                              v-model="tagDraft"
                              class="tag-editor-input"
                              maxlength="64"
                              show-word-limit
                              placeholder="标签（最长64字符），留空表示清除标签"
                            />
                            <el-input
                              v-model="captionDraft"
                              type="textarea"
                              :rows="3"
                              maxlength="1000"
                              show-word-limit
                              placeholder="多句配文用 | 分隔，例如：躺平中|今天也很努力地摸鱼"
                            />
                            <div class="caption-editor-footer">
                              <el-button size="mini" @click="closeCaptionEditor">取消</el-button>
                              <el-button
                                size="mini"
                                type="primary"
                                :loading="captionSaving"
                                @click="saveCaptions(image)"
                              >保存</el-button>
                            </div>
                          </div>
                          <div
                            slot="reference"
                            class="image-captions"
                            title="点击编辑标签与配文"
                            @click="openCaptionEditor(image)"
                          >
                            <span v-if="image.tag" class="tag-chip" :title="image.tag">{{ image.tag }}</span>
                            <template v-if="captionList(image.captions).length > 0">
                              <span
                                v-for="(caption, index) in captionList(image.captions)"
                                :key="index"
                                class="caption-chip"
                                :title="caption"
                              >{{ caption }}</span>
                            </template>
                            <span v-else-if="!image.tag" class="caption-empty">点击添加配文</span>
                          </div>
                        </el-popover>
                        <el-button
                          size="mini"
                          type="text"
                          class="image-delete danger-link"
                          icon="el-icon-delete"
                          @click="handleDeleteImage(image)"
                        >删除</el-button>
                      </div>
                      <div
                        v-if="imagesOf(scope.row, prototype, timeOfDay).length === 0"
                        class="image-placeholder"
                        @click="openUpload(scope.row, prototype, timeOfDay)"
                      >
                        <i class="el-icon-plus"></i>
                        <span>暂无图片，点击上传</span>
                      </div>
                    </div>
                  </div>
                </div>
              </el-tab-pane>
            </el-tabs>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="200" align="left" show-overflow-tooltip>
        <template slot-scope="scope">
          <span class="cell-name">{{ scope.row.name }}</span>
        </template>
      </el-table-column>
      <el-table-column label="最短时长" width="120" align="center">
        <template slot-scope="scope">
          <span class="cell-duration">{{ scope.row.durationMin || 0 }} 小时</span>
        </template>
      </el-table-column>
      <el-table-column label="最长时长" width="120" align="center">
        <template slot-scope="scope">
          <span class="cell-duration">{{ scope.row.durationMax || 0 }} 小时</span>
        </template>
      </el-table-column>
      <el-table-column label="图片" width="100" align="center">
        <template slot-scope="scope">
          <span class="image-count" :class="{ 'is-empty': totalImages(scope.row) === 0 }">
            {{ totalImages(scope.row) }} 张
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
      <el-table-column label="状态" width="90" align="center">
        <template slot-scope="scope">
          <el-switch
            :value="scope.row.status === 1"
            :disabled="!!scope.row.statusUpdating"
            @change="(val) => handleStatusChange(scope.row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="210" align="center">
        <template slot-scope="scope">
          <el-button size="mini" type="text" @click="toggleImagePanel(scope.row)">
            {{ isExpanded(scope.row) ? '收起图片' : '管理图片' }}
          </el-button>
          <el-button size="mini" type="text" @click="openEdit(scope.row)">编辑</el-button>
          <el-button size="mini" type="text" class="danger-link" @click="handleDelete(scope.row)">
            删除
          </el-button>
        </template>
      </el-table-column>
      <template slot="empty">
        <div class="empty-tip">该小场景下暂无动作，点击右上角「新增动作」开始配置</div>
      </template>
    </el-table>

    <!-- 动作新增/编辑 -->
    <CustomDialog
      :title="dialogTitle"
      :visible.sync="dialogVisible"
      width="560px"
      :confirm-loading="saving"
      @confirm="handleSubmit"
      @cancel="dialogVisible = false"
    >
      <el-form ref="actionForm" :model="form" :rules="rules" label-width="90px" @submit.native.prevent>
        <el-form-item label="名称" prop="name">
          <el-input
            v-model="form.name"
            maxlength="100"
            show-word-limit
            placeholder="如：小憩、看书、故宫红墙前散步"
          />
        </el-form-item>
        <el-form-item label="时长区间" prop="durationMin">
          <div class="duration-row">
            <el-input-number v-model="form.durationMin" :min="1" :max="240" controls-position="right" />
            <span class="duration-sep">~</span>
            <el-input-number v-model="form.durationMax" :min="1" :max="240" controls-position="right" />
            <span class="form-hint">单位：小时</span>
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

    <!-- 图片上传 -->
    <CustomDialog
      title="上传动作图片"
      :visible.sync="uploadDialogVisible"
      width="560px"
      :confirm-loading="uploading"
      confirm-text="确认上传"
      @confirm="submitUpload"
      @cancel="closeUpload"
      @close="closeUpload"
    >
      <div class="upload-context">
        <span class="context-item">动作：{{ uploadContext.actionName }}</span>
        <span class="context-item">原型：{{ uploadContext.petPrototype }}</span>
        <span class="context-item">时段：{{ uploadContext.timeOfDay }}</span>
      </div>
      <el-upload
        ref="imageUpload"
        action="#"
        class="image-upload"
        drag
        :auto-upload="false"
        :show-file-list="false"
        :on-change="handleFileChange"
        accept="image/png,image/jpeg,image/webp,image/gif"
      >
        <div v-if="previewUrl" class="upload-preview">
          <img :src="previewUrl" alt="preview" />
          <div class="upload-preview-name">{{ uploadFileName }}</div>
        </div>
        <template v-else>
          <i class="el-icon-upload"></i>
          <div class="el-upload__text">将图片拖到此处，或<em>点击选择</em></div>
        </template>
        <div class="el-upload__tip" slot="tip">支持 png / jpeg / webp / gif，建议不超过 5MB</div>
      </el-upload>
      <div class="captions-field">
        <div class="captions-label">图片标签</div>
        <el-input
          v-model="uploadTag"
          maxlength="64"
          show-word-limit
          placeholder="选填，单标签，如：摸鱼、吃饭"
        />
        <div class="captions-hint">
          仅用于管理端分类标注与筛选，最长 64 字符，不参与故事运行时的图片匹配。
        </div>
      </div>
      <div class="captions-field">
        <div class="captions-label">图片配文</div>
        <el-input
          v-model="uploadCaptions"
          type="textarea"
          :rows="3"
          maxlength="1000"
          show-word-limit
          placeholder="多句配文用 | 分隔，例如：躺平中|今天也很努力地摸鱼"
        />
        <div class="captions-hint">
          小程序展示时会从多句配文中随机取一句；上传后可点击图片下方的配文区域随时修改。
        </div>
      </div>
    </CustomDialog>
  </div>
</template>

<script>
import Api from "@/apis/api";
import CustomButton from "@/components/CustomButton.vue";
import CustomDialog from "@/components/CustomDialog.vue";

// 与后端 ai_story_action_image 约定一致的宠物原型与时段枚举
const PET_PROTOTYPES = ["锦鲤", "玉兔"];
const TIME_OF_DAYS = ["白天", "落日", "黑夜"];

export default {
  name: "ActionList",
  components: { CustomButton, CustomDialog },
  props: {
    smallSceneId: {
      type: String,
      required: true
    },
    smallSceneName: {
      type: String,
      default: ""
    }
  },
  data() {
    return {
      petPrototypes: PET_PROTOTYPES,
      timeOfDays: TIME_OF_DAYS,
      list: [],
      loading: false,
      saving: false,
      expandedKeys: [],
      activePrototype: PET_PROTOTYPES[0],
      // 动作弹窗
      dialogVisible: false,
      dialogTitle: "新增动作",
      form: {
        id: null,
        name: "",
        durationMin: 1,
        durationMax: 2,
        sortOrder: 0,
        status: 1
      },
      rules: {
        name: [{ required: true, message: "请输入动作名称", trigger: "blur" }]
      },
      // 上传弹窗
      uploadDialogVisible: false,
      uploading: false,
      uploadContext: { actionId: "", actionName: "", petPrototype: "", timeOfDay: "" },
      uploadFile: null,
      uploadFileName: "",
      uploadCaptions: "",
      uploadTag: "",
      previewUrl: "",
      // 标签筛选（仅前端本地过滤，图片已随动作列表全量下发）
      tagFilter: "",
      // 配文/标签内联编辑
      captionEditingId: "",
      captionDraft: "",
      tagDraft: "",
      captionSaving: false
    };
  },
  watch: {
    smallSceneId() {
      this.expandedKeys = [];
      this.fetchList();
    }
  },
  created() {
    this.fetchList();
  },
  beforeDestroy() {
    this.revokePreview();
  },
  methods: {
    fetchList() {
      if (!this.smallSceneId) {
        return;
      }
      this.loading = true;
      this.closeCaptionEditor();
      this.tagFilter = "";
      Api.storyEngine.getActionList(this.smallSceneId, ({ data }) => {
        this.loading = false;
        if (data.code === 0) {
          this.list = (data.data || []).map(item => ({
            ...item,
            images: item.images || [],
            statusUpdating: false
          }));
        } else {
          this.$message.error({ message: data.msg || "获取动作列表失败", showClose: true });
        }
      });
    },
    // ==================== 图片面板 ====================
    isExpanded(row) {
      return this.expandedKeys.indexOf(row.id) > -1;
    },
    toggleImagePanel(row) {
      this.$refs.actionTable.toggleRowExpansion(row, !this.isExpanded(row));
    },
    handleExpandChange(row, expandedRows) {
      this.closeCaptionEditor();
      // 手风琴模式：同时只展开一行图片面板；切换展开行时重置标签筛选（筛选范围为当前动作）
      this.tagFilter = "";
      const expanded = Array.isArray(expandedRows)
        ? expandedRows.length > 0 && expandedRows.some(item => item.id === row.id)
        : !!expandedRows;
      this.expandedKeys = expanded ? [row.id] : [];
    },
    imagesOf(row, petPrototype, timeOfDay) {
      return (row.images || []).filter(
        image =>
          image.petPrototype === petPrototype &&
          image.timeOfDay === timeOfDay &&
          (!this.tagFilter || image.tag === this.tagFilter)
      );
    },
    // 当前动作下全部已用标签（去重），供筛选下拉使用
    tagOptions(row) {
      const tags = (row.images || []).map(image => image.tag).filter(tag => !!tag);
      return [...new Set(tags)];
    },
    countByPrototype(row, petPrototype) {
      return (row.images || []).filter(image => image.petPrototype === petPrototype).length;
    },
    totalImages(row) {
      return (row.images || []).length;
    },
    thumbUrl(imageUrl) {
      if (!imageUrl) {
        return "";
      }
      return `${imageUrl}?x-oss-process=image/resize,w_200`;
    },
    captionList(captions) {
      if (!captions) {
        return [];
      }
      return captions
        .split("|")
        .map(item => item.trim())
        .filter(item => item.length > 0);
    },
    timeIcon(timeOfDay) {
      const icons = {
        白天: "el-icon-sunny",
        落日: "el-icon-sunset",
        黑夜: "el-icon-moon"
      };
      return icons[timeOfDay] || "el-icon-picture-outline";
    },
    // ==================== 图片上传 ====================
    openUpload(row, petPrototype, timeOfDay) {
      this.uploadContext = {
        actionId: row.id,
        actionName: row.name,
        petPrototype,
        timeOfDay
      };
      this.resetUploadFile();
      this.uploadCaptions = "";
      this.uploadTag = "";
      this.uploadDialogVisible = true;
    },
    handleFileChange(file) {
      if (!file || !file.raw) {
        return;
      }
      const maxSize = 5 * 1024 * 1024;
      if (file.raw.size > maxSize) {
        this.$message.warning({ message: "图片大小不能超过 5MB", showClose: true });
        return;
      }
      this.revokePreview();
      this.uploadFile = file.raw;
      this.uploadFileName = file.name;
      this.previewUrl = window.URL.createObjectURL(file.raw);
    },
    revokePreview() {
      if (this.previewUrl) {
        window.URL.revokeObjectURL(this.previewUrl);
        this.previewUrl = "";
      }
    },
    resetUploadFile() {
      this.revokePreview();
      this.uploadFile = null;
      this.uploadFileName = "";
      if (this.$refs.imageUpload) {
        this.$refs.imageUpload.clearFiles();
      }
    },
    closeUpload() {
      this.uploadDialogVisible = false;
      this.resetUploadFile();
    },
    submitUpload() {
      if (!this.uploadFile) {
        this.$message.warning({ message: "请先选择要上传的图片", showClose: true });
        return;
      }
      const formData = new FormData();
      formData.append("file", this.uploadFile);
      formData.append("petPrototype", this.uploadContext.petPrototype);
      formData.append("timeOfDay", this.uploadContext.timeOfDay);
      const captions = this.uploadCaptions.trim();
      if (captions) {
        formData.append("captions", captions);
      }
      const tag = this.uploadTag.trim();
      if (tag) {
        formData.append("tag", tag);
      }
      this.uploading = true;
      Api.storyEngine.uploadActionImage(
        this.uploadContext.actionId,
        formData,
        ({ data }) => {
          this.uploading = false;
          if (data.code === 0) {
            this.closeUpload();
            this.fetchList();
            this.$message.success({ message: "图片上传成功", showClose: true });
          } else {
            this.$message.error({ message: data.msg || "图片上传失败", showClose: true });
          }
        },
        ({ data }) => {
          this.uploading = false;
          this.$message.error({ message: (data && data.msg) || "图片上传失败", showClose: true });
        }
      );
    },
    // ==================== 配文/标签内联编辑 ====================
    openCaptionEditor(image) {
      if (this.captionSaving) {
        return;
      }
      // 同时只允许编辑一张图片，重复点击则收起
      if (this.captionEditingId === image.id) {
        this.closeCaptionEditor();
        return;
      }
      this.captionEditingId = image.id;
      this.captionDraft = image.captions || "";
      this.tagDraft = image.tag || "";
    },
    closeCaptionEditor() {
      this.captionEditingId = "";
      this.captionDraft = "";
      this.tagDraft = "";
    },
    saveCaptions(image) {
      // 以 | 分隔归一化，去除空白句与多余空格
      const captions = this.captionList(this.captionDraft).join("|");
      // 后端为整体更新语义：标签需与配文一起提交，空串表示清除标签
      const tag = this.tagDraft.trim();
      this.captionSaving = true;
      Api.storyEngine.updateActionImage(
        { id: image.id, captions, tag },
        ({ data }) => {
          this.captionSaving = false;
          if (data.code === 0) {
            this.$set(image, "captions", captions);
            this.$set(image, "tag", tag);
            this.closeCaptionEditor();
            this.$message.success({ message: "配文与标签已更新", showClose: true });
          } else {
            this.$message.error({ message: data.msg || "更新失败", showClose: true });
          }
        },
        ({ data }) => {
          this.captionSaving = false;
          this.$message.error({ message: (data && data.msg) || "更新失败", showClose: true });
        }
      );
    },
    handleDeleteImage(image) {
      this.$confirm("确定删除该图片记录吗？", "警告", {
        confirmButtonText: "确定删除",
        cancelButtonText: "取消",
        type: "warning"
      })
        .then(() => {
          Api.storyEngine.deleteActionImage(
            image.id,
            ({ data }) => {
              if (data.code === 0) {
                this.fetchList();
                this.$message.success({ message: "图片已删除", showClose: true });
              } else {
                this.$message.error({ message: data.msg || "图片删除失败", showClose: true });
              }
            },
            ({ data }) => {
              this.$message.error({
                message: (data && data.msg) || "图片删除失败",
                showClose: true
              });
            }
          );
        })
        .catch(() => {});
    },
    // ==================== 动作新增/编辑 ====================
    openCreate() {
      this.dialogTitle = "新增动作";
      this.form = { id: null, name: "", durationMin: 1, durationMax: 2, sortOrder: 0, status: 1 };
      if (this.$refs.actionForm) {
        this.$refs.actionForm.clearValidate();
      }
      this.dialogVisible = true;
    },
    openEdit(row) {
      this.dialogTitle = "编辑动作";
      this.form = {
        id: row.id,
        name: row.name,
        durationMin: row.durationMin || 1,
        durationMax: row.durationMax || 1,
        sortOrder: row.sortOrder == null ? 0 : row.sortOrder,
        status: row.status == null ? 1 : row.status
      };
      this.dialogVisible = true;
    },
    handleSubmit() {
      this.$refs.actionForm.validate(valid => {
        if (!valid) {
          return;
        }
        if (this.form.durationMax < this.form.durationMin) {
          this.$message.warning({ message: "最长时长不能小于最短时长", showClose: true });
          return;
        }
        this.saving = true;
        const payload = {
          id: this.form.id,
          smallSceneId: this.smallSceneId,
          name: this.form.name.trim(),
          durationMin: this.form.durationMin,
          durationMax: this.form.durationMax,
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
          Api.storyEngine.updateAction(payload, onSuccess, onFail);
        } else {
          Api.storyEngine.saveAction(payload, onSuccess, onFail);
        }
      });
    },
    handleStatusChange(row, checked) {
      const nextStatus = checked ? 1 : 0;
      this.$set(row, "statusUpdating", true);
      const payload = {
        id: row.id,
        smallSceneId: this.smallSceneId,
        name: row.name,
        durationMin: row.durationMin,
        durationMax: row.durationMax,
        sortOrder: row.sortOrder,
        status: nextStatus
      };
      Api.storyEngine.updateAction(
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
      this.$confirm(`删除动作「${row.name}」将同时删除其下所有图片记录，是否继续？`, "警告", {
        confirmButtonText: "确定删除",
        cancelButtonText: "取消",
        type: "warning"
      })
        .then(() => {
          Api.storyEngine.deleteAction(
            row.id,
            ({ data }) => {
              if (data.code === 0) {
                this.expandedKeys = this.expandedKeys.filter(key => key !== row.id);
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

.story-table {
  border: 1px solid #eef3fd;
  border-radius: 6px;
  box-shadow: 0 2px 12px rgba(74, 124, 253, 0.08);
}

.cell-name {
  font-weight: 500;
  color: #342f45;
}

.cell-duration {
  color: #5a6080;
}

.image-count {
  color: #4a7cfd;
  font-weight: 500;

  &.is-empty {
    color: #c8cde4;
    font-weight: 400;
  }
}

.empty-tip {
  padding: 30px 0;
  color: #a3a8c3;
  font-size: 13px;
}

/* ==================== 图片管理面板 ==================== */
.image-manager {
  padding: 4px 16px 12px;
  background: #fafbff;
}

.time-columns {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.time-column {
  border: 1px solid #eef3fd;
  border-radius: 8px;
  background: #fff;
  padding: 10px 12px 12px;
}

.time-column-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 8px;
  border-bottom: 1px dashed #eef3fd;
  margin-bottom: 10px;

  .time-label {
    font-size: 13px;
    font-weight: 600;
    color: #342f45;

    i {
      color: #8154fc;
      margin-right: 4px;
    }
  }

  .time-header-right {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .time-count {
    font-size: 12px;
    color: #a3a8c3;
  }
}

.image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 10px;
}

.image-card {
  border: 1px solid #eef3fd;
  border-radius: 6px;
  overflow: hidden;
  background: #fff;
  transition: box-shadow 0.2s ease, transform 0.2s ease;

  &:hover {
    box-shadow: 0 4px 14px rgba(74, 124, 253, 0.18);
    transform: translateY(-2px);
  }
}

.image-thumb {
  width: 100%;
  height: 120px;
  display: block;
  background: #f2f5fd;
}

.image-error {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 120px;
  color: #c8cde4;
  font-size: 22px;
}

.image-captions {
  padding: 6px 6px 2px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  min-height: 24px;
  cursor: pointer;
  border-radius: 4px;
  transition: background 0.2s ease;

  &:hover {
    background: #f7f9ff;

    .caption-empty {
      color: #4a7cfd;
    }
  }

  .caption-chip {
    max-width: 100%;
    padding: 1px 6px;
    border-radius: 8px;
    background: #f2f5fd;
    color: #5a6080;
    font-size: 11px;
    line-height: 16px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .caption-empty {
    font-size: 11px;
    color: #c8cde4;
  }

  .tag-chip {
    max-width: 100%;
    padding: 1px 6px;
    border-radius: 8px;
    background: #efeaff;
    color: #8154fc;
    font-size: 11px;
    line-height: 16px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.tag-filter {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;

  .tag-filter-label {
    font-size: 12px;
    color: #5a6080;
  }

  .tag-filter-select {
    width: 180px;
  }
}

.tag-editor-input {
  margin-bottom: 8px;
}

.caption-editor {
  .caption-editor-footer {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 10px;
  }
}

.image-delete {
  width: 100%;
  padding: 4px 0 6px;
}

.image-placeholder {
  grid-column: 1 / -1;
  height: 120px;
  border: 1px dashed #d3ddf7;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #a3a8c3;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;

  i {
    font-size: 18px;
  }

  &:hover {
    border-color: #4a7cfd;
    color: #4a7cfd;
    background: #f7f9ff;
  }
}

/* ==================== 弹窗 ==================== */
.duration-row {
  display: flex;
  align-items: center;
  gap: 8px;

  .duration-sep {
    color: #a3a8c3;
  }
}

.form-hint {
  margin-left: 6px;
  font-size: 12px;
  color: #a3a8c3;
}

.upload-context {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;

  .context-item {
    font-size: 12px;
    color: #5a6080;
    background: #f2f5fd;
    border-radius: 10px;
    padding: 3px 10px;
  }
}

.image-upload {
  :deep(.el-upload-dragger) {
    width: 100%;
    height: auto;
    min-height: 160px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
  }
}

.upload-preview {
  padding: 10px;

  img {
    max-width: 240px;
    max-height: 160px;
    border-radius: 6px;
    object-fit: cover;
  }

  .upload-preview-name {
    margin-top: 6px;
    font-size: 12px;
    color: #5a6080;
  }
}

.captions-field {
  margin-top: 18px;

  .captions-label {
    font-size: 13px;
    color: #342f45;
    font-weight: 500;
    margin-bottom: 8px;
  }

  .captions-hint {
    margin-top: 6px;
    font-size: 12px;
    line-height: 1.5;
    color: #a3a8c3;
  }
}

:deep(.el-table__header th) {
  color: #342f45;
  background: #edf2fc !important;
}

:deep(.el-table__expanded-cell) {
  padding: 0 !important;
  background: #fafbff;
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

.prototype-tabs {
  :deep(.el-tabs__item.is-active) {
    color: #4a7cfd;
  }

  :deep(.el-tabs__active-bar) {
    background-color: #4a7cfd;
  }
}
</style>
