/**
 * Express服务器 - 用户认证API
 * 提供注册和登录功能，连接MySQL数据库
 */

const express = require('express');
const cors = require('cors');
const db = require('./db');

const app = express();
const PORT = process.env.PORT || 3000;

// ==================== 中间件配置 ====================
app.use(cors());  // 允许跨域请求
app.use(express.json());  // 解析JSON请求体

// 请求日志中间件
app.use((req, res, next) => {
    console.log(`[${new Date().toISOString()}] ${req.method} ${req.url}`);
    next();
});

// ==================== API路由 ====================

/**
 * 健康检查端点
 */
app.get('/', (req, res) => {
    res.json({
        success: true,
        message: 'StudyApp用户认证API',
        version: '1.0.0',
        endpoints: {
            register: 'POST /register',
            login: 'POST /login',
            health: 'GET /health'
        }
    });
});

/**
 * 健康检查（包含数据库状态）
 */
app.get('/health', async (req, res) => {
    try {
        const dbConnected = await db.testConnection();
        res.json({
            success: true,
            timestamp: new Date().toISOString(),
            database: {
                connected: dbConnected,
                host: '127.0.0.1',
                port: 3307,
                database: 'studyapp_database'
            }
        });
    } catch (error) {
        res.status(500).json({
            success: false,
            message: '健康检查失败',
            error: error.message
        });
    }
});

/**
 * POST /register - 用户注册
 * 请求体: { "username": "user1", "password": "pass123", "role": "student" }
 * role可选，默认值为'student'
 */
app.post('/register', async (req, res) => {
    try {
        const { username, password, role = 'student' } = req.body;

        // 验证必要字段
        if (!username || !password) {
            return res.status(400).json({
                success: false,
                message: '用户名和密码为必填字段'
            });
        }

        // 检查用户名是否已存在
        const existingUsers = await db.query(
            'SELECT id FROM users WHERE username = ? LIMIT 1',
            [username]
        );

        if (existingUsers.length > 0) {
            return res.status(409).json({
                success: false,
                message: '用户名已存在'
            });
        }

        // 插入新用户
        // 注意：生产环境应该对密码进行哈希处理（使用bcrypt）
        const result = await db.query(
            'INSERT INTO users (username, password, role) VALUES (?, ?, ?)',
            [username, password, role]
        );

        console.log(`✅ 用户注册成功: ${username} (ID: ${result.insertId})`);

        res.status(201).json({
            success: true,
            message: '注册成功',
            data: {
                id: result.insertId,
                username,
                role,
                createdAt: new Date().toISOString()
            }
        });

    } catch (error) {
        console.error('注册失败:', error);

        // 处理唯一约束冲突
        if (error.code === 'ER_DUP_ENTRY') {
            return res.status(409).json({
                success: false,
                message: '用户名已存在'
            });
        }

        res.status(500).json({
            success: false,
            message: '注册失败，服务器内部错误',
            error: error.message
        });
    }
});

/**
 * POST /login - 用户登录
 * 请求体: { "username": "user1", "password": "pass123" }
 */
app.post('/login', async (req, res) => {
    try {
        const { username, password } = req.body;

        // 验证必要字段
        if (!username || !password) {
            return res.status(400).json({
                success: false,
                message: '用户名和密码为必填字段'
            });
        }

        // 查询用户
        const users = await db.query(
            'SELECT id, username, password, role, created_at FROM users WHERE username = ? LIMIT 1',
            [username]
        );

        if (users.length === 0) {
            return res.status(401).json({
                success: false,
                message: '用户名或密码错误'
            });
        }

        const user = users[0];

        // 验证密码（注意：这里存储的是明文密码，生产环境应使用bcrypt.compare）
        if (user.password !== password) {
            return res.status(401).json({
                success: false,
                message: '用户名或密码错误'
            });
        }

        console.log(`✅ 用户登录成功: ${username}`);

        // 移除密码字段，不返回给客户端
        const { password: _, ...userWithoutPassword } = user;

        res.json({
            success: true,
            message: '登录成功',
            data: userWithoutPassword
        });

    } catch (error) {
        console.error('登录失败:', error);
        res.status(500).json({
            success: false,
            message: '登录失败，服务器内部错误',
            error: error.message
        });
    }
});

// ==================== 错误处理 ====================

// 404处理
app.use((req, res) => {
    res.status(404).json({
        success: false,
        message: 'API端点不存在',
        endpoint: req.url
    });
});

// 全局错误处理
app.use((err, req, res, next) => {
    console.error('服务器错误:', err);
    res.status(500).json({
        success: false,
        message: '服务器内部错误',
        error: process.env.NODE_ENV === 'production' ? 'Internal server error' : err.message
    });
});

// ==================== 服务器启动 ====================

async function startServer() {
    try {
        // 测试数据库连接
        console.log('🔌 测试数据库连接...');
        const dbConnected = await db.testConnection();

        if (!dbConnected) {
            console.log('⚠️  数据库连接失败，请检查：');
            console.log('   1. MySQL服务是否运行（端口3307）');
            console.log('   2. 数据库配置是否正确（db.js）');
            console.log('   3. 数据库是否存在: studyapp_database');
            console.log('');
        } else {
            // 初始化数据库表
            console.log('🔄 初始化数据库表...');
            await db.initializeTables();
        }

        // 启动HTTP服务器
        app.listen(PORT, () => {
            console.log('='.repeat(60));
            console.log('🚀 StudyApp用户认证服务器启动成功');
            console.log('='.repeat(60));
            console.log(`📡 访问地址: http://localhost:${PORT}`);
            console.log(`🏥 健康检查: http://localhost:${PORT}/health`);
            console.log('');
            console.log('📋 API端点:');
            console.log('   POST /register      # 用户注册');
            console.log('   POST /login         # 用户登录');
            console.log('');

            if (!dbConnected) {
                console.log('💡 数据库连接问题解决方案:');
                console.log('   1. 启动MySQL: sudo service mysql start (Linux/Mac)');
                console.log('   2. 或: net start MySQL (Windows)');
                console.log('   3. 创建数据库: CREATE DATABASE studyapp_database;');
                console.log('   4. 检查端口3307是否正确');
            }

            console.log('='.repeat(60));
        });

    } catch (error) {
        console.error('❌ 服务器启动失败:', error);
        process.exit(1);
    }
}

// 启动服务器
startServer();

module.exports = app;