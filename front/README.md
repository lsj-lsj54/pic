# 云图库前端

原生 HTML + CSS + ES Module 实现，**无需构建、无第三方依赖**，改完刷新即可生效。

## 目录结构

```
front/
├── index.html                 页面骨架（只有容器，内容由 JS 渲染）
├── css/style.css              全部样式
├── js/
│   ├── app.js                 入口：登录态管理 + hash 路由
│   ├── api.js                 后端接口封装（统一解包 BaseResponse）
│   ├── ui.js                  通用工具：提示、弹窗、表单、格式化、全局状态、空间用量条
│   ├── components/picture.js  图片公共组件：上传（含压缩）、详情、编辑
│   └── views/                 各页面
│       ├── auth.js            登录 / 注册
│       ├── gallery.js         图片浏览（首页）
│       ├── mine.js            我的图片（公共图库）
│       ├── space.js           我的空间（空间列表 / 创建 / 重命名 / 删除）
│       ├── spaceDetail.js     空间详情（空间内的图片，可上传、编辑、重传、删除）
│       ├── adminPicture.js    图片管理（管理员）
│       └── adminUser.js       用户管理（管理员）
├── server.js                  零依赖静态服务器，并把 /api 代理到后端
├── smoke-test.mjs             模块冒烟检查（开发辅助）
└── mock-backend.mjs           假后端，用于单测代理转发（开发辅助）
```

## 功能清单

| 模块 | 功能 |
| --- | --- |
| 用户 | 注册、登录、退出、登录态自动恢复（刷新不掉线） |
| 图片浏览 | 分页网格、关键词搜索（防抖）、分类筛选、排序、详情弹窗、查看原图 |
| 我的图片 | 批量上传、显示审核状态、编辑信息、重新上传原图、删除（公共图库部分） |
| 我的空间 | 空间列表、创建（级别决定限额）、重命名、删除（连带删除空间内图片）、用量进度条 |
| 空间详情 | 空间内的图片分页浏览、上传到该空间（含用量校验）、编辑、重传、删除 |
| 图片管理 | 全部图片分页、按状态/关键词筛选、通过/拒绝（可改判）、编辑、重传、删除 |
| 用户管理 | 用户分页、按昵称/角色筛选、新增、编辑、删除（不能删自己） |

权限与后端保持一致：删除、编辑「仅本人或管理员」，审核与图片/用户管理「仅管理员」，非管理员上传的图片进入**待审核**状态，需管理员在图片管理中通过后才会出现在首页。

## 公共图库与私有空间

后端把「空间」作为图片的归属：`spaceId` 为空表示**公共图库**，不为空表示**私有空间**。前端据此分成两块：

- **我的图片**（`#/mine`）：公共图库中我上传的图片；
- **我的空间**（`#/space`）：我创建的空间，进入空间（`#/space/detail?id=xxx`）后能看到并管理空间内的图片。

对应的后端规则（前端已按此对接，越权会拿到 40101）：

| 操作 | 接口 | 权限 |
| --- | --- | --- |
| 空间列表（自己的） | `POST /space/my/list/page/vo` | 登录，用户 id 由后端从登录态写入 |
| 空间详情 | `GET /space/get/vo?id=` | 仅空间创建者或管理员 |
| 创建空间 | `POST /space/add` | 登录；普通用户只能建普通版 |
| 重命名空间 | `POST /space/edit` | 仅空间创建者或管理员 |
| 删除空间 | `POST /space/delete` | 仅空间创建者或管理员，空间内图片一并删除 |
| 空间级别字典 | `GET /space/list/level` | 公开 |
| 空间内的图片 | `POST /picture/list/page/vo`（带 `spaceId`） | 仅空间创建者或管理员 |
| 上传到空间 | `POST /picture/upload`（表单带 `spaceId`）/ `POST /picture/upload/url`（body 带 `spaceId`） | 目标空间必须属于自己，且校验空间数量与容量上限 |

> 上传到空间时后端会先校验数量、再按真实文件体积校验容量，超限会拒绝上传并把已传的文件从对象存储删掉。
> 空间用量（`totalCount` / `totalSize`）由后端在上传、删除图片时自动维护，前端只用 `GET /space/get/vo` 拿最新值渲染进度条。


## 启动方式

### 方式一：用自带的静态服务器（推荐）

同源访问，不存在跨域和 Cookie 问题：

```bash
node server.js
# 打开 http://localhost:8667
```

