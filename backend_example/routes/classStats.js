/**
 * 班级统计API路由
 * 文件：routes/classStats.js
 * 用法：在你的Express应用中导入并注册此路由
 */

const express = require('express');
const router = express.Router();

// 假设你已经有数据库连接
// 这里以mysql2为例，如果是其他数据库请相应修改
const db = require('../config/database'); // 修改为你的数据库连接文件

/**
 * GET /api/stats/class/:classId
 * 获取班级统计数据
 *
 * 请求参数：
 * - classId: 班级ID (路径参数)
 *
 * 响应格式：
 * {
 *   "totalStudents": 45,
 *   "activeStudents": 38,
 *   "averageWatchTime": 125,
 *   "completionRate": 78,
 *   "topStudents": [
 *     { "name": "张三", "progress": 95 },
 *     { "name": "李四", "progress": 88 }
 *   ]
 * }
 */
router.get('/class/:classId', async (req, res) => {
    try {
        const { classId } = req.params;

        console.log(`📊 请求班级统计: classId=${classId}`);

        // 1. 验证班级是否存在
        const classExists = await checkClassExists(classId);
        if (!classExists) {
            return res.status(404).json({
                success: false,
                message: `班级ID ${classId} 不存在`,
                error: 'CLASS_NOT_FOUND'
            });
        }

        // 2. 并行获取所有统计数据（提高性能）
        const [
            totalStudents,
            activeStudents,
            averageWatchTime,
            completionRate,
            topStudents
        ] = await Promise.all([
            getTotalStudents(classId),
            getActiveStudents(classId),
            getAverageWatchTime(classId),
            getCompletionRate(classId),
            getTopStudents(classId)
        ]);

        // 3. 构建响应
        const response = {
            totalStudents,
            activeStudents,
            averageWatchTime: Math.round(averageWatchTime),
            completionRate: Math.round(completionRate),
            topStudents
        };

        console.log(`✅ 返回统计:`, {
            totalStudents,
            activeStudents,
            averageWatchTime: response.averageWatchTime,
            completionRate: response.completionRate,
            topStudentsCount: topStudents.length
        });

        res.json(response);

    } catch (error) {
        console.error('❌ 获取统计信息失败:', error);

        // 根据错误类型返回不同的状态码
        if (error.code === 'ECONNREFUSED') {
            return res.status(503).json({
                success: false,
                message: '数据库连接失败',
                error: 'DATABASE_UNAVAILABLE'
            });
        }

        res.status(500).json({
            success: false,
            message: '服务器内部错误',
            error: error.message || 'UNKNOWN_ERROR'
        });
    }
});

/**
 * 检查班级是否存在
 */
async function checkClassExists(classId) {
    try {
        const [rows] = await db.query(
            'SELECT id FROM classes WHERE id = ? LIMIT 1',
            [classId]
        );
        return rows.length > 0;
    } catch (error) {
        console.error('检查班级存在失败:', error);
        throw error;
    }
}

/**
 * 获取班级总学生数
 */
async function getTotalStudents(classId) {
    try {
        const [rows] = await db.query(
            'SELECT COUNT(*) as count FROM students WHERE class_id = ?',
            [classId]
        );
        return rows[0]?.count || 0;
    } catch (error) {
        console.error('获取总学生数失败:', error);
        return 0; // 出错时返回0，不中断其他查询
    }
}

/**
 * 获取活跃学生数（最近7天有学习记录）
 */
async function getActiveStudents(classId) {
    try {
        const [rows] = await db.query(`
            SELECT COUNT(DISTINCT lr.student_id) as count
            FROM learning_records lr
            JOIN students s ON lr.student_id = s.id
            WHERE s.class_id = ?
            AND lr.last_watch_time >= DATE_SUB(NOW(), INTERVAL 7 DAY)
        `, [classId]);
        return rows[0]?.count || 0;
    } catch (error) {
        console.error('获取活跃学生数失败:', error);
        return 0;
    }
}

/**
 * 获取平均观看时长（分钟）
 */
async function getAverageWatchTime(classId) {
    try {
        const [rows] = await db.query(`
            SELECT COALESCE(AVG(lr.watch_time), 0) as average
            FROM learning_records lr
            JOIN students s ON lr.student_id = s.id
            WHERE s.class_id = ?
        `, [classId]);
        return rows[0]?.average || 0;
    } catch (error) {
        console.error('获取平均观看时长失败:', error);
        return 0;
    }
}

/**
 * 获取平均完成率（0-100%）
 */
async function getCompletionRate(classId) {
    try {
        const [rows] = await db.query(`
            SELECT COALESCE(AVG(lr.progress), 0) as average
            FROM learning_records lr
            JOIN students s ON lr.student_id = s.id
            WHERE s.class_id = ?
        `, [classId]);
        return rows[0]?.average || 0;
    } catch (error) {
        console.error('获取平均完成率失败:', error);
        return 0;
    }
}

/**
 * 获取优秀学生列表（进度前5名）
 */
async function getTopStudents(classId, limit = 5) {
    try {
        const [rows] = await db.query(`
            SELECT
                s.name,
                ROUND(COALESCE(AVG(lr.progress), 0)) as progress
            FROM learning_records lr
            RIGHT JOIN students s ON lr.student_id = s.id
            WHERE s.class_id = ?
            GROUP BY s.id, s.name
            ORDER BY progress DESC
            LIMIT ?
        `, [classId, limit]);

        return rows.map(row => ({
            name: row.name,
            progress: row.progress
        }));
    } catch (error) {
        console.error('获取优秀学生失败:', error);
        return [];
    }
}

/**
 * 测试端点：验证API是否正常工作
 */
router.get('/test/:classId', async (req, res) => {
    try {
        const { classId } = req.params;

        const testResults = {
            database: await testDatabaseConnection(),
            classExists: await checkClassExists(classId),
            hasStudents: await getTotalStudents(classId) > 0,
            timestamp: new Date().toISOString()
        };

        res.json({
            success: true,
            message: 'API测试结果',
            data: testResults
        });

    } catch (error) {
        res.status(500).json({
            success: false,
            message: '测试失败',
            error: error.message
        });
    }
});

/**
 * 测试数据库连接
 */
async function testDatabaseConnection() {
    try {
        await db.query('SELECT 1 as connection_test');
        return { connected: true, status: 'OK' };
    } catch (error) {
        return {
            connected: false,
            status: 'ERROR',
            error: error.message
        };
    }
}

/**
 * 可选：获取班级详情
 */
router.get('/class/:classId/details', async (req, res) => {
    try {
        const { classId } = req.params;

        const [rows] = await db.query(`
            SELECT
                c.id,
                c.name,
                c.description,
                COUNT(s.id) as total_students,
                c.created_at
            FROM classes c
            LEFT JOIN students s ON c.id = s.class_id
            WHERE c.id = ?
            GROUP BY c.id
        `, [classId]);

        if (rows.length === 0) {
            return res.status(404).json({
                success: false,
                message: '班级不存在'
            });
        }

        res.json({
            success: true,
            data: rows[0]
        });

    } catch (error) {
        console.error('获取班级详情失败:', error);
        res.status(500).json({
            success: false,
            message: '服务器内部错误'
        });
    }
});

module.exports = router;