-- MySQL：在已建表的 growth 数据库中手动执行；可重复执行。
-- 5 GB 按项目现有 1024 进制计算，即 5 GiB = 5368709120 字节。
-- 只升级旧默认 512 MiB 配额，保留其他自定义配额，不改变已用容量或媒体文件。
-- ALTER TABLE 会隐式提交；本脚本不依赖外层事务整体回滚。
ALTER TABLE family ALTER COLUMN storage_quota SET DEFAULT 5368709120;

UPDATE family
SET storage_quota = 5368709120
WHERE storage_quota = 536870912;
