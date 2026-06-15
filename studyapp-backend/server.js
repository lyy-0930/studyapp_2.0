/**
 * 学习应用后端 - 主服务器文件
 * 功能：提供用户注册和登录API接口
 * 数据库：MySQL（使用连接池）
 * 接口：POST /register, POST /login
 * 返回统一JSON格式：{ success: true/false, data/message: ... }
 */

// ================================
// 1. 引入必要的模块
// ================================
require('dotenv').config();
const express = require('express');
const bodyParser = require('body-parser');
const cors = require('cors');
const db = require('./db'); // MySQL数据库模块
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const https = require('https');
const crypto = require('crypto');
const bcrypt = require('bcrypt');
const { generateAccessToken, generateRefreshToken, refreshTokens, authenticate, optionalAuth, requireRole, requireOwnership, requireCourseOwnership } = require('./middleware/auth');
const { createAuditLogger, auditSuccess } = require('./middleware/audit');
const { trackLoginFailure, clearLoginAttempts, checkLoginLock, rateLimit, loginRateLimit, adminRateLimit, checkIdempotency } = require('./middleware/rateLimit');
const OpenAI = require('openai');

// 审计日志记录器
const audit = createAuditLogger(db);

const SALT_ROUNDS = 10;

// DeepSeek AI 客户端
const deepseek = new OpenAI({
    baseURL: 'https://api.deepseek.com',
    apiKey: process.env.DEEPSEEK_API_KEY
});

// ================================
// 2. 创建Express应用和配置
// ================================
const app = express();
const PORT = process.env.PORT || 3001; // 支持环境变量配置端口
const HOST = process.env.HOST || '0.0.0.0'; // 默认监听所有网络接口（支持外部访问）

// 配置中间件
app.use(cors()); // 允许跨域请求（前端App需要）
app.use(bodyParser.json()); // 解析JSON格式的请求体
app.use(bodyParser.urlencoded({ extended: true })); // 解析URL编码的请求体

// 所有API端点使用 res.json() 自动设置 application/json; charset=utf-8

// ================================
// 3. 头像上传配置
// ================================
const uploadsDir = path.join(__dirname, 'uploads', 'avatars');
if (!fs.existsSync(uploadsDir)) {
    fs.mkdirSync(uploadsDir, { recursive: true });
}

const avatarStorage = multer.diskStorage({
    destination: (req, file, cb) => cb(null, uploadsDir),
    filename: (req, file, cb) => {
        const userId = req.body.userId || 'unknown';
        const ext = path.extname(file.originalname).toLowerCase() || '.jpg';
        cb(null, `${userId}_${Date.now()}${ext}`);
    }
});

const uploadAvatar = multer({
    storage: avatarStorage,
    limits: { fileSize: 5 * 1024 * 1024 },
    fileFilter: (req, file, cb) => {
        const allowed = /jpeg|jpg|png|gif|webp/;
        const extOk = allowed.test(path.extname(file.originalname).toLowerCase());
        const mimeOk = allowed.test(file.mimetype);
        cb(null, extOk && mimeOk);
    }
});

// ================================
// 3b. 课程封面上传配置
// ================================
const courseImagesDir = path.join(__dirname, 'uploads', 'course-images');
if (!fs.existsSync(courseImagesDir)) {
    fs.mkdirSync(courseImagesDir, { recursive: true });
}

const courseImageStorage = multer.diskStorage({
    destination: (req, file, cb) => cb(null, courseImagesDir),
    filename: (req, file, cb) => {
        const courseName = req.body.courseName || 'course';
        const safeName = courseName.replace(/[^a-zA-Z0-9一-龥]/g, '_').substring(0, 30);
        const ext = path.extname(file.originalname).toLowerCase() || '.jpg';
        cb(null, `${safeName}_${Date.now()}${ext}`);
    }
});

const uploadCourseImage = multer({
    storage: courseImageStorage,
    limits: { fileSize: 5 * 1024 * 1024 },
    fileFilter: (req, file, cb) => {
        const allowed = /jpeg|jpg|png|gif|webp/;
        cb(null, allowed.test(path.extname(file.originalname).toLowerCase()) && allowed.test(file.mimetype));
    }
});

// 静态文件服务
app.use('/uploads', express.static(path.join(__dirname, 'uploads')));

// ================================
// 3d. 视频上传配置（本地部署替代 OSS）
// ================================
const videosDir = path.join(__dirname, 'uploads', 'videos');
if (!fs.existsSync(videosDir)) {
    fs.mkdirSync(videosDir, { recursive: true });
}

const videoStorage = multer.diskStorage({
    destination: (req, file, cb) => cb(null, videosDir),
    filename: (req, file, cb) => {
        const timestamp = Date.now();
        const safeName = Buffer.from(file.originalname, 'latin1').toString('utf8')
            .replace(/[^a-zA-Z0-9一-鿿._-]/g, '_')
            .substring(0, 100);
        cb(null, `${timestamp}_${safeName}`);
    }
});

const uploadVideo = multer({
    storage: videoStorage,
    limits: { fileSize: 2 * 1024 * 1024 * 1024 }, // 2GB
    fileFilter: (req, file, cb) => {
        const allowed = /mp4|avi|mov|mkv|wmv|flv|m4v/;
        const extOk = allowed.test(path.extname(file.originalname).toLowerCase());
        const mimeOk = /video\//.test(file.mimetype);
        cb(null, extOk || mimeOk);
    }
});

// ================================
// 3c. 课程资料（PPT/Word等）上传配置
// ================================
const materialsDir = path.join(__dirname, 'uploads', 'materials');
if (!fs.existsSync(materialsDir)) {
    fs.mkdirSync(materialsDir, { recursive: true });
}

const materialStorage = multer.diskStorage({
    destination: (req, file, cb) => cb(null, materialsDir),
    filename: (req, file, cb) => {
        const courseId = req.params.id || 'unknown';
        const timestamp = Date.now();
        const decodedName = Buffer.from(file.originalname, 'latin1').toString('utf8');
        const safeName = decodedName.replace(/[^a-zA-Z0-9一-鿿._-]/g, '_').substring(0, 100);
        cb(null, `course_${courseId}_${timestamp}_${safeName}`);
    }
});

const uploadMaterial = multer({
    storage: materialStorage,
    limits: { fileSize: 50 * 1024 * 1024 },
    fileFilter: (req, file, cb) => {
        const allowed = /pdf|doc|docx|ppt|pptx|xls|xlsx|txt|zip|rar|7z|png|jpg|jpeg|gif|mp4|avi|mov/;
        const extOk = allowed.test(path.extname(file.originalname).toLowerCase());
        const mimeOk = allowed.test(file.mimetype);
        cb(null, extOk || mimeOk);
    }
});

// ================================
// 3. 辅助函数：统一响应格式
// ================================
/**
 * 成功响应
 * @param {Object} res - Express响应对象
 * @param {*} data - 返回的数据
 * @param {string} message - 成功消息（可选）
 */
function successResponse(res, data, message = '操作成功') {
    res.json({
        success: true,
        message: message,
        data: data
    });
}

/**
 * 失败响应
 * @param {Object} res - Express响应对象
 * @param {string} message - 错误消息
 * @param {number} statusCode - HTTP状态码（默认400）
 */
function errorResponse(res, message, statusCode = 400) {
    res.status(statusCode).json({
        success: false,
        message: message
    });
}

/**
 * 标准化答案字母：处理 "A. 选项A" → "A"
 * 兼容 AI 生成和历史数据中带前缀的格式
 */
function normalizeLetter(s) {
    if (!s) return '';
    const trimmed = String(s).trim();
    const match = trimmed.match(/^([A-Za-z])\./);
    return match ? match[1] : trimmed;
}

// ================================
// 4. API路由定义
// ================================

// 4.1 根路径 - API文档
app.get('/', (req, res) => {
    res.json({
        success: true,
        message: 'StudyApp后端API运行正常',
        version: '2.0.0',
        database: 'MySQL',
        endpoints: [
            'POST /register - 用户注册（需username, password, role）',
            'POST /login - 用户登录（需username, password）'
        ],
        note: '所有接口返回统一格式：{ success: true/false, message: "...", data: ... }'
    });
});

// 4.2 用户注册接口
// 路径：POST /register
// 功能：插入用户数据，检查用户名是否已存在
// 请求体：{ username: "用户名", password: "密码", role: "角色（可选，默认student）", fullName, birthday, securityQuestion, securityAnswer }
// 安全：密码和密保答案均使用 bcrypt 哈希后存储，不保存明文
app.post('/register', async (req, res) => {
    try {
        const { username, password, role = 'student', fullName, birthday, securityQuestion, securityAnswer } = req.body;

        // 参数验证
        if (!username || !password) {
            return errorResponse(res, '用户名和密码不能为空');
        }

        if (username.length < 3 || username.length > 50) {
            return errorResponse(res, '用户名长度需在3-50个字符之间');
        }

        if (password.length < 6) {
            return errorResponse(res, '密码长度至少6位');
        }

        // 检查用户名是否已存在
        const checkSql = 'SELECT id FROM users WHERE username = ?';
        const existingUsers = await db.executeQuery(checkSql, [username]);

        if (existingUsers.length > 0) {
            return errorResponse(res, '用户名已存在');
        }

        // bcrypt 哈希密码，防止明文存储
        const hashedPassword = await bcrypt.hash(password, SALT_ROUNDS);

        // bcrypt 哈希密保答案（如提供）
        const hashedAnswer = securityAnswer
            ? await bcrypt.hash(securityAnswer, SALT_ROUNDS)
            : null;

        // 插入新用户（存储哈希值，不存明文）
        const insertSql = 'INSERT INTO users (username, password, role, full_name, birthday, security_question, security_answer) VALUES (?, ?, ?, ?, ?, ?, ?)';
        const result = await db.executeQuery(insertSql, [
            username, hashedPassword, role,
            fullName || null, birthday || null,
            securityQuestion || null, hashedAnswer
        ]);

        // 返回成功响应
        // 异步审计，不阻塞注册
        setImmediate(() => audit(req, { actionType: 'register', targetType: 'user', targetId: result.insertId, result: 'success', detail: { username, role } }));
        successResponse(res, {
            id: result.insertId,
            username: username,
            role: role
        }, '注册成功');

    } catch (error) {
        console.error('注册接口错误:', error);

        // 处理数据库唯一约束错误（备用检查）
        if (error.code === 'ER_DUP_ENTRY') {
            return errorResponse(res, '用户名已存在');
        }

        errorResponse(res, '服务器内部错误', 500);
    }
});

// 4.3 用户登录接口
// 路径：POST /login
// 功能：查询users表，bcrypt 校验密码，支持旧明文自动升级
// 请求体：{ username: "用户名", password: "密码" }
// 安全：不按明文密码查 SQL，查询后通过 bcrypt.compare 校验；含限流防爆破
app.post('/login', checkLoginLock, loginRateLimit, async (req, res) => {
    try {
        const { username, password } = req.body;

        // 参数验证
        if (!username || !password) {
            return errorResponse(res, '用户名和密码不能为空');
        }

        // 仅按用户名查询（绝不用密码做 WHERE 条件）
        const sql = 'SELECT id, username, password, role, avatar_url, must_change_password FROM users WHERE username = ?';
        const users = await db.executeQuery(sql, [username]);

        if (users.length === 0) {
            trackLoginFailure(username, req);
            audit(req, { actionType: 'login_failure', targetType: 'user', targetId: username, result: 'failure', detail: { reason: 'user_not_found' } });
            return errorResponse(res, '用户名或密码错误', 401);
        }

        const user = users[0];
        const storedPassword = user.password;

        // ==================== 密码校验 ====================
        let passwordMatch = false;
        let needsUpgrade = false;  // 是否需要升级为 bcrypt 哈希

        if (!storedPassword) {
            trackLoginFailure(username, req);
            audit(req, { actionType: 'login_failure', targetType: 'user', targetId: user.id, result: 'failure', detail: { reason: 'no_password' } });
            return errorResponse(res, '用户名或密码错误', 401);
        } else if (storedPassword.startsWith('$2b$') || storedPassword.startsWith('$2a$') || storedPassword.startsWith('$2y$')) {
            // 已经是 bcrypt 哈希 → 标准校验
            passwordMatch = await bcrypt.compare(password, storedPassword);
        } else {
            // 旧版明文密码 → 升级模式：直接比较，匹配后写回哈希
            passwordMatch = (password === storedPassword);
            if (passwordMatch) {
                needsUpgrade = true;
            }
        }

        if (!passwordMatch) {
            trackLoginFailure(username, req);
            audit(req, { actionType: 'login_failure', targetType: 'user', targetId: user.id, result: 'failure', detail: { reason: 'wrong_password' } });
            return errorResponse(res, '用户名或密码错误', 401);
        }

        // ==================== 密码升级（明文→哈希） ====================
        if (needsUpgrade) {
            try {
                const hashed = await bcrypt.hash(password, SALT_ROUNDS);
                await db.executeQuery(
                    'UPDATE users SET password = ?, must_change_password = false WHERE id = ?',
                    [hashed, user.id]
                );
                console.log(`✅ 用户 ${username} 密码已从明文升级为 bcrypt 哈希`);
            } catch (upgradeError) {
                console.warn('⚠️ 密码升级失败（不阻止登录）:', upgradeError.message);
            }
        }

        // ==================== 检查强制修改密码 ====================
        const mustChangePassword = !!user.must_change_password;

        // 更新用户最后活动时间
        try {
            await db.executeQuery(
                'UPDATE users SET last_active_at = CURRENT_TIMESTAMP WHERE id = ?',
                [user.id]
            );
        } catch (updateError) {
            console.warn('更新最后活动时间失败:', updateError.message);
        }

        // ==================== 登录成功，生成 JWT 令牌 ====================
        clearLoginAttempts(username, req);
        const accessToken = generateAccessToken(user);
        const refreshToken = generateRefreshToken(user);

        audit(req, { actionType: 'login_success', targetType: 'user', targetId: user.id, result: 'success', detail: { role: user.role } });

        successResponse(res, {
            id: user.id,
            username: user.username,
            role: user.role,
            avatarUrl: user.avatar_url || null,
            mustChangePassword: mustChangePassword,
            access_token: accessToken,
            refresh_token: refreshToken
        }, mustChangePassword ? '登录成功，但需要修改密码' : '登录成功');

    } catch (error) {
        console.error('登录接口错误:', error);
        errorResponse(res, '服务器内部错误', 500);
    }
});

// 4.4 刷新令牌接口
// 路径：POST /auth/refresh
// 功能：使用 refresh_token 换取新的 access_token + refresh_token
// 请求体：{ refresh_token: "..." }
app.post('/auth/refresh', async (req, res) => {
    try {
        const { refresh_token } = req.body;

        if (!refresh_token) {
            return errorResponse(res, '缺少 refresh_token');
        }

        const tokens = refreshTokens(refresh_token);
        successResponse(res, tokens, '令牌刷新成功');
    } catch (error) {
        console.error('刷新令牌错误:', error);
        if (error.name === 'TokenExpiredError') {
            return errorResponse(res, '刷新令牌已过期，请重新登录', 401);
        }
        if (error.name === 'JsonWebTokenError') {
            return errorResponse(res, '刷新令牌无效', 401);
        }
        errorResponse(res, '刷新令牌失败', 401);
    }
});

// 4.5 忘记密码 - 查询密保问题
// 路径：POST /forgot-password/query
// 请求体：{ username }
app.post('/forgot-password/query', async (req, res) => {
    try {
        const { username } = req.body;
        if (!username) {
            return errorResponse(res, '请输入用户名');
        }

        const users = await db.executeQuery(
            'SELECT full_name, birthday, security_question FROM users WHERE username = ?',
            [username]
        );

        if (users.length === 0) {
            return errorResponse(res, '该用户名不存在');
        }

        const user = users[0];
        if (!user.security_question) {
            return errorResponse(res, '该用户未设置密保问题，无法找回密码');
        }

        // 格式化日期，避免时区偏移问题
        let birthdayStr = '';
        if (user.birthday) {
            const bd = user.birthday instanceof Date ? user.birthday : new Date(user.birthday);
            const y = bd.getFullYear();
            const m = String(bd.getMonth() + 1).padStart(2, '0');
            const d = String(bd.getDate()).padStart(2, '0');
            birthdayStr = `${y}-${m}-${d}`;
        }

        successResponse(res, {
            fullName: user.full_name || '',
            birthday: birthdayStr,
            securityQuestion: user.security_question
        }, '查询成功');
    } catch (error) {
        console.error('忘记密码查询错误:', error);
        errorResponse(res, '服务器内部错误', 500);
    }
});

