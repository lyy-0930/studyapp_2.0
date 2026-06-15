/**
 * 限流与防爆破中间件
 *
 * 功能：
 *   1. 登录失败锁定 — 同一 IP 连续失败 N 次后临时锁定
 *   2. 接口限流 — 每个 IP 每分钟最多 M 次请求
 *   3. 防重复提交 — 基于操作幂等性检查
 *
 * 使用内存存储（生产环境建议替换为 Redis）
 */

// ============================
// 内存存储（进程重启即丢失）
// ============================
const loginAttempts = new Map();   // key: "ip:username", value: { count, lockedUntil }
const ipRequestCounts = new Map(); // key: "ip:route", value: { count, resetAt }
const idempotencyStore = new Map(); // key: idempotencyKey, value: processed

// ============================
// 配置
// ============================
const CONFIG = {
    // 登录锁定：5 次失败后锁定 15 分钟
    LOGIN_MAX_ATTEMPTS: 5,
    LOGIN_LOCK_DURATION_MS: 15 * 60 * 1000,

    // 通用限流：每个 IP 每分钟 60 次
    RATE_LIMIT_MAX: 60,
    RATE_LIMIT_WINDOW_MS: 60 * 1000,

    // 登录接口限流：每个 IP 每分钟 10 次
    LOGIN_RATE_LIMIT_MAX: 10,

    // 管理接口限流：每个 IP 每分钟 30 次
    ADMIN_RATE_LIMIT_MAX: 30,

    // 防重复提交：5 秒内同一操作不能重复
    IDEMPOTENCY_WINDOW_MS: 5000
};

// 每 5 分钟清理过期数据
setInterval(() => {
    const now = Date.now();
    for (const [key, val] of loginAttempts) {
        if (val.lockedUntil && val.lockedUntil < now) loginAttempts.delete(key);
    }
    for (const [key, val] of ipRequestCounts) {
        if (val.resetAt < now) ipRequestCounts.delete(key);
    }
    for (const [key, val] of idempotencyStore) {
        if (val < now - 60000) idempotencyStore.delete(key);
    }
}, 5 * 60 * 1000).unref();

// ============================
// 辅助函数
// ============================

function getClientIP(req) {
    const forwarded = req.headers['x-forwarded-for'];
    if (forwarded) return forwarded.split(',')[0].trim();
    return req.connection?.remoteAddress || req.ip || 'unknown';
}

// ============================
// 中间件
// ============================

/**
 * 登录失败计数 + 锁定
 */
function trackLoginFailure(username, req) {
    const ip = getClientIP(req);
    const key = `${ip}:${username}`;
    const now = Date.now();
    let record = loginAttempts.get(key);

    if (!record || (record.lockedUntil && record.lockedUntil < now)) {
        record = { count: 0, lockedUntil: null };
    }

    record.count++;

    if (record.count >= CONFIG.LOGIN_MAX_ATTEMPTS) {
        record.lockedUntil = now + CONFIG.LOGIN_LOCK_DURATION_MS;
        console.warn(`🔒 IP ${ip} 用户 ${username} 登录失败 ${record.count} 次，已锁定至 ${new Date(record.lockedUntil).toLocaleString()}`);
    }

    loginAttempts.set(key, record);
    return record;
}

/**
 * 登录成功清除失败计数
 */
function clearLoginAttempts(username, req) {
    const ip = getClientIP(req);
    const key = `${ip}:${username}`;
    loginAttempts.delete(key);
}

/**
 * 登录中间件：检查是否被锁定
 */
function checkLoginLock(req, res, next) {
    const { username } = req.body;
    if (!username) return next();

    const ip = getClientIP(req);
    const key = `${ip}:${username}`;
    const record = loginAttempts.get(key);

    if (record && record.lockedUntil && record.lockedUntil > Date.now()) {
        const remainingMin = Math.ceil((record.lockedUntil - Date.now()) / 60000);
        return res.status(429).json({
            success: false,
            message: `登录失败次数过多，请 ${remainingMin} 分钟后再试`
        });
    }

    next();
}

/**
 * 通用接口限流（按 IP + 路由）
 *
 * @param {number} maxRequests - 窗口内最大请求数
 * @param {number} windowMs - 窗口时长（毫秒）
 */
function rateLimit(maxRequests = CONFIG.RATE_LIMIT_MAX, windowMs = CONFIG.RATE_LIMIT_WINDOW_MS) {
    return (req, res, next) => {
        const ip = getClientIP(req);
        const route = req.route?.path || req.path;
        const key = `${ip}:${route}`;
        const now = Date.now();

        let record = ipRequestCounts.get(key);
        if (!record || record.resetAt < now) {
            record = { count: 0, resetAt: now + windowMs };
        }

        record.count++;
        ipRequestCounts.set(key, record);

        if (record.count > maxRequests) {
            const resetSec = Math.ceil((record.resetAt - now) / 1000);
            return res.status(429).json({
                success: false,
                message: `请求过于频繁，请 ${resetSec} 秒后再试`
            });
        }

        next();
    };
}

/**
 * 登录接口限流（更严格的限制）
 */
const loginRateLimit = rateLimit(CONFIG.LOGIN_RATE_LIMIT_MAX, CONFIG.RATE_LIMIT_WINDOW_MS);

/**
 * 管理接口限流
 */
const adminRateLimit = rateLimit(CONFIG.ADMIN_RATE_LIMIT_MAX, CONFIG.RATE_LIMIT_WINDOW_MS);

/**
 * 防重复提交中间件
 * 客户端需在请求头中提供 X-Idempotency-Key
 * 同一个 key 在窗口期内重复提交会被拒绝
 */
function checkIdempotency(req, res, next) {
    const key = req.headers['x-idempotency-key'];
    if (!key) return next(); // 不强制要求，没有就不检查

    const now = Date.now();
    const existing = idempotencyStore.get(key);

    if (existing && existing > now - CONFIG.IDEMPOTENCY_WINDOW_MS) {
        return res.status(409).json({
            success: false,
            message: '该操作已提交，请勿重复提交'
        });
    }

    idempotencyStore.set(key, now);
    next();
}

module.exports = {
    trackLoginFailure,
    clearLoginAttempts,
    checkLoginLock,
    rateLimit,
    loginRateLimit,
    adminRateLimit,
    checkIdempotency,
    getClientIP
};
