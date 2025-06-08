CREATE TABLE notification_state
(
  address      VARCHAR(61)                               NOT NULL PRIMARY KEY,
  version      BIGINT UNSIGNED                           NOT NULL,
  height       BIGINT UNSIGNED                           NOT NULL,
  persisted_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  updated_at   TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_notification_account FOREIGN KEY (address) REFERENCES account (address)
);