// 4.5 忘记密码 - 重置密码
// 路径：POST /forgot-password/reset
// 请求体：{ username, fullName, birthday, securityAnswer, newPassword }
// 安全：密保答案按 bcrypt 哈希校验（兼容旧明文），新密码 bcrypt 存储
app.post('/forgot-password/reset', async (req, res) => {
    try {
        const { username, fullName, birthday, securityAnswer, newPassword } = req.body;

        if (!username || !fullName || !birthday || !securityAnswer || !newPassword) {
            return errorResponse(res, '请填写所有必填字段');
        }

        if (newPassword.length < 6) {
            return errorResponse(res, '新密码长度至少6位');
        }

        const users = await db.executeQuery(
            'SELECT id, full_name, birthday, security_question, security_answer FROM users WHERE username = ?',
            [username]
        );

        if (users.length === 0) {
            return errorResponse(res, '该用户名不存在');
        }

        const user = users[0];

        if (!user.full_name || !user.birthday || !user.security_answer) {
            return errorResponse(res, '该用户未设置完整的密保信息，无法重置密码');
        }

        // 验证姓名
        if (fullName !== user.full_name) {
            return errorResponse(res, '姓名验证失败');
        }

        // 验证生日
        const inputDate = birthday.replace(/-/g, '');
        let storedDate;
        if (user.birthday instanceof Date) {
            const y = user.birthday.getFullYear();
            const m = String(user.birthday.getMonth() + 1).padStart(2, '0');
            const d = String(user.birthday.getDate()).padStart(2, '0');
            storedDate = `${y}${m}${d}`;
        } else {
            storedDate = String(user.birthday).replace(/-/g, '');
        }
        if (inputDate !== storedDate) {
            return errorResponse(res, '生日验证失败');
        }

        // ==================== 验证密保答案 ====================
        const storedAnswer = user.security_answer;
        let answerMatch = false;

        if (storedAnswer && (storedAnswer.startsWith('$2b$') || storedAnswer.startsWith('$2a$') || storedAnswer.startsWith('$2y$'))) {
            // bcrypt 哈希 → 标准校验
            answerMatch = await bcrypt.compare(securityAnswer, storedAnswer);
        } else {
            // 旧版明文 → 直接比较（忽略大小写），匹配后升级为哈希
            answerMatch = storedAnswer && (securityAnswer.toLowerCase() === storedAnswer.toLowerCase());
            if (answerMatch) {
                try {
                    const hashedAnswer = await bcrypt.hash(securityAnswer, SALT_ROUNDS);
                    await db.executeQuery(
                        'UPDATE users SET security_answer = ? WHERE id = ?',
                        [hashedAnswer, user.id]
                    );
                    console.log(`✅ 用户 ${username} 密保答案已从明文升级为 bcrypt 哈希`);
                } catch (upgradeError) {
                    console.warn('⚠️ 密保答案升级失败:', upgradeError.message);
                }
            }
        }

        if (!answerMatch) {
            return errorResponse(res, '密保答案错误');
        }

        // ==================== 更新密码（bcrypt 哈希） ====================
        const hashedPassword = await bcrypt.hash(newPassword, SALT_ROUNDS);
        await db.executeQuery('UPDATE users SET password = ?, must_change_password = false WHERE id = ?', [hashedPassword, user.id]);

        setImmediate(() => audit(req, { actionType: 'password_reset', targetType: 'user', targetId: user.id, result: 'success' }));
        successResponse(res, null, '密码重置成功，请使用新密码登录');

    } catch (error) {
        console.error('重置密码错误:', error);
        errorResponse(res, '服务器内部错误', 500);
    }
});

// 4.6 头像上传接口
// 路径：POST /upload/avatar
// 功能：上传用户头像，支持 jpeg/png/gif/webp，最大 5MB
// 安全：userId 从认证令牌获取，不接受客户端传入
app.post('/upload/avatar', authenticate, uploadAvatar.single('avatar'), async (req, res) => {
    try {
        if (!req.file) {
            return errorResponse(res, '请选择要上传的图片');
        }

        const userId = req.user.id;

        const users = await db.executeQuery('SELECT id, avatar_url FROM users WHERE id = ?', [userId]);
        if (users.length === 0) {
            fs.unlinkSync(req.file.path);
            return errorResponse(res, '用户不存在', 404);
        }

        // 删除旧头像文件
        const oldUrl = users[0].avatar_url;
        if (oldUrl) {
            const oldPath = path.join(__dirname, oldUrl);
            if (fs.existsSync(oldPath)) fs.unlinkSync(oldPath);
        }

        const avatarUrl = '/uploads/avatars/' + req.file.filename;
        await db.executeQuery('UPDATE users SET avatar_url = ? WHERE id = ?', [avatarUrl, userId]);

        console.log(`✅ 用户 ${userId} 头像已更新`);
        successResponse(res, { avatarUrl: avatarUrl }, '头像上传成功');
    } catch (error) {
        console.error('❌ 头像上传错误:', error);
        errorResponse(res, '头像上传失败', 500);
    }
});

// 4.5 课程封面上传接口
// 路径：POST /upload/course-image
// 功能：上传AI生成的课程封面图（教师/管理员）
app.post('/upload/course-image', authenticate, requireRole('teacher', 'admin'), uploadCourseImage.single('image'), async (req, res) => {
    try {
        if (!req.file) {
            return errorResponse(res, '请提供封面图片');
        }
        const imageUrl = `/uploads/course-images/${req.file.filename}`;
        successResponse(res, { imageUrl }, '封面上传成功');
    } catch (error) {
        console.error('课程封面上传错误:', error);
        errorResponse(res, '封面上传失败', 500);
    }
});

// 4.5B1 本地视频上传接口（本地部署替代 OSS）
// 路径：POST /upload/video
// 功能：上传视频到本地服务器（替代 OSS，适合开发和内网部署）
app.post('/upload/video', authenticate, uploadVideo.single('video'), async (req, res) => {
    try {
        if (!req.file) {
            return errorResponse(res, '请选择视频文件');
        }
        const fileUrl = `${req.protocol}://${req.get('host')}/uploads/videos/${req.file.filename}`;
        console.log(`✅ 视频上传成功: ${fileUrl}`);
        successResponse(res, { fileUrl, fileName: req.file.filename }, '视频上传成功');
    } catch (error) {
        console.error('视频上传错误:', error);
        errorResponse(res, '视频上传失败', 500);
    }
});

// 4.5B2 本地视频上传（原始流模式，不经过 multipart）
// 路径：POST /upload/video/raw
// 功能：接受原始文件流 + X-Filename 头，适用于 Android HttpURLConnection
// 注意：本地开发阶段暂不校验 JWT 令牌，方便测试。部署到公网时需加回 authenticate
app.post('/upload/video/raw', (req, res) => {
    try {
        const fileName = req.headers['x-filename'] || `upload_${Date.now()}.mp4`;
        const safeName = fileName.replace(/[^a-zA-Z0-9一-鿿._-]/g, '_').substring(0, 100);
        const timestamp = Date.now();
        const storedName = `${timestamp}_${safeName}`;
        const filePath = path.join(videosDir, storedName);

        const writeStream = fs.createWriteStream(filePath);
        req.pipe(writeStream);

        writeStream.on('finish', () => {
            const fileUrl = `${req.protocol}://${req.get('host')}/uploads/videos/${storedName}`;
            console.log(`✅ 原始流上传成功: ${fileUrl}`);
            successResponse(res, { fileUrl, fileName: storedName }, '视频上传成功');
        });

        writeStream.on('error', (err) => {
            console.error('❌ 视频写入错误:', err);
            errorResponse(res, '视频保存失败', 500);
        });
    } catch (error) {
        console.error('❌ 视频上传错误:', error);
        errorResponse(res, '视频上传失败', 500);
    }
});

// 4.5B AI课程封面生成接口
// 路径：POST /generate-course-image
// 功能：调用 Pollinations AI 根据提示词生成课程封面图（教师/管理员）
app.post('/generate-course-image', authenticate, requireRole('teacher', 'admin'), async (req, res) => {
    try {
        const { prompt, courseName } = req.body;
        if (!prompt && !courseName) {
            return errorResponse(res, '请提供提示词或课程名称');
        }

        // 构造英文prompt（Pollinations AI 对英文更稳定）
        const userText = prompt || courseName || 'course';
        const fullPrompt = `Course cover image, ${userText}, educational theme, modern minimal design, clean, professional style, 4K quality`;
        const encodedPrompt = encodeURIComponent(fullPrompt.substring(0, 300));
        const seed = Date.now();
        const imageUrl = `https://image.pollinations.ai/prompt/${encodedPrompt}?width=600&height=400&nologo=true&seed=${seed}`;

        // 生成文件名
        const safeName = (courseName || 'course').replace(/[^a-zA-Z0-9一-鿿]/g, '_').substring(0, 30);
        const filename = `${safeName}_ai_${Date.now()}.jpg`;
        const filePath = path.join(courseImagesDir, filename);

        console.log(`🎨 正在生成AI课程封面: ${userText}`);
        console.log(`   Pollinations URL: ${imageUrl.substring(0, 100)}...`);

        // 下载生成的图片
        await downloadImage(imageUrl, filePath, 45000);

        const resultUrl = `/uploads/course-images/${filename}`;
        console.log(`✅ AI封面生成成功: ${resultUrl}`);
        successResponse(res, { imageUrl: resultUrl }, '课程封面生成成功');
    } catch (error) {
        console.error('❌ AI课程封面生成错误:', error.message);
        errorResponse(res, 'AI封面生成失败: ' + error.message, 500);
    }
});

// ================================
// 4.5C 辅助函数：从URL下载图片到本地
// ================================
function downloadImage(url, filePath, timeout = 30000) {
    return new Promise((resolve, reject) => {
        const file = fs.createWriteStream(filePath);
        let timedOut = false;

        const req = https.get(url, (response) => {
            // 处理重定向
            if (response.statusCode >= 300 && response.statusCode < 400 && response.headers.location) {
                file.close();
                fs.unlink(filePath, () => {});
                console.log(`   → 重定向到 ${response.headers.location.substring(0, 60)}...`);
                return downloadImage(response.headers.location, filePath, timeout).then(resolve).catch(reject);
            }

            if (response.statusCode !== 200) {
                file.close();
                fs.unlink(filePath, () => {});
                return reject(new Error(`Pollinations返回HTTP ${response.statusCode}`));
            }

            response.pipe(file);
            file.on('finish', () => {
                file.close();
                if (timedOut) return;
                const stats = fs.statSync(filePath);
                if (stats.size === 0) {
                    fs.unlink(filePath, () => {});
                    reject(new Error('下载的图片为空'));
                } else {
                    resolve();
                }
            });
        });

        req.on('error', (err) => {
            file.close();
            fs.unlink(filePath, () => {});
            if (!timedOut) reject(err);
        });

        req.setTimeout(timeout, () => {
            timedOut = true;
            req.destroy();
            file.close();
            fs.unlink(filePath, () => {});
            reject(new Error('AI图片生成超时，请稍后重试'));
        });
    });
}

// 4.6 获取所有用户接口（仅管理员可用）
// 路径：GET /users
// 功能：获取所有用户（仅管理员）
app.get('/users', authenticate, requireRole('admin'), async (req, res) => {
    try {
        const users = await db.executeQuery('SELECT id, username, role, avatar_url FROM users');
        audit(req, { actionType: 'admin_list_users', result: 'success', detail: { total: users.length } });
        successResponse(res, users, '获取用户列表成功');
    } catch (error) {
        console.error('获取用户列表错误:', error);
        errorResponse(res, '服务器内部错误', 500);
    }
});

// 4.5 健康检查接口
// 路径：GET /health
// 功能：检查服务器和数据库连接状态
app.get('/health', async (req, res) => {
    try {
        // 测试数据库连接
        await db.executeQuery('SELECT 1');
        res.json({
            success: true,
            message: '服务器运行正常',
            timestamp: new Date().toISOString(),
            database: 'connected'
        });
    } catch (error) {
        res.status(500).json({
            success: false,
            message: '数据库连接失败',
            timestamp: new Date().toISOString(),
            database: 'disconnected'
        });
    }
});

// 4.6 分类管理接口
// 路径：GET /categories
// 功能：获取所有分类
app.get('/categories', async (req, res) => {
    try {
        const sql = `
            SELECT c.*, COUNT(co.id) as course_count
            FROM categories c
            LEFT JOIN courses co ON c.id = co.category_id
            GROUP BY c.id
            ORDER BY c.name ASC
        `;
        const categories = await db.executeQuery(sql);
        successResponse(res, categories, '获取分类列表成功');
    } catch (error) {
        console.error('获取分类列表错误:', error);
        errorResponse(res, '获取分类列表失败', 500);
    }
});

// 路径：POST /categories
// 功能：创建新分类（管理员）
app.post('/categories', authenticate, requireRole('admin'), async (req, res) => {
    try {
        const { name } = req.body;

        if (!name || !name.trim()) {
            return errorResponse(res, '分类名称不能为空');
        }

        const result = await db.executeQuery(
            'INSERT INTO categories (name) VALUES (?)',
            [name.trim()]
        );

        audit(req, { actionType: 'category_create', targetType: 'category', targetId: result.insertId, result: 'success', detail: { name: name.trim() } });
        successResponse(res, {
            id: result.insertId,
            name: name.trim()
        }, '分类创建成功');
    } catch (error) {
        if (error.code === 'ER_DUP_ENTRY') {
            return errorResponse(res, '分类名称已存在', 400);
        }
        console.error('创建分类错误:', error);
        errorResponse(res, '创建分类失败', 500);
    }
});

// 路径：PUT /categories/:id
// 功能：更新分类名称（管理员）
app.put('/categories/:id', authenticate, requireRole('admin'), async (req, res) => {
    try {
        const { id } = req.params;
        const { name } = req.body;

        if (!name || !name.trim()) {
            return errorResponse(res, '分类名称不能为空');
        }

        await db.executeQuery(
            'UPDATE categories SET name = ? WHERE id = ?',
            [name.trim(), id]
        );

        audit(req, { actionType: 'category_update', targetType: 'category', targetId: id, result: 'success', detail: { name: name.trim() } });
        successResponse(res, { id: parseInt(id), name: name.trim() }, '分类更新成功');
    } catch (error) {
        if (error.code === 'ER_DUP_ENTRY') {
            return errorResponse(res, '分类名称已存在', 400);
        }
        console.error('更新分类错误:', error);
        errorResponse(res, '更新分类失败', 500);
    }
});

// 路径：DELETE /categories/:id
// 功能：删除分类（管理员）
app.delete('/categories/:id', authenticate, requireRole('admin'), async (req, res) => {
    try {
        const { id } = req.params;
        await db.executeQuery('DELETE FROM categories WHERE id = ?', [id]);
        audit(req, { actionType: 'category_delete', targetType: 'category', targetId: id, result: 'success' });
        successResponse(res, null, '分类删除成功');
    } catch (error) {
        console.error('删除分类错误:', error);
        errorResponse(res, '删除分类失败', 500);
    }
});

// 4.7 获取课程列表接口
// 路径：GET /courses
// 功能：获取所有课程或按教师筛选的课程
// 参数：teacherId (可选) - 教师用户ID
app.get('/courses', async (req, res) => {
    try {
        const { teacherId } = req.query;
        let sql = `
            SELECT c.*, cat.name as category_name
            FROM courses c
            LEFT JOIN categories cat ON c.category_id = cat.id
        `;
        const params = [];

        if (teacherId) {
            sql += ' WHERE c.teacher_id = ?';
            params.push(teacherId);
        }

        sql += ' ORDER BY c.created_at DESC';

        const courses = await db.executeQuery(sql, params);
        successResponse(res, courses, '获取课程列表成功');
    } catch (error) {
        console.error('获取课程列表错误:', error);
        errorResponse(res, '获取课程列表失败', 500);
    }
});

// 4.6.1 获取用户已选课程接口
// 路径：GET /getCourses
// 安全：userId 从认证令牌获取，不接受客户端传入
app.get('/getCourses', authenticate, async (req, res) => {
    try {
        const userId = req.user.id;

        const sql = `
            SELECT c.*, cat.name as category_name, ce.student_id, ce.enrolled_at,
                   COALESCE(sr.progress, 0) as progress
            FROM courses c
            LEFT JOIN categories cat ON c.category_id = cat.id
            JOIN course_enrollments ce ON c.id = ce.course_id
            LEFT JOIN study_records sr ON c.id = sr.course_id AND ce.student_id = sr.student_id
            WHERE ce.student_id = ?
        `;
        const courses = await db.executeQuery(sql, [userId]);

        // 转换为与 /courses 一致的响应格式
        const result = courses.map(c => ({
            id: c.id,
            name: c.name,
            description: c.description,
            teacher: c.teacher_name,
            teacher_name: c.teacher_name,
            credit: c.credit,
            video_url: c.video_url,
            image_url: c.image_url,
            category_id: c.category_id,
            category_name: c.category_name,
            created_at: c.created_at,
            selected_at: c.enrolled_at,
            progress: c.progress
        }));

        successResponse(res, result, '获取已选课程成功');
    } catch (error) {
        console.error('获取已选课程错误:', error);
        errorResponse(res, '获取已选课程失败', 500);
    }
});

