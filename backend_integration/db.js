/**
 * MySQL数据库连接配置
 * 使用mysql2的promise版本和连接池
 */

const mysql = require('mysql2/promise');

// 数据库配置
const dbConfig = {
    host: process.env.DB_HOST || '127.0.0.1',
    port: parseInt(process.env.DB_PORT) || 3307,
    user: process.env.DB_USER || 'root',
    password: process.env.DB_PASSWORD || '123456',
    database: process.env.DB_NAME || 'studyapp_database',
    waitForConnections: true,
    connectionLimit: 10,      // 连接池大小
    queueLimit: 0,           // 无队列限制
    enableKeepAlive: true,
    keepAliveInitialDelay: 0
};

// 创建连接池
const pool = mysql.createPool(dbConfig);

/**
 * 测试数据库连接
 * @returns {Promise<boolean>} 连接是否成功
 */
async function testConnection() {
    try {
        const connection = await pool.getConnection();
        console.log('✅ MySQL数据库连接成功');
        connection.release();
        return true;
    } catch (error) {
        console.error('❌ MySQL数据库连接失败:', error.message);
        return false;
    }
}

/**
 * 初始化users表（如果不存在）
 * @returns {Promise<void>}
 */
async function initializeTables() {
    try {
        await pool.query(`
            CREATE TABLE IF NOT EXISTS users (
                id INT PRIMARY KEY AUTO_INCREMENT,
                username VARCHAR(50) UNIQUE NOT NULL,
                password VARCHAR(100) NOT NULL,
                role VARCHAR(20) DEFAULT 'student',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_username (username)
            )
        `);
        console.log('✅ users表已就绪');
    } catch (error) {
        console.error('❌ 初始化users表失败:', error.message);
        throw error;
    }
}

/**
 * 执行SQL查询（封装pool.query）
 * @param {string} sql SQL语句
 * @param {Array} params 参数数组
 * @returns {Promise<Array>} 查询结果
 */
async function query(sql, params) {
    try {
        const [rows] = await pool.query(sql, params);
        return rows;
    } catch (error) {
        console.error('SQL查询错误:', error.message);
        throw error;
    }
}

// 导出
module.exports = {
    pool,
    query,
    testConnection,
    initializeTables
};