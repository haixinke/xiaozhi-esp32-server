import { getServiceUrl } from '../api';
import RequestService from '../httpRequest';

export default {
    // 分页查询小物件列表
    getSmallObjectPage(params, callback) {
        const queryParams = new URLSearchParams({
            page: params.page || 1,
            limit: params.limit || 10
        }).toString();

        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/small-object/page?${queryParams}`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('获取小物件列表失败:', err)
                RequestService.reAjaxFun(() => {
                    this.getSmallObjectPage(params, callback)
                })
            }).send()
    },

    // 查询可选物件类型（含 used 占用标记），供新增弹窗的类型下拉使用
    getObjectTypes(callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/small-object/object-types`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('获取物件类型失败:', err)
                RequestService.reAjaxFun(() => {
                    this.getObjectTypes(callback)
                })
            }).send()
    },

    // 新增小物件
    // 必须注册 fail 回调：后端业务拒绝（类型已被占用/类型不存在/故事超长）返回 HTTP 200 + code != 0，
    // httpRequest 走 fail 分支而不调用 success，页面需要据此复位保存按钮，否则按钮会一直处于 loading 禁用态
    saveSmallObject(data, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/small-object/save`)
            .method('POST')
            .data(data)
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .fail((res) => {
                RequestService.clearRequestTime()
                if (failCallback) {
                    failCallback(res)
                }
            })
            .networkFail((err) => {
                console.error('新增小物件失败:', err)
                RequestService.reAjaxFun(() => {
                    this.saveSmallObject(data, callback, failCallback)
                })
            }).send()
    },

    // 更新小物件（全量更新类型、图片、故事）
    // 与 save 一样必须注册 fail 回调复位保存按钮，理由同上
    updateSmallObject(data, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/small-object/update`)
            .method('PUT')
            .data(data)
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .fail((res) => {
                RequestService.clearRequestTime()
                if (failCallback) {
                    failCallback(res)
                }
            })
            .networkFail((err) => {
                console.error('更新小物件失败:', err)
                RequestService.reAjaxFun(() => {
                    this.updateSmallObject(data, callback, failCallback)
                })
            }).send()
    },

    // 启用/停用：只提交 id 与目标状态，不回传图片与故事，
    // 避免并发下用陈旧副本覆盖他人刚改过的故事内容
    updateSmallObjectStatus(data, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/small-object/update-status`)
            .method('PUT')
            .data(data)
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .fail((res) => {
                RequestService.clearRequestTime()
                if (failCallback) {
                    failCallback(res)
                }
            })
            .networkFail((err) => {
                console.error('切换小物件状态失败:', err)
                RequestService.reAjaxFun(() => {
                    this.updateSmallObjectStatus(data, callback, failCallback)
                })
            }).send()
    },

    // 删除小物件（软删，删除后列表与详情均按不存在处理）
    // 必须注册 fail 回调：后端业务拒绝（记录已被他人删除/不存在）返回 HTTP 200 + code != 0，
    // httpRequest 走 fail 分支而不调用 success，页面需要据此提示失败而不是静默无反应
    deleteSmallObject(id, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/small-object/delete/${id}`)
            .method('DELETE')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .fail((res) => {
                RequestService.clearRequestTime()
                if (failCallback) {
                    failCallback(res)
                }
            })
            .networkFail((err) => {
                console.error('删除小物件失败:', err)
                RequestService.reAjaxFun(() => {
                    this.deleteSmallObject(id, callback, failCallback)
                })
            }).send()
    },

    // 上传物件图片（/upload/image 需要 Bearer 鉴权，走 RequestService 统一带 token）
    // scene 为后端 @RequestParam query 参数，必须拼在 URL 上；放 FormData 后端读不到
    uploadImage(formData, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/upload/image?scene=small-object`)
            .method('POST')
            .data(formData)
            // header() 会整体替换默认 header（含 json content-type）；
            // 传空对象让 flyio 检测到 FormData 后自动删掉 Content-Type，由浏览器生成 multipart boundary
            .header({})
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .fail((res) => {
                RequestService.clearRequestTime()
                if (failCallback) {
                    failCallback(res)
                }
            })
            .networkFail((err) => {
                console.error('上传物件图片失败:', err)
                if (failCallback) {
                    failCallback(err)
                }
            }).send()
    }
}