// 4.7 创建课程接口
// 路径：POST /courses
// 功能：教师创建新课程
// 安全：teacherId 从认证令牌获取，不接受客户端传入
app.post('/courses', authenticate, requireRole('teacher', 'admin'), async (req, res) => {
    try {
        const { name, description, credit = 2, videoUrl, imageUrl, categoryId } = req.body;
        const teacherId = req.user.id;

        if (!name) {
            return errorResponse(res, '课程名称不能为空');
        }

        // 从数据库获取教师姓名
        const teacherCheck = await db.executeQuery(
            'SELECT id, username FROM users WHERE id = ?',
            [teacherId]
        );

        if (teacherCheck.length === 0) {
            return errorResponse(res, '教师不存在', 404);
        }

        const teacherName = teacherCheck[0].username;

        const sql = `
            INSERT INTO courses (name, description, teacher_id, teacher_name, credit, video_url, image_url, category_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        `;
        const result = await db.executeQuery(sql, [name, description || null, teacherId, teacherName, credit, videoUrl || null, imageUrl || null, categoryId || null]);

        successResponse(res, {
            courseId: result.insertId,
            name: name,
            teacherName: teacherName
        }, '课程创建成功');
    } catch (error) {
        console.error('创建课程错误:', error);
        errorResponse(res, '创建课程失败', 500);
    }
});

// 4.7B 获取课程题目接口
// 路径：GET /courses/:id/questions?status=published
// 功能：获取指定课程的所有题目，支持按状态筛选（学生传?status=published只看已发布）
app.get('/courses/:id/questions', async (req, res) => {
    try {
        const courseId = req.params.id;
        const status = req.query.status; // 可选: "published" 或 "draft"

        // 检查课程是否存在
        const courseCheck = await db.executeQuery('SELECT id FROM courses WHERE id = ?', [courseId]);
        if (courseCheck.length === 0) {
            return errorResponse(res, '课程不存在', 404);
        }

        let sql = 'SELECT * FROM questions WHERE course_id = ?';
        const params = [courseId];

        if (status) {
            sql += ' AND status = ?';
            params.push(status);
        }

        sql += ' ORDER BY id ASC';

        const questions = await db.executeQuery(sql, params);

        // 解析options JSON字段
        const result = questions.map(q => ({
            id: q.id,
            course_id: q.course_id,
            question_text: q.question_text,
            options: typeof q.options === 'string' ? JSON.parse(q.options) : q.options,
            correct_answer: q.correct_answer,
            status: q.status || 'published',
            source: q.source || 'ai',
            created_at: q.created_at
        }));

        successResponse(res, result, '获取题目列表成功');
    } catch (error) {
        console.error('获取题目列表错误:', error);
        errorResponse(res, '获取题目列表失败', 500);
    }
});

// 4.7C 保存课程题目接口
// 路径：POST /courses/:id/questions
// 功能：保存PPT自动生成的选择题（教师/管理员，需课程归属）
app.post('/courses/:id/questions', authenticate, requireRole('teacher', 'admin'), requireCourseOwnership(db), async (req, res) => {
    try {
        const courseId = req.params.id;
        const { questions } = req.body;

        if (!questions || !Array.isArray(questions) || questions.length === 0) {
            return errorResponse(res, '请提供有效的题目列表');
        }

        // 检查课程是否存在
        const courseCheck = await db.executeQuery('SELECT id FROM courses WHERE id = ?', [courseId]);
        if (courseCheck.length === 0) {
            return errorResponse(res, '课程不存在', 404);
        }

        // 删除该课程旧的题目（重新生成时覆盖）
        await db.executeQuery('DELETE FROM questions WHERE course_id = ?', [courseId]);

        // 批量插入题目
        for (const q of questions) {
            const { questionText, options, correctAnswer } = q;
            if (!questionText || !options || !correctAnswer) continue;
            const optionsJson = JSON.stringify(options);
            await db.executeQuery(
                'INSERT INTO questions (course_id, question_text, options, correct_answer) VALUES (?, ?, ?, ?)',
                [courseId, questionText, optionsJson, correctAnswer]
            );
        }

        successResponse(res, { courseId: parseInt(courseId), count: questions.length }, '题目保存成功');
    } catch (error) {
        console.error('保存题目错误:', error);
        errorResponse(res, '保存题目失败', 500);
    }
});

// 4.7G 提交测验答案接口
// 路径：POST /courses/:id/quiz/submit
// 功能：学生提交课程测验答案，自动评分并记录
// 安全：studentId 从认证令牌获取，不接受客户端传入
app.post('/courses/:id/quiz/submit', authenticate, async (req, res) => {
    try {
        const courseId = req.params.id;
        const studentId = req.user.id;
        const { answers } = req.body;

        if (!answers) {
            return errorResponse(res, '答案不能为空');
        }

        // 获取该课程已发布的题目（只对已发布题目评分）
        const questions = await db.executeQuery(
            "SELECT * FROM questions WHERE course_id = ? AND status = 'published'", [courseId]
        );

        if (questions.length === 0) {
            return errorResponse(res, '该课程暂无测验题目');
        }

        // 逐题评分别
        let correctCount = 0;
        for (const q of questions) {
            const userAnswer = answers[String(q.question_text)] || answers[String(q.id)];
            if (userAnswer && normalizeLetter(userAnswer) === normalizeLetter(q.correct_answer)) {
                correctCount++;
            }
        }

        const totalQuestions = questions.length;
        const score = Math.round((correctCount / totalQuestions) * 100);

        // 存储答题记录（upsert）
        const answersJson = JSON.stringify(answers);
        await db.executeQuery(`
            INSERT INTO quiz_attempts (course_id, student_id, score, total_questions, answers)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
            score = VALUES(score),
            total_questions = VALUES(total_questions),
            answers = VALUES(answers),
            submitted_at = CURRENT_TIMESTAMP
        `, [courseId, studentId, score, totalQuestions, answersJson]);

        successResponse(res, {
            score: score,
            totalQuestions: totalQuestions,
            correctCount: correctCount,
            submittedAt: new Date().toISOString()
        }, '提交成功');
    } catch (error) {
        console.error('提交测验错误:', error);
        errorResponse(res, '提交测验失败', 500);
    }
});

// 4.7H 获取测验结果接口
// 路径：GET /courses/:id/quiz/result
// 功能：获取学生某课程的测验结果
// 安全：studentId 从认证令牌获取，不接受客户端传入
app.get('/courses/:id/quiz/result', authenticate, async (req, res) => {
    try {
        const courseId = req.params.id;
        const studentId = req.user.id;

        const attempts = await db.executeQuery(
            'SELECT * FROM quiz_attempts WHERE course_id = ? AND student_id = ?',
            [courseId, studentId]
        );

        if (attempts.length === 0) {
            return successResponse(res, null, '尚未作答');
        }

        const attempt = attempts[0];

        // 解析学生答案
        let answers = {};
        try {
            answers = JSON.parse(attempt.answers);
        } catch (e) {
            answers = {};
        }

        // 获取该课程所有已发布题目（含正确答案），让学生能回顾
        const questions = await db.executeQuery(
            "SELECT * FROM questions WHERE course_id = ? AND status = 'published'",
            [courseId]
        );

        const formattedQuestions = questions.map(q => {
            const studentAnswer = answers[String(q.question_text)] || answers[String(q.id)] || null;
            return {
                id: q.id,
                question_text: q.question_text,
                options: typeof q.options === 'string' ? JSON.parse(q.options) : q.options,
                correct_answer: normalizeLetter(q.correct_answer),
                student_answer: studentAnswer,
                is_correct: studentAnswer !== null && normalizeLetter(studentAnswer) === normalizeLetter(q.correct_answer)
            };
        });

        successResponse(res, {
            score: attempt.score,
            totalQuestions: attempt.total_questions,
            correctCount: Math.round(attempt.score / 100 * attempt.total_questions),
            submittedAt: attempt.submitted_at,
            questions: formattedQuestions
        }, '获取答题结果成功');
    } catch (error) {
        console.error('获取测验结果错误:', error);
        errorResponse(res, '获取测验结果失败', 500);
    }
});

// ============================================================
// 4.7I 获取课程学生统计数据（教师查看学生明细）
// ============================================================

// 4.7I.1 获取课程下每个学生的学习统计
// 路径：GET /courses/:id/students/stats
// 功能：获取某门课程所有学生的观看时长、点击次数、完成度、答题得分（教师/管理员）
app.get('/courses/:id/students/stats', authenticate, requireRole('teacher', 'admin'), async (req, res) => {
    try {
        const courseId = req.params.id;

        // 检查课程
        const courseCheck = await db.executeQuery('SELECT id, name FROM courses WHERE id = ?', [courseId]);
        if (courseCheck.length === 0) {
            return errorResponse(res, '课程不存在', 404);
        }

        // 获取选课学生
        const enrollments = await db.executeQuery(`
            SELECT u.id, u.username, ce.enrolled_at
            FROM course_enrollments ce
            JOIN users u ON ce.student_id = u.id
            WHERE ce.course_id = ?
            ORDER BY u.username ASC
        `, [courseId]);

        if (enrollments.length === 0) {
            return successResponse(res, { courseId: parseInt(courseId), students: [] }, '该课程暂无学生');
        }

        const studentsStats = [];

        for (const stu of enrollments) {
            // 学习记录统计
            const studyStats = await db.executeQuery(`
                SELECT
                    COALESCE(SUM(watch_time), 0) as total_watch_time,
                    COALESCE(AVG(progress), 0) as avg_progress,
                    COALESCE(SUM(click_count), 0) as total_click_count,
                    COUNT(*) as study_record_count
                FROM study_records
                WHERE course_id = ? AND student_id = ?
            `, [courseId, stu.id]);

            const ss = studyStats[0] || {};

            // 答题记录
            const quizAttempt = await db.executeQuery(`
                SELECT score, total_questions, submitted_at
                FROM quiz_attempts
                WHERE course_id = ? AND student_id = ?
            `, [courseId, stu.id]);

            const quiz = quizAttempt[0] || null;

            studentsStats.push({
                studentId: stu.id,
                studentName: stu.username,
                enrolledAt: stu.enrolled_at,
                totalWatchTime: parseInt(ss.total_watch_time) || 0,
                averageProgress: Math.round((parseFloat(ss.avg_progress) || 0) * 10) / 10,
                totalClickCount: parseInt(ss.total_click_count) || 0,
                studyRecordCount: parseInt(ss.study_record_count) || 0,
                quiz: quiz ? {
                    score: quiz.score,
                    totalQuestions: quiz.total_questions,
                    submittedAt: quiz.submitted_at
                } : null
            });
        }

        // 总体汇总
        const totalWatchTime = studentsStats.reduce((s, st) => s + st.totalWatchTime, 0);
        const totalClicks = studentsStats.reduce((s, st) => s + st.totalClickCount, 0);

        successResponse(res, {
            courseId: parseInt(courseId),
            courseName: courseCheck[0].name,
            totalStudents: studentsStats.length,
            totalWatchTime,
            totalClickCount: totalClicks,
            students: studentsStats
        }, '获取学生统计数据成功');
    } catch (error) {
        console.error('获取学生统计数据错误:', error);
        errorResponse(res, '获取学生统计数据失败', 500);
    }
});

// 4.7I.2 获取课程各题目的正确率
// 路径：GET /courses/:id/questions/accuracy
// 功能：统计每道题的正确率（答对人数/答题人数）（教师/管理员）
app.get('/courses/:id/questions/accuracy', authenticate, requireRole('teacher', 'admin'), async (req, res) => {
    try {
        const courseId = req.params.id;

        // 获取课程的所有题目
        const questions = await db.executeQuery(
            'SELECT * FROM questions WHERE course_id = ? ORDER BY id ASC',
            [courseId]
        );

        if (questions.length === 0) {
            return successResponse(res, { courseId: parseInt(courseId), questions: [] }, '该课程暂无题目');
        }

        // 获取所有答题记录
        const attempts = await db.executeQuery(
            'SELECT * FROM quiz_attempts WHERE course_id = ?',
            [courseId]
        );

        const totalAttempts = attempts.length;

        // 对每道题统计正确率
        const questionAccuracy = questions.map(q => {
            let correctCount = 0;
            let answeredCount = 0;

            for (const attempt of attempts) {
                let answers;
                try {
                    answers = typeof attempt.answers === 'string' ? JSON.parse(attempt.answers) : attempt.answers;
                } catch (e) {
                    continue;
                }

                // 答案可能以 question_text 或 question.id 为 key
                const userAnswer = answers[String(q.question_text)] || answers[String(q.id)];
                if (userAnswer !== undefined) {
                    answeredCount++;
                    if (normalizeLetter(userAnswer) === normalizeLetter(q.correct_answer)) {
                        correctCount++;
                    }
                }
            }

            return {
                questionId: q.id,
                questionText: q.question_text,
                options: typeof q.options === 'string' ? JSON.parse(q.options) : q.options,
                correctAnswer: q.correct_answer,
                totalAttempts: totalAttempts,
                answeredCount: answeredCount,
                correctCount: correctCount,
                accuracy: answeredCount > 0 ? Math.round((correctCount / answeredCount) * 1000) / 10 : 0
            };
        });

        successResponse(res, {
            courseId: parseInt(courseId),
            totalQuestions: questions.length,
            totalAttempts: totalAttempts,
            questions: questionAccuracy
        }, '获取题目正确率成功');
    } catch (error) {
        console.error('获取题目正确率错误:', error);
        errorResponse(res, '获取题目正确率失败', 500);
    }
});

// ============================================================
// 4.8 AI智能出题 + 教师题目管理 API
// ============================================================

// 4.8A 存储幻灯片文本接口
// 路径：POST /courses/:id/slide-texts
// 功能：保存PPT解析后的幻灯片文本，供AI生成题目使用（教师/管理员，需课程归属）
app.post('/courses/:id/slide-texts', authenticate, requireRole('teacher', 'admin'), requireCourseOwnership(db), async (req, res) => {
    try {
        const courseId = req.params.id;
        const { slideTexts } = req.body;

        if (!slideTexts || !Array.isArray(slideTexts) || slideTexts.length === 0) {
            return errorResponse(res, '请提供有效的幻灯片文本列表');
        }

        // 检查课程是否存在
        const courseCheck = await db.executeQuery('SELECT id FROM courses WHERE id = ?', [courseId]);
        if (courseCheck.length === 0) {
            return errorResponse(res, '课程不存在', 404);
        }

        // 先删除旧数据
        await db.executeQuery('DELETE FROM course_slide_texts WHERE course_id = ?', [courseId]);

        // 批量插入
        for (let i = 0; i < slideTexts.length; i++) {
            await db.executeQuery(
                'INSERT INTO course_slide_texts (course_id, slide_index, slide_text) VALUES (?, ?, ?)',
                [courseId, i + 1, slideTexts[i]]
            );
        }

        successResponse(res, { courseId: parseInt(courseId), count: slideTexts.length }, '幻灯片文本保存成功');
    } catch (error) {
        console.error('保存幻灯片文本错误:', error);
        errorResponse(res, '保存幻灯片文本失败', 500);
    }
});

