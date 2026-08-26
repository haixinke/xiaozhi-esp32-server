/* eslint-disable test/no-import-node-test -- zero-dependency source regression gate */
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { describe, it } from 'node:test'

const batchDialogSource = await readFile(
  new URL('../src/components/nfc/NfcBatchDialog.vue', import.meta.url),
  'utf8'
)
const assetManagementSource = await readFile(
  new URL('../src/views/nfc/NfcAssetManagement.vue', import.meta.url),
  'utf8'
)
const dictSqlSource = await readFile(
  new URL('../../manager-api/src/main/resources/db/changelog/202608261000.sql', import.meta.url),
  'utf8'
)
const masterYamlSource = await readFile(
  new URL('../../manager-api/src/main/resources/db/changelog/db.changelog-master.yaml', import.meta.url),
  'utf8'
)

const DICT_TYPE = 'EGG_PET_PROTOTYPE'

describe('NFC batch dialog - batchNo field', () => {
  it('renders a batchNo form item bound to form.batchNo', () => {
    assert.match(batchDialogSource, /label="批次号"\s+prop="batchNo"/)
    assert.match(batchDialogSource, /v-model="form\.batchNo"/)
  })

  it('declares batchNo in form data and reset object', () => {
    // 初始 data 与 handleClose 的重置对象都必须含 batchNo，否则二次打开残留旧值
    const occurrences = batchDialogSource.match(/batchNo:\s*''/g) || []
    assert.equal(occurrences.length, 2, 'batchNo 应同时出现在 data.form 与 handleClose 重置对象中')
  })

  it('requires batchNo via validation rules with 64 char limit', () => {
    assert.match(batchDialogSource, /batchNo:\s*\[[\s\S]*?required:\s*true[\s\S]*?\]/)
    assert.match(batchDialogSource, /max:\s*64,\s*message:\s*'批次号长度不能超过 64 个字符'/)
  })
})

describe('NFC prototype options - dictionary driven', () => {
  it('no longer hardcodes pinyin prototype values', () => {
    // 后端 PdcNfcPrototype.isValid 与 PetServiceImpl.requireValidPrototype 只接受中文值，
    // 任何拼音字面量都会导致创建批次被拒或筛选恒为空
    for (const [name, source] of [
      ['NfcBatchDialog.vue', batchDialogSource],
      ['NfcAssetManagement.vue', assetManagementSource]
    ]) {
      assert.doesNotMatch(source, /JINLI/, `${name} 不应残留 JINLI`)
      assert.doesNotMatch(source, /YUTU/, `${name} 不应残留 YUTU`)
    }
  })

  it('loads prototype options from the dictionary API in both files', () => {
    for (const [name, source] of [
      ['NfcBatchDialog.vue', batchDialogSource],
      ['NfcAssetManagement.vue', assetManagementSource]
    ]) {
      assert.match(source, new RegExp(`getDictDataByType\\('${DICT_TYPE}'\\)`), `${name} 应调用字典接口`)
      assert.match(source, /v-for="item in prototypeOptions"/, `${name} 应遍历 prototypeOptions`)
      assert.match(source, /:value="item\.key"/, `${name} 应以 item.key 作为提交值`)
      assert.match(source, /prototypeOptions:\s*\[\]/, `${name} 应声明 prototypeOptions`)
    }
  })

  it('triggers prototype fetch on dialog open and page load', () => {
    assert.match(batchDialogSource, /this\.fetchPrototypes\(\)/)
    assert.match(assetManagementSource, /created\(\)\s*\{[\s\S]*?this\.fetchPrototypes\(\)/)
  })

  it('falls back to an empty list instead of stale hardcoded values', () => {
    // 生产域刻意不做本地兜底：下拉为空可暴露异常，硬编码兜底可能产出错误资产
    for (const source of [batchDialogSource, assetManagementSource]) {
      assert.match(source, /\.catch\(\(\)\s*=>\s*\{[\s\S]*?prototypeOptions\s*=\s*\[\]/)
    }
  })
})

describe('EGG_PET_PROTOTYPE dictionary seed', () => {
  it('seeds dict type 105 with the expected dict_type code', () => {
    assert.match(dictSqlSource, /\(105,\s*'EGG_PET_PROTOTYPE'/)
    assert.match(dictSqlSource, /delete from `sys_dict_type` where `id` = 105;/)
    assert.match(dictSqlSource, /delete from `sys_dict_data` where `dict_type_id` = 105;/)
  })

  it('stores Chinese dict_value matching backend validation', () => {
    // dict_value 必须是中文，与 PdcNfcPrototype.code 一致
    assert.match(dictSqlSource, /\(105001,\s*105,\s*'锦鲤',\s*'锦鲤'/)
    assert.match(dictSqlSource, /\(105002,\s*105,\s*'玉兔',\s*'玉兔'/)
    assert.doesNotMatch(dictSqlSource, /'JINLI'|'YUTU'/)
  })

  it('is registered in the liquibase master changelog', () => {
    assert.match(masterYamlSource, /id:\s*202608261000/)
    assert.match(masterYamlSource, /path:\s*classpath:db\/changelog\/202608261000\.sql/)
  })
})
