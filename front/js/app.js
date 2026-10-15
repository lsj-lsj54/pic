/**
 * 应用入口：登录态管理 + 视图路由
 */
import { userApi, setNotLoginHandler } from './api.js';
import { state, $, $$, toast, escapeHtml, renderSkeleton } from './ui.js';

/** 路由表，hash 的 path 对应一个懒加载视图 */
const routes = [
  { path: '/', view: () => import('./views/gallery.js') },
  { path: '/login', view: () => import('./views/auth.js'), layout: 'blank' },
  { path: '/mine', view: () => import('./views/mine.js'), auth: true },
  { path: '/space', view: () => import('./views/space.js'), auth: true },
  { path: '/space/detail', view: () => import('./views/spaceDetail.js'), auth: true },
  { path: '/analyze', view: () => import('./views/analyze.js'), auth: true },
  { path: '/admin/picture', view: () => import('./views/adminPicture.js'), admin: true },
  { path: '/admin/user', view: () => import('./views/adminUser.js'), admin: true },
];

/** 当前视图实例，切换路由前先调用它的 destroy */
let currentView = null;

/** 因未登录被拦截的地址，登录成功后跳回 */
let pendingPath = null;

/* ------------------------------ 路由 ------------------------------ */

/** 解析 hash，例如 #/mine?a=1 => { path: '/mine', query: { a: '1' } } */
function parseHash() {
  const raw = window.location.hash.replace(/^#/, '') || '/';
  const [path, search = ''] = raw.split('?');
  const query = {};
  new URLSearchParams(search).forEach((value, key) => {
    query[key] = value;
  });
  return { path: path || '/', query };
}

/** 匹配路由规则 */
function matchRoute(path) {
  return routes.find((item) => item.path === path) || null;
}

/**
 * 编程式跳转
 *
 * @param {string} path  路由路径
 * @param {object} query 查询参数
 */
export function navigate(path, query = {}) {
  const search = new URLSearchParams(query).toString();
  window.location.hash = search ? `${path}?${search}` : path;
}

/** 渲染页面骨架，blank 布局只有内容区（登录页用） */
function renderShell(layout) {
  const app = $('#app');
  if (layout === 'blank') {
    app.innerHTML = '<main id="view"></main>';
    return;
  }
  const adminNav = state.isAdmin()
    ? `<a href="#/admin/picture" data-nav="/admin/picture">图片管理</a>
       <a href="#/admin/user" data-nav="/admin/user">用户管理</a>`
    : '';
  app.innerHTML = `
    <header class="header">
      <div class="header-inner">
        <a class="logo" href="#/">
          <span class="logo-mark">图</span>
          <span>云图库</span>
        </a>
        <nav class="nav">
          <a href="#/" data-nav="/">图片浏览</a>
          <a href="#/mine" data-nav="/mine">我的图片</a>
          <a href="#/space" data-nav="/space">我的空间</a>
          <a href="#/analyze" data-nav="/analyze">空间分析</a>
          ${adminNav}
        </nav>
        <div class="user-area">
          ${state.isLogin()
    ? `<span class="user-name">${escapeHtml(state.getCurrentUser().userName
      || state.getCurrentUser().userAccount)}</span>
              ${state.isAdmin() ? '<span class="tag tag-admin">管理员</span>' : ''}
              <button class="btn btn-sm" id="logout-btn" type="button">退出</button>`
    : '<button class="btn btn-sm btn-primary" id="login-btn" type="button">登录 / 注册</button>'}
        </div>
      </div>
    </header>
    <main id="view" class="container"></main>`;
  bindShellEvents();
}

/** 绑定头部事件并高亮当前菜单 */
function bindShellEvents() {
  const { path } = parseHash();
  $$('[data-nav]').forEach((link) => {
    const target = link.dataset.nav;
    // 详情页也要让「我的空间」保持高亮，所以子路径用前缀匹配
    const active = target === '/' ? path === '/' : path.startsWith(target);
    link.classList.toggle('active', active);
  });

  const loginBtn = $('#login-btn');
  if (loginBtn) {
    loginBtn.addEventListener('click', () => navigate('/login'));
  }
  const logoutBtn = $('#logout-btn');
  if (logoutBtn) {
    logoutBtn.addEventListener('click', async () => {
      try {
        await userApi.logout();
      } catch (e) {
        // 退出接口失败也照样清理本地登录态
      }
      state.setCurrentUser(null);
      pendingPath = null;
      toast('已退出登录', 'success');
      navigate('/login');
    });
  }
}

/** 路由分发 */
async function resolveRoute() {
  const { path, query } = parseHash();
  const route = matchRoute(path);

  // 未知路径统一回到首页
  if (!route) {
    navigate('/');
    return;
  }
  // 权限判断
  if (route.auth && !state.isLogin()) {
    pendingPath = route.path;
    toast('请先登录', 'info');
    navigate('/login');
    return;
  }
  if (route.admin && !state.isAdmin()) {
    toast('需要管理员权限', 'error');
    navigate('/');
    return;
  }

  if (currentView && typeof currentView.destroy === 'function') {
    currentView.destroy();
  }
  currentView = null;

  renderShell(route.layout);
  const viewEl = $('#view');
  // 路由切换时先铺一层骨架屏，避免「白屏 → 内容突然出现」的割裂感
  viewEl.innerHTML = renderSkeleton(6);

  const module = await route.view();
  currentView = module.default;
  currentView.render(viewEl, query);
}

/* ------------------------------ 登录态 ------------------------------ */

/** 拉取当前登录用户，未登录时静默处理 */
async function loadLoginUser() {
  try {
    const user = await userApi.getLoginUser();
    state.setCurrentUser(user);
  } catch (e) {
    state.setCurrentUser(null);
  }
}

/**
 * 登录成功后跳转
 * <p>
 * 这里必须做两件事：
 * 1. 刷新登录态。登录接口的返回只落在 auth.js 的局部变量里，不写回全局状态的话，
 *    头部会一直显示「登录 / 注册」按钮，state.isAdmin() 也始终是 false；
 * 2. 真正改变 hash 触发路由。若只是重新执行当前路由，而此刻 hash 仍是 #/login，
 *    结果就是把登录面板又渲染一遍，用户会觉得「登录了但没跳走」。
 */
export async function onLoginSuccess() {
  const target = pendingPath;
  pendingPath = null;
  // 重新拉一次当前用户，保证全局状态与头部渲染都是最新的
  await loadLoginUser();
  navigate(target || (state.isAdmin() ? '/admin/picture' : '/'));
}

/* ------------------------------ 全局登录拦截 ------------------------------ */

function initNotLoginHandler() {
  setNotLoginHandler(() => {
    if (!state.isLogin()) {
      return;
    }
    state.setCurrentUser(null);
    toast('登录已过期，请重新登录', 'error');
    navigate('/login');
  });
}

/**
 * 校验登录态，未登录时引导去登录页
 *
 * @returns {boolean} 是否已登录
 */
export function requireLogin() {
  if (state.isLogin()) {
    return true;
  }
  toast('请先登录后再使用该功能', 'info');
  navigate('/login');
  return false;
}

/* ------------------------------ 启动 ------------------------------ */

async function bootstrap() {
  initNotLoginHandler();
  await loadLoginUser();

  window.addEventListener('hashchange', () => {
    resolveRoute().catch((e) => toast(e.message, 'error'));
  });

  await resolveRoute();
}

bootstrap().catch((e) => {
  document.body.innerHTML = `<div class="boot-error">应用启动失败：${escapeHtml(e.message)}</div>`;
});
