import { getServiceUrl } from '../api';
import RequestService from '../httpRequest';
import { buildReleaseEvidencePayload } from '../../utils/pdcNfcReleaseEvidence.mjs';

export default {
  // ==================== 商品类型 ====================

  /**
   * 分页查询商品类型
   */
  listProductTypes(params, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/pdc/nfc/product-type/list`)
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
    // 后端当前无此端点，保留占位
    console.warn('createProductType: backend endpoint not implemented')
    callback({ data: { code: -1, msg: '暂不支持新增商品类型' } })
  },

  /**
   * 登记当前领取页发布证据
   */
  registerReleaseEvidence(data, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/pdc/nfc/product-type/release-evidence`)
      .method('POST')
      .data(buildReleaseEvidencePayload(data))
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        console.error('登记发布证据失败:', err);
        callback({ data: { code: -1, msg: '登记发布证据网络请求失败' } });
      }).send();
  },

  // ==================== 批次 ====================

  /**
   * 分页查询批次
   */
  listBatches(params, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/pdc/nfc/batch/list`)
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
      .url(`${getServiceUrl()}/pdc/nfc/batch/create`)
      .method('POST')
      .data(data)
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        console.error('新增批次失败:', err);
        // 创建类 POST 无 requestId 幂等保护，不做自动重试（避免超时后重复创建）
        callback({ data: { code: -1, msg: '新增批次网络请求失败，请刷新列表确认是否已创建后再重试' } });
      }).send();
  },

  // ==================== Scheme 任务 ====================

  /**
   * 为指定批次启动 Scheme 任务
   */
  startSchemeJob(batchId, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/pdc/nfc/scheme/generate/${batchId}`)
      .method('POST')
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        console.error('启动 Scheme 任务失败:', err);
        // 变更类 POST 不做自动重试；后端状态机保证重复提交只会报错不会产生重复任务
        callback({ data: { code: -1, msg: '启动 Scheme 任务网络请求失败，请刷新确认任务状态后再重试' } });
      }).send();
  },

  /**
   * 重试 Scheme 任务
   */
  retrySchemeJob(batchId, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/pdc/nfc/scheme/retry/${batchId}`)
      .method('POST')
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        console.error('重试 Scheme 任务失败:', err);
        callback({ data: { code: -1, msg: '重试 Scheme 任务网络请求失败，请刷新确认任务状态后再重试' } });
      }).send();
  },

  /**
   * 取消 Scheme 任务
   */
  cancelSchemeJob(jobId, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/pdc/nfc/scheme/cancel/${jobId}`)
      .method('POST')
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        console.error('取消 Scheme 任务失败:', err);
        // 变更类 POST 不做自动重试；取消是状态机幂等转换，超时后由用户确认状态再操作
        callback({ data: { code: -1, msg: '取消 Scheme 任务网络请求失败，请刷新确认任务状态后再重试' } });
      }).send();
  },

  /**
   * 查询 Scheme 任务进度
   */
  schemeJobProgress(batchId, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/pdc/nfc/scheme/progress/${batchId}`)
      .method('GET')
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        console.error('获取 Scheme 任务进度失败:', err);
        RequestService.reAjaxFun(() => {
          this.schemeJobProgress(batchId, callback);
        });
      }).send();
  },

  // ==================== 写卡任务 ====================

  /**
   * 为指定批次创建写卡任务
   * @param mode 写卡模式：FACTORY_CSV（默认，工厂 CSV 通道）或 MANUAL（手动写卡，ADR 0003）
   */
  createWriteJob(batchId, mode, callback) {
    const query = mode ? `?mode=${encodeURIComponent(mode)}` : '';
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/pdc/nfc/write/create/${batchId}${query}`)
      .method('POST')
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        console.error('创建写卡任务失败:', err);
        // 创建类 POST 不做自动重试（后端原子状态翻转兜底，但避免产生孤儿任务）
        callback({ data: { code: -1, msg: '创建写卡任务网络请求失败，请刷新确认任务状态后再重试' } });
      }).send();
  },

  /**
   * 下载写卡任务数据
   */
  downloadWriteJob(jobId, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/pdc/nfc/write/download/${jobId}`)
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
      .url(`${getServiceUrl()}/pdc/nfc/write/${jobId}/import`)
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
      .url(`${getServiceUrl()}/pdc/nfc/write/cancel/${jobId}`)
      .method('POST')
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        console.error('取消写卡任务失败:', err);
        // 变更类 POST 不做自动重试；取消是状态机幂等转换，超时后由用户确认状态再操作
        callback({ data: { code: -1, msg: '取消写卡任务网络请求失败，请刷新确认任务状态后再重试' } });
      }).send();
  },

  /**
   * 查询写卡任务详情
   */
  getWriteJob(jobId, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/pdc/nfc/write/progress/${jobId}`)
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

  // ==================== 手动写卡模式（ADR 0003） ====================

  /**
   * 手动写卡任务内资产列表（不含 Scheme 明文）
   */
  getManualWriteAssets(jobId, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/pdc/nfc/write/manual/${jobId}/assets`)
      .method('GET')
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        console.error('获取手动写卡资产列表失败:', err);
        RequestService.reAjaxFun(() => {
          this.getManualWriteAssets(jobId, callback);
        });
      }).send();
  },

  /**
   * 单条解密查看 Scheme（每次调用后端记审计）
   */
  revealManualScheme(jobId, assetId, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/pdc/nfc/write/manual/${jobId}/assets/${assetId}/scheme`)
      .method('GET')
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        console.error('获取 Scheme 失败:', err);
        RequestService.reAjaxFun(() => {
          this.revealManualScheme(jobId, assetId, callback);
        });
      }).send();
  },

  /**
   * 手动写卡单资产标记：MARK_WRITTEN / MARK_WRITE_FAILED / MARK_VERIFIED / MARK_LOCKED
   */
  markManualAsset(jobId, assetId, action, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/pdc/nfc/write/manual/${jobId}/assets/${assetId}/mark`)
      .method('POST')
      .data({ action })
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        console.error('标记资产失败:', err);
        // 标记类 POST 不做自动重试，由用户确认状态后再操作
        callback({ data: { code: -1, msg: '标记网络请求失败，请刷新确认资产状态后再重试' } });
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
