import { getServiceUrl } from '../api';
import RequestService from '../httpRequest';

export default {
    // 分页查询题组列表（可按大类过滤），返回组名、排序与组内题数
    getGroupPage(params, callback) {
        const query = {
            page: params.page || 1,
            limit: params.limit || 10
        };
        if (params.categoryCode) {
            query.categoryCode = params.categoryCode;
        }
        const queryParams = new URLSearchParams(query).toString();

        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/listen/group/page?${queryParams}`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('获取题组列表失败:', err)
                RequestService.reAjaxFun(() => {
                    this.getGroupPage(params, callback)
                })
            }).send()
    },

    // 获取题组详情（含组内题目与解析后的 A-E 选项）
    getGroupDetail(id, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/listen/group/${id}`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('获取题组详情失败:', err)
                RequestService.reAjaxFun(() => {
                    this.getGroupDetail(id, callback)
                })
            }).send()
    }
}
