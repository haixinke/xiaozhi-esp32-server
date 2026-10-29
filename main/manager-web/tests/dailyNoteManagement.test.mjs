/* eslint-disable test/no-import-node-test -- zero-dependency source regression gate */
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { describe, it } from 'node:test'

const read = (path) => readFile(new URL(path, import.meta.url), 'utf8')

const apiIndexSource = await read('../src/apis/api.js')
const routerSource = await read('../src/router/index.js')
const headerBarSource = await read('../src/components/HeaderBar.vue')

describe('daily note management - wiring', () => {
  it('registers the dailyNote api module in apis/api.js', () => {
    assert.match(apiIndexSource, /import\s+dailyNote\s+from\s+'\.\/module\/dailyNote\.js'/)
    // import 之外的第二处出现即在默认导出对象上注册（末行带逗号，与 listenQuestion 先例一致）
    assert.match(apiIndexSource, /^\s+dailyNote\s*,/m)
  })

  it('dailyNote module exposes page, detail, create, update and delete endpoints', async () => {
    const moduleSource = await read('../src/apis/module/dailyNote.js')
    assert.match(moduleSource, /\/admin\/daily-note\/page/)
    assert.match(moduleSource, /\/admin\/daily-note\/\$\{id\}/)
    assert.match(moduleSource, /method\('GET'\)/)
    assert.match(moduleSource, /method\('POST'\)/)
    assert.match(moduleSource, /method\('PUT'\)/)
    assert.match(moduleSource, /method\('DELETE'\)/)
    assert.match(moduleSource, /getPage/)
    assert.match(moduleSource, /getDetail/)
    assert.match(moduleSource, /create/)
    assert.match(moduleSource, /update/)
    assert.match(moduleSource, /remove/)
  })

  it('page wires list, date filter, shared create/edit form and physical-delete confirmation', async () => {
    const viewSource = await read('../src/views/DailyNoteManagement.vue')
    // 日期精确过滤
    assert.match(viewSource, /handleDateChange/)
    assert.match(viewSource, /value-format="yyyy-MM-dd"/)
    // 新增/编辑共用表单
    assert.match(viewSource, /handleCreate\(/)
    assert.match(viewSource, /handleEdit\(/)
    assert.match(viewSource, /handleSave\(/)
    assert.match(viewSource, /Api\.dailyNote\.create\(/)
    assert.match(viewSource, /Api\.dailyNote\.update\(/)
    assert.match(viewSource, /Api\.dailyNote\.getPage\(/)
    assert.match(viewSource, /Api\.dailyNote\.remove\(/)
    // 删除确认展示日期、标题并明示物理删除不可恢复
    assert.match(viewSource, /handleDelete\(/)
    assert.match(viewSource, /物理删除，不可恢复/)
    assert.match(viewSource, /row\.noteDate/)
    assert.match(viewSource, /row\.title/)
  })

  it('registers the daily note route with requiresAuth', () => {
    assert.match(routerSource, /path:\s*'\/daily-note-management'/)
    const routeStart = routerSource.indexOf("path: '/daily-note-management'")
    // 路由块内含 component 函数的闭包，不能用首个 '},' 截断，取固定窗口覆盖到 meta
    const routeBlock = routerSource.slice(routeStart, routeStart + 400)
    assert.match(routeBlock, /requiresAuth:\s*true/)
    assert.match(routeBlock, /DailyNoteManagement\.vue/)
  })

  it('adds the daily note dropdown item to the operations menu', () => {
    assert.match(headerBarSource, /handleRouter\('dailyNoteManagement'\)/)
    assert.match(headerBarSource, /小纸条管理/)
    assert.match(headerBarSource, /dailyNoteManagement:\s*['"]\/daily-note-management['"]/)
  })

  it('includes the route in both active-tab OR chains', () => {
    const chains = headerBarSource.match(/\$route\.path === '\/daily-note-management'/g) || []
    assert.equal(chains.length, 2, 'class 绑定与图标 filter 两处 OR 链都要包含小纸条管理路由')
  })
})
