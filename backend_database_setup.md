# 班级统计数据库设计与API实现

## 📊 数据库表结构设计

### 1. 学生表 (students)
```sql
CREATE TABLE students (
    id INT PRIMARY KEY AUTO_INCREMENT,
    class_id INT NOT NULL,           -- 所属班级ID
    name VARCHAR(100) NOT NULL,      -- 学生姓名
    student_number VARCHAR(50),      -- 学号
    email VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_class_id (class_id)
);
```

### 2. 学习记录表 (learning_records)
```sql
CREATE TABLE learning_records (
    id INT PRIMARY KEY AUTO_INCREMENT,
    student_id INT NOT NULL,         -- 学生ID
    course_id INT NOT NULL,          -- 课程ID
    watch_time INT DEFAULT 0,        -- 观看时长（分钟）
    progress INT DEFAULT 0,          -- 学习进度（0-100%）
    last_watch_time TIMESTAMP,       -- 最后观看时间
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_student_id (student_id),
    INDEX idx_course_id (course_id),
    INDEX idx_last_watch (last_watch_time)
);
```

### 3. 班级表 (classes)
```sql
CREATE TABLE classes (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,      -- 班级名称
    teacher_id INT NOT NULL,         -- 教师ID
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_teacher_id (teacher_id)
);
```

### 4. 活跃学生定义
活跃学生：最近7天有学习记录的学生

## 🚀 现有后端集成步骤

### 步骤1：安装数据库驱动
```bash
# MySQL
npm install mysql2

# PostgreSQL
npm install pg

# 或者使用ORM（推荐）
npm install sequelize mysql2
```

### 步骤2：在现有Express应用中添加统计API

创建新文件 `routes/stats.js`：

```javascript
const express = require('express');
const router = express.Router();
const db = require('../config/database'); // 你的数据库连接

/**
 * GET /api/stats/class/:classId
 * 获取班级统计数据
 */
router.get('/class/:classId', async (req, res) => {
    try {
        const { classId } = req.params;
        
        // 1. 验证班级是否存在
        const [classResult] = await db.query(
            'SELECT * FROM classes WHERE id = ?',
            [classId]
        );
        
        if (classResult.length === 0) {
            return res.status(404).json({
                success: false,
                message: '班级不存在'
            });
        }
        
        // 2. 获取总学生数
        const [totalStudentsResult] = await db.query(
            'SELECT COUNT(*) as count FROM students WHERE class_id = ?',
            [classId]
        );
        const totalStudents = totalStudentsResult[0].count;
        
        // 3. 获取活跃学生数（最近7天有学习记录）
        const [activeStudentsResult] = await db.query(`
            SELECT COUNT(DISTINCT lr.student_id) as count
            FROM learning_records lr
            JOIN students s ON lr.student_id = s.id
            WHERE s.class_id = ? 
            AND lr.last_watch_time >= DATE_SUB(NOW(), INTERVAL 7 DAY)
        `, [classId]);
        const activeStudents = activeStudentsResult[0].count;
        
        // 4. 获取平均观看时长
        const [avgWatchResult] = await db.query(`
            SELECT AVG(lr.watch_time) as average
            FROM learning_records lr
            JOIN students s ON lr.student_id = s.id
            WHERE s.class_id = ?
        `, [classId]);
        const averageWatchTime = Math.round(avgWatchResult[0].average || 0);
        
        // 5. 获取平均完成率
        const [avgProgressResult] = await db.query(`
            SELECT AVG(lr.progress) as average
            FROM learning_records lr
            JOIN students s ON lr.student_id = s.id
            WHERE s.class_id = ?
        `, [classId]);
        const completionRate = Math.round(avgProgressResult[0].average || 0);
        
        // 6. 获取优秀学生（进度前5名）
        const [topStudentsResult] = await db.query(`
            SELECT 
                s.name,
                ROUND(AVG(lr.progress)) as progress
            FROM learning_records lr
            JOIN students s ON lr.student_id = s.id
            WHERE s.class_id = ?
            GROUP BY s.id, s.name
            ORDER BY progress DESC
            LIMIT 5
        `, [classId]);
        
        const topStudents = topStudentsResult.map(row => ({
            name: row.name,
            progress: row.progress
        }));
        
        // 7. 返回统计数据
        res.json({
            totalStudents,
            activeStudents,
            averageWatchTime,
            completionRate,
            topStudents
        });
        
    } catch (error) {
        console.error('获取统计信息失败:', error);
        res.status(500).json({
            success: false,
            message: '服务器内部错误',
            error: error.message
        });
    }
});

module.exports = router;
```

### 步骤3：在主应用中注册路由

在你的主Express文件（如 `app.js` 或 `server.js`）中添加：

```javascript
const statsRouter = require('./routes/stats');

// 注册统计路由
app.use('/api/stats', statsRouter);
```

### 步骤4：添加测试数据（可选）

创建 `scripts/seed_test_data.js`：

