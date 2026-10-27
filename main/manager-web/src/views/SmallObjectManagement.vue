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
                <!-- 新增功能由后续迭代实现，本片只占位展示入口 -->
                <CustomButton icon="el-icon-plus" type="confirm" size="small">
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
              :show-operations="false"
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
            </CustomTable>
          </el-card>
        </div>
      </div>
    </div>
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
      requestSequence: 0
    };
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
</style>