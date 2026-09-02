// 智控台页面批量截图脚本（软著操作手册素材）
// 用法: node capture_screenshots.mjs
// 流程: 弹出 Chromium 窗口打开登录页 -> 用户手动登录 -> 自动遍历路由截图
// 输出: main/official-docs/screenshots/*.png
import { chromium } from 'playwright'
import { mkdirSync, existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const OUT_DIR = join(dirname(fileURLToPath(import.meta.url)), '../../screenshots/01-智控台')
// 登录态缓存：首次手动登录后保存，重跑免登录
const AUTH_FILE = join(dirname(fileURLToPath(import.meta.url)), '.auth.json')
const BASE = 'http://localhost:8001'

// 路由与截图文件名（跳过登录/注册/找回密码等无业务内容页）
const PAGES = [
  ['/home', '01-首页'],
  ['/agent-template-management', '02-智能体模板'],
  ['/role-config', '03-角色配置'],
  ['/voice-print', '04-声纹管理'],
  ['/voice-clone-management', '05-音色克隆'],
  ['/voice-resource-management', '06-音色资源'],
  ['/device-management', '07-设备管理'],
  ['/user-management', '08-用户管理'],
  ['/model-config', '09-模型配置'],
  ['/provider-management', '10-供应商管理'],
  ['/params-management', '11-参数管理'],
  ['/dict-management', '12-字典管理'],
  ['/knowledge-base-management', '13-知识库管理'],
  ['/feature-management', '14-功能配置'],
  ['/replacement-word-management', '15-替换词管理'],
  ['/address-book-management', '16-通讯录管理'],
  ['/pdc-nfc/product-types', '17-NFC商品类型'],
  ['/pdc-nfc/batches', '18-NFC批次管理'],
  ['/pdc-nfc/scheme', '19-NFC-Scheme任务'],
  ['/pdc-nfc/write', '20-NFC写卡任务'],
  ['/pdc-nfc/assets', '21-NFC资产管理'],
  ['/pdc-nfc/activation', '22-NFC扫码激活'],
  ['/pdc-nfc/audit', '23-NFC审计日志'],
  ['/story-engine-management', '24-故事引擎'],
  ['/feedback-management', '25-反馈管理'],
  ['/server-side-management', '26-服务端管理'],
  ['/ota-management', '27-OTA管理'],
]

mkdirSync(OUT_DIR, { recursive: true })

const browser = await chromium.launch({ headless: false })
const context = await browser.newContext({
  viewport: { width: 1440, height: 900 },
  storageState: existsSync(AUTH_FILE) ? AUTH_FILE : undefined,
})
const page = await context.newPage()

await page.goto(BASE + '/#/home', { waitUntil: 'networkidle' })

// 无缓存登录态时等用户手动登录（跳到 /home）
if (!page.url().includes('/home')) {
  console.log('请在弹出的浏览器窗口中登录智控台...')
  await page.waitForURL(/\/home/, { timeout: 5 * 60 * 1000 })
  await context.storageState({ path: AUTH_FILE })
  console.log('登录态已缓存到 .auth.json')
}
console.log('开始截图')

for (const [route, name] of PAGES) {
  try {
    await page.goto(BASE + '/#' + route, { waitUntil: 'networkidle', timeout: 20000 })
    await page.waitForTimeout(1500)
    await page.screenshot({ path: join(OUT_DIR, name + '.png'), fullPage: false })
    console.log('OK', name)
  } catch (e) {
    console.log('FAIL', name, e.message.split('\n')[0])
  }
}

await browser.close()
console.log('完成，输出目录:', OUT_DIR)
