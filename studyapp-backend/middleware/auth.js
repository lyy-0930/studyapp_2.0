/**
 * 认证与授权中间件
 *
 * 提供：
 *   authenticate  - JWT 令牌验证，解析后挂载到 req.user
 *   requireRole   - RBAC 角色校验，支持指定多个角色
 *   requireOwnership - 资源归属校验（用于验证用户只能操作自己的数据）
 */

const jwt = require('jsonwebtoken');

const JWT_SECRET = process.env.JWT_SECRET || 'studyapp_jwt_secret';
const JWT_REFRESH_SECRET = process.env.JWT_REFRESH_SECRET || 'studyapp_refresh_secret';

/**
 * 统一错误响应
 */
function authError(res, message, statusCode = 401) {
    return res.status(statusCode).json({
        success: false,
        message: message
    });
}

/**
 * 生成 Access Token（短时效，15 分钟）
 */
function generateAccessToken(user) {
    return jwt.sign(
        {
            sub: user.id,
            username: user.username,
            role: user.role
        },
        JWT_SECRET,
        { expiresIn: '15m' }
    );
}

/**
 * 生成 Refresh Token（长时效，7 天）
 */
function generateRefreshToken(user) {
    return jwt.sign(
        {
            sub: user.id,
            username: user.username,
            role: user.role,
            type: 'refresh'
        },
        JWT_REFRESH_SECRET,
        { expiresIn: '7d' }
    );
}

/**
 * 验证 Refresh Token 并生成新令牌对
 */
function refreshTokens(refreshToken) {
    const decoded = jwt.verify(refreshToken, JWT_REFRESH_SECRET);
    if (decoded.type !== 'refresh') {
        throw new Error('令牌类型错误');
    }
    const user = { id: decoded.sub, username: decoded.username, role: decoded.role };
    return {
        access_token: generateAccessToken(user),
        refresh_token: generateRefreshToken(user)
    };
}

// ========================================================================
// Express 中间件
// ========================================================================

/**
 * JWT 认证中间件
 * 从 Authorization: Bearer <token> 中提取并校验令牌
 * 验证通过后将用户信息挂载到 req.user
 * 验证失败返回 401
 */
function authenticate(req, res, next) {
    const authHeader = req.headers.authorization;

    if (!authHeader || !authHeader.startsWith('Bearer ')) {
        return authError(res, '未提供认证令牌或令牌格式错误');
    }

    const token = authHeader.split(' ')[1];

    try {
        const decoded = jwt.verify(token, JWT_SECRET);
        req.user = {
            id: decoded.sub,
            username: decoded.username,
            role: decoded.role
        };
        next();
    } catch (err) {
        if (err.name === 'TokenExpiredError') {
            return authError(res, '访问令牌已过期，请使用刷新令牌', 401);
        }
        return authError(res, '访问令牌无效', 401);
    }
}

/**
 * 可选认证中间件
 * 有 token 则解析，没有也不拒绝
 * 用于混合权限路由（如公开课程列表，但登录后可看额外信息）
 */
function optionalAuth(req, res, next) {
    const authHeader = req.headers.authorization;

    if (authHeader && authHeader.startsWith('Bearer ')) {
        const token = authHeader.split(' ')[1];
        try {
            const decoded = jwt.verify(token, JWT_SECRET);
            req.user = {
                id: decoded.sub,
                username: decoded.username,
                role: decoded.role
            };
        } catch (err) {
            // token 无效就当未登录处理
        }
    }

    next();
}

/**
 * RBAC 角色校验中间件
 * 用法：router.use(requireRole('admin')) 或 requireRole('teacher', 'admin')
 * 必须在 authenticate 之后使用
 *
 * @param  {...string} roles 允许的角色列表
 */
function requireRole(...roles) {
    return (req, res, next) => {
        if (!req.user) {
            return authError(res, '未认证，请先登录', 401);
        }

        if (!roles.includes(req.user.role)) {
            return authError(res, `权限不足，需要 ${roles.join(' 或 ')} 角色`, 403);
        }

        next();
    };
}

/**
 * 资源归属校验（验证用户只能操作自己的数据）
 * 需要在 authenticate + requireRole 之后使用
 *
 * @param {function} getOwnerId - 从请求中提取资源拥有者 ID 的函数
 *    签名为 (req) => number | string | null
 */
function requireOwnership(getOwnerId) {
    return (req, res, next) => {
        if (!req.user) {
            return authError(res, '未认证', 401);
        }

        // 管理员可以操作任何资源
        if (req.user.role === 'admin') {
            return next();
        }

        const ownerId = getOwnerId(req);
        if (ownerId === null || ownerId === undefined) {
            return authError(res, '无法确定资源归属', 400);
        }

        if (Number(req.user.id) !== Number(ownerId)) {
            return authError(res, '无权操作其他用户的数据', 403);
        }

        next();
    };
}

/**
 * 课程归属校验中间件工厂
 * 验证当前教师是否为课程的创建者（管理员可以操作任何课程）
 * 需要在 authenticate + requireRole('teacher', 'admin') 之后使用
 *
 * @param {function} db - 数据库查询函数
 */
function requireCourseOwnership(db) {
    return async (req, res, next) => {
        try {
            if (!req.user) {
                return authError(res, '未认证', 401);
            }

            // 管理员可以操作任何课程
            if (req.user.role === 'admin') {
                return next();
            }

            const courseId = req.params.id || req.params.courseId;
            if (!courseId) {
                return authError(res, '缺少课程 ID', 400);
            }

            const courses = await db.executeQuery(
                'SELECT teacher_id FROM courses WHERE id = ?',
                [courseId]
            );

            if (courses.length === 0) {
                return authError(res, '课程不存在', 404);
            }

            if (Number(courses[0].teacher_id) !== Number(req.user.id)) {
                return authError(res, '只能操作自己的课程', 403);
            }

            req.course = courses[0]; // 挂载课程信息供后续使用
            next();
        } catch (error) {
            console.error('课程归属校验错误:', error);
            return authError(res, '校验课程归属失败', 500);
        }
    };
}

module.exports = {
    generateAccessToken,
    generateRefreshToken,
    refreshTokens,
    authenticate,
    optionalAuth,
    requireRole,
    requireOwnership,
    requireCourseOwnership
};
