# 部署指南

> 本文是[项目 README](../README.md) 的部署分册，从服务器选型一直讲到日常运维。

面向「个人图库」这种小体量项目：**一台 2核2G 的轻量云服务器，用 Docker Compose 把 MySQL + Redis + 后端 + 前端一起跑起来**。

---

## 一、服务器怎么选

### 关键前提：要不要备案

这是最先要决定的，直接影响选哪里的机器：

| | 中国大陆机房 | 香港 / 海外机房 |
| --- | --- | --- |
| 域名备案 | **必须**，1~20 个工作日，需实名 | 不需要 |
| 国内访问速度 | 快（延迟 20~50ms） | 一般（延迟 50~150ms，晚高峰易抖动） |
| 价格 | 有大量新用户特惠，很便宜 | 略贵 |

- **只有 IP、不用域名**：大陆机房也可以，但 80/443 端口未备案会被拦，得用非标准端口访问。
- **想用域名 + 图省事**：选香港/海外，免备案，代价是国内访问慢一些。
- 你是个人项目，如果平时只给自己和少数人看，**香港轻量 + 非标端口** 或 **大陆轻量 + 备案** 都可行。

### 配置建议

这个项目的实际开销：Spring Boot 堆内存约 300~500MB、MySQL 约 300~400MB、Nginx 内存占用极小。

| 配置 | 是否可行 | 说明 |
| --- | --- | --- |
| 2核2G | **推荐起步** | 够用。MySQL 需要按 `docker-compose.yml` 里那样限制 buffer pool |
| 2核1G | 勉强 | 必须加 swap，且 MySQL 要压到 128M buffer pool，容易 OOM |
| 2核4G | 舒服 | 以后加 Redis、Elasticsearch 也有余量 |

**磁盘**：40GB 起步。注意图片本身放在腾讯云 COS 上，不占服务器磁盘，所以 40GB 只装系统和数据库，够用很久。

### 价格参考（2025 年，新用户特惠）

> 以下数字来自公开的促销文章，**价格波动很大，务必以官网活动页实时价为准**。

