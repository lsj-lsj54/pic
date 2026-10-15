# 云图库 · 私有化图片管理平台

![Java](https://img.shields.io/badge/Java-8%20%7C%20JDK%2017-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.6.13-6db33f)
![MyBatis-Plus](https://img.shields.io/badge/MyBatis--Plus-3.5.9-2f6fed)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479a1)
![Redis](https://img.shields.io/badge/Redis-7-dc382d)
![COS](https://img.shields.io/badge/对象存储-腾讯云%20COS-0052d9)
![Frontend](https://img.shields.io/badge/前端-零依赖%20ES%20Module-success)

把图片传上来、自动压好缩略图、过一遍审核，再按「**公共图库 / 私有空间**」两套模型管理起来。

后端 Spring Boot + MyBatis-Plus + MySQL + Redis + 腾讯云 COS；前端零依赖原生 ES Module（无构建、无第三方运行时依赖）；
配套 Docker Compose 一套命令部署上线。

| | 说明 |
| --- | --- |
| **上传** | 本地文件 / 图片链接 / 关键词批量抓取三条入口，单张超过 900KB 在前端先压缩，后端统一走模板方法落对象存储 |
| **加速** | 一次上传产出「原图 + webp 缩略图」，列表页用缩略图、详情页用原图；列表查询走 Caffeine → Redis → MySQL 三级读取 |
| **治理** | 非管理员上传的图片自动进入待审核，管理员审核通过后才出现在公共图库 |
| **空间** | 公共图库 + 私有空间两级模型，空间按级别（普通/专业/旗舰）限数量与容量，用量随图片增删实时结算 |
| **权限** | 注解式角色鉴权（AOP）+ 数据级归属校验：图片、空间都只有本人（或空间创建者）与管理员可操作 |

> 详细部署（服务器选型、Docker Compose、Nginx、HTTPS、日常运维）见 [docs/deploy.md](docs/deploy.md)。

<!-- 想放截图时：把图片放到 docs/images/ 下，然后取消下面这段注释即可（在 GitHub 上会渲染成表格）

## 界面预览

| 图片浏览 | 我的空间 | 空间详情 |
| --- | --- | --- |
| ![](docs/images/gallery.png) | ![](docs/images/space.png) | ![](docs/images/space-detail.png) |

| 图片管理（管理员） | 上传（本地压缩） | 图片详情 |
| --- | --- | --- |
| ![](docs/images/admin-picture.png) | ![](docs/images/upload.png) | ![](docs/images/detail.png) |

-->

---

## 目录

- [功能一览](#功能一览)
- [技术栈](#技术栈)
- [系统架构](#系统架构)
- [目录结构](#目录结构)
- [核心设计与实现](#核心设计与实现)
- [工程实践与踩坑记录](#工程实践与踩坑记录)
- [快速开始](#快速开始)
- [接口一览](#接口一览)
- [测试](#测试)
- [部署](#部署)
- [后续规划](#后续规划)

---

## 功能一览

| 模块 | 能力 |
| --- | --- |
| 用户 | 注册（MD5 加盐）、登录 / 注销、登录态自动恢复、角色（user / admin）、注解式权限校验 |
| 图片浏览 | 分页网格、名称/简介关键词搜索（防抖）、分类筛选、按时间/体积排序、详情弹窗（缩略图 → 原图）、查看原图、按主色调（腾讯云 imageAve）查图（工具栏取色器 + 容差滑块，卡片主色块可一键「按此色找相似」） |
| 图片上传 | 本地文件（可多选、前端 canvas 压缩）、图片链接导入、关键词批量抓取（必应，单次上限 30 张、自动去重） |
| 图片管理 | 分页查询、按审核状态筛选、通过 / 拒绝（可改判）、编辑信息（标签数组 ↔ JSON）、重新上传、删除（库 + 缓存 + 对象存储三处一致） |
| 我的图片 | 自己上传的公共图库图片，带审核状态角标，可编辑 / 重传 / 删除 |
| 空间 | 创建（级别决定限额）、重命名、删除（连带清理空间内图片）、空间列表带数量与容量进度条 |
| 空间分析 | 空间资源用量（容量/数量仪表盘）、分类分布（分组条形图）、标签词云、大小分段（饼图）、上传趋势（按天/周/月折线）；管理员可汇总全系统并查看空间使用排行（ECharts） |
| 交互与观感 | 全屏图片查看器（滚轮/双击缩放、拖拽平移、键盘 ←→/±/f 与缩略图导航）、卡片悬停遮罩 + 快速操作、列表骨架屏、Toast 堆叠与进度条、滚动渐入、按钮涟漪；统一设计 token（色彩/阴影/圆角/缓动）与 :focus-visible 焦点环，动效遵循 prefers-reduced-motion |
| 空间内图片 | 上传到指定空间（上传前校验数量、拿到真实体积后校验容量）、浏览、编辑、重传、删除，用量实时回填 |
| 用户管理 | 用户分页、按昵称/角色筛选、新增、编辑、删除（不能删自己） |
| 前端体验 | hash 路由 + 视图懒加载、统一请求封装（自动解包 BaseResponse、40100 自动跳登录）、Toast / Modal / 分页等通用组件 |

---

## 技术栈

| 分类 | 选型 |
| --- | --- |
| 语言 / 构建 | Java 8 语言级别（构建与运行使用 JDK 17）、Maven 3.8 |
| 后端框架 | Spring Boot 2.6.13、Spring AOP（自定义 `@AuthCheck`）、Spring Session（HttpSession 登录态） |
| 持久层 | MyBatis-Plus 3.5.9（含 `mybatis-plus-jsqlparser` 分页插件）、MySQL 8、逻辑删除、雪花 ID |
| 缓存 | Caffeine 3.1.8（本地一级）+ Redis / Spring Data Redis（分布式二级） |
| 对象存储 | 腾讯云 COS Java SDK 5.6.227 + 数据万象（上传即产出 webp 压缩图） |
| 工具库 | Hutool 5.8.26（HTTP / 文件 / 日期）、jsoup 1.15.3（抓取解析）、Lombok |
| 接口文档 | Knife4j 4.4.0（OpenAPI 2 / Swagger） |
| 前端 | 原生 HTML + CSS + ES Module，零构建、零第三方运行时依赖；Node 内置模块写的静态服务器 + `/api` 反向代理 |
| 测试 | JUnit 5.10.2 + Mockito（后端）、自研 Node 冒烟与端到端脚本（前端） |
| 部署 | Docker 多阶段构建、Docker Compose（MySQL + Redis + 后端 + Nginx 前端）、Nginx 反向代理 |

---

## 系统架构

```
                     ┌───────────────────────────────┐
   浏览器  ──────────▶│  Nginx / node server.js       │
                     │  静态资源 + /api 反向代理      │
                     └───────────────┬───────────────┘
                                     │  /api/**
                     ┌───────────────▼───────────────┐
                     │  Spring Boot  (context-path)   │
                     │  Controller → Service → Mapper │
                     │  AOP 鉴权 / 全局异常 / 参数校验 │
                     └───┬───────────┬───────────┬───┘
                         │           │           │
              ┌──────────▼──┐  ┌─────▼─────┐  ┌──▼──────────────┐
              │  MySQL 8    │  │  Redis    │  │  腾讯云 COS      │
              │  业务数据    │  │  列表缓存 │  │  原图 + 缩略图   │
              └─────────────┘  └───────────┘  └─────────────────┘
                                     ▲
                          Caffeine 本地缓存（进程内一级）
```

三条主要链路：

1. **上传**：`Controller` 鉴权 → `Service` 组装入参（校验 / 空间归属 / 配额）→ `PictureUploadTemplate` 模板方法（校验 → 生成 key → 落临时文件 → 上传 COS → 封装结果 → 清理临时文件）→ 写库 → 维护空间用量 → 清缓存。
2. **列表**：`/picture/list/page/vo`（普通查询）或 `/picture/list/page/vo/cache`（带缓存）→ Caffeine 命中即返回；未命中查 Redis；仍未命中查 MySQL 并回填两级缓存。
3. **访问图片**：浏览器直接读 COS 地址（**前提：存储桶配置为公共读**，或改用 CDN / 临时签名 URL），后端只负责存储与元数据，不代理图片流量。

---

## 目录结构

```
.
├── src/main/java/com/lsj/demo
│   ├── controller          # 接口层：user / picture / space / file
│   │   └── aop             # @AuthCheck 注解式鉴权切面
│   ├── service             # 业务层接口与实现（含空间配额、缓存、审核逻辑）
│   ├── manager             # 外部能力封装
│   │   ├── CosManager      # 对象存储：上传、压缩规则、按 url 反查 key 删除
│   │   ├── FileManager     # 早期上传实现（已废弃，保留对比）
│   │   ├── factory         # 上传方式工厂（file / url）
│   │   └── uplode          # 上传模板与两种实现（本地文件 / URL 下载）
│   ├── mapper              # MyBatis-Plus Mapper
│   ├── model
│   │   ├── entity          # 数据库实体
│   │   ├── dto             # 请求对象（按模块分包）
│   │   ├── vo              # 返回对象（统一提供 voToEntity / entityToVo）
│   │   └── enums           # 空间级别、审核状态、上传方式、用户角色
│   ├── common              # 统一响应、分页基类、权限工具、全局异常处理
│   └── exception           # 错误码与业务异常
├── src/main/resources
│   ├── application.yml         # 主配置（端口 / 数据源 / multipart）
│   └── application-local.yml   # 本地密钥（已 gitignore，需自行创建）
├── src/test/java           # 单元测试 145 项
├── front/                  # 前端（原生 ES Module，详见 front/README.md）
│   ├── js/{api,ui,app}.js  # 请求封装 / 通用组件 / 路由入口
│   ├── js/views/           # 各页面（浏览、我的图片、我的空间、空间详情、管理端）
│   ├── js/components/      # 上传、详情、编辑弹窗与图片压缩
│   ├── server.js           # 零依赖静态服务器 + /api 代理
│   └── smoke-test.mjs      # 模块冒烟检查；e2e-test.mjs 端到端回归
├── sql/                    # 建表脚本（user / picture / space）
├── deploy/                 # Dockerfile 之外的部署资产：compose、mysql 初始化、nginx、.env 模板
├── docs/deploy.md          # 部署指南
├── Dockerfile              # 后端多阶段构建（Maven 构建 + JRE 运行）
└── Dockerfile.front        # 前端 Nginx 镜像
```

---

## 核心设计与实现

### 1. 图片上传：模板方法 + 两种实现

本地文件与「图片链接导入」的流程只差三步（怎么校验、文件名从哪来、怎么落地成临时文件），其余完全一致。
因此把流程固化成模板方法 `PictureUploadTemplate.uploadPicture()`（声明为 `final`，子类不可改流程），
只留三个抽象钩子给子类实现：

```
validPicture(输入)      → 校验：类型白名单、体积上限（URL 额外做 HEAD 探测）
getOriginFilename(输入) → 取原始文件名（URL 会忽略 query、处理百分号编码）
processFile(输入, 文件) → 落地临时文件（本地转存 / 远程下载，边下边校验体积）
```

骨架统一负责：生成对象存储 key → 建临时文件 → 调子类落地 → 上传 → 解析结果 → **finally 清理临时文件**。
新增上传方式（比如以后接对象存储直传、多源导入）只需实现这三个方法并注册成 bean，工厂自动发现，不用改主干代码。

### 2. 缩略图：一次上传产出两份，按需压缩

上传时给 COS 数据万象带一条 `imageMogr2/format/webp` 规则，**一次请求同时产出原图与 webp 压缩图**，
压缩图与原图同目录、只换后缀（`/public/1/xxx.png` → `/public/1/xxx.webp`）。原图本身就是 webp 时跳过压缩，省一次存储与流量。
前端列表页加载缩略图、详情页加载原图，实测同一张图从 32.5 KB 降到 14.2 KB（省约 56%）。

### 3. 列表性能：Caffeine → Redis → MySQL 三级读取

- 只缓存**与用户身份无关**的公开数据：查询条件被强制为 `reviewStatus = 已通过` 且 `spaceId IS NULL`，因此缓存可以共用同一个 key，不会串号；
- 缓存 key 由真正影响结果的字段拼装后取 MD5，标签列表先排序再入 key（顺序不同不应产生两份缓存）；
- Redis TTL = 5 分钟 + 0~5 分钟随机抖动，避免同一时刻批量 key 失效造成缓存雪崩；Caffeine 本地缓存 5 分钟，比 Redis 稍短，两级自然错开；
- 图片增删改、审核后清理本地缓存；Redis 侧靠 TTL 自然过期（单机部署可接受，多实例部署的处理方式已在代码注释中写明）。

### 4. 图片审核：状态机 + 双端闭环

- 非管理员上传 / 编辑一律打回**待审核**；管理员操作自动过审，审核人与审核时间由后端写入，**前端无法伪造审核参数**；
- 审核状态变更后立即清缓存，公共图库的可见性随之变化；
- 管理端支持改判（通过 ↔ 拒绝），拒绝时可带上审核信息。

### 5. 空间模型：公共图库 / 私有空间 + 配额

- `spaceId` 为空表示**公共图库**（所有人可见），不为空表示**私有空间**（仅空间创建者与管理员可见、可操作）；
- 空间级别（普通版 100 张 / 100MB，专业版 1000 张 / 1GB，旗舰版 10000 张 / 10GB）在建空间时自动填充限额，非管理员只能创建普通版；
- 上传先校验数量（放不下就不用白跑一趟上传），拿到真实体积后再校验容量，**超限会把刚传上去的文件从对象存储删掉**，不留垃圾；
- 用量（`totalCount` / `totalSize`）用 SQL 原子自增自减维护（`totalCount = totalCount + (?)`），避免「先查再更新」在并发上传时丢更新；
- 公共列表查询会**显式追加 `spaceId IS NULL`**：不加这个条件，私有空间里的图片会出现在公共图库中（越权泄漏）。

### 6. 批量抓取

解析必应图片搜索的异步结果片段，优先取结果条目 `m` 属性里的 `murl`（原图直链），
依次回退到 `murl` / `data-src` / `absUrl(src)`，并处理协议相对地址；同一批内按地址去重、单张失败不影响其余、数量有上限。

### 7. 前端：零依赖的模块化 SPA

- hash 路由 + `import()` 视图懒加载，模块按 `api / ui / components / views` 分层，改完刷新即生效，没有构建步骤；
- 统一请求封装：自动解包 `BaseResponse`、携带 `credentials: 'include'` 维持登录态、`40100` 自动清理登录态并跳登录页；
- 上传前用 canvas 压缩（超过 900KB 或 1600px 才压，优先 webp 以保留透明，浏览器不支持时退回 jpeg 并铺白底），压缩后仍超 2MB 本地直接拦下；
- 所有渲染到页面的用户输入统一走 `escapeHtml`，避免存储型 XSS。

---

## 工程实践与踩坑记录

这些都是真实踩到并修掉的问题，记录在此，也方便后来者少走弯路：

| 问题 | 现象 | 处理 |
| --- | --- | --- |
| 雪花 ID 精度丢失 | 19 位主键超过 JS `Number.MAX_SAFE_INTEGER`，前端拿到的 id 被改写，详情接口报「请求数据不存在」、分页 `total` 变 0 | 自定义 Jackson 序列化器：只把超出安全范围的 `Long` 转成字符串，`total`、`size` 等仍保持数字 |
| 分页 `total` 恒为 0 | MyBatis-Plus 3.5.9 起分页插件被拆到独立模块 | 显式引入 `mybatis-plus-jsqlparser` |
| 链式 wrapper 报错 | `lambdaQuery()` 的结果直接传给 `list()` / `update()` 时抛 `can not use this method for "getSqlFirst"`（3.5.9 的 `AbstractChainWrapper#getSqlFirst` 是抛异常实现） | 统一改用具体的 `LambdaQueryWrapper` / `LambdaUpdateWrapper` 传给 mapper |
| 循环依赖启动失败 | `PictureService` ↔ `SpaceService` 互相注入，Boot 2.6+ 默认禁止循环引用 | 用 `@Lazy` 在一侧延迟注入，并在注释里说明原因 |
| 压缩图跑到桶根目录 | CI 规则的 `fileId` 用 `FileUtil.mainName(key)` 会丢掉目录部分 | 改成完整路径推导，保证压缩图与原图同目录 |
| 大文件上传必失败 | 只配了 `max-file-size` 没配 `max-request-size`，multipart 解析仍被请求体上限拦下，且报的是笼统的系统错误 | 两个上限都显式配置，并在 e2e 里加了 1 MiB / 1.5 MiB / 3 MiB 边界回归 |
| 删除对象存储无效 | 删除接口需要的是对象 key，直接把完整 url 传进去不会报错，但什么也没删掉 | 增加 `parseKeyFromUrl` 反推 key，并加「同 url 被多条记录引用则跳过」的保护 |
| CDN 地址导入被误杀 | 必应缩略图 `/th/id/OIP-C.TTm1he_xxx?w=155` 点号后面是 CDN 加密串，被当作文件后缀判为「文件类型错误」，合法图片传不上去 | 不再凭地址后缀判类型：先看 `Content-Type`，下载后按文件头（JPEG/PNG/WebP magic number）确认并纠正后缀；下载遇 5xx 自动重试一次 |
| Lombok 与 JDK 21 不兼容 | JDK 21 下编译报 `NoSuchFieldError: JCTree$JCImport.qualid` | 构建统一使用 JDK 17（Docker 构建镜像同样固定 17） |

---

## 快速开始

### 环境要求

| 依赖 | 版本 / 说明 |
| --- | --- |
| JDK | **17**（Lombok 1.18.22 与 JDK 21 不兼容；源码语言级别为 Java 8） |
| Maven | 3.8+ |
| MySQL | 8.0 |
| Redis | 7（可选：不启动时不要走带缓存的接口即可） |
| 腾讯云 COS | 需要一个存储桶，并在控制台开启数据万象图片处理 |
| Node.js | 18+（只用于启动前端静态服务器，前端本身无构建） |

### 1. 初始化数据库

```bash
mysql -uroot -p < deploy/mysql/init.sql
```

`deploy/mysql/init.sql` 全部是 `CREATE TABLE IF NOT EXISTS`，可重复执行，建的是 `picture` 库，包含三张表：
`user`（用户）、`picture`（图片，审核字段与 `spaceId` 直接建在表里）、`space`（空间）。

### 2. 配置

| 配置 | 位置 | 说明 |
| --- | --- | --- |
| 端口 / context-path / multipart 上限 | `application.yml` | 后端 8666，接口前缀 `/api`，单请求与单文件上限均为 10MB |
| 数据库账号密码 | `application-local.yml` 或环境变量 | `application.yml` 里是 `${MYSQL_USERNAME:root}` / `${MYSQL_PASSWORD:}` 占位符，**仓库中不含任何密码** |
| 腾讯云 COS 五项参数 | `application-local.yml` 或环境变量 | `cos.client.host / secretId / secretKey / region / bucket` |
| Redis 连接 | `application-local.yml` | 本地不起 Redis 时，只要不走带缓存的接口即可 |

`src/main/resources/application-local.yml` **已被 `.gitignore` 排除**，本地按下面的结构自建即可（`spring.profiles.active=local` 会自动加载）：

```yaml
spring:
  datasource:
    username: root
    password: <你的本地数据库密码>
  redis:
    database: 3
    host: 127.0.0.1
    port: 6379
cos:
  client:
    host: https://your-bucket-appid.cos.ap-guangzhou.myqcloud.com
    secretId: <你的 SecretId>
    secretKey: <你的 SecretKey>
    region: ap-guangzhou
    bucket: your-bucket-appid
```

部署时不需要这个文件：`deploy/docker-compose.yml` 通过 `SPRING_DATASOURCE_*`、`COS_CLIENT_*` 等环境变量注入（模板见 `deploy/.env.example`）。

### 3. 启动后端

```bash
mvn clean package -DskipTests
java -jar target/demo-0.0.1-SNAPSHOT.jar
# 或开发模式：mvn spring-boot:run
```

启动后接口地址为 `http://localhost:8666/api`，接口文档 Knife4j：`http://localhost:8666/api/doc.html`。

### 4. 启动前端

```bash
cd front
node server.js        # 打开 http://localhost:8667
```

`front/server.js` 是零依赖静态服务器，并把 `/api/**` 原样代理到后端，浏览器眼里只有一个源，不存在跨域与 Cookie 问题。
用其他静态服务器（Live Server 等）打开 `front/` 目录也可以，此时走跨域，需要前后端都用 `localhost` 访问。

### 5. 创建管理员

密码是加盐 MD5（盐值见 `UserServiceImpl`），不能直接写明文。最省事的做法是先在前端注册一个账号，再改角色：

```sql
UPDATE user SET userRole = 'admin' WHERE userAccount = '你的账号';
```

---

## 接口一览

统一响应格式：`{ "code": 0, "data": ..., "message": "ok" }`；`code` 与 `ErrorCode` 一一对应（0 成功、40100 未登录、40101 无权限、40300 禁止访问、40400 不存在）。

### 用户

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| POST | `/user/register` | 注册 | 公开 |
| POST | `/user/login` | 登录（写入 Session） | 公开 |
| GET | `/user/get/login` | 获取当前登录用户 | 登录 |
| POST | `/user/logout` | 注销 | 登录 |
| GET | `/user/get/vo` | 按 id 获取脱敏用户 | 公开 |
| POST | `/user/list/page/vo` | 用户分页 | 管理员 |
| POST | `/user/add`、`/user/update`、`/user/delete`、`GET /user/get` | 用户增删改查 | 管理员 |

### 图片

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| POST | `/picture/upload` | 上传本地文件（可带 `id` 覆盖、`spaceId` 上传到空间） | 登录 |
| POST | `/picture/upload/url` | 图片链接导入 | 登录 |
| POST | `/picture/upload/batch` | 关键词批量抓取 | 管理员 |
| POST | `/picture/list/page/vo` | 分页查询（`spaceId` 为空查公共图库，不为空查自己的空间） | 公开 / 空间创建者 |
| POST | `/picture/list/page/vo/cache` | 分页查询（多级缓存，仅公共图库） | 公开 |
| GET | `/picture/get/vo` | 图片详情（私有空间的图片仅创建者可见） | 公开 / 空间创建者 |
| POST | `/picture/edit` | 编辑自己的图片 | 本人或管理员 |
| POST | `/picture/delete` | 删除图片（库 + 缓存 + 对象存储） | 本人或管理员 |
| POST | `/picture/review` | 审核（通过 / 拒绝） | 管理员 |
| POST | `/picture/update`、`GET /picture/get`、`POST /picture/list/page` | 管理端增改查 | 管理员 |

### 空间

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| POST | `/space/add` | 创建空间（普通用户仅普通版） | 登录 |
| POST | `/space/my/list/page/vo` | 我的空间分页（用户 id 由后端从登录态写入） | 登录 |
| GET | `/space/get/vo` | 空间详情 | 空间创建者或管理员 |
| POST | `/space/edit` | 重命名空间 | 空间创建者或管理员 |
| POST | `/space/delete` | 删除空间（连带删除空间内图片） | 空间创建者或管理员 |
| GET | `/space/list/level` | 空间级别字典 | 公开 |
| POST | `/space/list/page`、`/space/list/page/vo`、`GET /space/get`、`POST /space/update` | 管理端 | 管理员 |

### 文件（测试用）

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| POST | `/file/test/upload` | 上传任意文件到对象存储 | 管理员 |
| GET | `/file/test/download/` | 从对象存储下载 | 管理员 |

---

## 测试

### 后端单元测试（145 项）

```bash
mvn test
```

覆盖查询条件构造（排序、筛选、空值）、VO 与实体互转、空间权限规则、图片空间鉴权、
上传模板（本地文件 / URL / 后缀嗅探 / 下载重试 / 体积与类型校验）、对象存储删除、缓存序列化与 key 稳定性，
以及一个 `@SpringBootTest` 上下文加载用例（校验 Bean 装配与循环依赖处理）。

### 前端冒烟与端到端（无需浏览器）

```bash
cd front
node smoke-test.mjs                  # 11 个模块真实 import + 首屏与空间页面渲染
node e2e-test.mjs                    # 49 项真实后端回归
API_BASE=http://localhost:8688/api node e2e-test.mjs   # 打其他后端地址
```

`smoke-test.mjs` 搭了一个极简 DOM 桩真实加载所有模块，能查出语法、import 路径和首屏渲染阶段的运行时错误；
`e2e-test.mjs` 覆盖登录与会话、雪花 ID 精度、分页 `total` 自洽、详情接口、上传体积边界，
以及空间的完整链路（创建 → 列表 → 详情 → 上传到空间并校验用量累加 → 隔离校验 → 删除图片校验用量回落 → 重命名 → 删除）。

---

## 部署

推荐用 Docker Compose 在一台 2核2G 的轻量服务器上跑起 MySQL + Redis + 后端 + Nginx 前端：

```bash
cp deploy/.env.example deploy/.env    # 填数据库密码与 COS 密钥（.env 已被 gitignore）
chmod 600 deploy/.env
bash deploy/deploy.sh                 # 校验 → 构建 → 启动 → 健康检查，失败自动回滚
```

也可以手动执行 compose（等价，只是没有健康门禁与回滚）：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d --build
```

配套脚本与资产：

| 文件 | 用途 |
| --- | --- |
| `deploy/deploy.sh` | 一键发布：拦住占位密码、预校验 Nginx 配置、按 git SHA 打标签、健康检查、失败自动回滚 |
| `deploy/backup.sh` | 数据库备份：mysqldump + gzip + 按天保留 + 可选上传 COS |
| `deploy/docker-compose.yml` | MySQL + Redis + 后端 + 前端；端口、镜像标签均可用 `.env` 覆盖 |
| `deploy/docker-compose.https.yml` | HTTPS 叠加配置（挂证书 + 443），配腾讯云免费证书用 |
| `deploy/mysql/init.sql` | 首次部署建库建表（`CREATE TABLE IF NOT EXISTS`，可重复执行） |
| `deploy/mysql/migration-*.sql` | 已有库的增量迁移（缩略图字段、主色调字段） |
| `deploy/mysql/create-app-user.sql` | 建只对 `picture` 库有权限的应用账号（最小权限加固） |
| `deploy/nginx/default.conf` | HTTP 站点：静态托管 + `/api` 反代 + 运行时配置替换 |
| `deploy/nginx/https.conf` | HTTPS 站点：证书、TLS 参数、HSTS、80→443 跳转 |

服务器选型、备案、**腾讯云专项落地清单（安全组、COS 子账号最小权限、HTTPS、排障清单）**、日常运维与数据备份，见 **[docs/deploy.md](docs/deploy.md)**。

---

## 后续规划

- **AI 图片编辑**：接入多模态模型，支持以文生图、局部重绘、智能扩图，并保留编辑历史可回滚
- **图库分析**：图片内容自动打标与聚类，输出分类分布、上传趋势、存储占用等可视化报表
- **团队空间**：空间成员与角色体系（拥有者 / 管理员 / 成员），空间级别的配额分配与操作审计
- **图片协同编辑**：多人同时编辑图片信息与标签，基于 WebSocket 做实时同步与冲突处理

---

## 说明

- 前端详细说明（页面、接口约定、开发辅助脚本）见 [front/README.md](front/README.md)。
- 项目起步于编程导航「云图库」教程，其后按自己的需求做了大量重构与扩展：业务上新增空间与配额体系、审核闭环、多级缓存；
  工程上补齐了单元测试与端到端回归、修正了原实现中的若干缺陷（见[工程实践与踩坑记录](#工程实践与踩坑记录)），并完善了 Docker 部署方案。
- 仓库中不包含任何密钥：数据库账号密码与 COS 密钥都从 `application-local.yml`（已 gitignore）或环境变量读取，
  仓库里只有 `${...}` 占位符与 `deploy/.env.example` 模板。
- 欢迎提 Issue / PR；如果这个项目对你有帮助，点个 Star 就是最大的鼓励。