// 4.8B AI生成题目接口
// 路径：POST /courses/:id/questions/ai-generate
// 功能：调用DeepSeek AI根据幻灯片文本生成选择题（教师/管理员，需课程归属）
app.post('/courses/:id/questions/ai-generate', authenticate, requireRole('teacher', 'admin'), requireCourseOwnership(db), async (req, res) => {
    try {
        const courseId = req.params.id;
        const count = Math.min(req.body.count || 10, 30);

        // 检查课程是否存在
        const courseCheck = await db.executeQuery('SELECT id FROM courses WHERE id = ?', [courseId]);
        if (courseCheck.length === 0) {
            return errorResponse(res, '课程不存在', 404);
        }

        // 获取幻灯片文本
        const slideRows = await db.executeQuery(
            'SELECT slide_text FROM course_slide_texts WHERE course_id = ? ORDER BY slide_index ASC',
            [courseId]
        );

        if (slideRows.length === 0) {
            return errorResponse(res, '该课程没有幻灯片文本，请先上传PPT文件');
        }

        const slideTexts = slideRows.map(r => r.slide_text).filter(t => t.trim().length > 0);
        if (slideTexts.length === 0) {
            return errorResponse(res, '幻灯片文本内容为空');
        }

        // 检查API密钥
        if (!process.env.DEEPSEEK_API_KEY) {
            return errorResponse(res, '服务器未配置DeepSeek API密钥，请联系管理员', 503);
        }

        // 构建AI请求
        const joinedText = slideTexts.join('\n\n---\n\n').slice(0, 80000);
        const prompt = `根据以下课程幻灯片内容，生成${count}道中文选择题。每道题必须包含4个选项和正确答案。

必须返回严格的JSON格式，不要包含其他文字：
{"questions": [{"question_text": "题目内容", "options": ["选项A", "选项B", "选项C", "选项D"], "correct_answer": "A"}]}

注意：options中不要带"A. "等前缀，只写纯文本选项。correct_answer只写字母（A/B/C/D）。

幻灯片内容：
${joinedText}`;

        console.log(`🤖 正在调用DeepSeek AI为课程${courseId}生成${count}道题目...`);

        const response = await deepseek.chat.completions.create({
            model: 'deepseek-chat',
            messages: [
                { role: 'system', content: '你是一个教育测验生成器。根据课程材料生成高质量的中文选择题。确保正确答案确实正确，干扰项合理且有迷惑性。返回严格的JSON格式。' },
                { role: 'user', content: prompt }
            ],
            max_tokens: 4096,
            temperature: 0.7
        });

        const content = response.choices[0].message.content;
        console.log('✅ DeepSeek AI响应成功，长度:', content.length);

        // 解析JSON（处理AI可能输出```json ... ```包裹的情况）
        let jsonStr = content.trim();
        if (jsonStr.startsWith('```')) {
            jsonStr = jsonStr.replace(/```json?\n?/g, '').replace(/```/g, '').trim();
        }

        let parsed;
        try {
            parsed = JSON.parse(jsonStr);
        } catch (e) {
            console.warn('⚠️  AI返回的JSON解析失败，尝试修复...');
            // 尝试找到JSON部分
            const jsonMatch = jsonStr.match(/\{[\s\S]*\}/);
            if (jsonMatch) {
                parsed = JSON.parse(jsonMatch[0]);
            } else {
                throw new Error('无法解析AI返回的JSON');
            }
        }

        const questions = parsed.questions || [];
        if (questions.length === 0) {
            return errorResponse(res, 'AI生成的题目为空，请重试');
        }

        // 验证并插入题目
        let insertedCount = 0;
        for (const q of questions) {
            if (!q.question_text || !q.options || !Array.isArray(q.options) || q.options.length < 2 || !q.correct_answer) {
                console.warn('⚠️  跳过无效题目:', q.question_text);
                continue;
            }
            const optionsJson = JSON.stringify(q.options);
            // 标准化 correct_answer：兼容 AI 可能返回 "A. 选项A" 格式
            const normalizedAnswer = normalizeLetter(q.correct_answer);
            await db.executeQuery(
                'INSERT INTO questions (course_id, question_text, options, correct_answer, status, source) VALUES (?, ?, ?, ?, ?, ?)',
                [courseId, q.question_text, optionsJson, normalizedAnswer, 'draft', 'ai']
            );
            insertedCount++;
        }

        console.log(`✅ 成功为课程${courseId}生成${insertedCount}道AI题目`);

        successResponse(res, {
            courseId: parseInt(courseId),
            totalGenerated: insertedCount
        }, `成功生成${insertedCount}道题目（草稿状态，需审核发布）`);
    } catch (error) {
        console.error('AI生成题目错误:', error);
        if (error.status === 401) {
            errorResponse(res, 'DeepSeek API密钥无效，请检查配置', 503);
        } else if (error.code === 'rate_limit_exceeded' || error.status === 429) {
            errorResponse(res, 'AI服务调用过于频繁，请稍后重试', 429);
        } else {
            errorResponse(res, 'AI生成题目失败: ' + (error.message || '未知错误'), 500);
        }
    }
});

// 4.8C 编辑题目接口
// 路径：PUT /courses/:courseId/questions/:questionId
// 功能：教师编辑题目内容（教师/管理员，需课程归属）
app.put('/courses/:courseId/questions/:questionId', authenticate, requireRole('teacher', 'admin'), requireCourseOwnership(db), async (req, res) => {
    try {
        const courseId = req.params.courseId;
        const questionId = req.params.questionId;
        const { question_text, options, correct_answer, status } = req.body;

        // 验证题目存在且属于该课程
        const existing = await db.executeQuery(
            'SELECT * FROM questions WHERE id = ? AND course_id = ?',
            [questionId, courseId]
        );
        if (existing.length === 0) {
            return errorResponse(res, '题目不存在', 404);
        }

        const updates = [];
        const params = [];

        if (question_text !== undefined) {
            updates.push('question_text = ?');
            params.push(question_text);
        }
        if (options !== undefined) {
            updates.push('options = ?');
            params.push(JSON.stringify(options));
        }
        if (correct_answer !== undefined) {
            updates.push('correct_answer = ?');
            params.push(correct_answer);
        }
        if (status !== undefined) {
            updates.push('status = ?');
            params.push(status);
        }

        if (updates.length === 0) {
            return errorResponse(res, '没有提供要更新的字段');
        }

        params.push(questionId, courseId);
        await db.executeQuery(
            `UPDATE questions SET ${updates.join(', ')} WHERE id = ? AND course_id = ?`,
            params
        );

        successResponse(res, { questionId: parseInt(questionId) }, '题目更新成功');
    } catch (error) {
        console.error('编辑题目错误:', error);
        errorResponse(res, '编辑题目失败', 500);
    }
});

// 4.8D 删除题目接口
// 路径：DELETE /courses/:courseId/questions/:questionId
// 功能：教师删除题目（教师/管理员，需课程归属）
app.delete('/courses/:courseId/questions/:questionId', authenticate, requireRole('teacher', 'admin'), requireCourseOwnership(db), async (req, res) => {
    try {
        const courseId = req.params.courseId;
        const questionId = req.params.questionId;

        const result = await db.executeQuery(
            'DELETE FROM questions WHERE id = ? AND course_id = ?',
            [questionId, courseId]
        );

        if (result.affectedRows === 0) {
            return errorResponse(res, '题目不存在', 404);
        }

        successResponse(res, { questionId: parseInt(questionId) }, '题目删除成功');
    } catch (error) {
        console.error('删除题目错误:', error);
        errorResponse(res, '删除题目失败', 500);
    }
});

// 4.8E 发布/下架题目接口
// 路径：PATCH /courses/:courseId/questions/:questionId/status
// 功能：切换题目的发布状态（教师/管理员，需课程归属）
app.patch('/courses/:courseId/questions/:questionId/status', authenticate, requireRole('teacher', 'admin'), requireCourseOwnership(db), async (req, res) => {
    try {
        const courseId = req.params.courseId;
        const questionId = req.params.questionId;
        const { status } = req.body;

        if (!status || !['draft', 'published'].includes(status)) {
            return errorResponse(res, '状态值无效，请使用 draft 或 published');
        }

        const result = await db.executeQuery(
            'UPDATE questions SET status = ? WHERE id = ? AND course_id = ?',
            [status, questionId, courseId]
        );

        if (result.affectedRows === 0) {
            return errorResponse(res, '题目不存在', 404);
        }

        successResponse(res, { questionId: parseInt(questionId), status }, `题目已${status === 'published' ? '发布' : '下架'}`);
    } catch (error) {
        console.error('更新题目状态错误:', error);
        errorResponse(res, '更新题目状态失败', 500);
    }
});

// 4.8F 手动添加题目接口
// 路径：POST /courses/:courseId/questions/manual
// 功能：教师手动添加一道题目（教师/管理员，需课程归属）
app.post('/courses/:courseId/questions/manual', authenticate, requireRole('teacher', 'admin'), requireCourseOwnership(db), async (req, res) => {
    try {
        const courseId = req.params.courseId;
        const { question_text, options, correct_answer } = req.body;

        if (!question_text || !options || !Array.isArray(options) || options.length < 2 || !correct_answer) {
            return errorResponse(res, '请提供完整的题目信息（题目内容、至少2个选项、正确答案）');
        }

        // 检查课程是否存在
        const courseCheck = await db.executeQuery('SELECT id FROM courses WHERE id = ?', [courseId]);
        if (courseCheck.length === 0) {
            return errorResponse(res, '课程不存在', 404);
        }

        const optionsJson = JSON.stringify(options);
        const result = await db.executeQuery(
            'INSERT INTO questions (course_id, question_text, options, correct_answer, status, source) VALUES (?, ?, ?, ?, ?, ?)',
            [courseId, question_text, optionsJson, correct_answer, 'published', 'manual']
        );

        successResponse(res, {
            id: result.insertId,
            course_id: parseInt(courseId),
            question_text,
            options,
            correct_answer,
            status: 'published',
            source: 'manual'
        }, '题目添加成功');
    } catch (error) {
        console.error('手动添加题目错误:', error);
        errorResponse(res, '添加题目失败', 500);
    }
});

// 4.7D 获取课程资料接口
// 路径：GET /courses/:id/materials
// 功能：获取指定课程的所有学习资料（教师/管理员，需课程归属）
app.get('/courses/:id/materials', authenticate, requireRole('teacher', 'admin'), requireCourseOwnership(db), async (req, res) => {
    try {
        const courseId = req.params.id;
        const materials = await db.executeQuery(
            'SELECT * FROM course_materials WHERE course_id = ? ORDER BY uploaded_at DESC',
            [courseId]
        );
        successResponse(res, materials, '获取资料列表成功');
    } catch (error) {
        console.error('获取资料列表错误:', error);
        errorResponse(res, '获取资料列表失败', 500);
    }
});

// 4.7E 上传课程资料接口
// 路径：POST /courses/:id/materials
// 功能：上传学习资料（PDF/Word/PPT等），PPT自动转为PDF（教师/管理员，需课程归属）
app.post('/courses/:id/materials', authenticate, requireRole('teacher', 'admin'), requireCourseOwnership(db), uploadMaterial.single('file'), async (req, res) => {
    try {
        const courseId = req.params.id;

        if (!req.file) {
            return errorResponse(res, '请选择要上传的文件');
        }

        // 检查课程是否存在
        const courseCheck = await db.executeQuery('SELECT id FROM courses WHERE id = ?', [courseId]);
        if (courseCheck.length === 0) {
            fs.unlinkSync(req.file.path);
            return errorResponse(res, '课程不存在', 404);
        }

        // Windows下multer的originalname编码修复
        const rawName = req.file.originalname;
        const originalName = Buffer.from(rawName, 'latin1').toString('utf8');

        let finalFileUrl = '/uploads/materials/' + req.file.filename;
        let finalFileType = path.extname(originalName).toLowerCase().replace('.', '');
        let finalFileSize = req.file.size;
        let finalFileName = originalName;

        // PPT/PPTX → PDF 自动转换
        const pptExts = ['ppt', 'pptx'];
        if (pptExts.includes(finalFileType)) {
            try {
                const inputPath = req.file.path;
                const outputFileName = path.basename(req.file.filename, path.extname(req.file.filename)) + '.pdf';
                const outputPath = path.join(path.dirname(inputPath), outputFileName);
                const originalNameNoExt = path.basename(originalName, path.extname(originalName));

                // 查找 LibreOffice 可执行文件（优先用完整路径）
                const librePaths = [
                    path.join('C:', 'Program Files', 'LibreOffice', 'program', 'soffice.exe'),
                    path.join('C:', 'Program Files (x86)', 'LibreOffice', 'program', 'soffice.exe'),
                    'soffice',
                ];
                let sofficePath = null;
                for (const p of librePaths) {
                    const r = require('child_process').spawnSync(p, ['--version'], { timeout: 5000, stdio: 'ignore', windowsHide: true });
                    if (r.status === 0 && !r.error) {
                        sofficePath = p;
                        break;
                    }
                }
                // 如果检测命令超时，但文件确实存在（第一次初始化较慢），直接使用完整路径
                if (!sofficePath && fs.existsSync(path.join('C:', 'Program Files', 'LibreOffice', 'program', 'soffice.exe'))) {
                    sofficePath = path.join('C:', 'Program Files', 'LibreOffice', 'program', 'soffice.exe');
                }

                if (sofficePath) {
                    console.log(`🔄 正在将PPT转换为PDF: ${originalName}`);
                    const result = require('child_process').spawnSync(
                        sofficePath,
                        ['--headless', '--convert-to', 'pdf', '--outdir', path.dirname(inputPath), inputPath],
                        { timeout: 120000, stdio: 'pipe', encoding: 'utf-8', windowsHide: true }
                    );
                    console.log('📄 LibreOffice输出:', result);

                    if (fs.existsSync(outputPath)) {
                        const pdfStat = fs.statSync(outputPath);
                        finalFileUrl = '/uploads/materials/' + outputFileName;
                        finalFileType = 'pdf';
                        finalFileSize = pdfStat.size;
                        finalFileName = originalNameNoExt + '.pdf';

                        // 删除原始PPT文件
                        try { fs.unlinkSync(inputPath); } catch (_) {}
                        console.log(`✅ PPT转换PDF成功: ${outputFileName}`);
                    }
                } else {
                    console.warn('⚠️ 未找到LibreOffice，跳过PPT转PDF（保留原文件）');
                }
            } catch (convError) {
                console.warn('⚠️ PPT转PDF失败，保留原文件:', convError.message);
            }
        }

        await db.executeQuery(
            'INSERT INTO course_materials (course_id, file_name, file_url, file_type, file_size) VALUES (?, ?, ?, ?, ?)',
            [courseId, finalFileName, finalFileUrl, finalFileType, finalFileSize]
        );

        successResponse(res, {
            fileName: finalFileName,
            fileUrl: finalFileUrl,
            fileType: finalFileType,
            fileSize: finalFileSize
        }, '资料上传成功');
    } catch (error) {
        console.error('上传资料错误:', error);
        errorResponse(res, '上传资料失败', 500);
    }
});

// 4.7F1 转换旧PPT资料为PDF接口
// 路径：POST /courses/:id/materials/convert-ppt
// 功能：将课程中旧PPT文件批量转换为PDF（教师/管理员，需课程归属）
app.post('/courses/:id/materials/convert-ppt', authenticate, requireRole('teacher', 'admin'), requireCourseOwnership(db), async (req, res) => {
    try {
        const courseId = req.params.id;

        // 查找所有PPT/PPTX资料
        const pptMaterials = await db.executeQuery(
            "SELECT * FROM course_materials WHERE course_id = ? AND file_type IN ('ppt', 'pptx')",
            [courseId]
        );

        if (pptMaterials.length === 0) {
            return successResponse(res, { converted: 0, failed: 0 }, '没有需要转换的PPT文件');
        }

        let converted = 0, failed = 0;
        const uploadsDir = path.join(__dirname, 'public', 'uploads', 'materials');

        for (const mat of pptMaterials) {
            try {
                const origFileName = mat.file_name;
                const origFilePath = path.join(uploadsDir, path.basename(mat.file_url));
                const baseName = path.basename(origFileName, path.extname(origFileName));

                if (!fs.existsSync(origFilePath)) {
                    failed++;
                    continue;
                }

                const outputPath = path.join(uploadsDir, baseName + '.pdf');

                require('child_process').spawnSync(
                    path.join('C:', 'Program Files', 'LibreOffice', 'program', 'soffice.exe'),
                    ['--headless', '--convert-to', 'pdf', '--outdir', uploadsDir, origFilePath],
                    { timeout: 120000, stdio: 'pipe', encoding: 'utf-8', windowsHide: true }
                );

                if (fs.existsSync(outputPath)) {
                    const pdfStat = fs.statSync(outputPath);
                    const pdfFileName = baseName + '.pdf';
                    const pdfFileUrl = '/uploads/materials/' + pdfFileName;

                    await db.executeQuery(
                        'UPDATE course_materials SET file_name = ?, file_url = ?, file_type = ?, file_size = ? WHERE id = ?',
                        [pdfFileName, pdfFileUrl, 'pdf', pdfStat.size, mat.id]
                    );

                    try { fs.unlinkSync(origFilePath); } catch (_) {}
                    converted++;
                } else {
                    failed++;
                }
            } catch (e) {
                console.warn(`转换旧PPT失败 [${mat.file_name}]:`, e.message);
                failed++;
            }
        }

        successResponse(res, { converted, failed, total: pptMaterials.length }, `转换完成: ${converted}成功, ${failed}失败`);
    } catch (error) {
        console.error('批量转换PPT错误:', error);
        errorResponse(res, '批量转换失败', 500);
    }
});

