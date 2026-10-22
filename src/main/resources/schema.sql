PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS big_activity (
    id                       INTEGER PRIMARY KEY AUTOINCREMENT,
    name                     TEXT    NOT NULL,
    act_type                 TEXT    NOT NULL,
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

CREATE INDEX IF NOT EXISTS idx_big_activity_name ON big_activity (name);
CREATE INDEX IF NOT EXISTS idx_big_activity_act_type ON big_activity (act_type);

CREATE TABLE IF NOT EXISTS product_activity (
    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
    act_id                  INTEGER NOT NULL,
    act_type                TEXT    NOT NULL,
    poi_id                  TEXT    NOT NULL,
    sku_id                  TEXT,
    upc                     TEXT,
    product_discount_json   TEXT    NOT NULL DEFAULT '{}',
    created_by              TEXT    NOT NULL,
    created_at              TEXT    NOT NULL,
    updated_by              TEXT    NOT NULL,
    updated_at              TEXT    NOT NULL,
    CONSTRAINT fk_product_activity_big_activity
        FOREIGN KEY (act_id) REFERENCES big_activity (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_product_activity_act_id ON product_activity (act_id);
CREATE INDEX IF NOT EXISTS idx_product_activity_poi_id ON product_activity (poi_id);
CREATE INDEX IF NOT EXISTS idx_product_activity_sku_id ON product_activity (sku_id);
CREATE INDEX IF NOT EXISTS idx_product_activity_upc ON product_activity (upc);
