/**
 * 后端接口封装
 * <p>
 * 后端 context-path 为 /api，跨域配置已开启 allowCredentials，
 * 因此所有请求都要带上 credentials: 'include'（携带 JSESSIONID 维持登录态）。
 */

/**
 * 后端基础地址
 * <p>
 * 部署时可以只改这一处；也支持在 index.html 里于本模块之前注入
 * <code>window.__API_BASE__ = '/api'</code> 来覆盖（同源部署推荐这么写）。
 */
export const API_BASE = (typeof window !== 'undefined' && window.__API_BASE__)
  || 'http://localhost:8666/api';

/**
 * 首页列表是否使用带缓存的接口
 * <p>
 * 打开后走 /picture/list/page/vo/cache（Caffeine + Redis 多级缓存），
 * 前提是服务端 Redis 可用；否则接口会连接失败，首页加载不出来。
 * 本地没起 Redis 时保持 false 即可。
 */
export const USE_CACHE = (typeof window !== 'undefined' && window.__USE_CACHE__ === true);

/** 业务状态码，与后端 ErrorCode 保持一致 */
export const CODE = {
  SUCCESS: 0,
  PARAMS_ERROR: 40000,
  NOT_LOGIN_ERROR: 40100,
  NO_AUTH_ERROR: 40101,
  NOT_FOUND_ERROR: 40400,
  FORBIDDEN_ERROR: 40300,
  SYSTEM_ERROR: 50000,
};

/** 登录态失效时统一跳转，由 app.js 注入，避免 api 层依赖 UI */
let onNotLogin = null;

/**
 * 注册未登录回调
 *
 * @param {Function} handler 回调函数
 */
export function setNotLoginHandler(handler) {
  onNotLogin = handler;
}

/**
 * 统一请求方法，自动解包 BaseResponse
 *
 * @param {string} url    接口路径，以 / 开头
 * @param {object} options fetch 配置，body 为普通对象时自动转 JSON
 * @returns {Promise<any>} 接口返回的 data 字段
 */
async function request(url, options = {}) {
  const { method = 'GET', body, headers = {} } = options;
  const init = {
    method,
    // 跨域携带 Cookie，否则登录态会丢
    credentials: 'include',
    headers: { ...headers },
  };

  if (body instanceof FormData) {
    // FormData 不能手动设置 Content-Type，需要浏览器自己补 boundary
    init.body = body;
  } else if (body !== undefined) {
    init.headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(body);
  }

  let response;
  try {
    response = await fetch(API_BASE + url, init);
  } catch (e) {
    // 服务端在解析请求体阶段直接断开连接时，浏览器同样只会抛网络错误，
    // 因此这里要提示排查方向，不能只让用户以为后端没启动。
    throw new Error(`请求发送失败（${API_BASE}）。请确认后端已启动；若正在上传大文件，可能是服务端体积限制导致连接被断开。`);
  }

  if (!response.ok) {
    throw new Error(`请求失败（HTTP ${response.status}）`);
  }

  const text = await response.text();
  let result;
  try {
    result = JSON.parse(text);
  } catch (e) {
    // 返回的不是 JSON，通常意味着请求没有走到后端（被网关/代理拦截或重定向）
    throw new Error(`服务端返回异常内容（HTTP ${response.status}）：${text.slice(0, 60)}`);
  }
  if (result.code !== CODE.SUCCESS) {
    if (result.code === CODE.NOT_LOGIN_ERROR && typeof onNotLogin === 'function') {
      onNotLogin();
    }
    throw new Error(result.message || '请求失败');
  }
  return result.data;
}

/** 把对象里为空的值去掉，避免给后端传一堆 null */
function cleanParams(params = {}) {
  const result = {};
  Object.keys(params).forEach((key) => {
    const value = params[key];
    if (value === null || value === undefined || value === '') {
      return;
    }
    if (Array.isArray(value) && value.length === 0) {
      return;
    }
    result[key] = value;
  });
  return result;
}

/* ------------------------------ 用户模块 ------------------------------ */