// 4.7F0 修复已有资料乱码文件名接口
// 路径：POST /courses/:id/materials/fix-names
// 功能：将数据库中已存在的乱码文件名（latin1编码）恢复为UTF-8中文（教师/管理员，需课程归属）
app.post('/courses/:id/materials/fix-names', authenticate, requireRole('teacher', 'admin'), requireCourseOwnership(db), async (req, res) => {
    try {
        const courseId = req.params.id;
        const materials = await db.executeQuery(
            'SELECT id, file_name FROM course_materials WHERE course_id = ?',
            [courseId]
        );

        let fixed = 0;
        for (const mat of materials) {
            const recovered = Buffer.from(mat.file_name, 'latin1').toString('utf8');
            if (recovered !== mat.file_name) {
                await db.executeQuery('UPDATE course_materials SET file_name = ? WHERE id = ?', [recovered, mat.id]);
                fixed++;
            }
        }

        successResponse(res, { fixed, total: materials.length }, `已修复 ${fixed} 个文件名`);
    } catch (error) {
        console.error('修复文件名错误:', error);
        errorResponse(res, '修复文件名失败', 500);
    }
});

// 4.7F 删除课程资料接口
// 路径：DELETE /courses/:id/materials/:materialId
// 功能：删除指定学习资料（教师/管理员，需课程归属）
app.delete('/courses/:id/materials/:materialId', authenticate, requireRole('teacher', 'admin'), requireCourseOwnership(db), async (req, res) => {
    try {
        const { id, materialId } = req.params;

        // 查找资料获取文件路径
        const materials = await db.executeQuery(
            'SELECT * FROM course_materials WHERE id = ? AND course_id = ?',
            [materialId, id]
        );

        if (materials.length === 0) {
            return errorResponse(res, '资料不存在', 404);
        }

        // 删除物理文件
        const filePath = path.join(__dirname, materials[0].file_url);
        if (fs.existsSync(filePath)) {
            fs.unlinkSync(filePath);
        }

        // 删除数据库记录
        await db.executeQuery('DELETE FROM course_materials WHERE id = ?', [materialId]);

        successResponse(res, null, '资料删除成功');
    } catch (error) {
        console.error('删除资料错误:', error);
        errorResponse(res, '删除资料失败', 500);
    }
});

// 4.7G 删除课程接口
// 路径：DELETE /courses/:id
// 功能：删除课程及所有关联数据（选课记录、学习记录、题目、资料）（教师/管理员，需课程归属）
app.delete('/courses/:id', authenticate, requireRole('teacher', 'admin'), requireCourseOwnership(db), async (req, res) => {
    try {
        const courseId = req.params.id;

        // 检查课程是否存在
        const courseCheck = await db.executeQuery('SELECT id, name FROM courses WHERE id = ?', [courseId]);
        if (courseCheck.length === 0) {
            return errorResponse(res, '课程不存在', 404);
        }

        // 删除资料物理文件
        const materials = await db.executeQuery(
            'SELECT file_url FROM course_materials WHERE course_id = ?',
            [courseId]
        );
        for (const mat of materials) {
            const filePath = path.join(__dirname, mat.file_url);
            if (fs.existsSync(filePath)) {
                fs.unlinkSync(filePath);
            }
        }

        // 级联删除（外键会自动处理：course_materials, questions, study_records, course_enrollments）
        await db.executeQuery('DELETE FROM courses WHERE id = ?', [courseId]);

        successResponse(res, { courseId: parseInt(courseId) }, '课程删除成功');
    } catch (error) {
        console.error('删除课程错误:', error);
        errorResponse(res, '删除课程失败', 500);
    }
});

// 4.8 学生选课接口
// 路径：POST /courses/:courseId/enroll
// 功能：学生选择课程
// 安全：studentId 从认证令牌获取，不接受客户端传入
app.post('/courses/:courseId/enroll', authenticate, async (req, res) => {
    try {
        const courseId = req.params.courseId;
        const studentId = req.user.id;
        const { studentName } = req.body;

        if (!studentName) {
            return errorResponse(res, '学生姓名不能为空');
        }

        // 检查课程是否存在
        const courseCheck = await db.executeQuery('SELECT id FROM courses WHERE id = ?', [courseId]);
        if (courseCheck.length === 0) {
            return errorResponse(res, '课程不存在', 404);
        }

        // 检查学生是否存在且角色正确
        const studentCheck = await db.executeQuery(
            'SELECT id, username, role FROM users WHERE id = ? AND role = ?',
            [studentId, 'student']
        );
        if (studentCheck.length === 0) {
            return errorResponse(res, '学生不存在或角色不正确', 404);
        }

        // 检查是否已选课
        const enrollmentCheck = await db.executeQuery(
            'SELECT id FROM course_enrollments WHERE course_id = ? AND student_id = ?',
            [courseId, studentId]
        );
        if (enrollmentCheck.length > 0) {
            return errorResponse(res, '该学生已选此课程', 400);
        }

        const sql = `
            INSERT INTO course_enrollments (course_id, student_id, student_name)
            VALUES (?, ?, ?)
        `;
        await db.executeQuery(sql, [courseId, studentId, studentName]);

        // 自动创建学生和教师的会话
        try {
            const courseInfo = await db.executeQuery(
                'SELECT teacher_id FROM courses WHERE id = ?',
                [courseId]
            );
            if (courseInfo.length > 0) {
                const teacherId = courseInfo[0].teacher_id;
                const user1Id = Math.min(parseInt(studentId), parseInt(teacherId));
                const user2Id = Math.max(parseInt(studentId), parseInt(teacherId));

                // 检查会话是否已存在
                const existingConv = await db.executeQuery(
                    'SELECT id FROM conversations WHERE user1_id = ? AND user2_id = ?',
                    [user1Id, user2Id]
                );

                if (existingConv.length === 0) {
                    await db.executeQuery(
                        'INSERT INTO conversations (user1_id, user2_id) VALUES (?, ?)',
                        [user1Id, user2Id]
                    );
                    console.log(`✅ 自动创建会话: 学生${studentId} <-> 教师${teacherId}`);
                }
            }
        } catch (convError) {
            console.warn('⚠️  创建会话时出现错误:', convError.message);
        }

        successResponse(res, {
            courseId: courseId,
            studentId: studentId,
            enrolledAt: new Date().toISOString()
        }, '选课成功');
    } catch (error) {
        console.error('选课错误:', error);
        errorResponse(res, '选课失败', 500);
    }
});

// 4.9 记录学习进度接口
// 路径：POST /study/record
// 功能：记录学生的学习进度和点击次数
// 安全：studentId 从认证令牌获取，不接受客户端传入
app.post('/study/record', authenticate, async (req, res) => {
    try {
        const { courseId, watchTime = 0, progress = 0, clickCount = 0 } = req.body;
        const studentId = req.user.id;

        if (!courseId) {
            return errorResponse(res, '课程ID不能为空');
        }

        // 验证数据范围
        const validWatchTime = Math.max(0, parseInt(watchTime) || 0);
        const validProgress = Math.max(0, Math.min(100, parseInt(progress) || 0));
        const validClickCount = Math.max(0, parseInt(clickCount) || 0);

        // 检查学生是否已选课
        const enrollmentCheck = await db.executeQuery(
            'SELECT id FROM course_enrollments WHERE course_id = ? AND student_id = ?',
            [courseId, studentId]
        );
        if (enrollmentCheck.length === 0) {
            return errorResponse(res, '学生未选此课程', 400);
        }

        const sql = `
            INSERT INTO study_records (course_id, student_id, watch_time, progress, click_count)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
            watch_time = watch_time + VALUES(watch_time),
            progress = GREATEST(progress, VALUES(progress)),
            click_count = click_count + VALUES(click_count),
            last_watch_time = CURRENT_TIMESTAMP,
            updated_at = CURRENT_TIMESTAMP
        `;
        await db.executeQuery(sql, [courseId, studentId, validWatchTime, validProgress, validClickCount]);

        successResponse(res, {
            courseId: courseId,
            studentId: studentId,
            watchTime: validWatchTime,
            progress: validProgress,
            clickCount: validClickCount,
            recordedAt: new Date().toISOString()
        }, '学习记录保存成功');
    } catch (error) {
        console.error('记录学习进度错误:', error);
        errorResponse(res, '记录学习进度失败', 500);
    }
});

// 4.10 获取用户的会话列表
// 路径：GET /conversations
// 功能：获取用户的所有会话（含最后一条消息和未读数）
// 安全：userId 从认证令牌获取，不接受客户端传入
app.get('/conversations', authenticate, async (req, res) => {
    try {
        const userId = req.user.id;

        // 自动创建缺失的会话（兼容旧数据：选课发生在聊天功能上线前）
        try {
            const missingEnrollments = await db.executeQuery(`
                SELECT DISTINCT ce.student_id, c.teacher_id
                FROM course_enrollments ce
                JOIN courses c ON ce.course_id = c.id
                WHERE ce.student_id = ? OR c.teacher_id = ?
            `, [userId, userId]);

            for (const enr of missingEnrollments) {
                const user1Id = Math.min(parseInt(enr.student_id), parseInt(enr.teacher_id));
                const user2Id = Math.max(parseInt(enr.student_id), parseInt(enr.teacher_id));
                const existing = await db.executeQuery(
                    'SELECT id FROM conversations WHERE user1_id = ? AND user2_id = ?',
                    [user1Id, user2Id]
                );
                if (existing.length === 0) {
                    await db.executeQuery(
                        'INSERT INTO conversations (user1_id, user2_id) VALUES (?, ?)',
                        [user1Id, user2Id]
                    );
                    console.log(`✅ 自动补建会话: 学生${enr.student_id} <-> 教师${enr.teacher_id}`);
                }
            }
        } catch (convError) {
            console.warn('⚠️  补建会话时出现错误:', convError.message);
        }

        const convs = await db.executeQuery(`
            SELECT c.id, c.user1_id, c.user2_id, c.updated_at,
                (SELECT content FROM messages WHERE conversation_id = c.id ORDER BY created_at DESC LIMIT 1) as last_message,
                (SELECT created_at FROM messages WHERE conversation_id = c.id ORDER BY created_at DESC LIMIT 1) as last_message_at,
                (SELECT COUNT(*) FROM messages WHERE conversation_id = c.id AND sender_id != ? AND is_read = FALSE) as unread_count
            FROM conversations c
            WHERE c.user1_id = ? OR c.user2_id = ?
            ORDER BY c.updated_at DESC
        `, [userId, userId, userId]);

        // 补充对方的用户信息
        const result = [];
        for (const conv of convs) {
            const otherUserId = conv.user1_id === userId ? conv.user2_id : conv.user1_id;
            const userInfo = await db.executeQuery(
                'SELECT id, username, role, avatar_url FROM users WHERE id = ?',
                [otherUserId]
            );
            const otherUser = userInfo.length > 0 ? userInfo[0] : { id: otherUserId, username: '未知', role: '' };

            result.push({
                id: conv.id,
                otherUserId: otherUser.id,
                otherUsername: otherUser.username,
                otherUserRole: otherUser.role,
                otherUserAvatarUrl: otherUser.avatar_url || '',
                lastMessage: conv.last_message || '',
                lastMessageAt: conv.last_message_at || conv.updated_at,
                unreadCount: conv.unread_count || 0
            });
        }

        successResponse(res, result, '获取会话列表成功');
    } catch (error) {
        console.error('获取会话列表错误:', error);
        errorResponse(res, '获取会话列表失败', 500);
    }
});

// 4.11 获取会话消息列表
// 路径：GET /conversations/:id/messages
// 功能：获取指定会话的所有消息
// 安全：currentUserId 从认证令牌获取，不接受客户端传入
app.get('/conversations/:id/messages', authenticate, async (req, res) => {
    try {
        const conversationId = req.params.id;

        const messages = await db.executeQuery(`
            SELECT id, conversation_id, sender_id, content, created_at, is_read
            FROM messages
            WHERE conversation_id = ?
            ORDER BY created_at ASC
        `, [conversationId]);

        // 标记所有消息为已读
        await db.executeQuery(
            'UPDATE messages SET is_read = TRUE WHERE conversation_id = ? AND sender_id != ?',
            [conversationId, req.user.id]
        );

        const result = messages.map(msg => ({
            id: msg.id,
            conversationId: msg.conversation_id,
            senderId: msg.sender_id,
            content: msg.content,
            createdAt: msg.created_at,
            isRead: Boolean(msg.is_read)
        }));

        successResponse(res, result, '获取消息列表成功');
    } catch (error) {
        console.error('获取消息列表错误:', error);
        errorResponse(res, '获取消息列表失败', 500);
    }
});

// 4.12 发送消息
// 路径：POST /messages
// 功能：发送一条消息
// 安全：senderId 从认证令牌获取，不接受客户端传入
app.post('/messages', authenticate, async (req, res) => {
    try {
        const { conversationId, content } = req.body;
        const senderId = req.user.id;

        if (!conversationId || !content) {
            return errorResponse(res, '会话ID和消息内容不能为空');
        }

        if (content.trim().length === 0) {
            return errorResponse(res, '消息内容不能为空');
        }

        const result = await db.executeQuery(
            'INSERT INTO messages (conversation_id, sender_id, content) VALUES (?, ?, ?)',
            [conversationId, senderId, content.trim()]
        );

        // 更新会话的updated_at
        await db.executeQuery(
            'UPDATE conversations SET updated_at = CURRENT_TIMESTAMP WHERE id = ?',
            [conversationId]
        );

        successResponse(res, {
            id: result.insertId,
            conversationId: conversationId,
            senderId: senderId,
            content: content.trim(),
            createdAt: new Date().toISOString(),
            isRead: false
        }, '发送成功');
    } catch (error) {
        console.error('发送消息错误:', error);
        errorResponse(res, '发送消息失败', 500);
    }
});

// 4.13 获取教师统计数据接口
// 路径：GET /teacher/stats
// 功能：获取教师的课程统计数据
// 安全：教师身份从认证令牌获取，不接受客户端传入
app.get('/teacher/stats', authenticate, requireRole('teacher', 'admin'), async (req, res) => {
    try {
        const actualTeacherId = req.user.id;
        const teacherCheck = await db.executeQuery(
            'SELECT id, username FROM users WHERE id = ?',
            [actualTeacherId]
        );
        if (teacherCheck.length === 0) {
            return errorResponse(res, '教师不存在', 404);
        }
        const teacher = teacherCheck[0];

        // 1. 获取教师的所有课程
        const courses = await db.executeQuery(
            'SELECT id, name, description, teacher_name, credit, created_at FROM courses WHERE teacher_id = ? ORDER BY created_at DESC',
            [actualTeacherId]
        );

        // 如果没有任何课程，返回空数据
        if (courses.length === 0) {
            return successResponse(res, {
                teacherId: actualTeacherId,
                teacherName: teacher.username,
                totalCourses: 0,
                totalStudents: 0,
                totalWatchTime: 0,
                averageWatchDuration: 0,
                averageProgress: 0,
                averageQuizAccuracy: 0,
                courses: []
            }, '教师暂无课程数据');
        }

        const courseStats = [];
        let totalStudents = 0;
        let totalWatchTime = 0;
        let totalProgress = 0;
        let totalClickCount = 0;
        let totalQuizAccuracy = 0;

        // 2. 为每门课程统计数据
        for (const course of courses) {
            // 获取选课学生数
            const enrollmentResult = await db.executeQuery(
                'SELECT COUNT(*) as count FROM course_enrollments WHERE course_id = ?',
                [course.id]
            );
            const enrolledStudents = enrollmentResult[0]?.count || 0;

            // 获取学习记录统计
            const studyStatsResult = await db.executeQuery(`
                SELECT
                    COUNT(*) as total_records,
                    AVG(watch_time) as avg_watch_time,
                    AVG(progress) as avg_progress,
                    SUM(watch_time) as total_watch_time,
                    AVG(click_count) as avg_click_count,
                    SUM(click_count) as total_click_count
                FROM study_records
                WHERE course_id = ?
            `, [course.id]);

            const stats = studyStatsResult[0] || {};
            const avgWatchTime = parseFloat(stats.avg_watch_time) || 0;
            const avgProgress = parseFloat(stats.avg_progress) || 0;
            const courseWatchTime = parseFloat(stats.total_watch_time) || 0;
            const avgClickCount = parseFloat(stats.avg_click_count) || 0;
            const courseClickCount = parseFloat(stats.total_click_count) || 0;

            // 获取答题正确率统计
            const quizStatsResult = await db.executeQuery(`
                SELECT AVG(score) as avg_quiz_accuracy, COUNT(*) as attempt_count
                FROM quiz_attempts WHERE course_id = ?
            `, [course.id]);
            const quizStats = quizStatsResult[0] || {};
            const avgQuizAccuracy = parseFloat(quizStats.avg_quiz_accuracy) || 0;
            const quizAttemptCount = parseInt(quizStats.attempt_count) || 0;

            courseStats.push({
                courseId: course.id,
                courseName: course.name,
                description: course.description,
                teacherName: course.teacher_name,
                enrolledStudents: enrolledStudents,
                averageWatchTime: Math.round(avgWatchTime * 10) / 10,
                averageProgress: Math.round(avgProgress * 10) / 10,
                totalWatchTime: Math.round(courseWatchTime),
                averageClickCount: Math.round(avgClickCount * 10) / 10,
                totalClickCount: Math.round(courseClickCount),
                averageQuizAccuracy: Math.round(avgQuizAccuracy * 10) / 10,
                quizAttemptCount: quizAttemptCount,
                createdAt: course.created_at
            });

            // 累加总体统计
            totalStudents += enrolledStudents;
            totalWatchTime += courseWatchTime;
            totalProgress += avgProgress;
            totalClickCount += courseClickCount;
            totalQuizAccuracy += avgQuizAccuracy;
        }

        // 3. 计算总体统计数据
        const totalCourses = courses.length;
        const averageWatchDuration = totalCourses > 0 ? totalWatchTime / totalCourses : 0;
        const averageOverallProgress = totalCourses > 0 ? totalProgress / totalCourses : 0;
        const averageClickCount = totalCourses > 0 ? totalClickCount / totalCourses : 0;

        // 4. 返回统计数据
        successResponse(res, {
            teacherId: actualTeacherId,
            teacherName: teacher.username,
            totalCourses: totalCourses,
            totalStudents: totalStudents,
            totalWatchTime: Math.round(totalWatchTime),
            averageWatchDuration: Math.round(averageWatchDuration * 10) / 10,
            averageProgress: Math.round(averageOverallProgress * 10) / 10,
            averageClickCount: Math.round(averageClickCount * 10) / 10,
            averageQuizAccuracy: totalCourses > 0 ? Math.round((totalQuizAccuracy / totalCourses) * 10) / 10 : 0,
            courses: courseStats
        }, '获取教师统计数据成功');
    } catch (error) {
        console.error('获取教师统计数据错误:', error);
        errorResponse(res, '获取教师统计数据失败', 500);
    }
});

