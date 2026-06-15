// 学习应用后端API - 简单版本（使用JSON文件存储）
// 功能：用户注册、登录，支持student/teacher/admin三种身份
// 作者：Claude AI助手
// 日期：2026-04-19

// ================================
// 1. 引入必要的模块
// ================================
const express = require('express');
const bodyParser = require('body-parser');
const cors = require('cors');
const fs = require('fs');
const path = require('path');

// ================================
// 2. 创建Express应用和配置
// ================================
const app = express();
const PORT = 3000; // 后端服务器端口号

// 配置中间件
app.use(cors()); // 允许跨域请求（Android App需要）
app.use(bodyParser.json()); // 解析JSON格式的请求体
app.use(bodyParser.urlencoded({ extended: true })); // 解析URL编码的请求体

// ================================
// 3. JSON文件存储配置
// ================================
const USERS_FILE = path.join(__dirname, 'users.json');

// 初始化users.json文件（如果不存在）
function initUsersFile() {
    if (!fs.existsSync(USERS_FILE)) {
        const initialUsers = {
            // 初始用户数据，用于测试
            users: [
                {
                    id: 1,
                    username: 'student001',
                    password: '123456', // 注意：实际应用中密码应该加密存储
                    role: 'student',
                    createdAt: new Date().toISOString()
                },
                {
                    id: 2,
                    username: 'teacher001',
                    password: '123456',
                    role: 'teacher',
                    createdAt: new Date().toISOString()
                },
                {
                    id: 3,
                    username: 'admin001',
                    password: '123456',
                    role: 'admin',
                    createdAt: new Date().toISOString()
                }
            ]
        };

        fs.writeFileSync(USERS_FILE, JSON.stringify(initialUsers, null, 2), 'utf8');
        console.log('✅ 已创建初始users.json文件');
    }
}

// 读取所有用户数据
function readUsers() {
    try {
        const data = fs.readFileSync(USERS_FILE, 'utf8');
        return JSON.parse(data);
    } catch (error) {
        console.error('❌ 读取用户数据失败:', error.message);
        return { users: [] };
    }
}

// 写入用户数据
function writeUsers(data) {
    try {
        fs.writeFileSync(USERS_FILE, JSON.stringify(data, null, 2), 'utf8');
        return true;
    } catch (error) {
        console.error('❌ 写入用户数据失败:', error.message);
        return false;
    }
}

// ================================
// 4. API路由定义
// ================================

// 4.1 根路径 - API文档
app.get('/', (req, res) => {
    res.json({
        message: 'StudyApp简单版后端API运行正常',
        version: '1.0.0',
        description: '使用JSON文件存储数据的用户注册登录系统',
        endpoints: [
            'POST /register - 用户注册',
            'POST /login - 用户登录'
        ],
        testAccounts: [
            { username: 'student001', password: '123456', role: 'student' },
            { username: 'teacher001', password: '123456', role: 'teacher' },
            { username: 'admin001', password: '123456', role: 'admin' }
        ],
        note: '密码未加密，仅用于演示目的'
    });
});

// 4.2 用户注册接口
// 路径：POST /register
// 功能：创建新用户，用户名不能重复，role只能是student/teacher/admin
app.post('/register', (req, res) => {
    const { username, password, role } = req.body;

    // 参数验证
    if (!username || !password || !role) {
        return res.status(400).json({
            success: false,
            message: '用户名、密码和角色都不能为空'
        });
    }

    // 验证role是否合法
    const validRoles = ['student', 'teacher', 'admin'];
    if (!validRoles.includes(role)) {
        return res.status(400).json({
            success: false,
            message: '角色只能是 student、teacher 或 admin'
        });
    }

    // 读取现有用户数据
    const data = readUsers();

    // 检查用户名是否已存在
    const existingUser = data.users.find(user => user.username === username);
    if (existingUser) {
        return res.status(400).json({
            success: false,
            message: '用户名已存在，请选择其他用户名'
        });
    }

    // 生成新用户ID（当前最大ID+1）
    const maxId = data.users.length > 0
        ? Math.max(...data.users.map(user => user.id))
        : 0;
    const newUserId = maxId + 1;

    // 创建新用户对象
    const newUser = {
        id: newUserId,
        username,
        password, // 注意：实际应用中应该加密存储
        role,
        createdAt: new Date().toISOString()
    };

    // 添加到用户列表
    data.users.push(newUser);

    // 保存到文件
    if (writeUsers(data)) {
        // 返回成功响应（不返回密码）
        const { password, ...userWithoutPassword } = newUser;
        res.status(201).json({
            success: true,
            message: '注册成功',
            user: userWithoutPassword
        });
    } else {
        res.status(500).json({
            success: false,
            message: '注册失败，服务器内部错误'
        });
    }
});

// 4.3 用户登录接口
// 路径：POST /login
// 功能：验证用户名和密码，返回用户信息
app.post('/login', (req, res) => {
    const { username, password } = req.body;

    // 参数验证
    if (!username || !password) {
        return res.status(400).json({
            success: false,
            message: '用户名和密码不能为空'
        });
    }

    // 读取用户数据
    const data = readUsers();

    // 查找匹配的用户
    const user = data.users.find(user =>
        user.username === username && user.password === password
    );

    if (!user) {
        // 用户不存在或密码错误
        return res.status(401).json({
            success: false,
            message: '用户名或密码错误'
        });
    }

    // 登录成功，返回用户信息（不返回密码）
    const { password: _, ...userWithoutPassword } = user;
    res.json({
        success: true,
        message: '登录成功',
        user: userWithoutPassword
    });
});

// 4.4 获取所有用户接口（仅用于测试，实际项目中应该限制权限）
// 路径：GET /users
// 功能：返回所有用户信息（不含密码）
app.get('/users', (req, res) => {
    const data = readUsers();

    // 移除所有用户的密码字段
    const usersWithoutPasswords = data.users.map(user => {
        const { password, ...userWithoutPassword } = user;
        return userWithoutPassword;
    });

    res.json({
        success: true,
        count: usersWithoutPasswords.length,
        users: usersWithoutPasswords
    });
});

// ================================
// 5. 启动服务器
// ================================

// 初始化users.json文件
initUsersFile();

// 启动服务器
app.listen(PORT, () => {
    console.log('='.repeat(50));
    console.log('✅ StudyApp简单版后端服务器已启动');
    console.log('📡 访问地址: http://localhost:' + PORT);
    console.log('📚 API文档: http://localhost:' + PORT + '/');
    console.log('💾 数据存储: ' + USERS_FILE);
    console.log('='.repeat(50));
    console.log('\n测试账号：');
    console.log('  学生: student001 / 123456');
    console.log('  教师: teacher001 / 123456');
    console.log('  管理员: admin001 / 123456');
    console.log('\n按 Ctrl+C 停止服务器');
});

// ================================
// 6. 导出app（用于测试）
// ================================
module.exports = app;