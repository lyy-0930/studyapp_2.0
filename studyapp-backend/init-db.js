// 学习应用后端API - 数据库初始化脚本
// 功能：初始化数据库表并插入测试数据

// ================================
// 1. 引入数据库模块
// ================================
const database = require('./database');

console.log('='.repeat(50));
console.log('📊 StudyApp数据库初始化');
console.log('='.repeat(50));

// ================================
// 2. 初始化数据库表
// ================================
const db = database.initDatabase();

// ================================
// 3. 插入测试数据
// ================================
function insertTestData() {
    console.log('\n📝 开始插入测试数据...');

    // 3.1 插入测试用户
    const users = [
        ['student001', '123456', 'student'],
        ['student002', '123456', 'student'],
        ['teacher001', '123456', 'teacher']
    ];

    let usersInserted = 0;
    users.forEach(user => {
        db.run('INSERT OR IGNORE INTO users (username, password, role) VALUES (?, ?, ?)', user, function(err) {
            if (err) {
                console.error('❌ 插入用户失败:', err.message);
            } else {
                if (this.changes > 0) {
                    usersInserted++;
                    console.log(`✅ 插入用户: ${user[0]}`);
                }
            }
        });
    });

    // 3.2 插入测试课程
    const courses = [
        ['高等数学', '大学高等数学课程', '张老师', 4],
        ['大学英语', '大学英语四级课程', '李老师', 3],
        ['计算机基础', '计算机入门课程', '王老师', 2]
    ];

    setTimeout(() => {
        let coursesInserted = 0;
        courses.forEach((course, index) => {
            db.run('INSERT OR IGNORE INTO courses (name, description, teacher, credit) VALUES (?, ?, ?, ?)',
                course,
                function(err) {
                    if (err) {
                        console.error('❌ 插入课程失败:', err.message);
                    } else {
                        if (this.changes > 0) {
                            coursesInserted++;
                            const courseId = this.lastID;
                            console.log(`✅ 插入课程: ${course[0]} (ID: ${courseId})`);

                            // 3.3 为每个课程插入测试视频
                            const videoUrl = `https://study-app-android-2026.oss-cn-hangzhou.aliyuncs.com/studyapp/videos/course${index + 1}_video${index + 1}.mp4`;
                            db.run('INSERT INTO videos (course_id, title, description, video_url, teacher) VALUES (?, ?, ?, ?, ?)',
                                [courseId, `${course[0]}第${index + 1}章`, `${course[0]}介绍视频`, videoUrl, course[2]],
                                function(videoErr) {
                                    if (videoErr) {
                                        console.error('❌ 插入视频失败:', videoErr.message);
                                    } else {
                                        console.log(`   📹 插入视频: ${course[0]}第${index + 1}章`);
                                    }
                                });

                            // 3.4 设置学生选课关系
                            // 学生1（ID: 1）选了所有课程
                            db.run('INSERT OR IGNORE INTO user_courses (user_id, course_id) VALUES (1, ?)',
                                [courseId],
                                function(ucErr) {
                                    if (ucErr) {
                                        console.error('❌ 插入选课关系失败:', ucErr.message);
                                    }
                                });

                            // 学生2（ID: 2）只选第一个课程
                            if (index === 0) {
                                db.run('INSERT OR IGNORE INTO user_courses (user_id, course_id) VALUES (2, ?)',
                                    [courseId],
                                    function(ucErr) {
                                        if (ucErr) {
                                            console.error('❌ 插入选课关系失败:', ucErr.message);
                                        }
                                    });
                            }
                        }
                    }
                });
        });

        // 3.5 等待所有操作完成
        setTimeout(() => {
            console.log('\n🎉 测试数据插入完成');
            console.log('='.repeat(50));
            console.log('📊 测试账号信息：');
            console.log('  学生1: student001 / 123456 (选了3门课程)');
            console.log('  学生2: student002 / 123456 (选了1门课程)');
            console.log('  教师: teacher001 / 123456');
            console.log('\n📚 测试课程：');
            console.log('  1. 高等数学 (4学分) - 张老师');
            console.log('  2. 大学英语 (3学分) - 李老师');
            console.log('  3. 计算机基础 (2学分) - 王老师');
            console.log('\n💡 启动服务器：npm start');
            console.log('💡 重新初始化：npm run init-db');
            console.log('='.repeat(50));

            // 关闭数据库连接
            db.close((err) => {
                if (err) {
                    console.error('❌ 关闭数据库连接失败:', err.message);
                } else {
                    console.log('✅ 数据库连接已关闭');
                }
            });
        }, 1000);
    }, 500);
}

// ================================
// 4. 执行初始化
// ================================
// 等待1秒后插入测试数据（确保表已创建）
setTimeout(insertTestData, 1000);