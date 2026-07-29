import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { canAccessRoute, readStoredUserInfo } from '../src/router/access.mjs'

describe('NFC route access', () => {
  it('denies unauthenticated access to requiresAuth routes', () => {
    const route = { meta: { requiresAuth: true, requiresSuperAdmin: true } }
    const result = canAccessRoute(route, null, { superAdmin: true })
    assert.equal(result.allowed, false)
    assert.equal(result.redirect, '/login')
  })

  it('denies unauthenticated access with empty token', () => {
    const route = { meta: { requiresAuth: true } }
    const result = canAccessRoute(route, '', { superAdmin: false })
    assert.equal(result.allowed, false)
    assert.equal(result.redirect, '/login')
  })

  it('denies non-superAdmin access to requiresSuperAdmin routes', () => {
    const route = { meta: { requiresAuth: true, requiresSuperAdmin: true } }
    const result = canAccessRoute(route, 'valid-token', { superAdmin: false })
    assert.equal(result.allowed, false)
    assert.equal(result.redirect, '/home')
  })

  it('allows superAdmin access to requiresSuperAdmin routes', () => {
    const route = { meta: { requiresAuth: true, requiresSuperAdmin: true } }
    const result = canAccessRoute(route, 'valid-token', { superAdmin: true })
    assert.equal(result.allowed, true)
    assert.equal(result.redirect, undefined)
  })

  it('allows authenticated non-superAdmin to access requiresAuth-only routes', () => {
    const route = { meta: { requiresAuth: true } }
    const result = canAccessRoute(route, 'valid-token', { superAdmin: false })
    assert.equal(result.allowed, true)
  })

  it('allows access to routes without meta', () => {
    const route = {}
    const result = canAccessRoute(route, null, null)
    assert.equal(result.allowed, true)
  })

  it('allows access to routes with meta but no auth requirements', () => {
    const route = { meta: { title: 'Some Page' } }
    const result = canAccessRoute(route, null, null)
    assert.equal(result.allowed, true)
  })

  it('handles missing userInfo gracefully for non-superAdmin routes', () => {
    const route = { meta: { requiresAuth: true } }
    const result = canAccessRoute(route, 'token', null)
    // null userInfo - superAdmin check is skipped because requiresSuperAdmin is falsy
    assert.equal(result.allowed, true)
  })

  it('handles undefined meta gracefully', () => {
    const route = { meta: undefined }
    const result = canAccessRoute(route, null, null)
    assert.equal(result.allowed, true)
  })
})

describe('readStoredUserInfo', () => {
  it('reads persisted user during refresh race', () => {
    const mockStorage = {
      getItem(key) {
        if (key === 'userInfo') {
          return JSON.stringify({ username: 'admin', superAdmin: true })
        }
        return null
      }
    }
    const userInfo = readStoredUserInfo(mockStorage)
    assert.deepEqual(userInfo, { username: 'admin', superAdmin: true })
  })

  it('returns null when userInfo is not stored', () => {
    const mockStorage = {
      getItem() {
        return null
      }
    }
    const userInfo = readStoredUserInfo(mockStorage)
    assert.equal(userInfo, null)
  })

  it('returns null when stored value is invalid JSON', () => {
    const mockStorage = {
      getItem() {
        return 'not valid json {{{'
      }
    }
    const userInfo = readStoredUserInfo(mockStorage)
    assert.equal(userInfo, null)
  })

  it('returns null when storage is undefined', () => {
    const userInfo = readStoredUserInfo(undefined)
    assert.equal(userInfo, null)
  })

  it('returns null when storage getItem throws', () => {
    const mockStorage = {
      getItem() {
        throw new Error('Storage error')
      }
    }
    const userInfo = readStoredUserInfo(mockStorage)
    assert.equal(userInfo, null)
  })
})
