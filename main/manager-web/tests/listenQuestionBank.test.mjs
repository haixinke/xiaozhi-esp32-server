/* eslint-disable test/no-import-node-test -- zero-dependency source regression gate */
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { describe, it } from 'node:test'

const read = (path) => readFile(new URL(path, import.meta.url), 'utf8')

const apiIndexSource = await read('../src/apis/api.js')
const routerSource = await read('../src/router/index.js')
const headerBarSource = await read('../src/components/HeaderBar.vue')

describe('listen question bank - wiring', () => {
  it('registers the listenQuestion api module in apis/api.js', () => {
    assert.match(apiIndexSource, /import\s+listenQuestion\s+from\s+'\.\/module\/listenQuestion\.js'/)
    // import 之外的第二处出现即在默认导出对象上注册
    assert.match(apiIndexSource, /^\s+listenQuestion$/m)
  })

  it('listenQuestion module exposes group page and detail endpoints', async () => {
    const moduleSource = await read('../src/apis/module/listenQuestion.js')
    assert.match(moduleSource, /\/admin\/listen\/group\/page/)
    assert.match(moduleSource, /\/admin\/listen\/group\/\$\{id\}/)
  })

  it('listenQuestion module exposes the import endpoint as multipart upload', async () => {
    const moduleSource = await read('../src/apis/module/listenQuestion.js')
    assert.match(moduleSource, /\/admin\/listen\/import/)
    // multipart：FormData + header({}) 让浏览器生成 boundary（同 smallObject.uploadImage 先例）
    assert.match(moduleSource, /FormData/)
    assert.match(moduleSource, /\.header\(\{\}\)/)
  })

  it('question bank page wires the import upload with success stats and conflict dialog', async () => {
    const viewSource = await read('../src/views/ListenQuestionBank.vue')
    // el-upload 自定义上传 + 导入按钮
    assert.match(viewSource, /el-upload/)
    assert.match(viewSource, /:http-request="uploadQuestionBank"/)
    assert.match(viewSource, /导入题库/)
    // 成功提示写入统计（N 组 M 题）
    assert.match(viewSource, /groupCount/)
    assert.match(viewSource, /questionCount/)
    // 失败弹窗展示冲突明细
    assert.match(viewSource, /conflictVisible/)
    assert.match(viewSource, /conflictList/)
  })

  it('registers the question bank route with requiresAuth', () => {
    assert.match(routerSource, /path:\s*'\/listen-question-bank'/)
    const routeStart = routerSource.indexOf("path: '/listen-question-bank'")
    // 路由块内含 component 函数的闭包，不能用首个 '},' 截断，取固定窗口覆盖到 meta
    const routeBlock = routerSource.slice(routeStart, routeStart + 400)
    assert.match(routeBlock, /requiresAuth:\s*true/)
    assert.match(routeBlock, /ListenQuestionBank\.vue/)
  })

  it('adds the question bank dropdown item to the operations menu', () => {
    assert.match(headerBarSource, /handleRouter\('listenQuestionBank'\)/)
    assert.match(headerBarSource, /题库运营/)
    assert.match(headerBarSource, /listenQuestionBank:\s*['"]\/listen-question-bank['"]/)
  })

  it('includes the route in both active-tab OR chains', () => {
    const chains = headerBarSource.match(/\$route\.path === '\/listen-question-bank'/g) || []
    assert.equal(chains.length, 2, 'class 绑定与图标 filter 两处 OR 链都要包含题库运营路由')
  })
})
