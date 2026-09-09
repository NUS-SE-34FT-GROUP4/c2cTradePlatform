-- =================================================================
-- C2C SecTrade - Database Initialization Script
-- Sprint 1: users, roles and item catalogue
-- =================================================================
-- This script handles:
-- 1. Database creation with UTF-8mb4 character set.
-- 2. Dropping and recreating all tables for a clean slate.
-- 3. Seeding essential data (roles, admin user).
-- 4. Seeding realistic test data for users and products (books).
-- =================================================================

-- Set session variables for character encoding
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- Create the database if it doesn't exist, ensuring correct character set
CREATE DATABASE IF NOT EXISTS trade CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE trade;

-- Disable foreign key checks to allow clean drop
SET FOREIGN_KEY_CHECKS = 0;

-- Drop tables in reverse order of dependency to avoid foreign key errors
DROP TABLE IF EXISTS `pms_product_media`;
DROP TABLE IF EXISTS `pms_product`;
DROP TABLE IF EXISTS `user_roles`;
DROP TABLE IF EXISTS `roles`;
DROP TABLE IF EXISTS `users`;

-- Re-enable foreign key checks
SET FOREIGN_KEY_CHECKS = 1;

-- =================================================================
-- Table Structure Definitions
-- =================================================================

-- Table `users`
CREATE TABLE `users` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'User unique ID',
    `username` VARCHAR(50) NOT NULL COMMENT 'Username, unique',
    `display_name` VARCHAR(100) NULL COMMENT 'Display name for the user',
    `password_hash` VARCHAR(255) NOT NULL COMMENT 'BCrypt hashed password',
    `payment_password_hash` VARCHAR(255) NULL COMMENT 'BCrypt hashed payment password (6 digits)',
    `email` VARCHAR(100) NOT NULL COMMENT 'User email, unique',
    `avatar_url` VARCHAR(255) NULL COMMENT 'User avatar URL',
    `balance` DECIMAL(15, 2) NOT NULL DEFAULT 0.00 COMMENT 'User balance',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update time',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_username` (`username`),
    UNIQUE INDEX `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Users table';

-- Table `roles`
CREATE TABLE `roles` (
    `id` INT NOT NULL AUTO_INCREMENT COMMENT 'Role unique ID',
    `name` VARCHAR(50) NOT NULL COMMENT 'Role name, unique',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_rolename` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Roles table';