export const userApi = {
  /** 用户注册，返回新用户 id */
  register: (data) => request('/user/register', { method: 'POST', body: data }),
  /** 用户登录，返回脱敏后的登录用户信息 */
  login: (data) => request('/user/login', { method: 'POST', body: data }),
  /** 获取当前登录用户，未登录会抛 40100 */
  getLoginUser: () => request('/user/get/login'),
  /** 用户注销 */
  logout: () => request('/user/logout', { method: 'POST', body: {} }),
  /** 修改当前登录用户的昵称，返回最新的登录用户信息 */
  updateMy: (data) => request('/user/update/my', { method: 'POST', body: data }),
  /** 创建用户（仅管理员） */
  add: (data) => request('/user/add', { method: 'POST', body: data }),
  /** 根据 id 获取用户（仅管理员） */
  getById: (id) => request(`/user/get?id=${encodeURIComponent(id)}`),
  /** 根据 id 获取脱敏用户信息 */
  getVOById: (id) => request(`/user/get/vo?id=${encodeURIComponent(id)}`),
  /** 更新用户（仅管理员） */
  update: (data) => request('/user/update', { method: 'POST', body: data }),
  /** 删除用户（仅管理员） */
  delete: (id) => request('/user/delete', { method: 'POST', body: { id } }),
  /** 分页获取用户脱敏列表（仅管理员） */
  listVOByPage: (query) => request('/user/list/page/vo', {
    method: 'POST',
    body: cleanParams(query),
  }),
};

/* ------------------------------ 图片模块 ------------------------------ */

export const pictureApi = {
  /**
   * 上传图片（同一个接口也用于重新上传）
   *
   * @param {File}   file    图片文件
   * @param {string} [id]    图片 id，传了表示覆盖已有图片
   * @param {string} [spaceId] 空间 id，传了表示上传到该私有空间
   */
  upload: (file, id, spaceId) => {
    const formData = new FormData();
    formData.append('file', file);
    if (id !== undefined && id !== null && id !== '') {
      // 后端 PictureUploadRequest.id 是 Long，表单字段用字符串即可绑定
      formData.append('id', String(id));
    }
    if (spaceId !== undefined && spaceId !== null && spaceId !== '') {
      formData.append('spaceId', String(spaceId));
    }
    return request('/picture/upload', { method: 'POST', body: formData });
  },
  /**
   * 通过 URL 上传图片（后端会把远程图片下载后转存到对象存储）
   *
   * @param {string}      pictureUrl 图片地址
   * @param {string}      [id]       图片 id，传了表示覆盖已有图片
   * @param {string}      [spaceId]  空间 id，传了表示上传到该私有空间
   */
  uploadByUrl: (pictureUrl, id, spaceId) => {
    const body = { pictureUrl };
    if (id !== undefined && id !== null && id !== '') {
      body.id = String(id);
    }
    if (spaceId !== undefined && spaceId !== null && spaceId !== '') {
      body.spaceId = String(spaceId);
    }
    return request('/picture/upload/url', { method: 'POST', body });
  },
  /** 删除图片（本人或管理员） */
  delete: (id) => request('/picture/delete', { method: 'POST', body: { id } }),
  /** 更新图片（仅管理员） */
  update: (data) => request('/picture/update', { method: 'POST', body: data }),
  /** 编辑图片（本人或管理员） */
  edit: (data) => request('/picture/edit', { method: 'POST', body: data }),
  /** 根据 id 获取图片实体（仅管理员） */
  getById: (id) => request(`/picture/get?id=${encodeURIComponent(id)}`),
  /** 根据 id 获取图片封装类 */
  getVOById: (id) => request(`/picture/get/vo?id=${encodeURIComponent(id)}`),
  /** 分页获取图片实体列表（仅管理员） */
  listByPage: (query) => request('/picture/list/page', {
    method: 'POST',
    body: cleanParams(query),
  }),
  /** 分页获取图片封装列表，后端限制 size <= 20 */
  listVOByPage: (query) => request('/picture/list/page/vo', {
    method: 'POST',
    body: cleanParams(query),
  }),
  /**
   * 分页获取图片封装列表（带 Caffeine + Redis 多级缓存）
   * <p>
   * 后端需要 Redis 可用，否则该接口会连接失败。
   * 由 config.js 里的 __USE_CACHE__ 控制前端是否走这条路径。
   */
  listVOByPageWithCache: (query) => request('/picture/list/page/vo/cache', {
    method: 'POST',
    body: cleanParams(query),
  }),
  /** 图片审核（仅管理员） */
  review: (data) => request('/picture/review', { method: 'POST', body: data }),
};

