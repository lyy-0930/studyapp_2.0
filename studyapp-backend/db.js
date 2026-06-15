/**
 * 学习应用后端 - MySQL数据库连接模块
 * 功能：提供MySQL数据库连接池，支持连接复用和高效查询
 * 使用mysql2/promise库，支持async/await语法
 * 配置：本地MySQL数据库（端口3307）
 */

// ================================
// 1. 引入必要的模块
// ================================
const mysql = require('mysql2/promise');

// ================================
// 2. 数据库连接配置
// ================================
const dbConfig = {
    host: process.env.DB_HOST || '127.0.0.1',       // MySQL服务器地址
    port: parseInt(process.env.DB_PORT) || 3307,    // MySQL服务器端口（默认3306，这里使用3307）
    user: process.env.DB_USER || 'root',             // 数据库用户名
    password: process.env.DB_PASSWORD || '123456',   // 数据库密码
    database: process.env.DB_NAME || 'studyapp_database', // 数据库名称
    waitForConnections: true,     // 等待连接（连接池满时）
    connectionLimit: 10,          // 连接池最大连接数
    queueLimit: 0,                // 排队连接数限制（0表示不限制）
    enableKeepAlive: true,        // 保持连接活跃
    keepAliveInitialDelay: 0,     // 保持连接初始延迟
    charset: 'utf8mb4'            // 支持中文等4字节UTF-8字符
};

// ================================
// 3. 创建数据库连接池
// ================================
let pool;

/**
 * 初始化数据库连接池
 * @returns {Promise<mysql.Pool>} 数据库连接池
 */
async function initPool() {
    try {
        // 创建连接池
        pool = mysql.createPool(dbConfig);

        // 测试连接
        const connection = await pool.getConnection();
        console.log('✅ MySQL数据库连接成功');
        connection.release(); // 释放连接回连接池
        return pool;
    } catch (error) {
        console.error('❌ MySQL数据库连接失败:', error.message);
        console.log('💡 请检查：');
        console.log('   1. MySQL服务是否启动');
        console.log('   2. 数据库配置是否正确（用户名、密码、端口）');
        console.log('   3. studyapp_database数据库是否存在');
        throw error; // 抛出错误，让调用者处理
    }
}

/**
 * 获取数据库连接池（单例模式）
 * @returns {Promise<mysql.Pool>} 数据库连接池
 */
async function getPool() {
    if (!pool) {
        pool = await initPool();
    }
    return pool;
}

/**
 * 执行SQL查询（带参数）
 * @param {string} sql - SQL查询语句，使用?作为占位符
 * @param {Array} params - 查询参数数组
 * @returns {Promise<Object>} 查询结果
 * @example
 * // 查询用户
 * const users = await executeQuery('SELECT * FROM users WHERE id = ?', [1]);
 * // 插入数据
 * const result = await executeQuery('INSERT INTO users (username, password) VALUES (?, ?)', ['test', '123']);
 */
async function executeQuery(sql, params = []) {
    try {
        const pool = await getPool();
        const [rows] = await pool.execute(sql, params);
        return rows;
    } catch (error) {
        console.error('❌ SQL查询执行失败:', error.message);
        console.error('📋 SQL语句:', sql);
        console.error('📋 参数:', params);
        throw error; // 抛出错误，让上层处理
    }
}

/**
 * 开启事务
 * @returns {Promise<mysql.PoolConnection>} 事务连接对象
 */
async function beginTransaction() {
    try {
        const pool = await getPool();
        const connection = await pool.getConnection();
        await connection.beginTransaction();
        return connection;
    } catch (error) {
        console.error('❌ 开启事务失败:', error.message);
        throw error;
    }
}

/**
 * 提交事务
 * @param {mysql.PoolConnection} connection - 事务连接对象
 */
async function commitTransaction(connection) {
    try {
        await connection.commit();
        connection.release();
    } catch (error) {
        console.error('❌ 提交事务失败:', error.message);
        throw error;
    }
}

/**
 * 回滚事务
 * @param {mysql.PoolConnection} connection - 事务连接对象
 */
async function rollbackTransaction(connection) {
    try {
        await connection.rollback();
        connection.release();
    } catch (error) {
        console.error('❌ 回滚事务失败:', error.message);
        throw error;
    }
}

/**
 * 关闭数据库连接池（在应用退出时调用）
 */
async function closePool() {
    if (pool) {
        await pool.end();
        console.log('✅ 数据库连接池已关闭');
    }
}

// ================================
// 4. 导出函数
// ================================
module.exports = {
    getPool,               // 获取连接池
    executeQuery,          // 执行查询（主要使用这个）
    beginTransaction,      // 开启事务
    commitTransaction,     // 提交事务
    rollbackTransaction,   // 回滚事务
    closePool              // 关闭连接池
};

// ================================
// 5. 使用示例（注释掉，供参考）
// ================================
/*
// 示例1：查询所有用户
async function getAllUsers() {
    try {
        const users = await executeQuery('SELECT * FROM users');
        console.log('用户列表:', users);
        return users;
    } catch (error) {
        console.error('查询失败:', error);
    }
}

// 示例2：插入新用户
async function createUser(username, password) {
    try {
        const result = await executeQuery(
            'INSERT INTO users (username, password, role) VALUES (?, ?, ?)',
            [username, password, 'student']
        );
        console.log('插入成功，ID:', result.insertId);
        return result;
    } catch (error) {
        console.error('插入失败:', error);
    }
}
*/