-- 为已有 picture 表补充主色调字段（用于「按颜色查图」）
--
-- 适用场景：数据库已经存在 picture 表，但不想重跑 create_table_picture.sql（重跑会因
-- reviewStatus / spaceId 列已存在而报错）时，单独执行本文件即可。
--
-- 主色调由腾讯云数据万象 imageAve 接口在上传时回填，历史数据该字段为空，
-- 查询会按 RGB 欧氏距离筛选，空值图片会被自动排除。

ALTER TABLE picture
    ADD COLUMN picColor  varchar(7)        null comment '主色调（腾讯云 imageAve 返回，形如 #736246）',
    ADD COLUMN picColorR tinyint unsigned  null comment '主色调 R 分量（0-255）',
    ADD COLUMN picColorG tinyint unsigned  null comment '主色调 G 分量（0-255）',
    ADD COLUMN picColorB tinyint unsigned  null comment '主色调 B 分量（0-255）';
