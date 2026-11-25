import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import { canAccessRoute } from '../src/router/access.mjs'

async function loadApi(response) {
  const source = await readFile(new URL('../src/apis/module/enterpriseInvite.js', import.meta.url), 'utf8')
  const calls = []
  const requestService = {
    sendRequest() {
      const call = {}
      const builder = {
        url: (value) => { call.url = value; return builder },
        method: (value) => { call.method = value; return builder },
        data: (value) => { call.data = value; return builder },
        type: (value) => { call.type = value; return builder },
        success: (handler) => { call.success = handler; return builder },
        fail: (handler) => { call.fail = handler; return builder },
        networkFail: (handler) => { call.networkFail = handler; return builder },
        send: () => {
          calls.push(call)
          const handler = response.handler || 'success'
          call[handler](response.result)
        }
      }
      return builder
    }
  }
  const load = new Function('getServiceUrl', 'RequestService',
    source.replace(/^import .*$/gm, '').replace('export default', 'return'))
  return { api: load(() => 'https://console.invalid/xiaozhi', requestService), calls }
}

test('enterprise list sends enterprise scope, pagination, and inactive status', async () => {
  const page = { list: [{ id: '345678901234567890', name: '活动' }], total: 1 }
  const { api, calls } = await loadApi({ result: { data: { code: 0, data: page } } })

  assert.deepEqual(await api.list({ page: 2, limit: 20, status: 0 }), page)
  assert.equal(calls[0].method, 'GET')
  const url = new URL(calls[0].url)
  assert.equal(url.pathname, '/xiaozhi/invite/page')
  assert.deepEqual(Object.fromEntries(url.searchParams), { type: '2', page: '2', limit: '20', status: '0' })
})

test('enterprise creation and updates send expiry and preserve large IDs', async () => {
  const { api, calls } = await loadApi({ result: { data: { code: 0 } } })
  const invite = { name: '活动', quota: 30, expireTime: '2026-11-26 22:00:00', remark: '第二批' }
  const id = '345678901234567890'

  await api.create(invite)
  await api.update({ id, name: '活动名称', expireTime: null })
  await api.update({ id, status: 0 })

  assert.deepEqual(calls.map(({ url, method, data }) => [new URL(url).pathname, method, data]), [
    ['/xiaozhi/invite', 'POST', invite],
    ['/xiaozhi/invite', 'PUT', { id, name: '活动名称', expireTime: null }],
    ['/xiaozhi/invite', 'PUT', { id, status: 0 }]
  ])
})

test('enterprise usage requests the selected code and record page', async () => {
  const page = { list: [{ id: '6', inviteeUserId: '88' }], total: 1 }
  const { api, calls } = await loadApi({ result: { data: { code: 0, data: page } } })

  assert.deepEqual(await api.usage('345678901234567890', { page: 3, limit: 20 }), page)
  assert.equal(calls[0].method, 'GET')
  const url = new URL(calls[0].url)
  assert.equal(url.pathname, '/xiaozhi/invite/345678901234567890/usage')
  assert.deepEqual(Object.fromEntries(url.searchParams), { page: '3', limit: '20' })
})

test('enterprise QR retrieves a PNG blob and refuses JSON error responses', async () => {
  const png = new Blob([Uint8Array.from([137, 80, 78, 71, 13, 10, 26, 10, 0])], { type: 'image/png' })
  const { api, calls } = await loadApi({ result: { data: png } })

  assert.equal(await api.downloadQrCode('345678901234567890'), png)
  assert.equal(calls[0].type, 'blob')
  assert.equal(calls[0].method, 'GET')
  assert.equal(new URL(calls[0].url).pathname, '/xiaozhi/invite/345678901234567890/qrcode')

  const errorBody = new Blob(['{"code":403,"msg":"Forbidden"}'], { type: 'application/json' })
  const { api: failedApi } = await loadApi({ result: { data: errorBody } })
  await assert.rejects(failedApi.downloadQrCode('345678901234567890'))
})

test('a persisted session with missing role information cannot open enterprise management', async () => {
  const source = await readFile(new URL('../src/router/index.js', import.meta.url), 'utf8')
  function Router({ routes }) { this.routes = routes }
  Router.prototype.beforeEach = () => {}
  const loadRouter = new Function('Vue', 'VueRouter', source.replace(/^import .*$/gm, '')
    .replace('export default router', 'return router'))
  const router = loadRouter({ use() {} }, Router)
  const enterpriseRoute = router.routes.find(({ path }) => path === '/enterprise-invite-management')

  assert.deepEqual(canAccessRoute(enterpriseRoute, null, null), { allowed: false, redirect: '/login' })
  assert.deepEqual(canAccessRoute(enterpriseRoute, 'session', null), { allowed: false, redirect: '/home' })
  assert.equal(canAccessRoute(enterpriseRoute, 'session', { superAdmin: true, role: 'operator' }).allowed, true)
})

test('enterprise table has a separate fixed-height footer in the page layout', async () => {
  const source = await readFile(new URL('../src/views/EnterpriseInviteManagement.vue', import.meta.url), 'utf8')

  assert.match(source, /<el-footer>\s*<VersionFooter\s*\/>\s*<\/el-footer>/)
})
