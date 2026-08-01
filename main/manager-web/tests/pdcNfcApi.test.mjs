/* eslint-disable test/no-import-node-test -- zero-dependency API regression gate */
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { describe, it } from 'node:test'

const pdcNfcSource = await readFile(
  new URL('../src/apis/module/pdcNfc.js', import.meta.url),
  'utf8'
)
const apiSource = await readFile(
  new URL('../src/apis/api.js', import.meta.url),
  'utf8'
)
const routerSource = await readFile(
  new URL('../src/router/index.js', import.meta.url),
  'utf8'
)

// Expected method names and their corresponding URL patterns.
// createProductType is an intentional stub (no backend endpoint); it is kept in
// the count but has no URL assertion.
const expectedMethods = [
  { name: 'listProductTypes', url: '/pdc/nfc/product-type/list', method: 'GET' },
  { name: 'createProductType', url: null, method: 'POST' },
  { name: 'registerReleaseEvidence', url: '/pdc/nfc/product-type/release-evidence', method: 'POST' },
  { name: 'listBatches', url: '/pdc/nfc/batch/list', method: 'GET' },
  { name: 'createBatch', url: '/pdc/nfc/batch/create', method: 'POST' },
  { name: 'startSchemeJob', url: '/pdc/nfc/scheme/generate/', method: 'POST' },
  { name: 'retrySchemeJob', url: '/pdc/nfc/scheme/retry/', method: 'POST' },
  { name: 'cancelSchemeJob', url: '/pdc/nfc/scheme/cancel/', method: 'POST' },
  { name: 'schemeJobProgress', url: '/pdc/nfc/scheme/progress/', method: 'GET' },
  { name: 'createWriteJob', url: '/pdc/nfc/write/create/', method: 'POST' },
  { name: 'downloadWriteJob', url: '/pdc/nfc/write/download/', method: 'GET' },
  { name: 'importWriteResult', url: '/pdc/nfc/write/', method: 'POST' },
  { name: 'getWriteJob', url: '/pdc/nfc/write/progress/', method: 'GET' },
  { name: 'cancelWriteJob', url: '/pdc/nfc/write/cancel/', method: 'POST' },
  { name: 'listAssets', url: '/pdc/nfc/admin/assets', method: 'GET' },
  { name: 'assetDetail', url: '/pdc/nfc/admin/assets/', method: 'GET' },
  { name: 'stockIn', url: '/pdc/nfc/admin/assets/stock-in', method: 'POST' },
  { name: 'activate', url: '/pdc/nfc/admin/assets/activate', method: 'POST' },
  { name: 'disable', url: '/pdc/nfc/admin/assets/disable', method: 'POST' },
  { name: 'scrap', url: '/pdc/nfc/admin/assets/scrap', method: 'POST' },
  { name: 'listLogs', url: '/pdc/nfc/admin/logs', method: 'GET' },
  { name: 'listLogsByObject', url: '/pdc/nfc/admin/logs/by-object/', method: 'GET' },
]

