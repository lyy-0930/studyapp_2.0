-- StudyApp MySQL数据库初始化脚本
-- 创建数据库和users表
-- 运行方式：在MySQL客户端中执行此脚本

-- 1. 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS studyapp_database
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

-- 2. 使用数据库
USE studyapp_database;

-- 3. 创建users表（如果不存在）
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(50) NOT NULL,
    role VARCHAR(20) DEFAULT 'student',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. 插入示例数据（可选）
INSERT IGNORE INTO users (username, password, role) VALUES
('student001', '123456', 'student'),
('student002', '123456', 'student'),
('teacher001', '123456', 'teacher');

-- 5. 显示创建结果
SHOW TABLES;
DESCRIBE users;
SELECT id, username, role FROM users;