CREATE TABLE orders
(
    id          UUID PRIMARY KEY,
    sku         VARCHAR(100) NOT NULL,
    quantity    INTEGER      NOT NULL,
    status      VARCHAR(30)  NOT NULL,

    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,

    CONSTRAINT chk_orders_quantity_positive
        CHECK (quantity > 0)
);