// ================================
// ═══ 权限保护：所有 /admin/* 路由需要 admin 角色 ═══
// ================================
app.use('/admin', authenticate, requireRole('admin'), adminRateLimit);

// 管理操作审计中间件（自动记录所有管理员请求）
app.use('/admin', (req, res, next) => {
    const originalJson = res.json.bind(res);
    res.json = function (body) {
        const statusCode = res.statusCode;
        setImmediate(() => audit(req, {
            actionType: 'admin_' + req.method.toLowerCase(),
            targetType: 'admin_api',
            targetId: req.originalUrl,
            result: statusCode >= 200 && statusCode < 300 ? 'success' : 'failure',
            detail: { method: req.method, path: req.originalUrl, statusCode }
        }));
        return originalJson(body);
    };
    next();
});

// 4.11 管理员获取所有用户接口
// 路径：GET /admin/users
// 功能：获取所有用户信息（需要管理员权限）
app.get('/admin/users', async (req, res) => {
    try {
        let sql = 'SELECT id, username, role, created_at FROM users WHERE 1=1';
        const params = [];

        // 按角色筛选
        if (req.query.role && req.query.role !== 'all') {
            sql += ' AND role = ?';
            params.push(req.query.role);
        }

        // 按用户名搜索
        if (req.query.search) {
            sql += ' AND username LIKE ?';
            params.push(`%${req.query.search}%`);
        }

        sql += ' ORDER BY id ASC';

        const users = await db.executeQuery(sql, params);
        successResponse(res, users, '获取用户列表成功');
    } catch (error) {
        console.error('获取用户列表错误:', error);
        errorResponse(res, '获取用户列表失败', 500);
    }
});

// 4.12 管理员获取实时在线用户接口
// 路径：GET /admin/online-users
// 功能：获取实时在线用户统计和列表（需要管理员权限）
// 在线用户定义：last_active_at在最近5分钟内的用户
app.get('/admin/online-users', async (req, res) => {
    try {
        // 获取总用户数
        const totalResult = await db.executeQuery('SELECT COUNT(*) as total FROM users');
        const totalUsers = totalResult[0]?.total || 0;

        // 获取在线用户数（最近5分钟有活动）
        const onlineResult = await db.executeQuery(`
            SELECT COUNT(*) as online
            FROM users
            WHERE last_active_at >= NOW() - INTERVAL 5 MINUTE
        `);
        const onlineUsers = onlineResult[0]?.online || 0;

        // 计算在线率
        const onlineRate = totalUsers > 0 ? (onlineUsers / totalUsers * 100) : 0;

        // 获取在线用户详情
        const onlineUsersList = await db.executeQuery(`
            SELECT id, username, role, last_active_at
            FROM users
            WHERE last_active_at >= NOW() - INTERVAL 5 MINUTE
            ORDER BY last_active_at DESC
        `);

        // 格式化最后活动时间
        const formattedList = onlineUsersList.map(user => ({
            id: user.id,
            username: user.username,
            role: user.role,
            last_active_at: user.last_active_at,
            is_online: true
        }));

        successResponse(res, {
            stats: {
                online_count: onlineUsers,
                total_users: totalUsers,
                online_rate: parseFloat(onlineRate.toFixed(1)), // 保留1位小数
                last_updated: new Date().toISOString()
            },
            online_users: formattedList
        }, '获取在线用户数据成功');
    } catch (error) {
        console.error('获取在线用户数据错误:', error);
        errorResponse(res, '获取在线用户数据失败', 500);
    }
});

// 4.13 管理员删除用户接口
// 路径：DELETE /admin/users/:id
// 功能：删除指定用户（需要管理员权限），并级联删除相关数据
app.delete('/admin/users/:id', async (req, res) => {
    let connection = null;
    try {
        const userId = parseInt(req.params.id);

        // 验证用户ID
        if (!userId || isNaN(userId) || userId <= 0) {
            return errorResponse(res, '无效的用户ID');
        }

        // 检查用户是否存在
        const userCheck = await db.executeQuery('SELECT id, role FROM users WHERE id = ?', [userId]);
        if (userCheck.length === 0) {
            return errorResponse(res, '用户不存在', 404);
        }

        const userRole = userCheck[0].role;
        console.log(`开始删除用户 ${userId} (角色: ${userRole})，级联删除相关数据...`);
        let courseIds = [];

        // 开启事务，确保数据一致性
        connection = await db.beginTransaction();

        // 1. 先删除用户的学习记录（study_records表）
        console.log(`删除用户 ${userId} 的学习记录...`);
        await connection.execute('DELETE FROM study_records WHERE student_id = ?', [userId]);

        // 2. 删除用户的选课记录（course_enrollments表）
        console.log(`删除用户 ${userId} 的选课记录...`);
        await connection.execute('DELETE FROM course_enrollments WHERE student_id = ?', [userId]);

        // 3. 如果用户是教师，删除该教师的所有课程及相关数据
        if (userRole === 'teacher') {
            console.log(`用户 ${userId} 是教师，删除其所有课程...`);

            // 3.1 先查询该教师的所有课程ID
            const teacherCourses = await connection.execute(
                'SELECT id FROM courses WHERE teacher_id = ?',
                [userId]
            );

            courseIds = teacherCourses[0].map(course => course.id);
            console.log(`找到 ${courseIds.length} 个课程需要删除:`, courseIds);

            if (courseIds.length > 0) {
                // 3.2 删除这些课程的学习记录
                console.log(`删除课程 ${courseIds.join(',')} 的学习记录...`);
                await connection.execute(
                    `DELETE FROM study_records WHERE course_id IN (${courseIds.map(() => '?').join(',')})`,
                    courseIds
                );

                // 3.3 删除这些课程的选课记录
                console.log(`删除课程 ${courseIds.join(',')} 的选课记录...`);
                await connection.execute(
                    `DELETE FROM course_enrollments WHERE course_id IN (${courseIds.map(() => '?').join(',')})`,
                    courseIds
                );

                // 3.4 最后删除课程本身
                console.log(`删除教师 ${userId} 的所有课程...`);
                await connection.execute(
                    `DELETE FROM courses WHERE teacher_id = ?`,
                    [userId]
                );
            }
        }

        // 4. 最后删除用户本身
        console.log(`删除用户 ${userId}...`);
        await connection.execute('DELETE FROM users WHERE id = ?', [userId]);

        // 提交事务
        await db.commitTransaction(connection);
        console.log(`用户 ${userId} 删除成功，事务已提交`);
        setImmediate(() => audit(req, { actionType: 'admin_delete_user', targetType: 'user', targetId: userId, result: 'success', detail: { deletedRole: userRole } }));

        successResponse(res, {
            deletedUserId: userId,
            deletedRole: userRole,
            deletedCourses: userRole === 'teacher' ? courseIds?.length || 0 : 0
        }, `用户删除成功${userRole === 'teacher' ? '，并删除了相关课程' : ''}`);
    } catch (error) {
        // 发生错误时回滚事务
        if (connection) {
            try {
                await db.rollbackTransaction(connection);
                console.log('事务已回滚');
            } catch (rollbackError) {
                console.error('回滚事务失败:', rollbackError);
            }
        }
        console.error('删除用户错误:', error);
        errorResponse(res, `删除用户失败: ${error.message}`, 500);
    }
});

// 4.13 管理员重置用户密码接口
// 路径：POST /admin/users/:id/reset-password
// 功能：生成随机临时密码，bcrypt 哈希存储，标记 must_change_password=true
// 安全：管理员不直接设置用户密码，改为生成临时密码，用户首次登录后必须修改
app.post('/admin/users/:id/reset-password', async (req, res) => {
    try {
        const userId = req.params.id;

        // 验证用户ID
        if (!userId || isNaN(userId)) {
            return errorResponse(res, '无效的用户ID');
        }

        // 检查用户是否存在
        const userCheck = await db.executeQuery('SELECT id, username FROM users WHERE id = ?', [userId]);
        if (userCheck.length === 0) {
            return errorResponse(res, '用户不存在', 404);
        }

        const username = userCheck[0].username;

        // 生成随机临时密码（12位，含大小写字母+数字）
        const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789';
        let tempPassword = '';
        for (let i = 0; i < 12; i++) {
            tempPassword += chars.charAt(Math.floor(Math.random() * chars.length));
        }

        // bcrypt 哈希临时密码
        const hashedPassword = await bcrypt.hash(tempPassword, SALT_ROUNDS);

        // 更新密码 + 标记需要修改密码
        await db.executeQuery(
            'UPDATE users SET password = ?, must_change_password = true WHERE id = ?',
            [hashedPassword, userId]
        );

        console.log(`✅ 用户 ${username}(${userId}) 密码已被管理员重置为临时密码`);
        setImmediate(() => audit(req, { actionType: 'admin_reset_password', targetType: 'user', targetId: userId, result: 'success', detail: { username } }));

        successResponse(res, {
            userId: userId,
            username: username,
            passwordReset: true,
            tempPassword: tempPassword,
            mustChangePassword: true,
            message: `临时密码为: ${tempPassword}，请告知用户首次登录后修改密码`
        }, '密码已重置为临时密码，用户首次登录后需修改密码');
    } catch (error) {
        console.error('重置密码错误:', error);
        errorResponse(res, '重置密码失败', 500);
    }
});

// 4.14 管理员获取活跃度排行榜接口
// 路径：GET /admin/activity-ranking
// 功能：获取用户活跃度排行榜（需要管理员权限）
// 参数：days（可选，默认7）- 统计最近多少天的数据
//       limit（可选，默认20）- 返回条数限制
app.get('/admin/activity-ranking', async (req, res) => {
    try {
        // 获取参数
        const days = parseInt(req.query.days) || 7;
        const limit = parseInt(req.query.limit) || 20;

        // 验证参数
        if (days <= 0 || days > 365) {
            return errorResponse(res, 'days参数必须在1-365之间');
        }
        if (limit <= 0 || limit > 100) {
            return errorResponse(res, 'limit参数必须在1-100之间');
        }

        // 计算起始时间
        const startDate = new Date();
        startDate.setDate(startDate.getDate() - days);
        const startDateStr = startDate.toISOString().slice(0, 19).replace('T', ' ');

        // 获取所有用户的学习记录统计
        const activityStats = await db.executeQuery(`
            SELECT
                u.id,
                u.username,
                u.role,
                u.last_active_at,
                COALESCE(SUM(sr.watch_time), 0) as total_watch_time,
                COALESCE(AVG(sr.progress), 0) as avg_progress,
                COALESCE(SUM(sr.click_count), 0) as total_click_count,
                COUNT(sr.id) as study_records_count,
                COUNT(DISTINCT DATE(sr.last_watch_time)) as login_count,
                COUNT(DISTINCT CASE WHEN sr.progress >= 100 THEN sr.course_id END) as completed_courses,
                COALESCE(SUM(qa.score), 0) as total_quiz_score
            FROM users u
            LEFT JOIN study_records sr ON u.id = sr.student_id
                AND sr.last_watch_time >= ?
                AND sr.watch_time > 0
            LEFT JOIN quiz_attempts qa ON u.id = qa.student_id
                AND qa.submitted_at >= ?
            GROUP BY u.id, u.username, u.role, u.last_active_at
            ORDER BY total_watch_time DESC
            LIMIT 20
        `, [startDateStr, startDateStr]);

        // ═══════════════════════════════════════════════
        // 活跃度计算公式（用户自定义）：
        // 活跃度 = 登录次数 × 1
        //        + 学习时长 × 0.5
        //        + 完成课程 × 20
        //        + 答题成绩 × 1
        // ═══════════════════════════════════════════════
        const statsWithScore = activityStats.map(stat => {
            const loginScore = (stat.login_count || 0) * 1;
            const watchTimeScore = (stat.total_watch_time || 0) * 0.5;
            const completedCourseScore = (stat.completed_courses || 0) * 20;
            const quizScore = (stat.total_quiz_score || 0) * 1;

            const activityScore = Math.round(
                (loginScore + watchTimeScore + completedCourseScore + quizScore) * 10
            ) / 10;

            return {
                ...stat,
                activity_score: activityScore,
                login_score: Math.round(loginScore * 10) / 10,
                watch_time_score: Math.round(watchTimeScore * 10) / 10,
                completed_course_score: Math.round(completedCourseScore * 10) / 10,
                quiz_score: Math.round(quizScore * 10) / 10
            };
        });

        // 按活跃度分数重新排序
        statsWithScore.sort((a, b) => b.activity_score - a.activity_score);

        // 计算总体统计
        const totalUsers = statsWithScore.length;
        const totalWatchTime = statsWithScore.reduce((sum, stat) => sum + stat.total_watch_time, 0);
        const totalClickCount = statsWithScore.reduce((sum, stat) => sum + stat.total_click_count, 0);
        const averageActivityScore = totalUsers > 0 ? statsWithScore.reduce((sum, stat) => sum + stat.activity_score, 0) / totalUsers : 0;
        const maxActivityScore = totalUsers > 0 ? Math.max(...statsWithScore.map(stat => stat.activity_score)) : 0;

        // 计算活跃度分布
        const highCount = statsWithScore.filter(stat => stat.activity_score >= 70).length;
        const mediumCount = statsWithScore.filter(stat => stat.activity_score >= 40 && stat.activity_score < 70).length;
        const lowCount = statsWithScore.filter(stat => stat.activity_score < 40).length;

        successResponse(res, {
            stats: {
                days: days,
                total_users: totalUsers,
                total_watch_time: totalWatchTime,
                total_click_count: totalClickCount,
                average_activity_score: parseFloat(averageActivityScore.toFixed(1)),
                max_activity_score: parseFloat(maxActivityScore.toFixed(1)),
                activity_distribution: {
                    high: highCount,
                    medium: mediumCount,
                    low: lowCount
                },
                last_updated: new Date().toISOString()
            },
            ranking: statsWithScore.map((stat, index) => ({
                rank: index + 1,
                user_id: stat.id,
                username: stat.username,
                role: stat.role,
                last_active_at: stat.last_active_at,
                activity_score: stat.activity_score,
                details: {
                    total_watch_time: parseInt(stat.total_watch_time) || 0,
                    avg_progress: parseFloat(Number(stat.avg_progress).toFixed(1)),
                    total_click_count: parseInt(stat.total_click_count) || 0,
                    study_records_count: stat.study_records_count || 0,
                    login_count: stat.login_count || 0,
                    completed_courses: stat.completed_courses || 0,
                    total_quiz_score: parseInt(stat.total_quiz_score) || 0,
                    scores: {
                        login: stat.login_score,
                        watch_time: stat.watch_time_score,
                        completed_course: stat.completed_course_score,
                        quiz: stat.quiz_score
                    }
                }
            }))
        }, '获取活跃度排行榜成功');
    } catch (error) {
        console.error('获取活跃度排行榜错误:', error);
        errorResponse(res, '获取活跃度排行榜失败', 500);
    }
});

