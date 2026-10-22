-- 旧库升级脚本：act_type / poi_id / sku_id 修正为整数类型
-- 适用版本：字段仍为 TEXT 的 act-mcp 数据库
--
-- 使用方法（先停止服务并备份 data/act-mcp.db）：
--   sqlite3 data/act-mcp.db < scripts/migrate-v2-int-types.sql
--
-- act_type 映射：1=满减活动，2=折扣活动，3=秒杀活动，4=优惠券活动，5=组合优惠

PRAGMA foreign_keys = OFF;

BEGIN TRANSACTION;

CREATE TABLE big_activity_new (
    id                       INTEGER PRIMARY KEY AUTOINCREMENT,
    name                     TEXT    NOT NULL,
    act_type                 INTEGER NOT NULL,
    start_time               TEXT    NOT NULL,
    end_time                 TEXT    NOT NULL,
    audience_scope           TEXT    NOT NULL DEFAULT 'ALL',
    budget                   NUMERIC NOT NULL DEFAULT 0,
    collection_discount_json TEXT    NOT NULL DEFAULT '{}',
    created_by               TEXT    NOT NULL,
    created_at               TEXT    NOT NULL,
    updated_by               TEXT    NOT NULL,
    updated_at               TEXT    NOT NULL
);

INSERT INTO big_activity_new
    (id, name, act_type, start_time, end_time, audience_scope, budget,
     collection_discount_json, created_by, created_at, updated_by, updated_at)
SELECT id,
       name,
       CASE TRIM(CAST(act_type AS TEXT))
           WHEN '满减活动' THEN 1
           WHEN '折扣活动' THEN 2
           WHEN '秒杀活动' THEN 3
           WHEN '优惠券活动' THEN 4
           WHEN '组合优惠' THEN 5
           ELSE CAST(act_type AS INTEGER)
       END,
       start_time,
       end_time,
       audience_scope,
       budget,
       collection_discount_json,
       created_by,
       created_at,
       updated_by,
       updated_at
FROM big_activity;

CREATE TABLE product_activity_new (
    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
    act_id                  INTEGER NOT NULL,
    act_type                INTEGER NOT NULL,
    poi_id                  INTEGER NOT NULL,
    sku_id                  INTEGER,
    upc                     TEXT,
    product_discount_json   TEXT    NOT NULL DEFAULT '{}',
    created_by              TEXT    NOT NULL,
    created_at              TEXT    NOT NULL,
    updated_by              TEXT    NOT NULL,
    updated_at              TEXT    NOT NULL,
    CONSTRAINT fk_product_activity_big_activity
        FOREIGN KEY (act_id) REFERENCES big_activity (id) ON DELETE CASCADE
);

INSERT INTO product_activity_new
    (id, act_id, act_type, poi_id, sku_id, upc, product_discount_json,
     created_by, created_at, updated_by, updated_at)
SELECT id,
       act_id,
       CASE TRIM(CAST(act_type AS TEXT))
           WHEN '满减活动' THEN 1
           WHEN '折扣活动' THEN 2
           WHEN '秒杀活动' THEN 3
           WHEN '优惠券活动' THEN 4
           WHEN '组合优惠' THEN 5
           ELSE CAST(act_type AS INTEGER)
       END,
       CAST(poi_id AS INTEGER),
       CASE
           WHEN sku_id IS NULL OR TRIM(CAST(sku_id AS TEXT)) = '' THEN NULL
           ELSE CAST(sku_id AS INTEGER)
       END,
       upc,
       product_discount_json,
       created_by,
       created_at,
       updated_by,
       updated_at
FROM product_activity;

DROP TABLE product_activity;
DROP TABLE big_activity;

ALTER TABLE product_activity_new RENAME TO product_activity;
ALTER TABLE big_activity_new RENAME TO big_activity;

CREATE INDEX idx_big_activity_name ON big_activity (name);
CREATE INDEX idx_big_activity_act_type ON big_activity (act_type);
CREATE INDEX idx_product_activity_act_id ON product_activity (act_id);
CREATE INDEX idx_product_activity_poi_id ON product_activity (poi_id);
CREATE INDEX idx_product_activity_sku_id ON product_activity (sku_id);
CREATE INDEX idx_product_activity_upc ON product_activity (upc);

COMMIT;

PRAGMA foreign_keys = ON;
