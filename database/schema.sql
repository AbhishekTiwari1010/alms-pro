-- ================================================================
--  ALMS PRO — Advanced Lift Management System
--  MySQL 8.0+ Schema
-- ================================================================

CREATE DATABASE IF NOT EXISTS alms_pro CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE alms_pro;

-- ----------------------------------------------------------------
-- BUILDINGS  (multi-building support)
-- ----------------------------------------------------------------
CREATE TABLE `buildings` (
    `id`           BIGINT AUTO_INCREMENT PRIMARY KEY,
    `code`         VARCHAR(20)  NOT NULL UNIQUE,   -- e.g. "HQ", "TOWER_B"
    `name`         VARCHAR(100) NOT NULL,
    `total_floors` INT          NOT NULL DEFAULT 60,
    `total_lifts`  INT          NOT NULL DEFAULT 6,
    `is_active`    TINYINT(1)   NOT NULL DEFAULT 1,
    `created_at`   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ----------------------------------------------------------------
-- USERS  (employees + admins)
-- ----------------------------------------------------------------
CREATE TABLE `users` (
    `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
    `employee_id`     VARCHAR(50)  NOT NULL UNIQUE,
    `email`           VARCHAR(255) NOT NULL UNIQUE,
    `password_hash`   VARCHAR(255) NOT NULL,
    `full_name`       VARCHAR(100) NOT NULL,
    `home_floor`      INT          NOT NULL DEFAULT 1,
    `building_id`     BIGINT       NOT NULL,
    `user_role`       ENUM('EMPLOYEE','ADMIN','SUPER_ADMIN') NOT NULL DEFAULT 'EMPLOYEE',
    `is_active`       TINYINT(1)   NOT NULL DEFAULT 1,
    `last_login`      TIMESTAMP    NULL DEFAULT NULL,
    `created_at`      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT `fk_user_building` FOREIGN KEY (`building_id`) REFERENCES `buildings`(`id`)
);

-- ----------------------------------------------------------------
-- LIFTS  (fully dynamic — no permanent floor zones)
-- ----------------------------------------------------------------
CREATE TABLE `lifts` (
    `id`                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    `building_id`          BIGINT       NOT NULL,
    `lift_number`          INT          NOT NULL,
    `lift_name`            VARCHAR(20)  NOT NULL,          -- e.g. "L-1", "Express-A"
    `current_floor`        INT          NOT NULL DEFAULT 1,
    `lift_status`          ENUM('IDLE','BUSY','MAINTENANCE','OFFLINE') NOT NULL DEFAULT 'IDLE',
    `lift_type`            ENUM('STANDARD','EXPRESS','FREIGHT','VIP') NOT NULL DEFAULT 'STANDARD',
    `capacity`             INT          NOT NULL DEFAULT 10, -- max persons
    `current_load`         INT          NOT NULL DEFAULT 0,
    `assigned_block_start` INT          NULL DEFAULT NULL,
    `assigned_block_end`   INT          NULL DEFAULT NULL,
    `active_trip_count`    INT          NOT NULL DEFAULT 0,
    `total_trips`          BIGINT       NOT NULL DEFAULT 0,  -- lifetime stat
    `last_maintenance`     TIMESTAMP    NULL DEFAULT NULL,
    `last_updated`         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uq_lift_building` (`building_id`, `lift_number`),
    CONSTRAINT `fk_lift_building` FOREIGN KEY (`building_id`) REFERENCES `buildings`(`id`)
);

-- ----------------------------------------------------------------
-- USER SAVED ROUTES  (custom named floor pairs)
-- ----------------------------------------------------------------
CREATE TABLE `saved_routes` (
    `id`           BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id`      BIGINT       NOT NULL,
    `route_name`   VARCHAR(100) NOT NULL,
    `from_floor`   INT          NOT NULL,
    `to_floor`     INT          NOT NULL,
    `icon`         VARCHAR(50)  NULL DEFAULT 'arrow-up',
    `color`        VARCHAR(20)  NULL DEFAULT '#00d4ff',
    `use_count`    INT          NOT NULL DEFAULT 0,
    `created_at`   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT `fk_route_user` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`)
);

-- ----------------------------------------------------------------
-- LIFT TRIPS  (full audit log)
-- ----------------------------------------------------------------
CREATE TABLE `lift_trips` (
    `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id`         BIGINT       NOT NULL,
    `employee_id`     VARCHAR(50)  NOT NULL,
    `lift_id`         BIGINT       NULL,           -- NULL if queued and never assigned
    `lift_number`     INT          NOT NULL DEFAULT -1,
    `building_id`     BIGINT       NOT NULL,
    `from_floor`      INT          NOT NULL,
    `to_floor`        INT          NOT NULL,
    `trip_type`       ENUM('ENTRY','EXIT','INTER_FLOOR','SCHEDULED') NOT NULL,
    `route_name`      VARCHAR(100) NULL,
    `trip_status`     ENUM('QUEUED','ASSIGNED','IN_PROGRESS','COMPLETED','CANCELLED') NOT NULL DEFAULT 'ASSIGNED',
    `priority`        ENUM('NORMAL','HIGH','EMERGENCY') NOT NULL DEFAULT 'NORMAL',
    `wait_seconds`    INT          NULL,            -- how long user waited
    `travel_seconds`  INT          NULL,            -- actual travel time
    `requested_at`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `assigned_at`     TIMESTAMP    NULL DEFAULT NULL,
    `completed_at`    TIMESTAMP    NULL DEFAULT NULL,
    CONSTRAINT `fk_trip_user`     FOREIGN KEY (`user_id`)     REFERENCES `users`(`id`),
    CONSTRAINT `fk_trip_building` FOREIGN KEY (`building_id`) REFERENCES `buildings`(`id`)
);

