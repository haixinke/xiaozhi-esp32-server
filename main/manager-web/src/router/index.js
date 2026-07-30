import Vue from 'vue'
import VueRouter from 'vue-router'
import { canAccessRoute, readStoredUserInfo } from './access.mjs'

Vue.use(VueRouter)

const routes = [
  {
    path: '/',
    name: 'welcome',
    component: function () {
      return import('../views/login.vue')
    }
  },
  {
    path: '/role-config',
    name: 'RoleConfig',
    component: function () {
      return import('../views/roleConfig.vue')
    }
  },
  {
    path: '/voice-print',
    name: 'VoicePrint',
    component: function () {
      return import('../views/VoicePrint.vue')
    }
  },
  {
    path: '/login',
    name: 'login',
    component: function () {
      return import('../views/login.vue')
    }
  },
  {
    path: '/home',
    name: 'home',
    component: function () {
      return import('../views/home.vue')
    }
  },
  {
    path: '/register',
    name: 'Register',
    component: function () {
      return import('../views/register.vue')
    }
  },
  {
    path: '/retrieve-password',
    name: 'RetrievePassword',
    component: function () {
      return import('../views/retrievePassword.vue')
    }
  },
  // 设备管理页面路由
  {
    path: '/device-management',
    name: 'DeviceManagement',
    component: function () {
      return import('../views/DeviceManagement.vue')
    }
  },
  // 添加用户管理路由
  {
    path: '/user-management',
    name: 'UserManagement',
    component: function () {
      return import('../views/UserManagement.vue')
    }
  },
  {
    path: '/model-config',
    name: 'ModelConfig',
    component: function () {
      return import('../views/ModelConfig.vue')
    }
  },
  {
    path: '/params-management',
    name: 'ParamsManagement',
    component: function () {
      return import('../views/ParamsManagement.vue')
    },
    meta: {
      requiresAuth: true,
      title: '参数管理'
    }
  },
  {
    path: '/knowledge-base-management',
    name: 'KnowledgeBaseManagement',
    component: function () {
      return import('../views/KnowledgeBaseManagement.vue')
    },
    meta: {
      requiresAuth: true,
      title: '知识库管理'
    }
  },
  {
    path: '/server-side-management',
    name: 'ServerSideManager',
    component: function () {
      return import('../views/ServerSideManager.vue')
    },
    meta: {
      requiresAuth: true,
      title: '服务端管理'
    }
  },
  {
    path: '/ota-management',
    name: 'OtaManagement',
    component: function () {
      return import('../views/OtaManagement.vue')
    },
    meta: {
      requiresAuth: true,
      title: 'OTA管理'
    }
  },
  {
    path: '/voice-resource-management',
    name: 'VoiceResourceManagement',
    component: function () {
      return import('../views/VoiceResourceManagement.vue')
    },
    meta: {
      requiresAuth: true,
      title: '音色资源开通'
    }
  },
  {
    path: '/voice-clone-management',
    name: 'VoiceCloneManagement',
    component: function () {
      return import('../views/VoiceCloneManagement.vue')
    },
    meta: {
      requiresAuth: true,
      title: '音色克隆管理'
    }
  },
  {
    path: '/dict-management',
    name: 'DictManagement',
    component: function () {
      return import('../views/DictManagement.vue')
    }
  },
  {
    path: '/provider-management',
    name: 'ProviderManagement',
    component: function () {
      return import('../views/ProviderManagement.vue')
    }
  },
  // 添加默认角色管理路由
  {
    path: '/agent-template-management',
    name: 'AgentTemplateManagement',
    component: function () {
      return import('../views/AgentTemplateManagement.vue')
    }
  },
  // 添加模板快速配置路由
  {
    path: '/template-quick-config',
    name: 'TemplateQuickConfig',
    component: function () {
      return import('../views/TemplateQuickConfig.vue')
    }
  },
  // 功能配置页面路由
  {
    path: '/feature-management',
    name: 'FeatureManagement',
    component: function () {
      return import('../views/FeatureManagement.vue')
    },
    meta: {
      requiresAuth: true,
      title: '功能配置'
    }
  },
  // 替换词管理
  {
    path: '/replacement-word-management',
    name: 'ReplacementWordManagement',
    component: function () {
      return import('../views/ReplacementWordManagement.vue')
    },
    meta: {
      requiresAuth: true,
      title: '替换词管理'
    }
  },
  // 通讯录管理页面路由
  {
    path: '/address-book-management',
    name: 'AddressBookManagement',
    component: function () {
      return import('../views/AddressBookManagement.vue')
    },
    meta: {
      requiresAuth: true,
      title: '通讯录管理'
    }
  },
  // ==================== NFC 生产管理路由 ====================
  {
    path: '/pdc-nfc/product-types',
    name: 'NfcProductTypes',
    component: function () {
      return import('../views/nfc/NfcProductTypeManagement.vue')
    },
    meta: {
      requiresAuth: true,
      requiresSuperAdmin: true,
      title: 'NFC 商品类型'
    }
  },
  {
    path: '/pdc-nfc/batches',
    name: 'NfcBatches',
    component: function () {
      return import('../views/nfc/NfcBatchManagement.vue')
    },
    meta: {
      requiresAuth: true,
      requiresSuperAdmin: true,
      title: 'NFC 批次管理'
    }
  },
  {
    path: '/pdc-nfc/scheme',
    name: 'NfcScheme',
    component: function () {
      return import('../views/NfcPlaceholder.vue')
    },
    meta: {
      requiresAuth: true,
      requiresSuperAdmin: true,
      title: 'NFC Scheme 任务'
    }
  },
  {
    path: '/pdc-nfc/write',
    name: 'NfcWrite',
    component: function () {
      return import('../views/NfcPlaceholder.vue')
    },
    meta: {
      requiresAuth: true,
      requiresSuperAdmin: true,
      title: 'NFC 写卡任务'
    }
  },
  {
    path: '/pdc-nfc/assets',
    name: 'NfcAssets',
    component: function () {
      return import('../views/nfc/NfcAssetManagement.vue')
    },
    meta: {
      requiresAuth: true,
      requiresSuperAdmin: true,
      title: 'NFC 资产管理'
    }
  },
  {
    path: '/pdc-nfc/activation',
    name: 'NfcActivation',
    component: function () {
      return import('../views/nfc/NfcActivationManagement.vue')
    },
    meta: {
      requiresAuth: true,
      requiresSuperAdmin: true,
      title: 'NFC 扫码激活'
    }
  },
  {
    path: '/pdc-nfc/audit',
    name: 'NfcAudit',
    component: function () {
      return import('../views/nfc/NfcOperationLogManagement.vue')
    },
    meta: {
      requiresAuth: true,
      requiresSuperAdmin: true,
      title: 'NFC 审计日志'
    }
  },
]
const router = new VueRouter({
  base: process.env.VUE_APP_PUBLIC_PATH || '/',
  routes
})

