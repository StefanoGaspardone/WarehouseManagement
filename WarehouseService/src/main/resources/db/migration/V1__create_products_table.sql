CREATE TABLE products (
  id          UUID            NOT NULL DEFAULT gen_random_uuid(),
  name        VARCHAR(255)    NOT NULL,
  bar_code    VARCHAR(13)     NOT NULL,
  created_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  updated_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL,

  CONSTRAINT pk_products PRIMARY KEY (id),
  CONSTRAINT uq_products_bar_code UNIQUE (bar_code),
  CONSTRAINT chk_products_bar_code CHECK (bar_code ~ '^[0-9]{8,13}$')
);