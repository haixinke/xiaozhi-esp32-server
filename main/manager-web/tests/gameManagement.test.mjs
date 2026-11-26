/* eslint-disable test/no-import-node-test -- zero-dependency source regression gate */
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { describe, it } from 'node:test'

const read = (path) => readFile(new URL(path, import.meta.url), 'utf8')

const viewSource = await read('../src/views/GameManagement.vue')

describe('game management - category wiring (ticket 01)', () => {
  it('list shows the game category column with 内部游戏/外部游戏 labels', () => {
    // 表格列注册
    assert.match(viewSource, /prop:\s*'gameCategory'/)
    assert.match(viewSource, /label:\s*'游戏分类'/)
    // 分类显示名映射
    assert.match(viewSource, /INTERNAL:\s*'内部游戏'/)
    assert.match(viewSource, /EXTERNAL:\s*'外部游戏'/)
  })

  it('add dialog defaults to INTERNAL and form carries gameCategory/jumpUrl', () => {
    // emptyForm 默认内部游戏，jumpUrl 初始为空（锚定方法定义，避开 data 中的 this.emptyForm() 调用）
    const emptyFormStart = viewSource.indexOf('emptyForm() {')
    const emptyFormBlock = viewSource.slice(emptyFormStart, emptyFormStart + 400)
    assert.match(emptyFormBlock, /gameCategory:\s*'INTERNAL'/)
    assert.match(emptyFormBlock, /jumpUrl:\s*''/)
  })

  it('form has a category radio group and jump-url input hidden for internal games', () => {
    // 游戏分类单选
    assert.match(viewSource, /el-radio-group/)
    assert.match(viewSource, /v-model="editForm\.gameCategory"/)
    assert.match(viewSource, /内部游戏/)
    assert.match(viewSource, /外部游戏/)
    // 跳转链接仅外部游戏可见
    assert.match(viewSource, /v-if="editForm\.gameCategory === 'EXTERNAL'"/)
    assert.match(viewSource, /v-model="editForm\.jumpUrl"/)
  })

  it('edit dialog echoes the saved category and jump url', () => {
    const handleEditStart = viewSource.indexOf('handleEdit(row)')
    const handleEditBlock = viewSource.slice(handleEditStart, handleEditStart + 900)
    assert.match(handleEditBlock, /gameCategory:\s*d\.gameCategory/)
    assert.match(handleEditBlock, /jumpUrl:\s*d\.jumpUrl/)
  })

  it('save payload round-trips gameCategory and jumpUrl', () => {
    const handleSaveStart = viewSource.indexOf('handleSave()')
    const handleSaveBlock = viewSource.slice(handleSaveStart, handleSaveStart + 1200)
    assert.match(handleSaveBlock, /gameCategory:\s*this\.editForm\.gameCategory/)
    assert.match(handleSaveBlock, /jumpUrl:/)
  })

  it('toggle status payload includes category and jump url so they are not wiped', () => {
    const toggleStart = viewSource.indexOf('handleToggleStatus(row)')
    const toggleBlock = viewSource.slice(toggleStart, toggleStart + 700)
    // 启停走完整 update 载荷，缺这两个字段会把已保存分类/链接清掉
    assert.match(toggleBlock, /gameCategory:\s*row\.gameCategory/)
    assert.match(toggleBlock, /jumpUrl:\s*row\.jumpUrl/)
  })
})
