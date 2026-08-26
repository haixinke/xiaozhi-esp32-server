import { getServiceUrl } from '../api';
import RequestService from '../httpRequest';

export default {
    // 分页查询用户反馈（status/type 可选筛选，page/limit 分页）
    getFeedbackPage(params, callback) {
        const queryParams = new URLSearchParams({
            status: params.status === '' || params.status === undefined ? '' : params.status,
            type: params.type || '',
            page: params.page || 1,
            limit: params.limit || 10
        }).toString();

        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/feedback/page?${queryParams}`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('获取用户反馈列表失败:', err)
                RequestService.reAjaxFun(() => {
                    this.getFeedbackPage(params, callback)
                })
            }).send()
    },

    // 查询反馈详情
    getFeedbackDetail(id, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/feedback/${id}`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('获取反馈详情失败:', err)
                RequestService.reAjaxFun(() => {
                    this.getFeedbackDetail(id, callback)
                })
            }).send()
    },

    // 处理反馈：更新状态与备注，data = { id, status, remark }
    handleFeedback(data, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/feedback/update`)
            .method('PUT')
            .data(data)
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('处理反馈失败:', err)
                RequestService.reAjaxFun(() => {
                    this.handleFeedback(data, callback)
                })
            }).send()
    }
}