-- Table `user_roles`
CREATE TABLE `user_roles` (
    `user_id` BIGINT NOT NULL COMMENT 'User ID',
    `role_id` INT NOT NULL COMMENT 'Role ID',
    PRIMARY KEY (`user_id`, `role_id`),
    INDEX `fk_user_roles_role_id_idx` (`role_id`),
    CONSTRAINT `fk_user_roles_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_user_roles_role_id` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User-role association table';

-- Table `pms_product`
CREATE TABLE `pms_product` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Product ID',
    `user_id` BIGINT NOT NULL COMMENT 'Seller user ID',
    `name` VARCHAR(255) NOT NULL COMMENT 'Product name',
    `description` TEXT NULL COMMENT 'Product description',
    `price` DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT 'Price',
    `stock` INT NOT NULL DEFAULT 1 COMMENT 'Stock (default 1 for used items)',
    `condition_level` INT NOT NULL DEFAULT 9 COMMENT 'Condition (1-10)',
    `location` VARCHAR(255) NULL COMMENT 'Location',
    `category` VARCHAR(100) NULL DEFAULT 'other' COMMENT 'Product category',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT 'Status: 1=For Sale, 2=Sold, 0=Delisted',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update time',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_price` (`price`),
    INDEX `idx_category` (`category`),
    CONSTRAINT `fk_product_user` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Products table';

-- Table `pms_product_media`
CREATE TABLE `pms_product_media` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Media ID',
    `product_id` BIGINT NOT NULL COMMENT 'Product ID',
    `url` VARCHAR(1024) NOT NULL COMMENT 'Media URL',
    `media_type` TINYINT NOT NULL COMMENT 'Media type: 1=Image, 2=Video',
    `sort_order` INT NULL DEFAULT 0 COMMENT 'Sort order',
    PRIMARY KEY (`id`),
    INDEX `idx_product_id` (`product_id`),
    CONSTRAINT `fk_media_product` FOREIGN KEY (`product_id`) REFERENCES `pms_product`(`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Product media table';

-- =================================================================
-- Seeding Essential Data
-- =================================================================

-- Insert base roles
INSERT INTO `roles` (`id`, `name`) VALUES (1, 'ROLE_USER'), (2, 'ROLE_ADMIN')
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- Create a default admin account (username: admin, password: admin123)
-- BCrypt hash for 'admin123'
INSERT INTO `users` (`id`, `username`, `display_name`, `password_hash`, `email`, `avatar_url`)
VALUES (1, 'admin', '系统管理员', '$2a$12$9l1r7OVMYW3xsv/JQchZKutlvkIJgopmbNC3jEA3hUkNbN/ivzMn2', 'admin@rebook.trade', 'https://i.pravatar.cc/150?u=admin')
ON DUPLICATE KEY UPDATE username=VALUES(username), display_name=VALUES(display_name), password_hash=VALUES(password_hash);

-- Assign roles to admin user
INSERT INTO `user_roles` (`user_id`, `role_id`) VALUES (1, 1), (1, 2)
ON DUPLICATE KEY UPDATE user_id=VALUES(user_id);

-- =================================================================
-- Test Seller Accounts
-- Password for all users: admin123
-- =================================================================

-- 1. Create Users
-- IDs 101-105 to avoid conflicts
INSERT INTO `users` (`id`, `username`, `display_name`, `password_hash`, `email`, `avatar_url`, `balance`) VALUES
(101, 'seller_lvl1', 'Seller Level 1', '$2a$12$9l1r7OVMYW3xsv/JQchZKutlvkIJgopmbNC3jEA3hUkNbN/ivzMn2', 'seller1@test.com', 'https://i.pravatar.cc/150?u=seller1', 1000.00),
(102, 'seller_lvl2', 'Seller Level 2', '$2a$12$9l1r7OVMYW3xsv/JQchZKutlvkIJgopmbNC3jEA3hUkNbN/ivzMn2', 'seller2@test.com', 'https://i.pravatar.cc/150?u=seller2', 2000.00),
(103, 'seller_lvl3', 'Seller Level 3', '$2a$12$9l1r7OVMYW3xsv/JQchZKutlvkIJgopmbNC3jEA3hUkNbN/ivzMn2', 'seller3@test.com', 'https://i.pravatar.cc/150?u=seller3', 3000.00),
(104, 'seller_lvl4', 'Seller Level 4', '$2a$12$9l1r7OVMYW3xsv/JQchZKutlvkIJgopmbNC3jEA3hUkNbN/ivzMn2', 'seller4@test.com', 'https://i.pravatar.cc/150?u=seller4', 4000.00),
(105, 'seller_lvl5', 'Seller Level 5', '$2a$12$9l1r7OVMYW3xsv/JQchZKutlvkIJgopmbNC3jEA3hUkNbN/ivzMn2', 'seller5@test.com', 'https://i.pravatar.cc/150?u=seller5', 5000.00)
ON DUPLICATE KEY UPDATE 
    password_hash=VALUES(password_hash), 
    display_name=VALUES(display_name),
    email=VALUES(email);

-- 2. Assign Roles (ROLE_USER = 1)
INSERT INTO `user_roles` (`user_id`, `role_id`) VALUES
(101, 1),
(102, 1),
(103, 1),
(104, 1),
(105, 1)
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);


-- =================================================================
-- Seed Catalogue Data (Sprint 1: item browsing)
-- =================================================================

INSERT INTO `pms_product` (`id`, `user_id`, `name`, `description`, `price`, `stock`, `condition_level`, `location`, `category`, `status`) VALUES
(1, 101, '九成新 iPhone 13 128G 午夜色', '自用一年，无磕碰，电池健康度 91%，配原装充电线。', 2899.00, 1, 9, '北京市', 'electronics', 1),
(2, 101, 'ThinkPad X1 Carbon 2021 i7/16G', '出差备用机，屏幕无划痕，键盘手感良好，送内胆包。', 4200.00, 1, 8, '北京市', 'electronics', 1),
(3, 102, 'Sony WH-1000XM4 头戴降噪耳机', '降噪效果依旧出色，耳罩已更换全新替换件。', 890.00, 1, 8, '上海市', 'electronics', 1),
(4, 102, '《算法导论》第三版 中文版', '考研复习用书，内页有少量铅笔笔记，可擦除。', 55.00, 1, 7, '上海市', 'books', 1),
(5, 102, '《深入理解计算机系统》第三版', '经典 CSAPP，几乎全新，只翻过前三章。', 78.00, 1, 9, '上海市', 'books', 1),
(6, 103, 'Kindle Paperwhite 4 8G', '阅读器功能正常，附赠保护壳，屏幕无亮点。', 420.00, 1, 8, '浙江省', 'electronics', 1),
(7, 103, '大学英语四六级真题套装', '含最近十年真题与解析，共 6 本，无笔记。', 42.00, 1, 9, '浙江省', 'books', 1),
(8, 103, 'Nike Air Force 1 白色 42码', '穿过三次，鞋盒鞋垫齐全，鞋底几乎无磨损。', 320.00, 1, 9, '浙江省', 'clothing', 1),
(9, 104, 'iPad Air 4 64G 天蓝色', '带原装 Apple Pencil 二代，屏幕贴防窥膜。', 2100.00, 1, 8, '广东省', 'electronics', 1),
(10, 104, 'Uniqlo 轻型羽绒服 男款 L', '去年冬天购入，穿着次数不多，无破损无污渍。', 180.00, 1, 8, '广东省', 'clothing', 1),
(11, 105, '罗技 MX Master 3 无线鼠标', '办公利器，滚轮正常，接收器与充电线齐全。', 380.00, 1, 9, '江苏省', 'electronics', 1),
(12, 105, '《人类简史》《未来简史》套装', '中信出版社正版，书脊轻微折痕，内页干净。', 68.00, 1, 8, '江苏省', 'books', 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), price=VALUES(price), description=VALUES(description);

INSERT INTO `pms_product_media` (`product_id`, `url`, `media_type`, `sort_order`) VALUES
(1, 'https://picsum.photos/seed/product1/600/600', 1, 0),
(2, 'https://picsum.photos/seed/product2/600/600', 1, 0),
(3, 'https://picsum.photos/seed/product3/600/600', 1, 0),
(4, 'https://picsum.photos/seed/product4/600/600', 1, 0),
(5, 'https://picsum.photos/seed/product5/600/600', 1, 0),
(6, 'https://picsum.photos/seed/product6/600/600', 1, 0),
(7, 'https://picsum.photos/seed/product7/600/600', 1, 0),
(8, 'https://picsum.photos/seed/product8/600/600', 1, 0),
(9, 'https://picsum.photos/seed/product9/600/600', 1, 0),
(10, 'https://picsum.photos/seed/product10/600/600', 1, 0),
(11, 'https://picsum.photos/seed/product11/600/600', 1, 0),
(12, 'https://picsum.photos/seed/product12/600/600', 1, 0);

-- =================================================================
-- End of Script
-- =================================================================
SELECT 'Database structure and essential data seeded successfully.' AS status;
