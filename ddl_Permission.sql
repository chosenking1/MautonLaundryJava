CREATE TABLE permissions
(
    id            BIGINT AUTO_INCREMENT NOT NULL,
    name          VARCHAR(255)          NOT NULL,
    `description` VARCHAR(255)          NULL,
    `resource`    VARCHAR(255)          NOT NULL,
    action        VARCHAR(255)          NOT NULL,
    CONSTRAINT pk_permissions PRIMARY KEY (id)
);

ALTER TABLE permissions
    ADD CONSTRAINT uc_permissions_name UNIQUE (name);