-- 迁移：picture.spaceId 的「公共图库」语义从 NULL 改为 0
-- 背景：旧版本用 spaceId 为 NULL 表示公共图库，新版本统一用 0 表示，
--       使所有请求都能显式携带 spaceId（公共图库传 0），并配合 ShardingSphere 按 spaceId 分表。
-- 执行前建议先备份 picture 表。

-- 1. 回填 NULL 为 0
UPDATE picture SET spaceId = 0 WHERE spaceId IS NULL;

-- 2. 列改为 NOT NULL DEFAULT 0
ALTER TABLE picture MODIFY spaceId bigint NOT NULL DEFAULT 0 COMMENT '空间 id（0 表示公共图库，>0 表示私有/团队空间）';

-- 3. 已存在的团队空间分表同样回填（若有数据）并同步列定义
--    （分表名规则为 picture_<spaceId>，可先 SHOW TABLES LIKE 'picture_%' 确认后再执行）
-- UPDATE picture_<spaceId> SET spaceId = 0 WHERE spaceId IS NULL;
-- ALTER TABLE picture_<spaceId> MODIFY spaceId bigint NOT NULL DEFAULT 0;
