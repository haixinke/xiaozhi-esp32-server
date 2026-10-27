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
    }
}