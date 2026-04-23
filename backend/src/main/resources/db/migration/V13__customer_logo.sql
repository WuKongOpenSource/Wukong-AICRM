ALTER TABLE crm_customer
    ADD COLUMN IF NOT EXISTS logo VARCHAR(500);

COMMENT ON COLUMN crm_customer.logo IS '公司Logo';
