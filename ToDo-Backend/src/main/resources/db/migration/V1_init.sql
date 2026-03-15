-- V1__init.sql
-- Flyway manages all schema changes. Never use ddl-auto=create/update in production.

CREATE TABLE users (
                       id          BIGINT          NOT NULL AUTO_INCREMENT,
                       username    VARCHAR(50)     NOT NULL UNIQUE,
                       email       VARCHAR(100)    NOT NULL UNIQUE,
                       password    VARCHAR(255)    NOT NULL,
                       created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                       PRIMARY KEY (id),
                       INDEX idx_users_email (email),
                       INDEX idx_users_username (username)
);

CREATE TABLE todos (
                       id          BIGINT          NOT NULL AUTO_INCREMENT,
                       user_id     BIGINT          NOT NULL,
                       title       VARCHAR(255)    NOT NULL,
                       description TEXT,
                       status      ENUM('PENDING','IN_PROGRESS','DONE') NOT NULL DEFAULT 'PENDING',
                       priority    ENUM('LOW','MEDIUM','HIGH')           NOT NULL DEFAULT 'MEDIUM',
                       due_date    DATE,
                       created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                       PRIMARY KEY (id),
                       FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                       INDEX idx_todos_user_id (user_id),               -- hot path: fetch by user
                       INDEX idx_todos_user_status (user_id, status),   -- composite for filtered queries
                       INDEX idx_todos_due_date (due_date)
);

CREATE TABLE audit_log (
                           id          BIGINT          NOT NULL AUTO_INCREMENT,
                           todo_id     BIGINT,
                           user_id     BIGINT,
                           action      VARCHAR(50)     NOT NULL,             -- CREATE, UPDATE, DELETE
                           payload     JSON,                                 -- event snapshot
                           created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           PRIMARY KEY (id),
                           INDEX idx_audit_todo_id (todo_id),
                           INDEX idx_audit_user_id (user_id)
);