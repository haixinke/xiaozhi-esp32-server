import { getServiceUrl } from '../api';
import RequestService from '../httpRequest';

export default {
    // 分页查询小纸条列表（默认日期倒序），可按完整日期精确过滤
    getPage(params, callback) {
        const query = {
            page: params.page || 1,
            limit: params.limit || 10
        };
        if (params.noteDate) {
            query.noteDate = params.noteDate;
        }
        const queryParams = new URLSearchParams(query).toString();

        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/daily-note/page?${queryParams}`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('获取小纸条列表失败:', err)
                RequestService.reAjaxFun(() => {
                    this.getPage(params, callback)
                })
            }).send()
    },

    // 获取小纸条详情（含完整正文）
    getDetail(id, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/daily-note/${id}`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('获取小纸条详情失败:', err)
                RequestService.reAjaxFun(() => {
                    this.getDetail(id, callback)
                })
            }).send()
    },

    // 新增小纸条（目标日期已存在时后端返回 10386）
    create(payload, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/daily-note`)
            .method('POST')
            .data(payload)
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
                console.error('新增小纸条失败:', err)
                RequestService.reAjaxFun(() => {
                    this.create(payload, callback, failCallback)
                })
            }).send()
    },

    // 编辑小纸条（编辑自身日期不算冲突，改为其他已有日期返回 10386）
    update(id, payload, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/daily-note/${id}`)
            .method('PUT')
            .data(payload)
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
                console.error('保存小纸条失败:', err)
                RequestService.reAjaxFun(() => {
                    this.update(id, payload, callback, failCallback)
                })
            }).send()
    },

    // 物理删除小纸条（不可恢复）
    remove(id, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/daily-note/${id}`)
            .method('DELETE')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('删除小纸条失败:', err)
                RequestService.reAjaxFun(() => {
                    this.remove(id, callback)
                })
            }).send()
    },

    // JSON 批量导入小纸条（multipart）。成功返回 {addedCount, overwrittenCount}；
    // 条目级校验失败时 code=10394 且 data 为逐行错误明细列表 [{row, field, reason}]
    importNotes(formData, callback, failCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/daily-note/import`)
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
                console.error('导入小纸条失败:', err)
                if (failCallback) {
                    failCallback(err)
                }
            }).send()
    }
}
