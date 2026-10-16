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
    }
}
