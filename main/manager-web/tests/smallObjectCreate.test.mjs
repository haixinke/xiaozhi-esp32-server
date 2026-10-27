/* eslint-disable test/no-import-node-test -- zero-dependency source regression gate */
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { describe, it } from 'node:test'

const apiSource = await readFile(
  new URL('../src/apis/module/smallObject.js', import.meta.url),
  'utf8'
)
const viewSource = await readFile(
  new URL('../src/views/SmallObjectManagement.vue', import.meta.url),
  'utf8'
)

/** 截出 saveSmallObject 方法体，避免 uploadImage 的 .fail 干扰断言 */
function saveMethodSource() {
  const start = apiSource.indexOf('saveSmallObject(data, callback')
  assert.notEqual(start, -1, 'smallObject.js 应定义 saveSmallObject')
  const end = apiSource.indexOf('\n    },', start)
  return apiSource.slice(start, end === -1 ? undefined : end)
}

/** 截出 handleSave 中发起保存请求的那段代码（新增与编辑共用一个请求分发） */
function saveCallSiteSource() {
  const start = viewSource.indexOf('const request = this.isEditMode')
  assert.notEqual(start, -1, 'SmallObjectManagement.vue 的 handleSave 应按模式分发 save/update 请求')
  const end = viewSource.indexOf('\n    }', start)
  return viewSource.slice(start, end === -1 ? undefined : end)
}

describe('small object create - rejected save must not lock the dialog', () => {
  // 后端业务拒绝（类型已被占用/类型不在字典/故事超长）返回 HTTP 200 + code != 0：
  // httpRequest 的 httpHandlerError 走 failCallback 分支、不调用 success 回调。
  // 若 save 请求不注册 fail 回调，页面里的 saving 永远不会复位，
  // el-button 的 :loading 会让"保存"按钮一直禁用，运营在弹窗里改完也无法重试。
  it('saveSmallObject accepts and invokes a fail callback', () => {
    const source = saveMethodSource()
    assert.match(source, /saveSmallObject\(data,\s*callback,\s*failCallback\)/)
    assert.match(source, /\.fail\(/)
    assert.match(source, /failCallback\(/)
  })

  it('save call passes both success and fail callbacks', () => {
    const source = saveCallSiteSource()
    const callStart = source.indexOf('request.call(')
    assert.notEqual(callStart, -1, '保存应通过 request.call 分发到 save/update')
    const callbacks = source.slice(callStart).match(/\(\{\s*data\s*\}\)\s*=>\s*\{/g) || []
    assert.equal(callbacks.length, 2, '保存调用应同时传入 success 与 fail 两个回调')
  })

  it('fail callback restores saving state and surfaces the backend message', () => {
    const source = saveCallSiteSource()
    const firstCallback = source.indexOf('({ data }) => {')
    const secondCallback = source.indexOf('({ data }) => {', firstCallback + 1)
    assert.notEqual(secondCallback, -1, '保存调用应传第二个（失败）回调')
    const failSource = source.slice(secondCallback)
    assert.match(failSource, /this\.saving = false/)
    assert.match(failSource, /data\.msg/)
  })
})