**腾讯云轻量应用服务器**（[参考](https://cloud.tencent.cn/developer/article/2581833?policyId=1004)）：

| 配置 | 促销价 |
| --- | --- |
| 4核4G3M / 40G SSD / 300G 月流量 | 38 元/年（限时秒杀）/ 79 元/年 |
| 2核2G3M / 40G SSD / 200G 月流量 | 68 元/年 |
| 2核2G4M | 99 元/年（**续费同价**） |
| 2核4G6M / 70G SSD / 600G 月流量 | 528 元/3 年 |

**阿里云轻量应用服务器**（[参考](https://developer.aliyun.com/article/1690936)）：

| 配置 | 促销价 |
| --- | --- |
| 轻量 2核2G / 40G ESSD / 峰值 200M | 38 元/年（每天 10 点、15 点抢购），常规 68 元/年 |
| 经济型 e 2核2G / 40G / 3M | 99 元/年（**续费同价**） |
| 通用算力型 u1 2核4G / 80G / 5M | 199 元/年（**续费同价**） |

**京东云**：2核4G / 5M / 500G 月流量，约 149 元/年（新人）。

### 三个避坑点

1. **一定选「续费同价」的机型。** 38 元/年那种秒杀款，第二年续费常常涨到 1000+ 元/年。这个坑比首年贵几十块严重得多。
2. **流量比带宽重要。** 图片走 COS，服务器流量主要是网页和接口，200G/月够个人用。但如果你打算去掉 COS、把图片存服务器本地，流量会涨得很快，要重新算。
3. **优先选你 COS 所在的那家云、同一个地域。** 你的 COS 在腾讯云广州，那就用腾讯云广州的机器 —— 可以走内网访问，省流量费、延迟也更低。

### 完全免费的方案

**Oracle Cloud 永久免费层**（[官方说明](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm)）配置相当大方：

- ARM（Ampere A1）：**2 OCPU + 12GB 内存**，或拆分最多 2 台
- AMD 微型实例：最多 2 台，各 1GB 内存
- 块存储 200GB，出网流量 10TB/月，外加 50GB 的 MySQL HeatWave

对 2核2G 来说这是绰绰有余的。但有几个现实问题：

- 注册需要**国际信用卡**验证（国内双币卡有时能过）
- 机房在海外（东京/首尔/大阪等），国内访问延迟高
- **闲置回收**：官方明确写了，7 天内 CPU 95 分位 <20%、网络 <20%、内存 <20% 会被判定为闲置并回收。个人图库访问量低，有被回收的风险
- 热门区域经常 `out of host capacity`，抢不到实例

适合当练手环境，**不建议把主站放上去**。

其他 PaaS（Railway、Render、Fly.io、Vercel + PlanetScale）也能跑，免费额度通常只有 512MB 内存，Spring Boot 跑起来很勉强，MySQL 免费额度也有行数/容量限制。**个人项目还是自建一台 VPS 最省心。**

---

## 二、部署步骤

前置：服务器装好 Docker 与 Compose（腾讯云/阿里云选 Ubuntu 22.04 镜像，然后 `curl -fsSL https://get.docker.com | sh`）。

### 1. 把代码传到服务器

```bash
# 方式一：git（推荐，方便后续更新）
git clone <你的仓库地址> picture && cd picture

# 方式二：本地打包上传（注意排除 target/ 和 .idea/）
scp -r ./* root@<服务器IP>:/root/picture/
```

> `application-local.yml` 被 `.gitignore` 排除了，**不会**进仓库 —— 这正是我们想要的，服务器上的密钥走环境变量注入。

### 2. 配置环境变量

```bash
cd picture
cp deploy/.env.example deploy/.env
vi deploy/.env      # 填 MySQL 密码、Redis 密码、腾讯云 COS 五个参数
chmod 600 deploy/.env   # 里面是明文密钥，收紧权限
```

COS 的五个参数抄你本地 `src/main/resources/application-local.yml` 里的值即可。

### 3. 启动

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d --build
```

第一次会构建镜像（Maven 要下依赖，慢一些）。起来后看状态：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.yml ps
docker compose --env-file deploy/.env -f deploy/docker-compose.yml logs -f app
```

看到 `Started DemoApplication` 就成功了，浏览器访问 `http://<服务器IP>/`。

### 4. 建管理员账号

数据库里的用户密码是 **加盐 MD5**（盐值 `yupi`），不能直接 INSERT 明文。两种方式：

**方式一（推荐）**：先用前端注册一个普通账号，再用 SQL 改成管理员：

```bash
docker exec -it picture-mysql mysql -uroot -p picture
```

```sql
UPDATE user SET userRole = 'admin' WHERE userAccount = '你的账号';
```

**方式二**：直接插入算好的哈希。盐值 `yupi` + 明文密码做 MD5：

```sql
INSERT INTO user (id, userAccount, userPassword, userName, userRole)
VALUES (1, 'admin', '<yupi+密码 的 MD5>', '管理员', 'admin');
```

### 5. 加域名和 HTTPS（可选）

- 域名解析 A 记录指向服务器 IP
- 大陆机房要先在云厂商备案系统提交
- HTTPS 用 Nginx 挂证书，或直接在前面加一层云厂商的免费 SSL

---

## 三、Redis 与列表缓存

`docker-compose.yml` 里 Redis 已经配好并起了，后端也**已经接上多级缓存**：

```
Caffeine（本机，5 分钟）→ Redis（分布式，5~10 分钟随机）→ MySQL
```

接口：`POST /picture/list/page/vo/cache`，只缓存普通用户可见的公开数据（强制 `reviewStatus = 已通过`），所以缓存内容与用户身份无关，可以共用同一个 key。

前端默认**走不带缓存的接口**，由 `front/config.js` 里的开关控制：

```js
window.__USE_CACHE__ = false;   // 本地没起 Redis 时保持 false
```

用提供的 Nginx 配置部署时，`sub_filter` 会把它自动改成 `true`（因为服务器上 Redis 是起着的）。**如果你自己不打算跑 Redis，记得把 `deploy/nginx/default.conf` 里那条 `__USE_CACHE__` 的 sub_filter 去掉**，否则首页会请求一个需要 Redis 的接口而加载失败。

本地想试缓存效果：

```bash
docker run -d --name redis -p 6379:6379 redis:7-alpine
# 然后把 front/config.js 里的 __USE_CACHE__ 改成 true
```

### 两个已知的缓存行为

1. **Redis 侧没有主动失效**。图片增删改、审核之后，代码会清掉本机 Caffeine（`clearPictureCache()`），但 Redis 里的数据靠 5~10 分钟 TTL 自然过期。单机部署下最多看到几分钟的旧列表，可以接受；**如果以后多实例部署、要求写入后立刻一致**，需要把 `PictureServiceImpl.clearPictureCache()` 里的注释落实 —— 一并删掉 Redis 中 `picture:listVO:` 开头的 key。
2. **缓存 key 只覆盖公开列表**。因为强制了 `reviewStatus = 已通过`，管理端按状态筛选的请求不会被缓存，也不会互相污染。

### Redis 后续还能用在哪

- **登录态**：目前用 `HttpSession`（单机内存），以后扩多台必须换成 Redis 存 session
- **批量抓取限流**：防止有人反复触发抓取

---

## 四、日常运维

```bash
cd picture

# 更新代码后重新部署
git pull
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d --build app front

# 看日志
docker compose --env-file deploy/.env -f deploy/docker-compose.yml logs -f app

# 备份数据库（建议加到 crontab 每天跑）
docker exec picture-mysql mysqldump -uroot -p$MYSQL_ROOT_PASSWORD picture > backup-$(date +%F).sql

# 只重启后端
docker compose --env-file deploy/.env -f deploy/docker-compose.yml restart app
```

**注意**：`mysql-data` 卷里的数据是持久化的，但 `deploy/mysql/init.sql` **只在数据卷为空时执行一次**。以后加表/加字段，要手动进容器执行 SQL。

已经准备好的增量脚本：

```bash
# 给已有库补充缩略图字段（缩略图功能需要）
docker exec -i picture-mysql mysql -uroot -p picture < deploy/mysql/migration-add-thumbnail.sql
```

也可以直接进容器手动执行：

```bash
docker exec -it picture-mysql mysql -uroot -p picture
```

---

## 五、部署前必须知道的两个改动

我修了两个会直接导致部署失败的问题，你拉代码时会一起带上：

**1. `pom.xml` 里 `<skip>true</skip>` 导致打不出可执行 jar**

原配置会跳过 `repackage`，`mvn package` 产出的是 **0.1MB、没有 Main-Class、没有内嵌依赖** 的普通 jar，`java -jar` 会直接报错。已移除该配置，现在打出来是 **50MB 的可执行 fat jar**（已验证内嵌 87 个依赖）。

**2. `sql/` 下的建表脚本不能直接用于初始化**

- `create_table_sql.sql` 建的是 `yu_picture` 库，而 `application.yml` 连的是 `picture` 库，**对不上**
- `create_table_picture.sql` 用 `ALTER TABLE` 追加审核字段，**重复执行会报 Duplicate column**
- 脚本里写死 `use picture`，容器初始化时容易因库不存在而失败

已统一整理为 [`deploy/mysql/init.sql`](../deploy/mysql/init.sql)，全部 `CREATE TABLE IF NOT EXISTS`，审核字段直接建在表里，可重复执行。

**另外提醒**：`application.yml` 里的数据库密码是明文 `748374075`，服务器上通过 `SPRING_DATASOURCE_PASSWORD` 覆盖，别把本地密码带到线上。本地那份 yml 建议也改掉。
