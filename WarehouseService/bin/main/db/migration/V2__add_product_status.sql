ALTER TABLE products
    ADD COLUMN status      VARCHAR(20)  NOT NULL DEFAULT 'IN_WAREHOUSE',
    ADD COLUMN assigned_to VARCHAR(255) NULL;

ALTER TABLE products
    ADD CONSTRAINT chk_products_assigned_to
        CHECK (
            (status = 'ASSIGNED' AND assigned_to IS NOT NULL)
                OR
            (status != 'ASSIGNED' AND assigned_to IS NULL)
            );