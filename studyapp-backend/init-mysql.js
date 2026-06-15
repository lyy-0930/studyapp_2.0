/**
 * MySQL数据库初始化脚本
 * 功能：创建users表（如果不存在）
 * 运行方式：npm run init-mysql
 */

const db = require('./db');

async function initDatabase() {
    try {
        console.log('📝 开始初始化MySQL数据库表...');

        // 创建users表（与用户要求的结构一致）
        const createTableSQL = `
            CREATE TABLE IF NOT EXISTS users (
                id INT AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(50) NOT NULL,
                password VARCHAR(50) NOT NULL,
                role VARCHAR(20) DEFAULT 'student',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                UNIQUE KEY unique_username (username)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
        `;

        await db.executeQuery(createTableSQL);
        console.log('✅ users表创建/已存在');

        // 检查表结构
        const checkSQL = `DESCRIBE users`;
        const tableInfo = await db.executeQuery(checkSQL);
        console.log('📊 表结构：');
        tableInfo.forEach(column => {
            console.log(`   ${column.Field} - ${column.Type} - ${column.Null === 'YES' ? 'NULL' : 'NOT NULL'}`);
        });

        // 插入示例数据（可选）
        const insertSampleSQL = `
            INSERT IGNORE INTO users (username, password, role) VALUES
            ('student001', '123456', 'student'),
            ('student002', '123456', 'student'),
            ('teacher001', '123456', 'teacher')
        `;

        const result = await db.executeQuery(insertSampleSQL);
        if (result.affectedRows > 0) {
            console.log(`✅ 插入了 ${result.affectedRows} 条示例数据`);
        } else {
            console.log('✅ 示例数据已存在（未重复插入）');
        }

        // 显示现有用户
        const users = await db.executeQuery('SELECT id, username, role FROM users');
        console.log('👥 现有用户：');
        users.forEach(user => {
            console.log(`   ${user.id}. ${user.username} (${user.role})`);
        });

        console.log('🎉 数据库初始化完成');
        process.exit(0);

    } catch (error) {
        console.error('❌ 数据库初始化失败:', error.message);
        console.error('💡 请检查：');
        console.error('   1. MySQL服务是否启动');
        console.error('   2. studyapp_database数据库是否存在');
        console.error('   3. 数据库配置是否正确');
        process.exit(1);
    }
}

// 执行初始化
initDatabase();