// 4.15 管理员获取学生学习统计接口
// 路径：GET /admin/learning-stats
// 功能：获取学生学习情况统计（需要管理员权限）
// 参数：limit（可选，默认20）- 返回条数限制
app.get('/admin/learning-stats', async (req, res) => {
    try {
        // 获取参数
        const limit = parseInt(req.query.limit) || 20;

        // 验证参数
        if (limit <= 0 || limit > 100) {
            return errorResponse(res, 'limit参数必须在1-100之间');
        }

        // 获取学生学习统计
        const learningStats = await db.executeQuery(`
            SELECT
                u.id,
                u.username,
                COUNT(sr.id) as total_learning_records,
                COALESCE(SUM(sr.watch_time), 0) as total_watch_time,
                COALESCE(AVG(sr.progress), 0) as average_progress,
                COALESCE(AVG(sr.click_count), 0) as average_click_count,
                COUNT(DISTINCT sr.course_id) as enrolled_course_count,
                SUM(CASE WHEN sr.progress >= 80 THEN 1 ELSE 0 END) as completed_course_count,
                MAX(sr.last_watch_time) as last_study_time
            FROM users u
            LEFT JOIN study_records sr ON u.id = sr.student_id
            WHERE u.role = 'student'
            GROUP BY u.id, u.username
            ORDER BY total_watch_time DESC, average_progress DESC, last_study_time DESC
            LIMIT ${limit}
        `);

        // 计算总体统计
        const totalStudents = learningStats.length;
        const totalWatchTime = learningStats.reduce((sum, stat) => sum + parseInt(stat.total_watch_time), 0);
        const totalLearningRecords = learningStats.reduce((sum, stat) => sum + parseInt(stat.total_learning_records), 0);
        const averageProgress = totalStudents > 0 ?
            learningStats.reduce((sum, stat) => sum + parseFloat(stat.average_progress), 0) / totalStudents : 0;
        const averageClickCount = totalStudents > 0 ?
            learningStats.reduce((sum, stat) => sum + parseFloat(stat.average_click_count), 0) / totalStudents : 0;
        const totalCompletedCourses = learningStats.reduce((sum, stat) => sum + parseInt(stat.completed_course_count), 0);

        successResponse(res, {
            stats: {
                total_students: totalStudents,
                total_watch_time: totalWatchTime,
                total_learning_records: totalLearningRecords,
                average_progress: parseFloat(averageProgress.toFixed(1)),
                average_click_count: parseFloat(averageClickCount.toFixed(1)),
                total_completed_courses: totalCompletedCourses,
                average_completed_courses: totalStudents > 0 ? parseFloat((totalCompletedCourses / totalStudents).toFixed(1)) : 0,
                last_updated: new Date().toISOString()
            },
            students: learningStats.map((stat, index) => ({
                rank: index + 1,
                student_id: stat.id,
                username: stat.username,
                total_watch_time: parseInt(stat.total_watch_time),
                average_progress: parseFloat(Number(stat.average_progress).toFixed(1)),
                average_click_count: parseFloat(Number(stat.average_click_count).toFixed(1)),
                enrolled_course_count: parseInt(stat.enrolled_course_count),
                completed_course_count: parseInt(stat.completed_course_count),
                total_learning_records: parseInt(stat.total_learning_records),
                last_study_time: stat.last_study_time
            }))
        }, '获取学生学习统计成功');
    } catch (error) {
        console.error('获取学生学习统计错误:', error);
        errorResponse(res, '获取学生学习统计失败', 500);
    }
});

// 路径：GET /admin/course-mastery-stats
// 功能：获取课程掌握度与正确率统计（需要管理员权限）
// 参数：limit（可选，默认20）- 返回条数限制
app.get('/admin/course-mastery-stats', async (req, res) => {
    try {
        // 获取参数
        const limit = parseInt(req.query.limit) || 20;

        // 验证参数
        if (limit <= 0 || limit > 100) {
            return errorResponse(res, 'limit参数必须在1-100之间');
        }

        // 获取课程掌握度统计
        const courseStats = await db.executeQuery(`
            SELECT
                c.id as course_id,
                c.name as course_name,
                c.description,
                c.teacher_name,
                c.credit,
                c.created_at,
                COUNT(DISTINCT ce.student_id) as total_students,
                COUNT(DISTINCT sr.id) as total_learning_records,
                COALESCE(AVG(sr.progress), 0) as average_progress,
                COALESCE(AVG(sr.click_count), 0) as average_click_count,
                COUNT(DISTINCT CASE WHEN sr.progress >= 80 THEN sr.id ELSE NULL END) as completed_records_count,
                COALESCE(AVG(qa.score * 100.0 / NULLIF(qa.total_questions, 0)), 0) as average_quiz_accuracy
            FROM courses c
            LEFT JOIN course_enrollments ce ON c.id = ce.course_id
            LEFT JOIN study_records sr ON c.id = sr.course_id
            LEFT JOIN quiz_attempts qa ON c.id = qa.course_id
            GROUP BY c.id, c.name, c.description, c.teacher_name, c.credit, c.created_at
            ORDER BY average_progress DESC, total_students DESC
            LIMIT ${limit}
        `);

        // 计算总体统计
        const totalCourses = courseStats.length;
        const totalStudents = courseStats.reduce((sum, stat) => sum + parseInt(stat.total_students), 0);
        const totalLearningRecords = courseStats.reduce((sum, stat) => sum + parseInt(stat.total_learning_records), 0);

        // 计算平均统计
        const averageProgress = totalCourses > 0 ?
            courseStats.reduce((sum, stat) => sum + parseFloat(stat.average_progress), 0) / totalCourses : 0;
        const averageClickCount = totalCourses > 0 ?
            courseStats.reduce((sum, stat) => sum + parseFloat(stat.average_click_count), 0) / totalCourses : 0;

        // 计算平均完成率（进度≥80%的记录比例）
        let averageCompletionRate = 0;
        if (totalLearningRecords > 0) {
            const totalCompletedRecords = courseStats.reduce((sum, stat) => sum + parseInt(stat.completed_records_count || 0), 0);
            averageCompletionRate = (totalCompletedRecords / totalLearningRecords) * 100;
        }

        // 计算掌握度分布
        const masteryDistribution = {
            proficient: 0,    // 精通（>=90%）
            good: 0,         // 良好（70-89%）
            medium: 0,       // 中等（50-69%）
            basic: 0,        // 基础（30-49%）
            beginner: 0      // 入门（<30%）
        };

        for (const stat of courseStats) {
            const progress = parseFloat(stat.average_progress) || 0;
            if (progress >= 90) {
                masteryDistribution.proficient++;
            } else if (progress >= 70) {
                masteryDistribution.good++;
            } else if (progress >= 50) {
                masteryDistribution.medium++;
            } else if (progress >= 30) {
                masteryDistribution.basic++;
            } else {
                masteryDistribution.beginner++;
            }
        }

        successResponse(res, {
            stats: {
                total_courses: totalCourses,
                total_students: totalStudents,
                total_learning_records: totalLearningRecords,
                average_progress: parseFloat(averageProgress.toFixed(1)),
                average_click_count: parseFloat(averageClickCount.toFixed(1)),
                average_completion_rate: parseFloat(averageCompletionRate.toFixed(1)),
                mastery_distribution: masteryDistribution,
                last_updated: new Date().toISOString()
            },
            courses: courseStats.map((stat, index) => {
                const progress = parseFloat(stat.average_progress) || 0;
                const quizAccuracy = parseFloat(stat.average_quiz_accuracy) || 0;

                // ═══════════════════════════════════════
                // 课程掌握度 = 视频完成率 × 40% + 测试成绩 × 60%
                // ═══════════════════════════════════════
                const compositeMastery = progress * 0.4 + quizAccuracy * 0.6;

                // 确定掌握度等级（基于综合掌握度）
                let masteryLevel;
                if (compositeMastery >= 90) {
                    masteryLevel = "精通";
                } else if (compositeMastery >= 70) {
                    masteryLevel = "良好";
                } else if (compositeMastery >= 50) {
                    masteryLevel = "中等";
                } else if (compositeMastery >= 30) {
                    masteryLevel = "基础";
                } else {
                    masteryLevel = "入门";
                }

                return {
                    rank: index + 1,
                    course_id: stat.course_id,
                    course_name: stat.course_name,
                    description: stat.description || "",
                    teacher_name: stat.teacher_name,
                    credit: stat.credit,
                    created_at: stat.created_at,
                    total_students: parseInt(stat.total_students),
                    total_learning_records: parseInt(stat.total_learning_records),
                    average_progress: parseFloat(progress.toFixed(1)),
                    average_click_count: parseFloat((parseFloat(stat.average_click_count) || 0).toFixed(1)),
                    average_quiz_accuracy: parseFloat(quizAccuracy.toFixed(1)),
                    average_completion_rate: stat.total_learning_records > 0 ?
                        parseFloat(((parseInt(stat.completed_records_count || 0) / parseInt(stat.total_learning_records)) * 100).toFixed(1)) : 0,
                    composite_mastery: parseFloat(compositeMastery.toFixed(1)),
                    mastery_level: masteryLevel
                };
            })
        }, '获取课程掌握度统计成功');
    } catch (error) {
        console.error('获取课程掌握度统计错误:', error);
        errorResponse(res, '获取课程掌握度统计失败', 500);
    }
});

// ================================
// 5. 管理员仪表盘概览接口
// ================================

// 路径：GET /admin/dashboard-overview
// 功能：获取管理员仪表盘概览数据（今日统计 + 学习趋势 + 时段分布）
app.get('/admin/dashboard-overview', async (req, res) => {
    try {
        // 并行查询所有统计数据
        const [
            todayStudyRows,
            todayNewUserRows,
            onlineRows,
            avgStudyRows,
            trendRows,
            hourlyRows,
            courseRankRows,
            masteryRows,
            studentCompleteRows
        ] = await Promise.all([
            // 1. 今日学习人数
            db.executeQuery(`
                SELECT COUNT(DISTINCT student_id) as count
                FROM study_records
                WHERE DATE(last_watch_time) = CURDATE()
            `),
            // 2. 今日新增用户
            db.executeQuery(`
                SELECT COUNT(*) as count
                FROM users
                WHERE DATE(created_at) = CURDATE()
            `),
            // 3. 在线人数（最近5分钟活跃）
            db.executeQuery(`
                SELECT COUNT(*) as count
                FROM users
                WHERE last_active_at >= DATE_SUB(NOW(), INTERVAL 5 MINUTE)
            `),
            // 4. 平均学习时长（今日）
            db.executeQuery(`
                SELECT COALESCE(AVG(watch_time), 0) as avg_watch_time
                FROM study_records
                WHERE DATE(last_watch_time) = CURDATE()
                    AND watch_time > 0
            `),
            // 5. 学习趋势（最近7天）
            db.executeQuery(`
                SELECT
                    DATE(last_watch_time) as date,
                    COUNT(DISTINCT student_id) as study_count,
                    COALESCE(SUM(watch_time), 0) as total_watch_time
                FROM study_records
                WHERE last_watch_time >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
                GROUP BY DATE(last_watch_time)
                ORDER BY date ASC
            `),
            // 6. 学习时段分布（最近7天）
            db.executeQuery(`
                SELECT
                    HOUR(last_watch_time) as hour,
                    COUNT(DISTINCT student_id) as student_count
                FROM study_records
                WHERE last_watch_time >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
                GROUP BY HOUR(last_watch_time)
                ORDER BY hour ASC
            `),
            // 7. 课程热度排行（按选课人数排序）
            db.executeQuery(`
                SELECT
                    c.id,
                    c.name as course_name,
                    c.teacher_name,
                    COUNT(e.id) as enroll_count
                FROM courses c
                LEFT JOIN course_enrollments e ON c.id = e.course_id
                GROUP BY c.id, c.name, c.teacher_name
                ORDER BY enroll_count DESC
                LIMIT 5
            `),
            // 8. 课程掌握度总览
            db.executeQuery(`
                SELECT
                    COUNT(*) as total_courses,
                    COALESCE(AVG(sr.progress), 0) as avg_progress,
                    COALESCE(AVG(sr.progress), 0) as avg_completion_rate
                FROM courses c
                LEFT JOIN study_records sr ON c.id = sr.course_id
            `),
            // 9. 学生完成率分布
            db.executeQuery(`
                SELECT
                    COUNT(DISTINCT CASE WHEN sr.progress >= 100 THEN sr.student_id END) as completed_students,
                    COUNT(DISTINCT sr.student_id) as total_students
                FROM study_records sr
            `)
        ]);

        // 获取活跃用户列表（排行榜用）
        const activeUsers = await db.executeQuery(`
            SELECT
                u.id,
                u.username,
                u.role,
                COALESCE(SUM(sr.watch_time), 0) as total_watch_time,
                COUNT(DISTINCT DATE(sr.last_watch_time)) as login_count
            FROM users u
            LEFT JOIN study_records sr ON u.id = sr.student_id
                AND sr.last_watch_time >= DATE_SUB(NOW(), INTERVAL 7 DAY)
            WHERE u.role = 'student'
            GROUP BY u.id, u.username, u.role
            ORDER BY total_watch_time DESC
            LIMIT 10
        `);

        // 填充学习趋势（确保7天都有数据）
        const trendMap = {};
        for (const row of trendRows) {
            const d = new Date(row.date);
            const key = d.toISOString().slice(0, 10);
            trendMap[key] = {
                study_count: row.study_count,
                total_watch_time: row.total_watch_time
            };
        }
        const fullTrend = [];
        for (let i = 6; i >= 0; i--) {
            const d = new Date();
            d.setDate(d.getDate() - i);
            const key = d.toISOString().slice(0, 10);
            const monthDay = (d.getMonth() + 1) + '/' + d.getDate();
            fullTrend.push({
                date: key,
                label: monthDay,
                study_count: trendMap[key]?.study_count || 0,
                total_watch_time: trendMap[key]?.total_watch_time || 0
            });
        }

        // 时段分布（0-23时）
        const hourlyMap = {};
        for (const row of hourlyRows) {
            hourlyMap[row.hour] = row.student_count;
        }
        const fullHourly = [];
        for (let h = 0; h < 24; h++) {
            fullHourly.push({
                hour: h,
                student_count: hourlyMap[h] || 0
            });
        }

        // 学生完成率
        const completedStudents = parseInt(studentCompleteRows[0]?.completed_students || 0);
        const totalStudents = parseInt(studentCompleteRows[0]?.total_students || 0);
        const completionRate = totalStudents > 0
            ? parseFloat(((completedStudents / totalStudents) * 100).toFixed(1))
            : 0;

        successResponse(res, {
            stats: {
                today_study_count: parseInt(todayStudyRows[0]?.count || 0),
                today_new_users: parseInt(todayNewUserRows[0]?.count || 0),
                online_count: parseInt(onlineRows[0]?.count || 0),
                average_study_time: Math.round(Number(avgStudyRows[0]?.avg_watch_time || 0)),
                student_completion_rate: completionRate,
                completed_students: completedStudents,
                total_students_with_records: totalStudents
            },
            learning_trend: fullTrend,
            hourly_distribution: fullHourly,
            course_ranking: courseRankRows.map((row, index) => ({
                rank: index + 1,
                course_id: row.id,
                course_name: row.course_name,
                teacher_name: row.teacher_name,
                enroll_count: parseInt(row.enroll_count || 0)
            })),
            active_users: activeUsers.map((row, index) => ({
                rank: index + 1,
                user_id: row.id,
                username: row.username,
                total_watch_time: parseInt(row.total_watch_time || 0),
                login_count: parseInt(row.login_count || 0)
            })),
            mastery_overview: {
                total_courses: parseInt(masteryRows[0]?.total_courses || 0),
                average_progress: parseFloat(Number(masteryRows[0]?.avg_progress || 0).toFixed(1)),
                average_completion_rate: parseFloat(Number(masteryRows[0]?.avg_completion_rate || 0).toFixed(1))
            }
        }, '获取仪表盘数据成功');
    } catch (error) {
        console.error('获取仪表盘概览错误:', error);
        errorResponse(res, '获取仪表盘数据失败', 500);
    }
});

// ================================
// 5. OSS 预签名上传 URL 接口
// ================================

