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

/** 截出 deleteSmallObject 方法体 */
function deleteMethodSource() {
  const start = apiSource.indexOf('deleteSmallObject(id, callback')
  assert.notEqual(start, -1, 'smallObject.js 应定义 deleteSmallObject')
  const end = apiSource.indexOf('\n    },', start)
  return apiSource.slice(start, end === -1 ? undefined : end)
}

/** 截出 handleDelete 方法体 */
function deleteCallSiteSource() {
  const start = viewSource.indexOf('handleDelete(row) {')
  assert.notEqual(start, -1, 'SmallObjectManagement.vue 应定义 handleDelete')
  const end = viewSource.indexOf('\n    },', start)
  return viewSource.slice(start, end === -1 ? undefined : end)
}

describe('small object delete - rejected delete must surface the error', () => {
  // 后端业务拒绝（记录已被他人删除/不存在）返回 HTTP 200 + code != 0：
  // httpRequest 的 httpHandlerError 走 failCallback 分支、不调用 success 回调。
  // 若 delete 请求不注册 fail 回调，页面在 success 里的 else 分支永远不会执行，
  // 运营点删除后会毫无反馈。
  it('deleteSmallObject accepts and invokes a fail callback', () => {
    const source = deleteMethodSource()
    assert.match(source, /deleteSmallObject\(id,\s*callback,\s*failCallback\)/)
    assert.match(source, /\.fail\(/)
    assert.match(source, /failCallback\(/)
  })

  it('delete call passes both success and fail callbacks', () => {
    // 截到 .catch 之前：只统计 deleteSmallObject 调用本身的回调，
    // 末尾 confirm 的 catch(() => {}) 不应计入
    const source = deleteCallSiteSource()
    const callStart = source.indexOf('deleteSmallObject(')
    assert.notEqual(callStart, -1, '删除应调用 deleteSmallObject')
    const callSource = source.slice(callStart, source.indexOf('.catch'))
    const callbacks = callSource.match(/\(\{\s*data\s*\}\)\s*=>\s*\{|\(\)\s*=>\s*\{/g) || []
    assert.equal(callbacks.length, 2, '删除调用应同时传入 success 与 fail 两个回调')
  })

  it('success callback only handles code === 0, failure surfaces data.msg', () => {
    const source = deleteCallSiteSource()
    const firstCallback = source.indexOf('deleteSmallObject(')
    const secondCallback = source.indexOf('({ data }) => {', firstCallback + 1)
    assert.notEqual(secondCallback, -1, '删除调用应传第二个（失败）回调')
    const failSource = source.slice(secondCallback)
    assert.match(failSource, /data\.msg/)
    assert.match(failSource, /删除失败/)
  })
})