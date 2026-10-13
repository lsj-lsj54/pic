-- 云图库数据库初始化脚本（可重复执行）
--
-- 与 src/main/resources/application.yml 中的库名保持一致：picture
-- 注意原来的两份脚本有几个坑，已在部署时统一到这里：
--   1. create_table_picture.sql 用 ALTER TABLE 追加审核字段，重复执行会报 Duplicate column；
--   2. 脚本里写死 `use picture`，容器初始化时容易因为库不存在而失败。
-- 这里全部改成 CREATE TABLE IF NOT EXISTS，并把审核字段直接放进建表语句。

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
    userId        bigint                             NOT NULL COMMENT '创建用户 id',
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
    INDEX idx_reviewStatus (reviewStatus)
) COMMENT '图片' COLLATE = utf8mb4_unicode_ci;
