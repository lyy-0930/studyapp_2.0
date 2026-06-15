/**
 * 密码安全迁移脚本
 * 功能：
 *   1. 添加 must_change_password 列
 *   2. 检测旧版明文密码，标记为需要修改密码
 *   3. 统计迁移结果
 *
 * 运行：node migrate-passwords.js
 */

require('dotenv').config();
const db = require('./db');

const BCRYPT_PREFIXES = ['$2b$', '$2a$', '$2y$'];

async function migrate() {
    console.log('='.repeat(50));
    console.log('🔐 密码安全迁移脚本');
    console.log('='.repeat(50));
    console.log('');

    try {
        // 1. 测试数据库连接
        console.log('🔌 测试数据库连接...');
        await db.executeQuery('SELECT 1');
        console.log('✅ 数据库连接成功');
        console.log('');

        // 2. 检查 must_change_password 列是否已存在
        console.log('🔍 检查 must_change_password 列...');
        let columnExists = false;
        try {
            await db.executeQuery('SELECT must_change_password FROM users LIMIT 1');
            columnExists = true;
            console.log('   must_change_password 列已存在');
        } catch (e) {
            console.log('   must_change_password 列不存在，需要添加');
        }

        if (!columnExists) {
            console.log('📦 添加 must_change_password 列...');
            await db.executeQuery(`
                ALTER TABLE users
                ADD COLUMN must_change_password TINYINT(1) NOT NULL DEFAULT 0
                COMMENT '是否需要修改密码（管理员重置或旧明文升级时标记为1）'
            `);
            console.log('✅  must_change_password 列已添加');
        }
        console.log('');

        // 3. 检测旧版明文密码
        console.log('🔍 扫描用户密码类型...');
        const allUsers = await db.executeQuery('SELECT id, username, password FROM users');
        let totalUsers = allUsers.length;
        let bcryptCount = 0;
        let plaintextCount = 0;
        let emptyCount = 0;

        const plaintextUserIds = [];

        for (const user of allUsers) {
            const pw = user.password || '';
            if (!pw) {
                emptyCount++;
            } else if (BCRYPT_PREFIXES.some(p => pw.startsWith(p))) {
                bcryptCount++;
            } else {
                plaintextCount++;
                plaintextUserIds.push(user.id);
            }
        }

        console.log(`   总用户数: ${totalUsers}`);
        console.log(`   bcrypt 哈希: ${bcryptCount}`);
        console.log(`   明文密码: ${plaintextCount}`);
        console.log(`   无密码: ${emptyCount}`);
        console.log('');

        // 4. 标记明文密码用户为 must_change_password = true
        if (plaintextUserIds.length > 0) {
            console.log('📦 标记明文密码用户为 must_change_password = true...');
            const batchSize = 50;
            for (let i = 0; i < plaintextUserIds.length; i += batchSize) {
                const batch = plaintextUserIds.slice(i, i + batchSize);
                const placeholders = batch.map(() => '?').join(',');
                await db.executeQuery(
                    `UPDATE users SET must_change_password = true WHERE id IN (${placeholders})`,
                    batch
                );
            }
            console.log(`✅ 已标记 ${plaintextUserIds.length} 个用户为"需要修改密码"`);
            console.log('   这些用户首次登录时，输入正确旧密码后会自动升级为 bcrypt 哈希');
            console.log('   同时 must_change_password 会被设为 false');
        } else {
            console.log('✅ 没有发现明文密码用户');
        }
        console.log('');

        // 5. 汇总
        console.log('='.repeat(50));
        console.log('📊 迁移完成汇总');
        console.log('='.repeat(50));
        console.log(`   总用户:        ${totalUsers}`);
        console.log(`   bcrypt 哈希:   ${bcryptCount}`);
        console.log(`   已标记修改密码: ${plaintextUserIds.length}`);
        console.log(`   无密码:        ${emptyCount}`);
        console.log('');
        console.log('💡 说明：');
        if (plaintextUserIds.length > 0) {
            console.log('   - 标记了 "需要修改密码" 的用户，登录后端会弹出修改密码提示');
            console.log('   - 用户使用旧密码登录时，密码会自动升级为 bcrypt 哈希');
            console.log('   - 升级后 must_change_password 自动设为 false');
        }
        console.log('   - 新注册用户自动使用 bcrypt 哈希存储密码');
        console.log('');

    } catch (error) {
        console.error('❌ 迁移失败:', error);
        process.exit(1);
    } finally {
        await db.closePool();
        console.log('🔌 数据库连接已关闭');
    }
}

migrate();