`server.js` 会把 `/api/**` 原样代理到后端 `http://localhost:8666`，所以浏览器眼里只有一个源。
可用环境变量覆盖：`PORT`（前端端口，默认 8667）、`API_PORT`（后端端口，默认 8666）、`API_HOST`（默认 localhost）。

> 请先启动 Spring Boot 后端；后端没起来时接口会返回一条明确的 502 提示，页面不会白屏。

### 方式二：用 VS Code Live Server 等静态服务器

直接打开 `front` 目录即可。此时走的是**跨域**请求，需要同时满足：

1. 后端 `application.yml` 里 `cos.client` 等配置正常、已启动；
2. `CorsConfig` 已开启跨域（项目里已配置 `allowedOriginPatterns("*")` + `allowCredentials(true)`）；
3. 用 `http://localhost:xxxx` 访问前端（不要用 `127.0.0.1`，前后端域名保持一致，否则 Cookie 的 SameSite 策略会拦掉登录态）。

## 接口地址配置

后端地址集中在 [`js/api.js`](js/api.js) 的 `API_BASE`：

```js
export const API_BASE = 'http://localhost:8666/api';
```

走 `server.js` 或把静态资源放进 Spring Boot 的 `static` 目录时，把它改成相对路径 `'/api'` 更稳妥（部署到服务器时尤其如此）。

## 与后端约定的一些细节

- **单文件最大 2MB**：后端 `FileManager.validPicture` 的限制。前端会把超过 900KB 或 1600px 的图片在本地压到 900KB 以内（优先 webp 以保留透明，浏览器不支持时退回 jpeg 并铺白底），压缩后仍超 2MB 的会在本地直接拦下，不浪费一次请求。
- **格式限制**：仅 `jpg / jpeg / png / webp`，与后端白名单一致。
- **分页上限**：首页与我的图片每页 12 条、管理页每页 10 条，均未超过后端「size ≤ 20」的爬虫限制。
- **登录态**：靠 `JSESSIONID`，所有请求都带 `credentials: 'include'`；接口返回 `40100` 时会自动清理登录态并跳登录页。
- **审核参数不可伪造**：管理员保存图片时走 `/picture/update`，普通用户走 `/picture/edit`，审核状态一律由后端 `fillReviewParams` 决定。
- **新增用户的默认密码**是 `12345678`（后端 `UserConstant.DEFAULT_PASSWORD`），创建后需要提醒用户尽快修改。
- **上传体积**：后端 `application.yml` 里 `spring.servlet.multipart` 的 `max-file-size` 与 `max-request-size` 必须**都**配置。只配 `max-file-size` 时，multipart 解析仍会被请求体上限拦下，表现为「超过 1MB 的文件必上传失败」，且错误信息会被包装成笼统的系统错误，极难排查。`e2e-test.mjs` 的第 8 组用例专门回归这一点。


## 开发辅助脚本

```bash
node smoke-test.mjs      # 搭 DOM 桩真实加载所有模块，查语法/import/首屏与空间页面渲染
node e2e-test.mjs        # 打真实后端，回归「登录/分页/Long 精度/详情/空间」等关键链路
node mock-backend.mjs    # 在 8666 起一个假后端，打印它收到的请求，用于验证代理转发
```

`e2e-test.mjs` 默认请求 `http://localhost:8666/api`，可用环境变量改：

```bash
API_BASE=http://localhost:8688/api node e2e-test.mjs
```

这些文件只服务于本地调试，不需要部署，可以直接删掉。

## 已知的后端依赖

前端的「查看详情 / 编辑 / 删除」都依赖后端返回的 id 是**字符串**形式。
数据库主键是雪花算法生成的 19 位数字，超过 JavaScript `Number` 的安全范围
（2^53-1，16 位），若后端直接以数字返回，`JSON.parse` 会把它改写，
再用改写的 id 去请求详情就会报「请求数据不存在」。

后端已通过 `JacksonConfig` 处理（只把超出安全范围的 Long 转成字符串，
分页的 `total`、`size` 等仍保持数字）。因此：

- 改动后端后**必须重启**才能生效；
- 若临时连的是旧后端，页面不会崩，但点「查看」会提示「请求数据不存在」，
  分页也会退化成「单页 + total 为 0」的样子（前端已做兜底显示）。
- 可运行 `node e2e-test.mjs` 自检，全部 PASS 说明后端已就绪。

