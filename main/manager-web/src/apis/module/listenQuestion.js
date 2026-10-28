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
    },

    // 增量导入题库 JSON（multipart）。成功返回 {groupCount, questionCount}；
    // 判重冲突时 code=10373 且 data 为冲突明细列表，由调用方弹窗展示
    importBank(formData, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/listen/import`)
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
                console.error('导入题库失败:', err)
                if (failCallback) {
                    failCallback(err)
                }
            }).send()
    }
}