// 全局处理重复导航，改为刷新页面
const originalPush = VueRouter.prototype.push
VueRouter.prototype.push = function push(location) {
  return originalPush.call(this, location).catch(err => {
    if (err.name === 'NavigationDuplicated') {
      // 如果是重复导航，刷新页面
      window.location.reload()
    } else {
      // 其他错误正常抛出
      throw err
    }
  })
}

// 需要登录才能访问的路由
const protectedRoutes = ['home', 'RoleConfig', 'DeviceManagement', 'UserManagement', 'ModelConfig', 'KnowledgeBaseManagement', 'KnowledgeFileUpload', 'AddressBookManagement']

// 路由守卫
router.beforeEach((to, from, next) => {
  // 检查是否是需要保护的路由
  if (protectedRoutes.includes(to.name)) {
    // 从localStorage获取token
    const token = localStorage.getItem('token')
    if (!token) {
      // 未登录，跳转到登录页
      next({ name: 'login', query: { redirect: to.fullPath } })
      return
    }
  }

  // NFC 路由使用 meta 驱动的访问控制
  if (to.meta && (to.meta.requiresAuth || to.meta.requiresSuperAdmin)) {
    const token = localStorage.getItem('token')
    // 优先使用 Vuex 中的 userInfo，刷新竞态时回退到 localStorage
    const vuexUserInfo = router.app && router.app.$store ? router.app.$store.state.userInfo : null
    const userInfo = vuexUserInfo || readStoredUserInfo()
    const result = canAccessRoute(to, token, userInfo)
    if (!result.allowed) {
      next({ path: result.redirect, query: result.redirect === '/login' ? { redirect: to.fullPath } : {} })
      return
    }
  }

  next()
})

export default router