-- ----------------------------------------------------------------
-- LIFT QUEUE  (requests waiting for a free lift)
-- ----------------------------------------------------------------
CREATE TABLE `lift_queue` (
    `id`           BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id`      BIGINT       NOT NULL,
    `employee_id`  VARCHAR(50)  NOT NULL,
    `building_id`  BIGINT       NOT NULL,
    `from_floor`   INT          NOT NULL,
    `to_floor`     INT          NOT NULL,
    `trip_type`    ENUM('ENTRY','EXIT','INTER_FLOOR','SCHEDULED') NOT NULL,
    `route_name`   VARCHAR(100) NULL,
    `priority`     ENUM('NORMAL','HIGH','EMERGENCY') NOT NULL DEFAULT 'NORMAL',
    `queue_status` ENUM('WAITING','PROCESSING','DONE','EXPIRED') NOT NULL DEFAULT 'WAITING',
    `queued_at`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `expires_at`   TIMESTAMP    NULL DEFAULT NULL,
    CONSTRAINT `fk_queue_user`     FOREIGN KEY (`user_id`)     REFERENCES `users`(`id`),
    CONSTRAINT `fk_queue_building` FOREIGN KEY (`building_id`) REFERENCES `buildings`(`id`)
);

-- ----------------------------------------------------------------
-- MAINTENANCE LOG
-- ----------------------------------------------------------------
CREATE TABLE `maintenance_log` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `lift_id`     BIGINT        NOT NULL,
    `reported_by` BIGINT        NOT NULL,
    `issue`       VARCHAR(255)  NOT NULL,
    `notes`       TEXT          NULL,
    `log_status`  ENUM('REPORTED','IN_PROGRESS','RESOLVED') NOT NULL DEFAULT 'REPORTED',
    `created_at`  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `resolved_at` TIMESTAMP     NULL DEFAULT NULL,
    CONSTRAINT `fk_maint_lift` FOREIGN KEY (`lift_id`)     REFERENCES `lifts`(`id`),
    CONSTRAINT `fk_maint_user` FOREIGN KEY (`reported_by`) REFERENCES `users`(`id`)
);

-- ----------------------------------------------------------------
-- ANALYTICS SNAPSHOTS  (hourly usage summaries for dashboard)
-- ----------------------------------------------------------------
CREATE TABLE `analytics_snapshots` (
    `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
    `building_id`     BIGINT    NOT NULL,
    `snapshot_hour`   TIMESTAMP NOT NULL,
    `total_trips`     INT       NOT NULL DEFAULT 0,
    `avg_wait_secs`   DECIMAL(8,2) NULL,
    `avg_travel_secs` DECIMAL(8,2) NULL,
    `peak_floor`      INT       NULL,
    `queued_count`    INT       NOT NULL DEFAULT 0,
    CONSTRAINT `fk_snap_building` FOREIGN KEY (`building_id`) REFERENCES `buildings`(`id`)
);

-- ================================================================
-- SEED DATA
-- ================================================================

-- Default building
INSERT INTO `buildings` (`code`, `name`, `total_floors`, `total_lifts`)
VALUES ('HQ', 'Headquarters Tower', 60, 6);

-- Lifts — all dynamic, start IDLE at floor 1
INSERT INTO `lifts`
    (`building_id`,`lift_number`,`lift_name`,`current_floor`,`lift_status`,`lift_type`,`capacity`)
VALUES
(1, 1, 'L-1', 1, 'IDLE', 'STANDARD', 12),
(1, 2, 'L-2', 1, 'IDLE', 'STANDARD', 12),
(1, 3, 'L-3', 1, 'IDLE', 'STANDARD', 12),
(1, 4, 'L-4', 1, 'IDLE', 'EXPRESS',  16),
(1, 5, 'L-5', 1, 'IDLE', 'EXPRESS',  16),
(1, 6, 'L-6', 1, 'IDLE', 'VIP',       8);

-- Super Admin  (password: Admin@123)
INSERT INTO `users`
    (`employee_id`,`email`,`password_hash`,`full_name`,`home_floor`,`building_id`,`user_role`)
VALUES
('ADMIN001','admin@alms.com',
 '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lh7y',
 'System Admin', 1, 1, 'SUPER_ADMIN');
