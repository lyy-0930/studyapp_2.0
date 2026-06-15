-- StudyApp MySQL数据库表初始化脚本
-- 运行方式：在MySQL客户端中执行此脚本
-- 前提：已创建数据库 studyapp_database

USE studyapp_database;

-- 1. 创建courses表（课程表）
CREATE TABLE IF NOT EXISTS courses (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    teacher_id INT NOT NULL,  -- 关联users表的教师ID
    teacher_name VARCHAR(50) NOT NULL,  -- 教师姓名（冗余存储，避免频繁join）
    credit INT DEFAULT 2,
    video_url VARCHAR(500),  -- 视频URL（如果有）
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_teacher_id (teacher_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. 创建course_enrollments表（学生选课表）
CREATE TABLE IF NOT EXISTS course_enrollments (
    id INT AUTO_INCREMENT PRIMARY KEY,
    course_id INT NOT NULL,
    student_id INT NOT NULL,
    student_name VARCHAR(50) NOT NULL,  -- 学生姓名
    enrolled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_course_student (course_id, student_id),  -- 防止重复选课
    INDEX idx_course_id (course_id),
    INDEX idx_student_id (student_id),
    INDEX idx_enrolled_at (enrolled_at),
    FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
    FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. 创建study_records表（学习记录表）
CREATE TABLE IF NOT EXISTS study_records (
    id INT AUTO_INCREMENT PRIMARY KEY,
    course_id INT NOT NULL,
    student_id INT NOT NULL,
    watch_time INT DEFAULT 0,  -- 观看时长（分钟）
    progress INT DEFAULT 0,     -- 学习进度（0-100%）
    click_count INT DEFAULT 0,  -- 点击次数（新增：记录学生点击视频的次数）
    last_watch_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY unique_course_student_record (course_id, student_id),  -- 每个学生对每门课程一条记录
    INDEX idx_course_student (course_id, student_id),
    INDEX idx_last_watch (last_watch_time),
    INDEX idx_progress (progress),
    INDEX idx_click_count (click_count),
    FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
    FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. 创建videos表（视频表，如果需要更详细的视频管理）
CREATE TABLE IF NOT EXISTS videos (
    id INT AUTO_INCREMENT PRIMARY KEY,
    course_id INT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    video_url VARCHAR(500) NOT NULL,
    duration INT,  -- 视频时长（分钟）
    teacher_name VARCHAR(50),
    upload_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_course_id (course_id),
    FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. 显示创建结果
SHOW TABLES;

-- 6. 显示表结构
DESCRIBE users;
DESCRIBE courses;
DESCRIBE course_enrollments;
DESCRIBE study_records;
DESCRIBE videos;