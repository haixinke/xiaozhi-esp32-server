import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { describe, it } from 'node:test'
import vm from 'node:vm'
import {
  buildReleaseEvidencePayload,
  buildReleaseEvidenceViewModel
} from '../src/utils/pdcNfcReleaseEvidence.mjs'

const pdcNfcSource = await readFile(
  new URL('../src/apis/module/pdcNfc.js', import.meta.url),
  'utf8'
)

const loadPdcNfcApi = () => {
  let requestBody
  let networkFailureHandler
  let retryCount = 0
  const request = {
    url: () => request,
    method: () => request,
    data: (body) => {
      requestBody = body
      return request
    },
    success: () => request,
    networkFail: (handler) => {
      networkFailureHandler = handler
      return request
    },
    send: () => request
  }
  const executableSource = pdcNfcSource
    .replace(/^import .*$/gm, '')
    .replace('export default {', 'module.exports = {')
  const context = {
    console,
    module: { exports: {} },
    RequestService: {
      sendRequest: () => request,
      clearRequestTime: () => {},
      reAjaxFun: () => {
        retryCount += 1
      }
    },
    getServiceUrl: () => '/api',
    buildReleaseEvidencePayload
  }
  vm.runInNewContext(executableSource, context)
  return {
    api: context.module.exports,
    requestBody: () => requestBody,
    triggerNetworkFailure: (error) => networkFailureHandler(error),
    retryCount: () => retryCount
  }
}

describe('release evidence request payload', () => {
  it('contains exactly the fixed three-field release evidence contract', () => {
    const payload = buildReleaseEvidencePayload({
      releaseVersion: '1.2.3',
      publishedAt: '2026-07-30T10:00:00+08:00',
      smokeEvidence: 'smoke-passed',
      productTypeId: 100,
      evidenceType: 'QUALITY_AUDIT'
    })

    assert.deepEqual(payload, {
      releaseVersion: '1.2.3',
      publishedAt: '2026-07-30T10:00:00+08:00',
      smokeEvidence: 'smoke-passed'
    })
  })

  it('sends the fixed payload without a product type identifier', () => {
    const { api, requestBody } = loadPdcNfcApi()

    api.registerReleaseEvidence({
      releaseVersion: '1.2.3',
      publishedAt: '2026-07-30T10:00:00+08:00',
      smokeEvidence: 'smoke-passed'
    }, () => {})

    assert.deepEqual(requestBody(), {
      releaseVersion: '1.2.3',
      publishedAt: '2026-07-30T10:00:00+08:00',
      smokeEvidence: 'smoke-passed'
    })
  })

  it('reports a network failure without replaying the mutation', () => {
    const { api, retryCount, triggerNetworkFailure } = loadPdcNfcApi()
    let response

    api.registerReleaseEvidence({
      releaseVersion: '1.2.3',
      publishedAt: '2026-07-30T10:00:00+08:00',
      smokeEvidence: 'smoke-passed'
    }, (result) => {
      response = result
    })
    triggerNetworkFailure(new Error('offline'))

    assert.equal(retryCount(), 0)
    assert.equal(response.data.code, -1)
  })
})

describe('release evidence display', () => {
  it('preserves all three evidence fields for review', () => {
    assert.deepEqual(buildReleaseEvidenceViewModel({
      releaseVersion: '1.2.3',
      publishedAt: '2026-07-30T10:00:00+08:00',
      smokeEvidence: 'smoke-passed-on-device-42'
    }), {
      releaseVersion: '1.2.3',
      publishedAt: '2026-07-30T10:00:00+08:00',
      smokeEvidence: 'smoke-passed-on-device-42'
    })
  })
})
