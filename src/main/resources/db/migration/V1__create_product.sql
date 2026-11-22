CREATE TABLE product (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku         VARCHAR(32)    NOT NULL UNIQUE,
    name        VARCHAR(120)   NOT NULL,
    category    VARCHAR(40)    NOT NULL,
    price       NUMERIC(12, 2) NOT NULL CHECK (price > 0),
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX product_category_idx ON product (category, id);
