/**
 * 运行时配置（部署时改这里，不用重新构建前端）
 *
 * 本文件必须在 js/app.js 之前加载，这样 js/api.js 才能读到 window.__API_BASE__。
 * 留空表示使用 js/api.js 里的默认值（本地开发用 http://localhost:8666/api）。
 *
 * 部署到服务器时推荐改成相对路径 '/api'：
 * 前端由 Nginx 托管、/api 反代到后端，浏览器眼里只有一个源，不存在跨域问题。
 *
 * 如果使用提供的 Nginx 配置，可以用 __API_BASE__ 占位符，
 * 由 Nginx 的 sub_filter 在响应时替换成真实地址。
 */
window.__API_BASE__ = '';
