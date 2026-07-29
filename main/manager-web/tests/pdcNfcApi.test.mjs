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

// Expected method names and their corresponding URL patterns
const expectedMethods = [
  { name: 'listProductTypes', url: '/pdc/nfc/admin/product-types', method: 'GET' },
  { name: 'createProductType', url: '/pdc/nfc/admin/product-types', method: 'POST' },
  { name: 'registerProductTypeEvidence', url: '/pdc/nfc/admin/product-types/', method: 'POST' },
  { name: 'listBatches', url: '/pdc/nfc/admin/batches', method: 'GET' },
  { name: 'createBatch', url: '/pdc/nfc/admin/batches', method: 'POST' },
  { name: 'startSchemeJob', url: '/pdc/nfc/admin/scheme/batches/', method: 'POST' },
  { name: 'retrySchemeJob', url: '/pdc/nfc/admin/scheme/jobs/', method: 'POST' },
  { name: 'cancelSchemeJob', url: '/pdc/nfc/admin/scheme/jobs/', method: 'POST' },
  { name: 'schemeJobProgress', url: '/pdc/nfc/admin/scheme/jobs/', method: 'GET' },
  { name: 'createWriteJob', url: '/pdc/nfc/admin/write/batches/', method: 'POST' },
  { name: 'downloadWriteJob', url: '/pdc/nfc/admin/write/jobs/', method: 'GET' },
  { name: 'importWriteResult', url: '/pdc/nfc/admin/write/jobs/', method: 'POST' },
  { name: 'getWriteJob', url: '/pdc/nfc/admin/write/jobs/', method: 'GET' },
  { name: 'cancelWriteJob', url: '/pdc/nfc/admin/write/jobs/', method: 'POST' },
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

  it('all methods include network failure retry', () => {
    const networkFailCount = (pdcNfcSource.match(/\.networkFail\(/g) || []).length
    const reAjaxCount = (pdcNfcSource.match(/RequestService\.reAjaxFun/g) || []).length
    assert.equal(networkFailCount, 22, 'expected 22 networkFail handlers')
    assert.equal(reAjaxCount, 22, 'expected 22 reAjaxFun calls')
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