```javascript
const db = require('../config/database');

async function seedTestData() {
    try {
        // 1. 创建测试班级
        await db.query(`
            INSERT INTO classes (name, teacher_id, description) 
            VALUES ('Android开发班', 1, 'Android应用开发课程')
        `);
        
        const [classResult] = await db.query('SELECT LAST_INSERT_ID() as id');
        const classId = classResult[0].id;
        
        // 2. 创建测试学生
        const students = [
            { name: '张三', student_number: '2023001' },
            { name: '李四', student_number: '2023002' },
            { name: '王五', student_number: '2023003' },
            { name: '赵六', student_number: '2023004' },
            { name: '孙七', student_number: '2023005' }
        ];
        
        for (const student of students) {
            await db.query(
                'INSERT INTO students (class_id, name, student_number) VALUES (?, ?, ?)',
                [classId, student.name, student.student_number]
            );
        }
        
        // 3. 创建学习记录
        const studentIds = [1, 2, 3, 4, 5];
        const progressValues = [95, 88, 82, 79, 76];
        
        for (let i = 0; i < studentIds.length; i++) {
            await db.query(`
                INSERT INTO learning_records 
                (student_id, course_id, watch_time, progress, last_watch_time)
                VALUES (?, 1, ?, ?, NOW() - INTERVAL ? DAY)
            `, [
                studentIds[i], 
                (progressValues[i] * 2) + 100, // 观看时间
                progressValues[i],             // 进度
                i                              // 最后观看时间偏移
            ]);
        }
        
        console.log('✅ 测试数据创建成功！');
        console.log(`📊 班级ID: ${classId}`);
        console.log(`🌐 API端点: http://localhost:3000/api/stats/class/${classId}`);
        
    } catch (error) {
        console.error('创建测试数据失败:', error);
    }
}

seedTestData();
```

## 🔧 完整后端实现文件

如果你需要从头创建完整的后端，可以使用以下代码：

### `server_with_db.js`
```javascript
const express = require('express');
const cors = require('cors');
const mysql = require('mysql2/promise');

const app = express();
const PORT = 3000;

// 中间件
app.use(cors());
app.use(express.json());

// 数据库配置
const dbConfig = {
    host: 'localhost',
    user: 'root',
    password: 'your_password',
    database: 'studyapp_db',
    waitForConnections: true,
    connectionLimit: 10,
    queueLimit: 0
};

// 创建数据库连接池
const pool = mysql.createPool(dbConfig);

// 健康检查
app.get('/', (req, res) => {
    res.json({
        message: 'StudyApp后端API（带数据库）',
        version: '1.0.0',
        database: '已连接'
    });
});

// 班级统计API
app.get('/api/stats/class/:classId', async (req, res) => {
    try {
        const { classId } = req.params;
        const connection = await pool.getConnection();
        
        try {
            // 这里放置上面的统计查询逻辑...
            // 使用connection.query()执行查询
            
        } finally {
            connection.release();
        }
        
    } catch (error) {
        console.error('API错误:', error);
        res.status(500).json({ error: '服务器内部错误' });
    }
});

// 启动服务器
app.listen(PORT, () => {
    console.log(`✅ 后端服务器运行中: http://localhost:${PORT}`);
    console.log(`📊 统计API: http://localhost:${PORT}/api/stats/class/1`);
});
```

## 📱 Android App配置

### 修改BASE_URL
根据你的后端部署位置，修改Android App中的BASE_URL：

**文件**: `app/src/main/java/com/example/studyapp/api/RetrofitClient.kt`

```kotlin
// 情况1：本地开发（Node.js在本地运行）
private const val BASE_URL = "http://10.0.2.2:3000"  // Android模拟器
// private const val BASE_URL = "http://192.168.1.100:3000"  // 真机，用电脑IP

// 情况2：已部署到服务器
// private const val BASE_URL = "https://your-domain.com"
```

### 测试连接
在Android App中运行ClassroomActivity，应该能看到真实的数据库数据。

## 🧪 测试步骤

1. **创建数据库和表**：
```sql
CREATE DATABASE studyapp_db;
USE studyapp_db;
-- 执行上面的CREATE TABLE语句
```

2. **启动后端**：
```bash
node server_with_db.js
```

3. **插入测试数据**：
```bash
node scripts/seed_test_data.js
```

4. **测试API**：
```bash
curl http://localhost:3000/api/stats/class/1
```

5. **运行Android App**：
- 确保BASE_URL正确
- 运行ClassroomActivity
- 查看Logcat中的网络请求

## 🔍 调试技巧

### 1. 检查网络请求
```kotlin
// RetrofitClient中已启用日志拦截器
.addInterceptor(HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.BODY
})
```

### 2. 常见错误解决

**错误**: `Unable to resolve host "10.0.2.2"`
- 原因：后端服务器未启动
- 解决：启动Node.js服务器

**错误**: `Connection refused`
- 原因：端口被占用或防火墙阻止
- 解决：检查端口3000是否可用，关闭防火墙

**错误**: `404 Not Found`
- 原因：API路径错误
- 解决：确保后端路由正确注册

**错误**: `Database connection failed`
- 原因：数据库配置错误
- 解决：检查数据库用户名、密码、权限

## 📈 性能优化建议

1. **数据库索引优化**：
```sql
-- 为常用查询字段添加索引
ALTER TABLE learning_records ADD INDEX idx_student_progress (student_id, progress);
ALTER TABLE students ADD INDEX idx_class_active (class_id, created_at);
```

2. **API缓存**：
```javascript
// 使用Redis缓存统计数据
const redis = require('redis');
const client = redis.createClient();

// 缓存5分钟
app.get('/api/stats/class/:classId', async (req, res) => {
    const cacheKey = `stats:class:${req.params.classId}`;
    
    // 尝试从缓存获取
    const cached = await client.get(cacheKey);
    if (cached) {
        return res.json(JSON.parse(cached));
    }
    
    // 从数据库查询
    const data = await getStatsFromDB(req.params.classId);
    
    // 存入缓存（5分钟）
    await client.setex(cacheKey, 300, JSON.stringify(data));
    
    res.json(data);
});
```

3. **分页查询**：
```javascript
// 如果需要更多优秀学生，支持分页
app.get('/api/stats/class/:classId/top-students', async (req, res) => {
    const { page = 1, limit = 10 } = req.query;
    const offset = (page - 1) * limit;
    
    // 分页查询...
});
```

现在你的Android App可以连接到真实的数据库后端了！