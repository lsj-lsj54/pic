-- 云图库数据库初始化脚本（可重复执行）
--
-- 与 src/main/resources/application.yml 中的库名保持一致：picture
-- 覆盖三张表：user（用户）、picture（图片，含审核字段与空间归属）、space（空间）。
--
-- 注意原来的几份脚本有几个坑，已在部署时统一到这里：
--   1. create_table_picture.sql 用 ALTER TABLE 追加审核字段，重复执行会报 Duplicate column；
--   2. 脚本里写死 `use picture`，容器初始化时容易因为库不存在而失败；
--   3. 早期版本漏了 space 表与 picture.spaceId，空间功能会直接报 Unknown column。
--   4. 早期版本漏了 picture.picColor 系列字段，实体已经声明了这些列，
--      MyBatis-Plus 查询会 SELECT 出来，库里没有就会报 Unknown column，
--      表现为「首页图片全部加载不出来、接口 500」。
-- 这里全部改成 CREATE TABLE IF NOT EXISTS，并把审核字段、空间字段、主色调字段直接放进建表语句。
--
-- 已有数据库的增量升级见同目录：migration-add-thumbnail.sql、migration-add-picture-color.sql
-- （init.sql 只在 MySQL 数据卷为空时执行一次，不会作用于已存在的库）。

CREATE DATABASE IF NOT EXISTS `picture`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `picture`;

-- 用户表
CREATE TABLE IF NOT EXISTS `user`
(
    id           bigint AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    userAccount  varchar(256)                           NOT NULL COMMENT '账号',
    userPassword varchar(512)                           NOT NULL COMMENT '密码',
    userName     varchar(256)                           NULL COMMENT '用户昵称',
    userAvatar   varchar(1024)                          NULL COMMENT '用户头像',
    userProfile  varchar(512)                           NULL COMMENT '用户简介',
    userRole     varchar(256) DEFAULT 'user'            NOT NULL COMMENT '用户角色：user/admin',
    editTime     datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '编辑时间',
    createTime   datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updateTime   datetime     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    isDelete     tinyint      DEFAULT 0                 NOT NULL COMMENT '是否删除',
    UNIQUE KEY uk_userAccount (userAccount),
    INDEX idx_userName (userName)
) COMMENT '用户' COLLATE = utf8mb4_unicode_ci;

-- 图片表（审核相关字段直接建在表里）
CREATE TABLE IF NOT EXISTS `picture`
(
    id            bigint AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    url           varchar(512)                       NOT NULL COMMENT '图片 url（原图）',
    thumbnailUrl  varchar(512)                       NULL COMMENT '缩略图 url（压缩后的 webp），列表页使用',
    name          varchar(128)                       NOT NULL COMMENT '图片名称',
    introduction  varchar(512)                       NULL COMMENT '简介',
    category      varchar(64)                        NULL COMMENT '分类',
    tags          varchar(512)                       NULL COMMENT '标签（JSON 数组）',
    picSize       bigint                             NULL COMMENT '图片体积',
    picWidth      int                                NULL COMMENT '图片宽度',
    picHeight     int                                NULL COMMENT '图片高度',
    picScale      double                             NULL COMMENT '图片宽高比例',
    picFormat     varchar(32)                        NULL COMMENT '图片格式',
    picColor      varchar(7)                         NULL COMMENT '主色调（腾讯云 imageAve 返回，形如 #736246）',
    picColorR     tinyint unsigned                   NULL COMMENT '主色调 R 分量（0-255）',
    picColorG     tinyint unsigned                   NULL COMMENT '主色调 G 分量（0-255）',
    picColorB     tinyint unsigned                   NULL COMMENT '主色调 B 分量（0-255）',
    userId        bigint                             NOT NULL COMMENT '创建用户 id',
    spaceId       bigint                             NOT NULL DEFAULT 0 COMMENT '空间 id（0 表示公共图库，>0 表示私有/团队空间）',
    reviewStatus  int      DEFAULT 0                 NOT NULL COMMENT '审核状态：0-待审核; 1-通过; 2-拒绝',
    reviewMessage varchar(512)                       NULL COMMENT '审核信息',
    reviewerId    bigint                             NULL COMMENT '审核人 ID',
    reviewTime    datetime                           NULL COMMENT '审核时间',
    createTime    datetime DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    editTime      datetime DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '编辑时间',
    updateTime    datetime DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    isDelete      tinyint  DEFAULT 0                 NOT NULL COMMENT '是否删除',
    INDEX idx_name (name),
    INDEX idx_introduction (introduction),
    INDEX idx_category (category),
    INDEX idx_tags (tags),
    INDEX idx_userId (userId),
    INDEX idx_spaceId (spaceId),
    INDEX idx_reviewStatus (reviewStatus)
) COMMENT '图片' COLLATE = utf8mb4_unicode_ci;

-- 空间表
-- 说明：私有空间是图片的容器，picture.spaceId 指向这里；
--     spaceId = 0 的图片属于「公共图库」，参与大众审核与展示。
CREATE TABLE IF NOT EXISTS `space`
(
    id         bigint AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    spaceName  varchar(128)                        NULL COMMENT '空间名称',
    spaceLevel int      DEFAULT 0                  NULL COMMENT '空间级别：0-普通版 1-专业版 2-旗舰版',
    maxSize    bigint   DEFAULT 0                  NULL COMMENT '空间图片的最大总大小',
    maxCount   bigint   DEFAULT 0                  NULL COMMENT '空间图片的最大数量',
    totalSize  bigint   DEFAULT 0                  NULL COMMENT '当前空间下图片的总大小',
    totalCount bigint   DEFAULT 0                  NULL COMMENT '当前空间下的图片数量',
    userId     bigint                              NOT NULL COMMENT '创建用户 id',
    createTime datetime DEFAULT CURRENT_TIMESTAMP  NOT NULL COMMENT '创建时间',
    editTime   datetime DEFAULT CURRENT_TIMESTAMP  NOT NULL COMMENT '编辑时间',
    updateTime datetime DEFAULT CURRENT_TIMESTAMP  NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    isDelete   tinyint  DEFAULT 0                  NOT NULL COMMENT '是否删除',
    INDEX idx_userId (userId),
    INDEX idx_spaceName (spaceName),
    INDEX idx_spaceLevel (spaceLevel)
) COMMENT '空间' COLLATE = utf8mb4_unicode_ci;