/**
 * 生成 OSS 预签名 PUT URL（服务端签名，客户端直传）
 * 客户端无需持有 AK/SK，只需向此接口请求上传凭证
 * 签名算法：base64(hmac-sha1(AKSecret, "PUT\n\n${contentType}\n${expires}\n/${bucket}/${objectKey}"))
 */
app.post('/api/oss/presigned-upload', authenticate, (req, res) => {
    try {
        const { fileName, contentType } = req.body;
        const teacherName = req.user.username;

        if (!fileName) {
            return errorResponse(res, '缺少 fileName 参数');
        }

        // 从环境变量读取 OSS 配置（服务端持有，不暴露给客户端）
        const accessKeyId = process.env.OSS_ACCESS_KEY_ID;
        const accessKeySecret = process.env.OSS_ACCESS_KEY_SECRET;
        const bucketName = process.env.OSS_BUCKET_NAME || 'study-app-android-2026';
        const region = process.env.OSS_REGION || 'oss-cn-hangzhou';

        if (!accessKeyId || !accessKeySecret) {
            console.error('❌ OSS 未配置：请设置 OSS_ACCESS_KEY_ID 和 OSS_ACCESS_KEY_SECRET 环境变量');
            return errorResponse(res, '服务器 OSS 未配置', 503);
        }

        // 生成对象存储路径（与 OSSConfig.generateVideoPath 保持一致）
        const timestamp = Date.now();
        const safeFileName = fileName.replace(/[^a-zA-Z0-9.\-_]/g, '_');
        const safeTeacherName = teacherName
            ? teacherName.replace(/[^a-zA-Z0-9一-龥]/g, '_')
            : '';
        const teacherPrefix = safeTeacherName ? `${safeTeacherName}_` : '';
        const objectKey = `studyapp/videos/${timestamp}_${teacherPrefix}${safeFileName}`;

        // 签名有效期（秒）
        const expires = Math.floor(Date.now() / 1000) + 3600; // 1 小时有效

        // 构造待签名字符串（Alibaba Cloud OSS 签名格式 v1）
        const resource = `/${bucketName}/${objectKey}`;
        const stringToSign = `PUT\n\n${contentType || ''}\n${expires}\n${resource}`;

        // 使用 HMAC-SHA1 签名
        const signature = crypto
            .createHmac('sha1', accessKeySecret)
            .update(stringToSign, 'utf8')
            .digest('base64');

        // 构建预签名 URL
        const host = `${bucketName}.${region}.aliyuncs.com`;
        const encodedSignature = encodeURIComponent(signature);
        const uploadUrl = `https://${host}/${objectKey}?OSSAccessKeyId=${accessKeyId}&Expires=${expires}&Signature=${encodedSignature}`;

        // 构建公开访问 URL
        const publicUrl = `https://${host}/${objectKey}`;

        console.log(`✅ 生成 OSS 预签名 URL: ${objectKey} (有效期 1 小时)`);

        successResponse(res, {
            uploadUrl,
            publicUrl,
            objectKey,
            expires,
            bucket: bucketName,
            region
        }, '获取上传地址成功');
    } catch (error) {
        console.error('❌ 生成 OSS 预签名 URL 失败:', error);
        errorResponse(res, '生成上传地址失败', 500);
    }
});

/**
 * 生成 OSS 预签名 GET URL（用于视频播放）
 * 因为 OSS Bucket 是私有的，不能直接访问，需要生成带签名的临时播放地址
 * 签名算法：base64(hmac-sha1(AKSecret, "GET\n\n\n${expires}\n/${bucket}/${objectKey}"))
 */
app.post('/api/oss/play-url', authenticate, (req, res) => {
    try {
        const { videoUrl } = req.body;

        if (!videoUrl) {
            return errorResponse(res, '缺少 videoUrl 参数');
        }

        // 从环境变量读取 OSS 配置
        const accessKeyId = process.env.OSS_ACCESS_KEY_ID;
        const accessKeySecret = process.env.OSS_ACCESS_KEY_SECRET;
        const bucketName = process.env.OSS_BUCKET_NAME || 'study-app-android-2026';
        const region = process.env.OSS_REGION || 'oss-cn-hangzhou';

        if (!accessKeyId || !accessKeySecret) {
            return errorResponse(res, '服务器 OSS 未配置', 503);
        }

        // 从 videoUrl 中提取 objectKey
        // URL 格式: https://bucket.oss-cn-hangzhou.aliyuncs.com/studyapp/videos/xxx.mp4
        const host = `${bucketName}.${region}.aliyuncs.com`;
        const prefix = `https://${host}/`;
        if (!videoUrl.startsWith(prefix)) {
            return errorResponse(res, '视频地址格式不匹配', 400);
        }
        const objectKey = videoUrl.substring(prefix.length);

        // 签名有效期（1 小时）
        const expires = Math.floor(Date.now() / 1000) + 3600;

        // 构造待签名字符串（GET 请求，不需要 Content-Type）
        const resource = `/${bucketName}/${objectKey}`;
        const stringToSign = `GET\n\n\n${expires}\n${resource}`;

        // HMAC-SHA1 签名
        const signature = crypto
            .createHmac('sha1', accessKeySecret)
            .update(stringToSign, 'utf8')
            .digest('base64');

        // 构建带签名的播放 URL
        const encodedSignature = encodeURIComponent(signature);
        const playUrl = `https://${host}/${objectKey}?OSSAccessKeyId=${accessKeyId}&Expires=${expires}&Signature=${encodedSignature}`;

        console.log(`✅ 生成播放签名 URL: ${objectKey} (有效期 1 小时)`);

        successResponse(res, {
            playUrl,
            objectKey,
            expires
        }, '获取播放地址成功');
    } catch (error) {
        console.error('❌ 生成播放签名 URL 失败:', error);
        errorResponse(res, '获取播放地址失败', 500);
    }
});

// ================================
// 6. 错误处理中间件
// ================================
// 404处理
app.use((req, res) => {
    errorResponse(res, '接口不存在', 404);
});

// 全局错误处理
app.use((err, req, res, next) => {
    console.error('全局错误:', err);
    errorResponse(res, '服务器内部错误', 500);
});

// ================================
// 6. 启动服务器
// ================================
async function startServer() {
    try {
        // 测试数据库连接
        console.log('🔌 正在连接MySQL数据库...');
        await db.executeQuery('SELECT 1');
        console.log('✅ MySQL数据库连接成功');

        // 确保users表有last_active_at字段
        try {
            await db.executeQuery(`
                ALTER TABLE users
                ADD COLUMN last_active_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            `);
            console.log('✅ 已添加last_active_at字段到users表');
        } catch (error) {
            if (error.code === 'ER_DUP_FIELDNAME') {
                console.log('ℹ️  last_active_at字段已存在');
            } else {
                console.warn('⚠️  添加last_active_at字段时出现错误:', error.message);
            }
        }

        // 确保users表有avatar_url字段
        try {
            await db.executeQuery(`
                ALTER TABLE users
                ADD COLUMN avatar_url VARCHAR(500) DEFAULT NULL
            `);
            console.log('✅ 已添加avatar_url字段到users表');
        } catch (error) {
            if (error.code === 'ER_DUP_FIELDNAME') {
                console.log('ℹ️  avatar_url字段已存在');
            } else {
                console.warn('⚠️  添加avatar_url字段时出现错误:', error.message);
            }
        }

        // 确保users表有full_name字段
        try {
            await db.executeQuery(`
                ALTER TABLE users
                ADD COLUMN full_name VARCHAR(100) DEFAULT NULL
            `);
            console.log('✅ 已添加full_name字段到users表');
        } catch (error) {
            if (error.code === 'ER_DUP_FIELDNAME') {
                console.log('ℹ️  full_name字段已存在');
            } else {
                console.warn('⚠️  添加full_name字段时出现错误:', error.message);
            }
        }

        // 确保users表有birthday字段
        try {
            await db.executeQuery(`
                ALTER TABLE users
                ADD COLUMN birthday DATE DEFAULT NULL
            `);
            console.log('✅ 已添加birthday字段到users表');
        } catch (error) {
            if (error.code === 'ER_DUP_FIELDNAME') {
                console.log('ℹ️  birthday字段已存在');
            } else {
                console.warn('⚠️  添加birthday字段时出现错误:', error.message);
            }
        }

        // 确保users表有security_question字段
        try {
            await db.executeQuery(`
                ALTER TABLE users
                ADD COLUMN security_question VARCHAR(200) DEFAULT NULL
            `);
            console.log('✅ 已添加security_question字段到users表');
        } catch (error) {
            if (error.code === 'ER_DUP_FIELDNAME') {
                console.log('ℹ️  security_question字段已存在');
            } else {
                console.warn('⚠️  添加security_question字段时出现错误:', error.message);
            }
        }

        // 确保users表有security_answer字段
        try {
            await db.executeQuery(`
                ALTER TABLE users
                ADD COLUMN security_answer VARCHAR(200) DEFAULT NULL
            `);
            console.log('✅ 已添加security_answer字段到users表');
        } catch (error) {
            if (error.code === 'ER_DUP_FIELDNAME') {
                console.log('ℹ️  security_answer字段已存在');
            } else {
                console.warn('⚠️  添加security_answer字段时出现错误:', error.message);
            }
        }

        // 确保courses表有image_url字段
        try {
            await db.executeQuery(`
                ALTER TABLE courses
                ADD COLUMN image_url VARCHAR(500) DEFAULT NULL
            `);
            console.log('✅ 已添加image_url字段到courses表');
        } catch (error) {
            if (error.code === 'ER_DUP_FIELDNAME') {
                console.log('ℹ️  image_url字段已存在');
            } else {
                console.warn('⚠️  添加image_url字段时出现错误:', error.message);
            }
        }

        // 创建categories表
        try {
            await db.executeQuery(`
                CREATE TABLE IF NOT EXISTS categories (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(100) NOT NULL UNIQUE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            `);
            console.log('✅ categories表创建/已存在');
        } catch (error) {
            console.warn('⚠️  创建categories表时出现错误:', error.message);
        }

        // 添加category_id到courses表
        try {
            await db.executeQuery(`
                ALTER TABLE courses
                ADD COLUMN category_id INT DEFAULT NULL
            `);
            console.log('✅ 已添加category_id字段到courses表');
        } catch (error) {
            if (error.code === 'ER_DUP_FIELDNAME') {
                console.log('ℹ️  category_id字段已存在');
            } else {
                console.warn('⚠️  添加category_id字段时出现错误:', error.message);
            }
        }

        // 添加外键约束（忽略已存在的错误）
        try {
            await db.executeQuery(`
                ALTER TABLE courses
                ADD CONSTRAINT fk_course_category
                FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL
            `);
            console.log('✅ 已添加category_id外键约束');
        } catch (error) {
            if (error.code === 'ER_DUP_KEY' || error.code === 'ER_FK_DUP_KEY' || error.code === 'ER_CANT_CREATE_FK') {
                console.log('ℹ️  外键约束已存在或无法创建');
            } else {
                console.warn('⚠️  添加外键约束时出现错误:', error.message);
            }
        }

        // 注意：默认分类不再自动初始化
        // 管理员在后台管理的分类会持久化存储在数据库中

        // 创建conversations表
        try {
            await db.executeQuery(`
                CREATE TABLE IF NOT EXISTS conversations (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    user1_id INT NOT NULL,
                    user2_id INT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY unique_conversation (user1_id, user2_id),
                    INDEX idx_user1 (user1_id),
                    INDEX idx_user2 (user2_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            `);
            console.log('✅ conversations表创建/已存在');
        } catch (error) {
            console.warn('⚠️  创建conversations表时出现错误:', error.message);
        }

        // 创建messages表
        try {
            await db.executeQuery(`
                CREATE TABLE IF NOT EXISTS messages (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    conversation_id INT NOT NULL,
                    sender_id INT NOT NULL,
                    content TEXT NOT NULL,
                    is_read BOOLEAN DEFAULT FALSE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_conversation (conversation_id),
                    INDEX idx_sender (sender_id),
                    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            `);
            console.log('✅ messages表创建/已存在');
        } catch (error) {
            console.warn('⚠️  创建messages表时出现错误:', error.message);
        }

        // 创建questions表（PPT生成的题目）
        try {
            await db.executeQuery(`
                CREATE TABLE IF NOT EXISTS questions (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    course_id INT NOT NULL,
                    question_text TEXT NOT NULL,
                    options JSON NOT NULL,
                    correct_answer VARCHAR(500) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_course (course_id),
                    FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            `);
            console.log('✅ questions表创建/已存在');
        } catch (error) {
            console.warn('⚠️  创建questions表时出现错误:', error.message);
        }

        // 创建course_materials表（课程学习资料）
        try {
            await db.executeQuery(`
                CREATE TABLE IF NOT EXISTS course_materials (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    course_id INT NOT NULL,
                    file_name VARCHAR(255) NOT NULL,
                    file_url VARCHAR(500) NOT NULL,
                    file_type VARCHAR(50),
                    file_size BIGINT DEFAULT 0,
                    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_course_materials (course_id),
                    FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            `);
            console.log('✅ course_materials表创建/已存在');
        } catch (error) {
            console.warn('⚠️  创建course_materials表时出现错误:', error.message);
        }

        // 创建quiz_attempts表（测验答题记录）
        try {
            await db.executeQuery(`
                CREATE TABLE IF NOT EXISTS quiz_attempts (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    course_id INT NOT NULL,
                    student_id INT NOT NULL,
                    score INT NOT NULL DEFAULT 0,
                    total_questions INT NOT NULL DEFAULT 0,
                    answers JSON NOT NULL,
                    submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY unique_attempt (course_id, student_id),
                    INDEX idx_quiz_course_student (course_id, student_id),
                    FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
                    FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            `);
            console.log('✅ quiz_attempts表创建/已存在');
        } catch (error) {
            console.warn('⚠️  创建quiz_attempts表时出现错误:', error.message);
        }

        // 添加questions表的status和source列（AI题目管理）
        try {
            await db.executeQuery(`
                ALTER TABLE questions
                ADD COLUMN status VARCHAR(20) DEFAULT 'published',
                ADD COLUMN source VARCHAR(20) DEFAULT 'ai'
            `);
            console.log('✅ questions表已添加status/source列');
        } catch (error) {
            // 列已存在时会报错，忽略
            if (!error.message.includes('Duplicate column')) {
                console.warn('⚠️  添加questions列时出现错误:', error.message);
            }
        }

        // 创建course_slide_texts表（PPT幻灯片文本）
        try {
            await db.executeQuery(`
                CREATE TABLE IF NOT EXISTS course_slide_texts (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    course_id INT NOT NULL,
                    slide_index INT NOT NULL DEFAULT 0,
                    slide_text TEXT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_course_slides (course_id),
                    FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            `);
            console.log('✅ course_slide_texts表创建/已存在');
        } catch (error) {
            console.warn('⚠️  创建course_slide_texts表时出现错误:', error.message);
        }

        // 启动HTTP服务器
        app.listen(PORT, HOST, () => {
            console.log('='.repeat(50));
            console.log('🚀 StudyApp后端服务器已启动');
            console.log(`📡 访问地址: http://${HOST}:${PORT}`);
            console.log(`🌐 网络接口: ${HOST === '0.0.0.0' ? '所有接口（外部可访问）' : HOST}`);
            console.log('📚 API文档: http://localhost:' + PORT + '/');
            console.log('💪 健康检查: http://localhost:' + PORT + '/health');
            console.log('='.repeat(50));
            console.log('\n🛠️  可用接口：');
            console.log('  POST /register - 用户注册');
            console.log('  POST /login    - 用户登录');
            console.log('  GET  /users    - 获取用户列表（测试）');
            console.log('  GET  /health   - 健康检查');
            console.log('\n按 Ctrl+C 停止服务器');
        });

    } catch (error) {
        console.error('❌ 服务器启动失败:', error.message);
        console.log('💡 请检查：');
        console.log('   1. MySQL服务是否启动');
        console.log('   2. 数据库配置是否正确（用户名、密码、端口）');
        console.log('   3. studyapp_database数据库是否存在');
        process.exit(1); // 退出进程
    }
}

// ================================
// 7. 优雅关闭
// ================================
process.on('SIGINT', async () => {
    console.log('\n🛑 收到关闭信号，正在优雅关闭...');
    await db.closePool();
    console.log('✅ 服务器已关闭');
    process.exit(0);
});

process.on('SIGTERM', async () => {
    console.log('\n🛑 收到终止信号，正在优雅关闭...');
    await db.closePool();
    console.log('✅ 服务器已关闭');
    process.exit(0);
});

// ================================
// 8. 启动服务器
// ================================
startServer();

// ================================
// 9. 导出app（用于测试）
// ================================
module.exports = app;