-- Apply BEFORE starting a production installation with ddl-auto=validate.
-- Local/QA/desktop ddl-auto=update applies the entity changes on restart.
BEGIN;
ALTER TABLE bills ADD COLUMN IF NOT EXISTS creation_request_key varchar(36);
ALTER TABLE bills ADD COLUMN IF NOT EXISTS creation_fingerprint varchar(64);
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_bills_tenant_request_key'
                   AND conrelid = 'bills'::regclass) THEN
        ALTER TABLE bills ADD CONSTRAINT uk_bills_tenant_request_key UNIQUE (tenant_id, creation_request_key);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_customers_tenant_contact'
                   AND conrelid = 'customers'::regclass) THEN
        ALTER TABLE customers ADD CONSTRAINT uk_customers_tenant_contact UNIQUE (tenant_id, contact_number);
    END IF;
END $$;
COMMIT;
