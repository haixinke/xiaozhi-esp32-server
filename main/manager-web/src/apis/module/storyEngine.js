import { getServiceUrl } from '../api';
import RequestService from '../httpRequest';


export default {
    // ==================== 大场景 ====================
    // 大场景列表
    getBigSceneList(callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/storyEngine/bigScene/list`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('获取大场景列表失败:', err)
                RequestService.reAjaxFun(() => {
                    this.getBigSceneList(callback)
                })
            }).send()
    },
    // 新增大场景
    saveBigScene(data, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/storyEngine/bigScene`)
            .method('POST')
            .data(data)
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .fail((err) => {
                RequestService.clearRequestTime()
                if (failCallback) {
                    failCallback(err)
                }
            })
            .networkFail((err) => {
                console.error('新增大场景失败:', err)
                RequestService.reAjaxFun(() => {
                    this.saveBigScene(data, callback, failCallback)
                })
            }).send()
    },
    // 修改大场景
    updateBigScene(data, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/storyEngine/bigScene`)
            .method('PUT')
            .data(data)
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .fail((err) => {
                RequestService.clearRequestTime()
                if (failCallback) {
                    failCallback(err)
                }
            })
            .networkFail((err) => {
                console.error('修改大场景失败:', err)
                RequestService.reAjaxFun(() => {
                    this.updateBigScene(data, callback, failCallback)
                })
            }).send()
    },
    // 删除大场景
    deleteBigScene(id, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/storyEngine/bigScene/${id}`)
            .method('DELETE')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .fail((err) => {
                RequestService.clearRequestTime()
                if (failCallback) {
                    failCallback(err)
                }
            })
            .networkFail((err) => {
                console.error('删除大场景失败:', err)
                RequestService.reAjaxFun(() => {
                    this.deleteBigScene(id, callback, failCallback)
                })
            }).send()
    },

    // ==================== 小场景 ====================
    // 小场景列表
    getSmallSceneList(bigSceneId, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/storyEngine/smallScene/list?bigSceneId=${bigSceneId}`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('获取小场景列表失败:', err)
                RequestService.reAjaxFun(() => {
                    this.getSmallSceneList(bigSceneId, callback)
                })
            }).send()
    },
    // 新增小场景
    saveSmallScene(data, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/storyEngine/smallScene`)
            .method('POST')
            .data(data)
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .fail((err) => {
                RequestService.clearRequestTime()
                if (failCallback) {
                    failCallback(err)
                }
            })
            .networkFail((err) => {
                console.error('新增小场景失败:', err)
                RequestService.reAjaxFun(() => {
                    this.saveSmallScene(data, callback, failCallback)
                })
            }).send()
    },
    // 修改小场景
    updateSmallScene(data, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/storyEngine/smallScene`)
            .method('PUT')
            .data(data)
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .fail((err) => {
                RequestService.clearRequestTime()
                if (failCallback) {
                    failCallback(err)
                }
            })
            .networkFail((err) => {
                console.error('修改小场景失败:', err)
                RequestService.reAjaxFun(() => {
                    this.updateSmallScene(data, callback, failCallback)
                })
            }).send()
    },
    // 批量调整小场景权重
    batchUpdateWeights(data, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/storyEngine/smallScene/batchWeights`)
            .method('PUT')
            .data(data)
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .fail((err) => {
                RequestService.clearRequestTime()
                if (failCallback) {
                    failCallback(err)
                }
            })
            .networkFail((err) => {
                console.error('批量调整权重失败:', err)
                RequestService.reAjaxFun(() => {
                    this.batchUpdateWeights(data, callback, failCallback)
                })
            }).send()
    },
    // 删除小场景
    deleteSmallScene(id, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/storyEngine/smallScene/${id}`)
            .method('DELETE')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .fail((err) => {
                RequestService.clearRequestTime()
                if (failCallback) {
                    failCallback(err)
                }
            })
            .networkFail((err) => {
                console.error('删除小场景失败:', err)
                RequestService.reAjaxFun(() => {
                    this.deleteSmallScene(id, callback, failCallback)
                })
            }).send()
    },
    // 权重汇总
    getWeightSummary(bigSceneId, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/storyEngine/smallScene/weightSummary?bigSceneId=${bigSceneId}`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('获取权重汇总失败:', err)
                RequestService.reAjaxFun(() => {
                    this.getWeightSummary(bigSceneId, callback)
                })
            }).send()
    },

    // ==================== 动作 ====================
    // 动作列表
    getActionList(smallSceneId, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/storyEngine/action/list?smallSceneId=${smallSceneId}`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('获取动作列表失败:', err)
                RequestService.reAjaxFun(() => {
                    this.getActionList(smallSceneId, callback)
                })
            }).send()
    },
    // 新增动作
    saveAction(data, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/storyEngine/action`)
            .method('POST')
            .data(data)
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .fail((err) => {
                RequestService.clearRequestTime()
                if (failCallback) {
                    failCallback(err)
                }
            })
            .networkFail((err) => {
                console.error('新增动作失败:', err)
                RequestService.reAjaxFun(() => {
                    this.saveAction(data, callback, failCallback)
                })
            }).send()
    },
    // 修改动作
    updateAction(data, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/storyEngine/action`)
            .method('PUT')
            .data(data)
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .fail((err) => {
                RequestService.clearRequestTime()
                if (failCallback) {
                    failCallback(err)
                }
            })
            .networkFail((err) => {
                console.error('修改动作失败:', err)
                RequestService.reAjaxFun(() => {
                    this.updateAction(data, callback, failCallback)
                })
            }).send()
    },
    // 删除动作
    deleteAction(id, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/storyEngine/action/${id}`)
            .method('DELETE')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .fail((err) => {
                RequestService.clearRequestTime()
                if (failCallback) {
                    failCallback(err)
                }
            })
            .networkFail((err) => {
                console.error('删除动作失败:', err)
                RequestService.reAjaxFun(() => {
                    this.deleteAction(id, callback, failCallback)
                })
            }).send()
    },

    // ==================== 动作图片 ====================
    // 上传动作图片，formData 需包含 file、petPrototype、timeOfDay、captions
    uploadActionImage(actionId, formData, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/storyEngine/action/${actionId}/image`)
            .method('POST')
            .data(formData)
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .fail((err) => {
                RequestService.clearRequestTime()
                if (failCallback) {
                    failCallback(err)
                }
            })
            .networkFail((err) => {
                console.error('上传动作图片失败:', err)
                RequestService.reAjaxFun(() => {
                    this.uploadActionImage(actionId, formData, callback, failCallback)
                })
            }).send()
    },
    // 修改动作图片配文
    updateActionImage(data, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/storyEngine/actionImage`)
            .method('PUT')
            .data(data)
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .fail((err) => {
                RequestService.clearRequestTime()
                if (failCallback) {
                    failCallback(err)
                }
            })
            .networkFail((err) => {
                console.error('修改动作图片配文失败:', err)
                RequestService.reAjaxFun(() => {
                    this.updateActionImage(data, callback, failCallback)
                })
            }).send()
    },
    // 删除动作图片
    deleteActionImage(id, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/storyEngine/actionImage/${id}`)
            .method('DELETE')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .fail((err) => {
                RequestService.clearRequestTime()
                if (failCallback) {
                    failCallback(err)
                }
            })
            .networkFail((err) => {
                console.error('删除动作图片失败:', err)
                RequestService.reAjaxFun(() => {
                    this.deleteActionImage(id, callback, failCallback)
                })
            }).send()
    }

}
