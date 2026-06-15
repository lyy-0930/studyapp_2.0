/**
 * 完整MySQL数据库表初始化脚本
 * 创建学习应用所需的所有表
 * 运行方式：node init-mysql-tables.js
 */

const db = require('./db');

async function initAllTables() {
    try {
        console.log('📝 开始初始化MySQL数据库表...');

        // 1. 确保users表存在并结构正确（已由init-mysql.js创建）
        console.log('✅ users表已存在（无需创建）');

        // 2. 创建courses表（课程表）
        const createCoursesTableSQL = `
            CREATE TABLE IF NOT EXISTS courses (
                id INT AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(200) NOT NULL,
                description TEXT,
                teacher_id INT NOT NULL,  -- 关联users表的教师ID
                teacher_name VARCHAR(50) NOT NULL,  -- 教师姓名（冗余存储，避免频繁join）
                credit INT DEFAULT 2,
                video_url VARCHAR(500),  -- 视频URL（如果有）
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_teacher_id (teacher_id),
                INDEX idx_created_at (created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
        `;

        await db.executeQuery(createCoursesTableSQL);
        console.log('✅ courses表创建/已存在');

        // 3. 创建course_enrollments表（学生选课表）
        const createEnrollmentsTableSQL = `
            CREATE TABLE IF NOT EXISTS course_enrollments (
                id INT AUTO_INCREMENT PRIMARY KEY,
                course_id INT NOT NULL,
                student_id INT NOT NULL,
                student_name VARCHAR(50) NOT NULL,  -- 学生姓名
                enrolled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                UNIQUE KEY unique_course_student (course_id, student_id),  -- 防止重复选课
                INDEX idx_course_id (course_id),
                INDEX idx_student_id (student_id),
                INDEX idx_enrolled_at (enrolled_at),
                FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
                FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
        `;

        await db.executeQuery(createEnrollmentsTableSQL);
        console.log('✅ course_enrollments表创建/已存在');

        // 4. 创建study_records表（学习记录表）
        const createStudyRecordsTableSQL = `
            CREATE TABLE IF NOT EXISTS study_records (
                id INT AUTO_INCREMENT PRIMARY KEY,
                course_id INT NOT NULL,
                student_id INT NOT NULL,
                watch_time INT DEFAULT 0,  -- 观看时长（分钟）
                progress INT DEFAULT 0,     -- 学习进度（0-100%）
                last_watch_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                UNIQUE KEY unique_course_student_record (course_id, student_id),  -- 每个学生对每门课程一条记录
                INDEX idx_course_student (course_id, student_id),
                INDEX idx_last_watch (last_watch_time),
                INDEX idx_progress (progress),
                FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
                FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
        `;

        await db.executeQuery(createStudyRecordsTableSQL);
        console.log('✅ study_records表创建/已存在');

        // 5. 创建videos表（视频表，如果需要更详细的视频管理）
        const createVideosTableSQL = `
            CREATE TABLE IF NOT EXISTS videos (
                id INT AUTO_INCREMENT PRIMARY KEY,
                course_id INT NOT NULL,
                title VARCHAR(200) NOT NULL,
                description TEXT,
                video_url VARCHAR(500) NOT NULL,
                duration INT,  -- 视频时长（分钟）
                teacher_name VARCHAR(50),
                upload_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_course_id (course_id),
                FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
        `;

        await db.executeQuery(createVideosTableSQL);
        console.log('✅ videos表创建/已存在');

        // 6. 显示所有表结构
        console.log('\n📊 数据库表结构概览：');
        const tables = ['users', 'courses', 'course_enrollments', 'study_records', 'videos'];

        for (const table of tables) {
            try {
                const tableInfo = await db.executeQuery(`DESCRIBE ${table}`);
                console.log(`\n${table}表 (${tableInfo.length}列)：`);
                tableInfo.forEach(column => {
                    console.log(`   ${column.Field} - ${column.Type} - ${column.Null === 'YES' ? 'NULL' : 'NOT NULL'}`);
                });
            } catch (error) {
                console.log(`\n${table}表：查询失败（可能不存在）`);
            }
        }

        // 7. 插入示例数据（可选）
        console.log('\n📋 是否插入示例数据？(y/n)');
        // 注意：这里需要用户交互，但在脚本中我们可以直接跳过
        // 如果希望自动插入，可以取消下面的注释

        // await insertSampleData();

        console.log('\n🎉 数据库表初始化完成！');
        console.log('\n🚀 下一步：');
        console.log('   1. 启动服务器: npm start');
        console.log('   2. 测试API接口');
        console.log('   3. 在Android App中连接后端');

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

/**
 * 插入示例数据（用于测试）
 */
async function insertSampleData() {
    try {
        console.log('\n📝 正在插入示例数据...');

        // 1. 检查是否有教师用户，如果没有则创建
        const teachers = await db.executeQuery('SELECT id, username FROM users WHERE role = "teacher"');

        if (teachers.length === 0) {
            console.log('   👨‍🏫 创建示例教师用户...');
            await db.executeQuery(
                'INSERT INTO users (username, password, role) VALUES (?, ?, ?)',
                ['teacher001', '123456', 'teacher']
            );
            const [teacherResult] = await db.executeQuery('SELECT LAST_INSERT_ID() as id');
            const teacherId = teacherResult.insertId;
            const teacherName = 'teacher001';
            console.log(`   ✅ 创建教师用户: ${teacherName} (ID: ${teacherId})`);
        } else {
            const teacherId = teachers[0].id;
            const teacherName = teachers[0].username;
            console.log(`   👨‍🏫 使用现有教师: ${teacherName} (ID: ${teacherId})`);
        }

        // 2. 创建示例课程
        console.log('   📚 创建示例课程...');
        const courses = [
            {
                name: 'Android开发基础',
                description: '学习Android应用开发的基础知识',
                teacher_id: teachers[0]?.id || 1,
                teacher_name: teachers[0]?.username || 'teacher001',
                credit: 2
            },
            {
                name: 'Kotlin编程实战',
                description: '掌握Kotlin语言的高级特性',
                teacher_id: teachers[0]?.id || 1,
                teacher_name: teachers[0]?.username || 'teacher001',
                credit: 3
            }
        ];

        for (const course of courses) {
            await db.executeQuery(
                'INSERT INTO courses (name, description, teacher_id, teacher_name, credit) VALUES (?, ?, ?, ?, ?)',
                [course.name, course.description, course.teacher_id, course.teacher_name, course.credit]
            );
            console.log(`   ✅ 创建课程: ${course.name}`);
        }

        // 3. 获取课程ID
        const courseList = await db.executeQuery('SELECT id, name FROM courses');
        console.log(`   📋 现有课程: ${courseList.map(c => `${c.name}(ID:${c.id})`).join(', ')}`);

        // 4. 检查是否有学生用户，如果没有则创建
        const students = await db.executeQuery('SELECT id, username FROM users WHERE role = "student"');

        if (students.length === 0) {
            console.log('   👨‍🎓 创建示例学生用户...');
            const studentNames = ['student001', 'student002', 'student003'];
            for (const name of studentNames) {
                await db.executeQuery(
                    'INSERT INTO users (username, password, role) VALUES (?, ?, ?)',
                    [name, '123456', 'student']
                );
                console.log(`   ✅ 创建学生用户: ${name}`);
            }
            // 重新获取学生列表
            const newStudents = await db.executeQuery('SELECT id, username FROM users WHERE role = "student"');
            students.push(...newStudents);
        }

        // 5. 创建选课记录
        console.log('   📝 创建示例选课记录...');
        for (const course of courseList) {
            for (const student of students) {
                await db.executeQuery(
                    'INSERT INTO course_enrollments (course_id, student_id, student_name) VALUES (?, ?, ?)',
                    [course.id, student.id, student.username]
                );
                console.log(`   ✅ 学生 ${student.username} 选课 ${course.name}`);
            }
        }

        // 6. 创建学习记录
        console.log('   📊 创建示例学习记录...');
        for (const course of courseList) {
            for (const student of students) {
                const watchTime = Math.floor(Math.random() * 120) + 10; // 10-130分钟
                const progress = Math.floor(Math.random() * 30) + 70; // 70-100%

                await db.executeQuery(
                    `INSERT INTO study_records (course_id, student_id, watch_time, progress, last_watch_time)
                     VALUES (?, ?, ?, ?, NOW() - INTERVAL ? DAY)
                     ON DUPLICATE KEY UPDATE
                     watch_time = VALUES(watch_time),
                     progress = VALUES(progress),
                     last_watch_time = VALUES(last_watch_time)`,
                    [course.id, student.id, watchTime, progress, Math.floor(Math.random() * 7)]
                );
                console.log(`   ✅ 学习记录: ${student.username} 学习 ${course.name} ${watchTime}分钟 ${progress}%`);
            }
        }

        console.log('\n🎉 示例数据插入完成！');
        console.log('📊 现在你可以：');
        console.log('   1. 查看教师统计数据');
        console.log('   2. 测试选课功能');
        console.log('   3. 验证学习记录');

    } catch (error) {
        console.error('❌ 插入示例数据失败:', error.message);
    }
}

// 执行初始化
initAllTables();