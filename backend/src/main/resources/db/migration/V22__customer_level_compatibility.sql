ALTER TABLE crm_customer
    ALTER COLUMN level TYPE VARCHAR(10);

ALTER TABLE crm_customer
    ALTER COLUMN level DROP DEFAULT;

CREATE INDEX IF NOT EXISTS idx_customer_update_time
    ON crm_customer (update_time DESC, create_time DESC);

COMMENT ON COLUMN crm_customer.level IS 'Customer level';
