import Vue from 'vue';
import VueI18n from 'vue-i18n';
import zhCN from './zh_CN';
import zhTW from './zh_TW';
import en from './en';
import de from './de';
import vi from './vi';
import ptBR from './pt_BR';

import enLocale from 'element-ui/lib/locale/lang/en'
import zhLocale from 'element-ui/lib/locale/lang/zh-CN'
import twLocale from 'element-ui/lib/locale/lang/zh-TW'
import deLocale from 'element-ui/lib/locale/lang/de'
import viLocale from 'element-ui/lib/locale/lang/vi'
import ptBRLocale from 'element-ui/lib/locale/lang/pt-br'


Vue.use(VueI18n);

// 从本地存储获取语言设置，如果没有则使用浏览器语言或默认语言
// 目前仅开放中文简体和繁体，其他语言（en/de/vi/pt_BR）暂时屏蔽，统一回退到 zh_CN
const getDefaultLanguage = () => {
  const savedLang = localStorage.getItem('userLanguage');
  if (savedLang === 'zh_CN' || savedLang === 'zh_TW') {
    return savedLang;
  }
  const browserLang = navigator.language || navigator.userLanguage;
  if (browserLang === 'zh-TW' || browserLang === 'zh-HK' || browserLang === 'zh-MO') {
    return 'zh_TW';
  }
  // if (browserLang.indexOf('de') === 0) {
  //   return 'de';
  // }
  // if (browserLang.indexOf('vi') === 0) {
  //   return 'vi';
  // }
  // if (browserLang === 'pt-BR' || browserLang === 'pt') {
  //   return 'pt_BR';
  // }
  // return 'en';
  return 'zh_CN';
};

const i18n = new VueI18n({
  locale: getDefaultLanguage(),
  fallbackLocale: 'zh_CN',
  messages: {
    'zh_CN': { ...zhLocale, ...zhCN },
    'zh_TW': { ...twLocale, ...zhTW },
    'en': { ...en, ...enLocale },
    'de': { ...de, ...deLocale },
    'vi': { ...vi, ...viLocale },
    'pt_BR': { ...ptBR, ...ptBRLocale }
  }
});

export default i18n;

// 提供一个方法来切换语言
export const changeLanguage = (lang) => {
  i18n.locale = lang;
  localStorage.setItem('userLanguage', lang);
  // 通知组件语言已更改
  Vue.prototype.$eventBus.$emit('languageChanged', lang);
};