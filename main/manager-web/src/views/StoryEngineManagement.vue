<template>
  <div class="welcome">
    <HeaderBar />
    <div class="main-wrapper">
      <div class="content-panel">
        <div class="content-area">
          <el-card class="story-card" shadow="never">
            <!-- 层级面包屑 -->
            <div class="crumb-bar">
              <el-breadcrumb separator="/">
                <el-breadcrumb-item>
                  <span
                    class="breadcrumb-link"
                    :class="{ 'is-current': currentLevel === 'bigScene' }"
                    @click="goToLevel('bigScene')"
                  >故事引擎</span>
                </el-breadcrumb-item>
                <el-breadcrumb-item v-if="currentLevel !== 'bigScene'">
                  <span
                    class="breadcrumb-link"
                    :class="{ 'is-current': currentLevel === 'smallScene' }"
                    @click="goToLevel('smallScene')"
                  >{{ selectedBigScene.name }}</span>
                </el-breadcrumb-item>
                <el-breadcrumb-item v-if="currentLevel === 'action'">
                  <span class="breadcrumb-link is-current">{{ selectedSmallScene.name }}</span>
                </el-breadcrumb-item>
              </el-breadcrumb>
              <span class="crumb-hint">{{ levelHint }}</span>
            </div>

            <!-- 三级内容区 -->
            <div class="level-body">
              <BigSceneList
                v-if="currentLevel === 'bigScene'"
                @manage-small-scenes="handleManageSmallScenes"
              />
              <SmallSceneList
                v-if="currentLevel === 'smallScene'"
                :bigSceneId="selectedBigScene.id"
                :bigSceneName="selectedBigScene.name"
                @manage-actions="handleManageActions"
                @back="goToLevel('bigScene')"
              />
              <ActionList
                v-if="currentLevel === 'action'"
                :smallSceneId="selectedSmallScene.id"
                :smallSceneName="selectedSmallScene.name"
                @back="goToLevel('smallScene')"
              />
            </div>
          </el-card>
        </div>
      </div>
    </div>
    <el-footer>
      <version-footer />
    </el-footer>
  </div>
</template>

<script>
import HeaderBar from "@/components/HeaderBar.vue";
import VersionFooter from "@/components/VersionFooter.vue";
import ActionList from "@/components/storyengine/ActionList.vue";
import BigSceneList from "@/components/storyengine/BigSceneList.vue";
import SmallSceneList from "@/components/storyengine/SmallSceneList.vue";

export default {
  name: "StoryEngineManagement",
  components: { HeaderBar, VersionFooter, BigSceneList, SmallSceneList, ActionList },
  data() {
    return {
      // 当前钻取层级：bigScene / smallScene / action
      currentLevel: "bigScene",
      selectedBigScene: { id: "", name: "" },
      selectedSmallScene: { id: "", name: "" }
    };
  },
  computed: {
    levelHint() {
      const hints = {
        bigScene: "维护大场景，再逐层配置小场景与动作",
        smallScene: "配置小场景的时段权重，各时段合计需为 100",
        action: "配置动作时长与不同原型、时段的展示图片"
      };
      return hints[this.currentLevel] || "";
    }
  },
  methods: {
    goToLevel(level) {
      if (level === "bigScene") {
        this.selectedBigScene = { id: "", name: "" };
        this.selectedSmallScene = { id: "", name: "" };
      } else if (level === "smallScene") {
        // 回退到小场景层时，仅清空动作层的上下文
        if (!this.selectedBigScene.id) {
          this.currentLevel = "bigScene";
          return;
        }
        this.selectedSmallScene = { id: "", name: "" };
      }
      this.currentLevel = level;
    },
    handleManageSmallScenes(bigScene) {
      this.selectedBigScene = { id: bigScene.id, name: bigScene.name };
      this.selectedSmallScene = { id: "", name: "" };
      this.currentLevel = "smallScene";
    },
    handleManageActions(smallScene) {
      this.selectedSmallScene = { id: smallScene.id, name: smallScene.name };
      this.currentLevel = "action";
    }
  }
};
</script>

<style lang="scss" scoped>
.welcome {
  min-width: 1000px;
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
  // 顶部 63px 底部 35px
  height: calc(100vh - 63px - 35px);
  padding: 20px 22px 0;
  position: relative;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
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
  min-width: 800px;
  overflow: auto;
  background-color: white;
  display: flex;
  flex-direction: column;
}

.story-card {
  background: white;
  flex: 1;
  display: flex;
  flex-direction: column;
  border: none;
  box-shadow: none;
  overflow: hidden;

  ::v-deep .el-card__body {
    padding: 14px 20px 20px;
    display: flex;
    flex-direction: column;
    flex: 1;
    overflow: hidden;
  }
}

.crumb-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 4px 0 14px;
  border-bottom: 1px solid #eef3fd;
  flex-shrink: 0;
}

.breadcrumb-link {
  cursor: pointer;
  font-size: 14px;
  color: #7079aa;
  transition: color 0.2s ease;

  &:hover {
    color: #4a7cfd;
  }

  &.is-current {
    color: #342f45;
    font-weight: 600;
    cursor: default;
  }
}

.crumb-hint {
  font-size: 12px;
  color: #a3a8c3;
  margin-left: auto;
}

.level-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding-top: 14px;
  overflow: auto;
}

:deep(.el-breadcrumb__separator) {
  color: #c8cde4;
}
</style>