/* ------------------------------ 空间模块 ------------------------------ */

/**
 * 空间接口
 * <p>
 * 规则与后端一致：空间只有创建者（或管理员）能查看和修改；
 * `spaceId` 为空表示公共空间，不为空表示对应的私有空间。
 */
export const spaceApi = {
  /** 创建空间，返回新空间 id（普通用户只能创建普通版） */
  add: (data) => request('/space/add', { method: 'POST', body: data }),
  /** 编辑空间名称（创建者或管理员） */
  edit: (data) => request('/space/edit', { method: 'POST', body: data }),
  /** 更新空间（仅管理员，可改级别与限额） */
  update: (data) => request('/space/update', { method: 'POST', body: data }),
  /** 删除空间（创建者或管理员），空间内的图片会一并删除 */
  delete: (id) => request('/space/delete', { method: 'POST', body: { id } }),
  /** 根据 id 获取空间实体（仅管理员） */
  getById: (id) => request(`/space/get?id=${encodeURIComponent(id)}`),
  /** 根据 id 获取空间封装类（创建者或管理员） */
  getVOById: (id) => request(`/space/get/vo?id=${encodeURIComponent(id)}`),
  /** 分页获取空间实体列表（仅管理员） */
  listByPage: (query) => request('/space/list/page', {
    method: 'POST',
    body: cleanParams(query),
  }),
  /** 分页获取空间封装列表（仅管理员） */
  listVOByPage: (query) => request('/space/list/page/vo', {
    method: 'POST',
    body: cleanParams(query),
  }),
  /** 分页获取当前用户自己的空间列表（用户 id 由后端从登录态写入） */
  myListVOByPage: (query) => request('/space/my/list/page/vo', {
    method: 'POST',
    body: cleanParams(query),
  }),
  /** 空间级别字典：text / value / maxSize / maxCount */
  listLevel: () => request('/space/list/level'),
};

/**
 * 空间图库分析接口
 * <p>
 * 统一传 { spaceId, timeDimension, limit }：spaceId 为空表示全空间汇总（仅管理员），
 * timeDimension 仅 trend 接口使用（day/week/month），limit 控制词云/排行条数。
 */
export const spaceAnalyzeApi = {
  /** 资源使用分析（仪表盘：容量/数量占比） */
  usage: (data) => request('/space/analyze/usage', { method: 'POST', body: cleanParams(data) }),
  /** 分类分析（分组条形图：数量 + 总大小） */
  category: (data) => request('/space/analyze/category', { method: 'POST', body: cleanParams(data) }),
  /** 标签分析（词云图） */
  tag: (data) => request('/space/analyze/tag', { method: 'POST', body: cleanParams(data) }),
  /** 大小分段分析（饼图） */
  size: (data) => request('/space/analyze/size', { method: 'POST', body: cleanParams(data) }),
  /** 上传趋势（折线图，data.timeDimension = day/week/month） */
  trend: (data) => request('/space/analyze/trend', { method: 'POST', body: cleanParams(data) }),
  /** 空间使用排行（柱状图，仅管理员） */
  rank: (data) => request('/space/analyze/rank', { method: 'POST', body: cleanParams(data) }),
};

/* ------------------------------ 文件模块 ------------------------------ */

export const fileApi = {
  /** 测试文件上传（仅管理员） */
  testUpload: (file) => {
    const formData = new FormData();
    formData.append('file', file);
    return request('/file/test/upload', { method: 'POST', body: formData });
  },
  /** 测试文件下载地址 */
  testDownloadUrl: (filepath) => `${API_BASE}/file/test/download/?filepath=${encodeURIComponent(filepath)}`,
};