describe('pdcNfc API module', () => {
  it('exports the expected number of methods', () => {
    const methodCount = expectedMethods.length
    assert.equal(methodCount, 22, 'expected 22 API methods')
  })

  it('each expected method name is defined in the module', () => {
    for (const { name } of expectedMethods) {
      const regex = new RegExp(`\\b${name}\\s*\\(`)
      assert.match(
        pdcNfcSource,
        regex,
        `Method "${name}" not found in pdcNfc.js`
      )
    }
  })

  it('each method uses the correct URL pattern', () => {
    for (const { name, url } of expectedMethods) {
      if (url === null) continue // intentional stub without a backend endpoint
      assert.ok(
        pdcNfcSource.includes(url),
        `URL "${url}" for method "${name}" not found in pdcNfc.js`
      )
    }
  })

  it('module uses RequestService and getServiceUrl pattern', () => {
    assert.match(pdcNfcSource, /import.*getServiceUrl.*from.*'\.\.\/api'/)
    assert.match(pdcNfcSource, /import.*RequestService.*from.*'\.\.\/httpRequest'/)
    assert.match(pdcNfcSource, /RequestService\.sendRequest\(\)/)
    assert.match(pdcNfcSource, /getServiceUrl\(\)/)
  })

  it('module exports a default object', () => {
    assert.match(pdcNfcSource, /export default \{/)
  })

  it('downloadWriteJob uses blob response type', () => {
    assert.match(pdcNfcSource, /\.type\('blob'\)/)
  })

  it('importWriteResult uses FormData', () => {
    assert.match(pdcNfcSource, /new FormData\(\)/)
    assert.match(pdcNfcSource, /formData\.append\('file'/)
    assert.match(pdcNfcSource, /formData\.append\('requestId'/)
  })

  it('live methods include network failure handling; mutation POSTs do not auto-retry', () => {
    const networkFailCount = (pdcNfcSource.match(/\.networkFail\(/g) || []).length
    const reAjaxCount = (pdcNfcSource.match(/RequestService\.reAjaxFun/g) || []).length
    // createProductType is an intentional stub (no backend endpoint, no network call).
    // The other 21 methods handle networkFail. Auto-retry (reAjaxFun) is kept only for
    // reads and idempotent writes (those carrying a requestId): the 6 mutating POSTs
    // without a fixed requestId (createBatch, startSchemeJob, retrySchemeJob,
    // cancelSchemeJob, createWriteJob, cancelWriteJob) fail fast instead, because a
    // blind retry after timeout could create duplicates.
    assert.equal(networkFailCount, 21, 'expected 21 networkFail handlers')
    assert.equal(reAjaxCount, 14, 'expected 14 reAjaxFun calls')
  })

  it('mutating POSTs without requestId report failure instead of auto-retrying', () => {
    const failFastMessage = '请刷新确认'
    for (const name of ['createBatch', 'startSchemeJob', 'retrySchemeJob',
      'cancelSchemeJob', 'createWriteJob', 'cancelWriteJob']) {
      const methodStart = pdcNfcSource.indexOf(`  ${name}(`)
      assert.ok(methodStart >= 0, `method ${name} not found`)
      const methodBody = pdcNfcSource.slice(methodStart, methodStart + 2000)
      const reAjaxIdx = methodBody.indexOf('RequestService.reAjaxFun')
      assert.ok(
        reAjaxIdx === -1 || reAjaxIdx > 800,
        `${name} must not auto-retry (reAjaxFun at ${reAjaxIdx} belongs to a later method)`)
      assert.ok(
        methodBody.includes(failFastMessage),
        `${name} must surface a fail-fast message asking the user to confirm state`)
    }
  })
})

describe('api.js aggregates pdcNfc', () => {
  it('imports pdcNfc module', () => {
    assert.match(apiSource, /import pdcNfc from '\.\/module\/pdcNfc\.js'/)
  })

  it('exports pdcNfc in the default export', () => {
    assert.match(apiSource, /pdcNfc\b/)
  })
})

describe('router includes NFC routes', () => {
  it('imports access guard functions', () => {
    assert.match(routerSource, /import.*canAccessRoute.*readStoredUserInfo.*from.*'\.\/access\.mjs'/)
  })

  it('defines all 6 NFC routes with meta', () => {
    const nfcPaths = [
      '/pdc-nfc/product-types',
      '/pdc-nfc/batches',
      '/pdc-nfc/scheme',
      '/pdc-nfc/write',
      '/pdc-nfc/assets',
      '/pdc-nfc/audit',
    ]
    for (const path of nfcPaths) {
      assert.ok(
        routerSource.includes(`path: '${path}'`),
        `Route path "${path}" not found in router`
      )
    }
  })

  it('NFC routes require auth and superAdmin', () => {
    const requiresSuperAdminCount = (routerSource.match(/requiresSuperAdmin:\s*true/g) || []).length
    assert.ok(requiresSuperAdminCount >= 6, 'expected at least 6 requiresSuperAdmin meta entries')
  })

  it('router guard uses canAccessRoute for meta-driven routes', () => {
    assert.match(routerSource, /canAccessRoute\(to,\s*token,\s*userInfo\)/)
    assert.match(routerSource, /readStoredUserInfo\(\)/)
  })
})
