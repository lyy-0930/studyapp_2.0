// 学习应用后端API - 数据库模块
// 功能：SQLite数据库连接和表初始化

// ================================
// 1. 引入必要的模块
// ================================
const sqlite3 = require('sqlite3').verbose();
const path = require('path');

// ================================
// 2. 数据库配置
// ================================
const DB_PATH = path.join(__dirname, 'db', 'studyapp.db');

// ================================
// 3. 数据库连接函数
// ================================
function connect() {
    // 创建数据库连接
    const db = new sqlite3.Database(DB_PATH, (err) => {
        if (err) {
            console.error('❌ 数据库连接失败:', err.message);
        } else {
            console.log('✅ 已连接到SQLite数据库');
        }
    });

    return db;
}

// ================================
// 4. 数据库表初始化函数
// ================================
function initDatabase() {
    const db = connect();

    console.log('📝 开始初始化数据库表...');

    // 4.1 创建用户表
    db.run(`
        CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT UNIQUE NOT NULL,
            password TEXT NOT NULL,
            role TEXT DEFAULT 'student',
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        )
    `, (err) => {
        if (err) {
            console.error('❌ 创建用户表失败:', err.message);
        } else {
            console.log('✅ 用户表创建/已存在');
        }
    });

    // 4.2 创建课程表
    db.run(`
        CREATE TABLE IF NOT EXISTS courses (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            description TEXT,
            teacher TEXT NOT NULL,
            credit INTEGER DEFAULT 2,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        )
    `, (err) => {
        if (err) {
            console.error('❌ 创建课程表失败:', err.message);
        } else {
            console.log('✅ 课程表创建/已存在');
        }
    });

    // 4.3 创建视频表
    db.run(`
        CREATE TABLE IF NOT EXISTS videos (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            course_id INTEGER NOT NULL,
            title TEXT NOT NULL,
            description TEXT,
            video_url TEXT NOT NULL,
            teacher TEXT,
            upload_time DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (course_id) REFERENCES courses(id)
        )
    `, (err) => {
        if (err) {
            console.error('❌ 创建视频表失败:', err.message);
        } else {
            console.log('✅ 视频表创建/已存在');
        }
    });

    // 4.4 创建用户-课程关联表
    db.run(`
        CREATE TABLE IF NOT EXISTS user_courses (
            user_id INTEGER NOT NULL,
            course_id INTEGER NOT NULL,
            selected_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (user_id, course_id),
            FOREIGN KEY (user_id) REFERENCES users(id),
            FOREIGN KEY (course_id) REFERENCES courses(id)
        )
    `, (err) => {
        if (err) {
            console.error('❌ 创建用户-课程关联表失败:', err.message);
        } else {
            console.log('✅ 用户-课程关联表创建/已存在');
        }
    });

    console.log('🎉 数据库表初始化完成');

    return db;
}

// ================================
// 5. 导出函数
// ================================
module.exports = {
    connect,
    initDatabase
};