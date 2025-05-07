CREATE TABLE wallet
(
  name                     VARCHAR(255)                              NOT NULL PRIMARY KEY,
  version                  INT UNSIGNED                                        NOT NULL,
  encrypted_entropy        VARBINARY(61)                                                            NOT NULl,
  encrypted_encryption_key VARBINARY(60),
  persisted_at             TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  updated_at               TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) NOT NULL
);

CREATE TABLE account
(
  address       VARCHAR(61)                               NOT NULL PRIMARY KEY,
  version       INT UNSIGNED                                    NOT NULl,
  account_index INT UNSIGNED                                    NOT NULL,
  wallet_name   VARCHAR(255)                              NOT NULL,
  persisted_at  TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  updated_at    TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) NOT NULL,
  disabled_at   TIMESTAMP(6) NULL,
  CONSTRAINT fk_account_wallet FOREIGN KEY (wallet_name) REFERENCES wallet (name)
);
