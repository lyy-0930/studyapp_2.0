/**
 * 审计日志中间件
 *
 * 功能：
 *   1. 记录所有管理操作、鉴权事件、上传/删除等关键操作
 *   2. 敏感字段自动脱敏（密码、token、密钥、身份证号、生日等）
 *   3. 自动捕获 IP、User-Agent
 *   4. 审计日志单独建表，应用账号无删除权限
 */

const SENSITIVE_FIELDS = [
    'password', 'newPassword', 'confirmPassword', 'oldPassword',
    'access_token', 'refresh_token', 'accessToken', 'refreshToken',
    'token', 'secret', 'apiKey', 'api_key',
    'id_number', 'idCard', 'id_card', 'ssn',
    'birthday', 'securityAnswer', 'security_answer',
    'credit_card', 'cvv', 'pin'
];

const SENSITIVE_PATTERNS = [
    /accessKeyId["\s:=]+[A-Za-z0-9]+/gi,
    /accessKeySecret["\s:=]+[A-Za-z0-9+/=]+/gi,
    /"password"\s*:\s*"[^"]+"/gi,
    /"token"\s*:\s*"[^"]+"/gi,
    /"secret"\s*:\s*"[^"]+"/gi
];

/**
 * 脱敏：递归地移除请求体/响应体中的敏感字段
 */
function sanitize(obj) {
    if (!obj || typeof obj !== 'object') return obj;
    if (Array.isArray(obj)) return obj.map(sanitize);

    const result = {};
    for (const [key, value] of Object.entries(obj)) {
        if (SENSITIVE_FIELDS.includes(key) || SENSITIVE_FIELDS.includes(key.toLowerCase())) {
            result[key] = '***REDACTED***';
        } else if (typeof value === 'object' && value !== null) {
            result[key] = sanitize(value);
        } else {
            result[key] = value;
        }
    }
    return result;
}

/**
 * 对字符串进行正则脱敏
 */
function sanitizeString(str) {
    if (typeof str !== 'string') return str;
    let result = str;
    for (const pattern of SENSITIVE_PATTERNS) {
        result = result.replace(pattern, (match) => {
            const eqIdx = match.indexOf('=') > 0 ? match.indexOf('=') : match.indexOf(':');
            if (eqIdx > 0) return match.substring(0, eqIdx + 1) + '"***REDACTED***"';
            return match;
        });
    }
    return result;
}

/**
 * 获取客户端 IP（支持代理）
 */
function getClientIP(req) {
    const forwarded = req.headers['x-forwarded-for'];
    if (forwarded) {
        return forwarded.split(',')[0].trim();
    }
    return req.connection?.remoteAddress || req.ip || 'unknown';
}

/**
 * 获取 User-Agent
 */
function getUserAgent(req) {
    return (req.headers['user-agent'] || '').substring(0, 500);
}

/**
 * 写入审计日志到数据库
 */
async function writeAuditLog(db, logEntry) {
    try {
        await db.executeQuery(
            `INSERT INTO audit_logs (operator_id, role, ip_address, user_agent, action_type, target_type, target_id, result, detail)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
            [
                logEntry.operatorId || null,
                (logEntry.role || '').substring(0, 50),
                (logEntry.ipAddress || '').substring(0, 50),
                (logEntry.userAgent || '').substring(0, 500),
                (logEntry.actionType || '').substring(0, 100),
                (logEntry.targetType || '').substring(0, 50),
                logEntry.targetId !== undefined ? String(logEntry.targetId).substring(0, 100) : null,
                (logEntry.result || 'success').substring(0, 20),
                logEntry.detail ? JSON.stringify(sanitize(logEntry.detail)).substring(0, 2000) : null
            ]
        );
    } catch (error) {
        console.error('⚠️ 写入审计日志失败:', error.message);
        // 审计日志写入失败不应影响主流程
    }
}

/**
 * 审计日志助手：记录操作
 *
 * @param {Object} db - 数据库模块
 * @param {Object} req - Express 请求对象
 * @param {Object} options
 * @param {string} options.actionType - 操作类型
 * @param {string} [options.targetType] - 目标类型
 * @param {string|number} [options.targetId] - 目标 ID
 * @param {string} [options.result] - 结果（success/failure/forbidden）
 * @param {Object} [options.detail] - 详情（自动脱敏）
 */
function createAuditLogger(db) {
    return async function audit(req, options = {}) {
        const logEntry = {
            operatorId: req.user?.id || null,
            role: req.user?.role || 'anonymous',
            ipAddress: getClientIP(req),
            userAgent: getUserAgent(req),
            actionType: options.actionType || 'unknown',
            targetType: options.targetType || null,
            targetId: options.targetId !== undefined ? options.targetId : null,
            result: options.result || 'success',
            detail: options.detail ? sanitize(options.detail) : null
        };

        await writeAuditLog(db, logEntry);
    };
}

/**
 * Express 中间件：自动审计成功操作（用在路由末尾）
 * 用法：router.use(auditSuccess(db, 'admin_delete_user', 'user'))
 */
function auditSuccess(db, actionType, targetType) {
    return (req, res, next) => {
        // 包装 res.json 以拦截响应
        const originalJson = res.json.bind(res);
        res.json = function (body) {
            if (res.statusCode >= 200 && res.statusCode < 300) {
                const auditFn = createAuditLogger(db);
                const targetId = req.params.id || req.params.userId || req.params.courseId || null;
                auditFn(req, {
                    actionType,
                    targetType,
                    targetId,
                    result: 'success',
                    detail: { method: req.method, path: req.originalUrl }
                }).catch(() => {});
            }
            return originalJson(body);
        };
        next();
    };
}

module.exports = {
    createAuditLogger,
    auditSuccess,
    sanitize,
    sanitizeString,
    getClientIP,
    writeAuditLog
};
