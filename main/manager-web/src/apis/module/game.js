import { getServiceUrl } from '../api';
import RequestService from '../httpRequest';

export default {
    // 分页查询游戏列表
    getGamePage(params, callback) {
        const queryParams = new URLSearchParams({
            gameType: params.gameType || '',
            page: params.page || 1,
            limit: params.limit || 10
        }).toString();

        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/game/page?${queryParams}`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('获取游戏列表失败:', err)
                RequestService.reAjaxFun(() => {
                    this.getGamePage(params, callback)
                })
            }).send()
    },

    // 查询游戏详情
    getGameDetail(id, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/game/${id}`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('获取游戏详情失败:', err)
                RequestService.reAjaxFun(() => {
                    this.getGameDetail(id, callback)
                })
            }).send()
    },

    // 新增游戏
    saveGame(data, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/game/save`)
            .method('POST')
            .data(data)
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('新增游戏失败:', err)
                RequestService.reAjaxFun(() => {
                    this.saveGame(data, callback)
                })
            }).send()
    },

    // 更新游戏
    updateGame(data, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/game/update`)
            .method('PUT')
            .data(data)
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('更新游戏失败:', err)
                RequestService.reAjaxFun(() => {
                    this.updateGame(data, callback)
                })
            }).send()
    },

    // 删除游戏
    deleteGame(id, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/game/delete/${id}`)
            .method('DELETE')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('删除游戏失败:', err)
                RequestService.reAjaxFun(() => {
                    this.deleteGame(id, callback)
                })
            }).send()
    },

    // 上传游戏图标（/upload/image 需要 Bearer 鉴权，走 RequestService 统一带 token）
    // scene 为后端 @RequestParam query 参数，必须拼在 URL 上；放 FormData 后端读不到
    uploadImage(formData, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/upload/image?scene=game-icon`)
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
                console.error('上传游戏图标失败:', err)
                if (failCallback) {
                    failCallback(err)
                }
            }).send()
    }
}
