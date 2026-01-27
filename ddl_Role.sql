CREATE TABLE role_permissions
(
    permission_id BIGINT NOT NULL,
    role_id       BIGINT NOT NULL,
    CONSTRAINT pk_role_permissions PRIMARY KEY (permission_id, role_id)
);

CREATE TABLE roles
(
    id            BIGINT AUTO_INCREMENT NOT NULL,
    name          VARCHAR(255)          NOT NULL,
    `description` VARCHAR(255)          NULL,
    CONSTRAINT pk_roles PRIMARY KEY (id)
);

ALTER TABLE roles
    ADD CONSTRAINT uc_roles_name UNIQUE (name);

ALTER TABLE role_permissions
    ADD CONSTRAINT fk_rolper_on_permission FOREIGN KEY (permission_id) REFERENCES permissions (id);

ALTER TABLE role_permissions
    ADD CONSTRAINT fk_rolper_on_role FOREIGN KEY (role_id) REFERENCES roles (id);


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