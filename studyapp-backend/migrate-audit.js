/**
 * 审计日志表迁移脚本
 * 创建 audit_logs 表，用于记录所有管理操作、鉴权事件、安全事件
 *
 * 运行：node migrate-audit.js
 */

require('dotenv').config();
const db = require('./db');

async function migrate() {
    console.log('='.repeat(50));
    console.log('📋 审计日志表迁移');
    console.log('='.repeat(50));

    try {
        await db.executeQuery('SELECT 1');
        console.log('✅ 数据库连接成功');

        // 检查表是否已存在
        const tableCheck = await db.executeQuery(
            "SELECT COUNT(*) as cnt FROM information_schema.tables WHERE table_schema = ? AND table_name = 'audit_logs'",
            [process.env.DB_NAME || 'studyapp_database']
        );

        if (tableCheck[0].cnt > 0) {
            console.log('ℹ️  audit_logs 表已存在，跳过创建');
        } else {
            console.log('📦 创建 audit_logs 表...');
            await db.executeQuery(`
                CREATE TABLE audit_logs (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    operator_id INT DEFAULT NULL COMMENT '操作者用户ID（未登录为NULL）',
                    role VARCHAR(50) DEFAULT NULL COMMENT '操作者角色',
                    ip_address VARCHAR(50) DEFAULT NULL COMMENT '客户端IP',
                    user_agent VARCHAR(500) DEFAULT NULL COMMENT '客户端User-Agent',
                    action_type VARCHAR(100) NOT NULL COMMENT '操作类型',
                    target_type VARCHAR(50) DEFAULT NULL COMMENT '目标资源类型（user/course/question/material/category等）',
                    target_id VARCHAR(100) DEFAULT NULL COMMENT '目标资源ID',
                    result VARCHAR(20) NOT NULL DEFAULT 'success' COMMENT '结果（success/failure/forbidden）',
                    detail TEXT DEFAULT NULL COMMENT '详情JSON（敏感字段已脱敏）',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
                    INDEX idx_created_at (created_at),
                    INDEX idx_action_type (action_type),
                    INDEX idx_operator_id (operator_id),
                    INDEX idx_result (result),
                    INDEX idx_target (target_type, target_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                  COMMENT='审计日志表：记录管理操作、鉴权事件、安全事件。应用账号无删除权限，日志仅可追加。'
            `);
            console.log('✅  audit_logs 表创建成功');
        }

        // 验证表结构
        const columns = await db.executeQuery('SHOW COLUMNS FROM audit_logs');
        console.log(`\n📊 表结构确认（${columns.length} 个字段）:`);
        columns.forEach(col => {
            console.log(`   ${col.Field.padEnd(20)} ${col.Type.padEnd(25)} ${col.Null === 'YES' ? 'NULL' : 'NOT NULL'} ${col.Default ? `DEFAULT ${col.Default}` : ''}`);
        });

        console.log('\n✅ 审计日志表迁移完成');
        console.log('💡 审计日志为追加写入，应用业务账号无 DELETE 权限');

    } catch (error) {
        console.error('❌ 迁移失败:', error);
        process.exit(1);
    } finally {
        await db.closePool();
    }
}

migrate();
