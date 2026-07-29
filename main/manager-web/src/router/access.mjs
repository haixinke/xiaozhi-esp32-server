/**
 * 路由访问控制工具
 *
 * canAccessRoute 是一个纯函数，根据路由 meta 信息判断当前用户是否有权访问。
 * readStoredUserInfo 从 localStorage 读取持久化的用户信息，解决页面刷新竞态。
 */

/**
 * 判断路由是否可访问
 * @param {Object} route - vue-router 的 route 对象
 * @param {string|null} token - 当前登录 token
 * @param {Object|null} userInfo - 当前用户信息
 * @returns {{ allowed: boolean, redirect?: string }}
 */
export function canAccessRoute(route, token, userInfo) {
  if (route.meta && route.meta.requiresAuth && !token) {
    return { allowed: false, redirect: '/login' };
  }
  if (route.meta && route.meta.requiresSuperAdmin && !userInfo.superAdmin) {
    return { allowed: false, redirect: '/home' };
  }
  return { allowed: true };
}

/**
 * 从 localStorage 读取持久化的 userInfo
 * 用于页面刷新时 Vuex 尚未恢复的场景
 * @param {Storage} [storage] - 可选的 storage 对象，便于测试注入
 * @returns {Object|null}
 */
export function readStoredUserInfo(storage) {
  const store = storage || (typeof globalThis !== 'undefined' ? globalThis.localStorage : undefined);
  try {
    if (!store) return null;
    const raw = store.getItem('userInfo');
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}
