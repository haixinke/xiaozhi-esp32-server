import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { emptyScannerState, acceptScan, removeScan, clearScanner } from '../src/utils/pdcNfcState.mjs'

describe('scanner', () => {
  it('trims, de-duplicates and caps at 500', () => {
    let state = emptyScannerState()
    state = acceptScan(state, ' A001\n')
    state = acceptScan(state, 'A001')
    assert.deepEqual(state.codes, ['A001'])
  })

  it('caps at maxCodes', () => {
    let state = emptyScannerState()
    for (let i = 0; i < 501; i++) {
      state = acceptScan(state, `CODE_${i}`)
    }
    assert.equal(state.codes.length, 500)
  })

  it('removes codes', () => {
    let state = { codes: ['A', 'B', 'C'], maxCodes: 500 }
    state = removeScan(state, 'B')
    assert.deepEqual(state.codes, ['A', 'C'])
  })

  it('clears all codes', () => {
    let state = { codes: ['A', 'B', 'C'], maxCodes: 500 }
    state = clearScanner(state)
    assert.deepEqual(state.codes, [])
    assert.equal(state.maxCodes, 500)
  })

  it('ignores empty and whitespace-only input', () => {
    let state = emptyScannerState()
    state = acceptScan(state, '')
    state = acceptScan(state, '   ')
    state = acceptScan(state, null)
    state = acceptScan(state, undefined)
    assert.deepEqual(state.codes, [])
  })

  it('preserves insertion order', () => {
    let state = emptyScannerState()
    state = acceptScan(state, 'C003')
    state = acceptScan(state, 'C001')
    state = acceptScan(state, 'C002')
    assert.deepEqual(state.codes, ['C003', 'C001', 'C002'])
  })

  it('removeScan on non-existent code is a no-op', () => {
    let state = { codes: ['A', 'B'], maxCodes: 500 }
    state = removeScan(state, 'Z')
    assert.deepEqual(state.codes, ['A', 'B'])
  })

  it('emptyScannerState returns fresh object each call', () => {
    const s1 = emptyScannerState()
    const s2 = emptyScannerState()
    assert.notEqual(s1, s2)
    assert.deepEqual(s1, s2)
  })

  it('acceptScan does not mutate original state', () => {
    const state = emptyScannerState()
    const newState = acceptScan(state, 'X001')
    assert.deepEqual(state.codes, [])
    assert.deepEqual(newState.codes, ['X001'])
  })
})
