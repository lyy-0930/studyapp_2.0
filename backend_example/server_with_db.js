/**
 * 完整后端服务器（带数据库连接）
 * 文件：server_with_db.js
 * 用法：node server_with_db.js
 */

const express = require('express');
const cors = require('cors');
const path = require('path');

// 导入数据库配置
const db = require('./config/database');

// 导入路由
const classStatsRouter = require('./routes/classStats');

const app = express();
const PORT = process.env.PORT || 3000;

// ==================== 中间件配置 ====================
app.use(cors({
    origin: '*', // 允许所有来源（生产环境应限制）
    methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization']
}));

app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// 请求日志中间件
app.use((req, res, next) => {
    console.log(`[${new Date().toISOString()}] ${req.method} ${req.url}`);
    next();
});

// ==================== API路由 ====================

// 健康检查端点
app.get('/', (req, res) => {
    res.json({
        success: true,
        message: 'StudyApp后端API服务器（数据库版）',
        version: '1.0.0',
        endpoints: {
            stats: 'GET /api/stats/class/:classId',
            test: 'GET /api/stats/test/:classId',
            details: 'GET /api/stats/class/:classId/details'
        },
        database: '已连接'
    });
});

// 注册统计路由
app.use('/api/stats', classStatsRouter);

// ==================== 管理端点 ====================

// 数据库状态检查
app.get('/admin/db-status', async (req, res) => {
    try {
        const isConnected = await db.testConnection();
        res.json({
            success: true,
            database: {
                connected: isConnected,
                timestamp: new Date().toISOString()
            }
        });
    } catch (error) {
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// 初始化数据库（仅开发环境）
if (process.env.NODE_ENV !== 'production') {
    app.post('/admin/init-db', async (req, res) => {
        try {
            await db.initializeDatabase();
            res.json({
                success: true,
                message: '数据库初始化完成'
            });
        } catch (error) {
            res.status(500).json({
                success: false,
                error: error.message
            });
        }
    });

    // 插入测试数据
    app.post('/admin/seed-data', async (req, res) => {
        try {
            const classId = await db.seedTestData();
            res.json({
                success: true,
                message: '测试数据插入完成',
                data: {
                    classId,
                    apiUrl: `/api/stats/class/${classId}`
                }
            });
        } catch (error) {
            res.status(500).json({
                success: false,
                error: error.message
            });
        }
    });
}

// ==================== 错误处理 ====================

// 404处理
app.use((req, res) => {
    res.status(404).json({
        success: false,
        message: 'API端点不存在',
        error: 'ENDPOINT_NOT_FOUND',
        requestedUrl: req.url
    });
});

// 全局错误处理
app.use((err, req, res, next) => {
    console.error('💥 服务器错误:', err);

    // 数据库连接错误
    if (err.code === 'ECONNREFUSED') {
        return res.status(503).json({
            success: false,
            message: '数据库服务不可用',
            error: 'DATABASE_UNAVAILABLE'
        });
    }

    // 数据库查询错误
    if (err.code && err.code.startsWith('ER_')) {
        return res.status(500).json({
            success: false,
            message: '数据库查询错误',
            error: err.code,
            sqlMessage: err.sqlMessage
        });
    }

    // 其他错误
    res.status(err.status || 500).json({
        success: false,
        message: '服务器内部错误',
        error: process.env.NODE_ENV === 'production' ? 'INTERNAL_ERROR' : err.message
    });
});

// ==================== 服务器启动 ====================

async function startServer() {
    try {
        // 测试数据库连接
        console.log('🔌 测试数据库连接...');
        const dbConnected = await db.testConnection();

        if (!dbConnected) {
            console.log('⚠️  数据库连接失败，但服务器将继续启动');
            console.log('💡 提示: API将返回模拟数据或错误');
        }

        // 启动HTTP服务器
        const server = app.listen(PORT, () => {
            console.log('='.repeat(60));
            console.log('🚀 StudyApp后端服务器启动成功');
            console.log('='.repeat(60));
            console.log(`📡 访问地址: http://localhost:${PORT}`);
            console.log(`📊 健康检查: http://localhost:${PORT}/`);
            console.log(`🛠️  数据库状态: http://localhost:${PORT}/admin/db-status`);
            console.log('');
            console.log('📋 主要API端点:');
            console.log(`   GET  /api/stats/class/1          # 班级1统计`);
            console.log(`   GET  /api/stats/test/1           # 测试API`);
            console.log(`   GET  /api/stats/class/1/details  # 班级详情`);
            console.log('');

            if (!dbConnected) {
                console.log('⚠️  警告: 数据库连接失败');
                console.log('💡 你可以:');
                console.log('   1. 检查MySQL是否运行: sudo service mysql start');
                console.log('   2. 创建数据库: CREATE DATABASE studyapp_db;');
                console.log('   3. 修改config/database.js中的配置');
                console.log('');
            } else if (process.env.NODE_ENV !== 'production') {
                console.log('🛠️  开发工具:');
                console.log(`   POST /admin/init-db         # 初始化数据库表`);
                console.log(`   POST /admin/seed-data       # 插入测试数据`);
            }

            console.log('='.repeat(60));

            // Android连接提示
            console.log('\n📱 Android连接配置:');
            console.log('   模拟器: BASE_URL = "http://10.0.2.2:3000"');
            console.log('   真机: BASE_URL = "http://<你的电脑IP>:3000"');
            console.log('\n💡 获取电脑IP: ipconfig (Windows) / ifconfig (Mac/Linux)');
        });

        // 优雅关闭
        process.on('SIGTERM', () => {
            console.log('🛑 收到关闭信号，正在优雅关闭...');
            server.close(() => {
                console.log('✅ 服务器已关闭');
                process.exit(0);
            });
        });

        process.on('SIGINT', () => {
            console.log('🛑 收到中断信号，正在关闭...');
            server.close(() => {
                console.log('✅ 服务器已关闭');
                process.exit(0);
            });
        });

    } catch (error) {
        console.error('❌ 服务器启动失败:', error);
        process.exit(1);
    }
}

// 启动服务器
startServer();

module.exports = app;