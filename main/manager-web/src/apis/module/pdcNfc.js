import { getServiceUrl } from '../api';
import RequestService from '../httpRequest';

export default {
  // ==================== 商品类型 ====================

  /**
   * 分页查询商品类型
   */
  listProductTypes(params, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/pdc/nfc/admin/product-types`)
      .method('GET')
      .data(params)
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        console.error('获取商品类型列表失败:', err);
        RequestService.reAjaxFun(() => {
          this.listProductTypes(params, callback);
        });
      }).send();
  },

  /**
   * 新增商品类型
   */
  createProductType(data, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/pdc/nfc/admin/product-types`)
      .method('POST')
      .data(data)
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        console.error('新增商品类型失败:', err);
        RequestService.reAjaxFun(() => {
          this.createProductType(data, callback);
        });
      }).send();
  },

  /**
   * 登记发布证据（商品类型）
   */
  registerProductTypeEvidence(productTypeId, data, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/pdc/nfc/admin/product-types/${productTypeId}/evidence`)
      .method('POST')
      .data(data)
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        console.error('登记发布证据失败:', err);
        RequestService.reAjaxFun(() => {
          this.registerProductTypeEvidence(productTypeId, data, callback);
        });
      }).send();
  },

  // ==================== 批次 ====================

  /**
   * 分页查询批次
   */
  listBatches(params, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/pdc/nfc/admin/batches`)
      .method('GET')
      .data(params)
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        console.error('获取批次列表失败:', err);
        RequestService.reAjaxFun(() => {
          this.listBatches(params, callback);
        });
      }).send();
  },

  /**
   * 新增批次
   */
  createBatch(data, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/pdc/nfc/admin/batches`)
      .method('POST')
      .data(data)
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        console.error('新增批次失败:', err);
        RequestService.reAjaxFun(() => {
          this.createBatch(data, callback);
        });
      }).send();
  },

  // ==================== Scheme 任务 ====================

  /**
   * 为指定批次启动 Scheme 任务
   */
  startSchemeJob(batchId, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/pdc/nfc/admin/scheme/batches/${batchId}/jobs`)
      .method('POST')
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        console.error('启动 Scheme 任务失败:', err);
        RequestService.reAjaxFun(() => {
          this.startSchemeJob(batchId, callback);
        });
      }).send();
  },

  /**
   * 重试 Scheme 任务
   */
  retrySchemeJob(jobId, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/pdc/nfc/admin/scheme/jobs/${jobId}/retry`)
      .method('POST')
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        console.error('重试 Scheme 任务失败:', err);
        RequestService.reAjaxFun(() => {
          this.retrySchemeJob(jobId, callback);
        });
      }).send();
  },

  /**
   * 取消 Scheme 任务
   */
  cancelSchemeJob(jobId, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/pdc/nfc/admin/scheme/jobs/${jobId}/cancel`)
      .method('POST')
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        console.error('取消 Scheme 任务失败:', err);
        RequestService.reAjaxFun(() => {
          this.cancelSchemeJob(jobId, callback);
        });
      }).send();
  },

  /**
   * 查询 Scheme 任务进度
   */
  schemeJobProgress(jobId, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/pdc/nfc/admin/scheme/jobs/${jobId}`)
      .method('GET')
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        console.error('获取 Scheme 任务进度失败:', err);
        RequestService.reAjaxFun(() => {
          this.schemeJobProgress(jobId, callback);
        });
      }).send();
  },

  // ==================== 写卡任务 ====================

  /**
   * 为指定批次创建写卡任务
   */
  createWriteJob(batchId, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/pdc/nfc/admin/write/batches/${batchId}/jobs`)
      .method('POST')
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        console.error('创建写卡任务失败:', err);
        RequestService.reAjaxFun(() => {
          this.createWriteJob(batchId, callback);
        });
      }).send();
  },

  /**
   * 下载写卡任务数据
   */
  downloadWriteJob(jobId, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/pdc/nfc/admin/write/jobs/${jobId}/download`)
      .method('GET')
      .type('blob')
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        console.error('下载写卡任务数据失败:', err);
        RequestService.reAjaxFun(() => {
          this.downloadWriteJob(jobId, callback);
        });
      }).send();
  },

  /**
   * 导入写卡结果
   */
  importWriteResult(jobId, file, requestId, callback) {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('requestId', requestId);
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/pdc/nfc/admin/write/jobs/${jobId}/import`)
      .method('POST')
      .data(formData)
      .header({ 'content-type': 'multipart/form-data' })
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        console.error('导入写卡结果失败:', err);
        RequestService.reAjaxFun(() => {
          this.importWriteResult(jobId, file, requestId, callback);
        });
      }).send();
  },

  /**
   * 取消写卡任务
   */
  cancelWriteJob(jobId, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/pdc/nfc/admin/write/jobs/${jobId}/cancel`)
      .method('POST')
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        console.error('取消写卡任务失败:', err);
        RequestService.reAjaxFun(() => {
          this.cancelWriteJob(jobId, callback);
        });
      }).send();
  },

  /**
   * 查询写卡任务详情
   */
  getWriteJob(jobId, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/pdc/nfc/admin/write/jobs/${jobId}`)
      .method('GET')
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        console.error('获取写卡任务详情失败:', err);
        RequestService.reAjaxFun(() => {
          this.getWriteJob(jobId, callback);
        });
      }).send();
  },

  // ==================== 资产 ====================

  /**
   * 分页查询资产
   */
  listAssets(params, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/pdc/nfc/admin/assets`)
      .method('GET')
      .data(params)
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        console.error('获取资产列表失败:', err);
        RequestService.reAjaxFun(() => {
          this.listAssets(params, callback);
        });
      }).send();
  },

  /**
   * 获取资产详情
   */
  assetDetail(id, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/pdc/nfc/admin/assets/${id}`)
      .method('GET')
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        console.error('获取资产详情失败:', err);
        RequestService.reAjaxFun(() => {
          this.assetDetail(id, callback);
        });
      }).send();
  },

  /**
   * 资产入库
   */
  stockIn(data, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/pdc/nfc/admin/assets/stock-in`)
      .method('POST')
      .data(data)
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        console.error('资产入库失败:', err);
        RequestService.reAjaxFun(() => {
          this.stockIn(data, callback);
        });
      }).send();
  },

  /**
   * 资产激活
   */
  activate(data, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/pdc/nfc/admin/assets/activate`)
      .method('POST')
      .data(data)
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        console.error('资产激活失败:', err);
        RequestService.reAjaxFun(() => {
          this.activate(data, callback);
        });
      }).send();
  },

  /**
   * 资产禁用
   */
  disable(data, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/pdc/nfc/admin/assets/disable`)
      .method('POST')
      .data(data)
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        console.error('资产禁用失败:', err);
        RequestService.reAjaxFun(() => {
          this.disable(data, callback);
        });
      }).send();
  },

  /**
   * 资产报废
   */
  scrap(data, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/pdc/nfc/admin/assets/scrap`)
      .method('POST')
      .data(data)
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        console.error('资产报废失败:', err);
        RequestService.reAjaxFun(() => {
          this.scrap(data, callback);
        });
      }).send();
  },

  // ==================== 操作日志 ====================

  /**
   * 分页查询操作日志
   */
  listLogs(params, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/pdc/nfc/admin/logs`)
      .method('GET')
      .data(params)
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        console.error('获取操作日志列表失败:', err);
        RequestService.reAjaxFun(() => {
          this.listLogs(params, callback);
        });
      }).send();
  },

  /**
   * 按对象查询操作日志
   */
  listLogsByObject(objectType, objectId, params, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/pdc/nfc/admin/logs/by-object/${objectType}/${objectId}`)
      .method('GET')
      .data(params)
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        console.error('获取对象操作日志失败:', err);
        RequestService.reAjaxFun(() => {
          this.listLogsByObject(objectType, objectId, params, callback);
        });
      }).send();
  }
}
