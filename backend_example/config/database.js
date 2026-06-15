/**
 * 数据库配置文件
 * 文件：config/database.js
 * 根据你的数据库配置修改以下值
 */

const mysql = require('mysql2/promise');

// 数据库配置 - 根据你的环境修改
const dbConfig = {
    host: process.env.DB_HOST || 'localhost',
    port: process.env.DB_PORT || 3306,
    user: process.env.DB_USER || 'root',
    password: process.env.DB_PASSWORD || 'your_password',
    database: process.env.DB_NAME || 'studyapp_db',
    waitForConnections: true,
    connectionLimit: 10,          // 连接池大小
    queueLimit: 0,                // 无队列限制
    enableKeepAlive: true,        // 保持连接活跃
    keepAliveInitialDelay: 0      // 立即开始保持连接
};

// 创建数据库连接池
const pool = mysql.createPool(dbConfig);

// 测试数据库连接
async function testConnection() {
    try {
        const connection = await pool.getConnection();
        console.log('✅ 数据库连接成功');

        // 检查必要表是否存在
        const [tables] = await connection.query(`
            SELECT TABLE_NAME
            FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = ?
        `, [dbConfig.database]);

        console.log(`📊 数据库 ${dbConfig.database} 中的表:`);
        tables.forEach(table => {
            console.log(`  - ${table.TABLE_NAME}`);
        });

        connection.release();
        return true;

    } catch (error) {
        console.error('❌ 数据库连接失败:', error.message);

        if (error.code === 'ER_BAD_DB_ERROR') {
            console.log('💡 提示: 数据库不存在，请先创建数据库:');
            console.log(`   CREATE DATABASE ${dbConfig.database};`);
        } else if (error.code === 'ER_ACCESS_DENIED_ERROR') {
            console.log('💡 提示: 用户名或密码错误，请检查数据库凭据');
        } else if (error.code === 'ECONNREFUSED') {
            console.log('💡 提示: 无法连接到数据库服务器，请确保MySQL正在运行');
        }

        return false;
    }
}

// 初始化数据库表（如果不存在）
async function initializeDatabase() {
    try {
        const connection = await pool.getConnection();

        console.log('🔄 初始化数据库表...');

        // 创建班级表
        await connection.query(`
            CREATE TABLE IF NOT EXISTS classes (
                id INT PRIMARY KEY AUTO_INCREMENT,
                name VARCHAR(100) NOT NULL,
                teacher_id INT NOT NULL,
                description TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_teacher_id (teacher_id)
            )
        `);
        console.log('✅ 创建/检查 classes 表');

        // 创建学生表
        await connection.query(`
            CREATE TABLE IF NOT EXISTS students (
                id INT PRIMARY KEY AUTO_INCREMENT,
                class_id INT NOT NULL,
                name VARCHAR(100) NOT NULL,
                student_number VARCHAR(50),
                email VARCHAR(100),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_class_id (class_id),
                FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE CASCADE
            )
        `);
        console.log('✅ 创建/检查 students 表');

        // 创建学习记录表
        await connection.query(`
            CREATE TABLE IF NOT EXISTS learning_records (
                id INT PRIMARY KEY AUTO_INCREMENT,
                student_id INT NOT NULL,
                course_id INT NOT NULL,
                watch_time INT DEFAULT 0,
                progress INT DEFAULT 0,
                last_watch_time TIMESTAMP,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_student_id (student_id),
                INDEX idx_course_id (course_id),
                INDEX idx_last_watch (last_watch_time),
                FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE
            )
        `);
        console.log('✅ 创建/检查 learning_records 表');

        connection.release();
        console.log('🎉 数据库初始化完成');

    } catch (error) {
        console.error('❌ 数据库初始化失败:', error);
        throw error;
    }
}

// 插入测试数据（开发环境使用）
async function seedTestData() {
    try {
        const connection = await pool.getConnection();

        console.log('🌱 插入测试数据...');

        // 插入测试班级
        const [classResult] = await connection.query(`
            INSERT INTO classes (name, teacher_id, description)
            VALUES ('Android开发2024级', 1, 'Android应用开发高级班')
        `);
        const classId = classResult.insertId;
        console.log(`✅ 创建班级 ID: ${classId}`);

        // 插入测试学生
        const students = [
            { name: '张三', number: '2024001' },
            { name: '李四', number: '2024002' },
            { name: '王五', number: '2024003' },
            { name: '赵六', number: '2024004' },
            { name: '孙七', number: '2024005' },
            { name: '周八', number: '2024006' },
            { name: '吴九', number: '2024007' }
        ];

        for (const student of students) {
            await connection.query(
                'INSERT INTO students (class_id, name, student_number) VALUES (?, ?, ?)',
                [classId, student.name, student.number]
            );
        }
        console.log(`✅ 插入 ${students.length} 名学生`);

        // 插入学习记录
        const studentIds = Array.from({ length: students.length }, (_, i) => i + 1);
        const progressValues = [95, 88, 82, 79, 76, 68, 55]; // 不同进度

        for (let i = 0; i < studentIds.length; i++) {
            const studentId = studentIds[i];
            const progress = progressValues[i];

            // 每个学生插入2-4条学习记录
            const recordCount = 2 + Math.floor(Math.random() * 3);

            for (let j = 0; j < recordCount; j++) {
                await connection.query(`
                    INSERT INTO learning_records
                    (student_id, course_id, watch_time, progress, last_watch_time)
                    VALUES (?, ?, ?, ?, DATE_SUB(NOW(), INTERVAL ? DAY))
                `, [
                    studentId,
                    1, // 课程ID
                    progress * 2 + Math.floor(Math.random() * 50), // 观看时间
                    progress + Math.floor(Math.random() * 10) - 5, // 进度 +/-5
                    Math.floor(Math.random() * 30) // 0-30天前
                ]);
            }
        }
        console.log('✅ 插入学习记录');

        connection.release();
        console.log('🎉 测试数据插入完成');
        console.log(`📊 API测试地址: http://localhost:3000/api/stats/class/${classId}`);

        return classId;

    } catch (error) {
        console.error('❌ 插入测试数据失败:', error);
        throw error;
    }
}

// 导出
module.exports = {
    pool,
    query: (...args) => pool.query(...args),
    testConnection,
    initializeDatabase,
    seedTestData
};