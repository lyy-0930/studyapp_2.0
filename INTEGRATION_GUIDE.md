# 班级统计功能 - 完整集成指南

## 📋 概述

本指南帮助你实现Android App与真实后端数据库的连接，显示真实的班级统计数据。

## 🎯 目标

1. ✅ Android App能够从真实后端获取数据
2. ✅ 后端连接到真实数据库（MySQL/PostgreSQL）
3. ✅ 显示真实的班级学习统计数据

## 🔧 准备工作

### 1. 数据库准备
- MySQL 5.7+ 或 PostgreSQL 10+
- 创建数据库：`studyapp_db`
- 确保数据库用户有权限

### 2. 后端准备
- Node.js 14+
- Express.js 4.x
- 现有后端项目

## 🚀 快速开始

### 方案A：使用完整后端示例（推荐）

```bash
# 1. 进入后端目录
cd backend_example

# 2. 安装依赖
npm install

# 3. 修改数据库配置
# 编辑 config/database.js，设置你的数据库信息

# 4. 启动服务器
node server_with_db.js

# 5. 初始化数据库（第一次运行）
# 访问 http://localhost:3000/admin/init-db (POST)
# 或使用 curl:
curl -X POST http://localhost:3000/admin/init-db

# 6. 插入测试数据
curl -X POST http://localhost:3000/admin/seed-data
```

### 方案B：集成到现有Express后端

1. **复制路由文件**：
   ```bash
   cp backend_example/routes/classStats.js your-project/routes/
   cp backend_example/config/database.js your-project/config/
   ```

2. **安装依赖**：
   ```bash
   npm install mysql2 cors
   ```

3. **在主应用中注册路由**：
   ```javascript
   // 在你的 app.js 或 server.js 中
   const classStatsRouter = require('./routes/classStats');
   app.use('/api/stats', classStatsRouter);
   ```

4. **配置数据库连接**：
   修改 `config/database.js` 中的数据库配置。

## 📱 Android App配置

### 1. 修改服务器地址

**文件**: `app/src/main/java/com/example/studyapp/api/RetrofitClient.kt`

```kotlin
// 根据你的环境选择一种：

// Android模拟器（默认）
private const val BASE_URL = "http://10.0.2.2:3000"

// 真机测试（手机和电脑同一WiFi）
// private const val BASE_URL = "http://192.168.1.100:3000" // 替换为你的电脑IP

// 生产服务器
// private const val BASE_URL = "https://your-domain.com"
```

### 2. 获取电脑IP地址（真机测试需要）

**Windows**:
```cmd
ipconfig
# 查找"无线局域网适配器 WLAN"或"以太网适配器"下的IPv4地址
```

**Mac/Linux**:
```bash
ifconfig | grep "inet "
# 或使用
ip addr show | grep "inet "
```

### 3. 修改班级ID（可选）

**文件**: `app/src/main/java/com/example/studyapp/ClassroomActivity.kt`

```kotlin
// 第47行，修改为你的班级ID
private val classId = "1"  // 改为实际的班级ID
```

## 🔍 验证连接

### 1. 后端API测试

```bash
# 测试API是否工作
curl http://localhost:3000/api/stats/class/1

# 预期响应：
{
  "totalStudents": 7,
  "activeStudents": 5,
  "averageWatchTime": 125,
  "completionRate": 78,
  "topStudents": [
    { "name": "张三", "progress": 95 },
    { "name": "李四", "progress": 88 }
  ]
}
```

### 2. Android App测试

1. 启动后端服务器
2. 运行Android App
3. 打开ClassroomActivity
4. 查看Logcat中的网络请求日志

## 🐛 故障排除

### 常见问题1：连接拒绝

**症状**: `Unable to resolve host "10.0.2.2"` 或 `Connection refused`

**解决**:
1. 检查后端是否运行：`curl http://localhost:3000`
2. 检查端口是否被占用：`netstat -an | grep 3000`
3. 检查防火墙设置

### 常见问题2：数据库连接失败

**症状**: API返回数据库错误

**解决**:
1. 检查数据库服务是否运行
2. 验证数据库配置（用户名、密码、数据库名）
3. 检查数据库权限：`GRANT ALL ON studyapp_db.* TO 'username'@'localhost';`

### 常见问题3：真机无法连接

**症状**: 模拟器可以，但真机无法连接

**解决**:
1. 确保手机和电脑在同一WiFi网络
2. 使用电脑IP地址，不是localhost
3. 关闭防火墙或添加端口3000例外
4. 重启路由器和设备

### 常见问题4：API返回404

**症状**: `404 Not Found`

**解决**:
1. 检查API路径是否正确
2. 确保路由已正确注册
3. 检查Express中间件顺序

## 📊 数据库管理

### 查看数据库数据

```sql
-- 连接MySQL
mysql -u root -p
USE studyapp_db;

-- 查看所有表
SHOW TABLES;

-- 查看班级数据
SELECT * FROM classes;

-- 查看学生数据
SELECT * FROM students;

-- 查看学习记录
SELECT * FROM learning_records;
```

### 添加真实数据

```sql
-- 1. 添加新班级
INSERT INTO classes (name, teacher_id, description)
VALUES ('Java高级班', 2, 'Java编程高级课程');

-- 2. 添加学生
INSERT INTO students (class_id, name, student_number)
VALUES (1, '新学生', '2024008');

-- 3. 添加学习记录
INSERT INTO learning_records 
(student_id, course_id, watch_time, progress, last_watch_time)
VALUES (8, 1, 150, 85, NOW());
```

## 🔄 高级配置

### 1. 使用环境变量

创建 `.env` 文件：

```env
DB_HOST=localhost
DB_PORT=3306
DB_USER=root
DB_PASSWORD=your_password
DB_NAME=studyapp_db
NODE_ENV=development
```

修改 `config/database.js`：

```javascript
const dbConfig = {
    host: process.env.DB_HOST,
    port: process.env.DB_PORT,
    user: process.env.DB_USER,
    password: process.env.DB_PASSWORD,
    database: process.env.DB_NAME
};
```

### 2. 使用连接池（已配置）

默认配置了连接池，支持高并发：

```javascript
{
    connectionLimit: 10,      // 最大连接数
    waitForConnections: true, // 等待可用连接
    queueLimit: 0             // 无队列限制
}
```

### 3. 添加API认证（可选）

```javascript
// 在路由中添加JWT验证
const jwt = require('jsonwebtoken');

router.get('/class/:classId', authenticateToken, async (req, res) => {
    // ... 原有逻辑
});

function authenticateToken(req, res, next) {
    const authHeader = req.headers['authorization'];
    const token = authHeader && authHeader.split(' ')[1];
    
    if (!token) {
        return res.status(401).json({ error: '需要认证' });
    }
    
    jwt.verify(token, process.env.ACCESS_TOKEN_SECRET, (err, user) => {
        if (err) return res.status(403).json({ error: '令牌无效' });
        req.user = user;
        next();
    });
}
```

## 📈 性能优化

### 1. 数据库索引

```sql
-- 为常用查询字段添加索引
ALTER TABLE learning_records 
ADD INDEX idx_student_progress (student_id, progress);

ALTER TABLE learning_records
ADD INDEX idx_last_watch_progress (last_watch_time, progress);
```

### 2. API缓存

```javascript
const redis = require('redis');
const client = redis.createClient();

// 缓存5分钟
app.get('/api/stats/class/:classId', async (req, res) => {
    const cacheKey = `stats:class:${req.params.classId}`;
    
    // 尝试从Redis获取
    const cached = await client.get(cacheKey);
    if (cached) {
        return res.json(JSON.parse(cached));
    }
    
    // 从数据库查询
    const data = await getStatsFromDB(req.params.classId);
    
    // 存入Redis，5分钟过期
    await client.setex(cacheKey, 300, JSON.stringify(data));
    
    res.json(data);
});
```

## 🧪 测试计划

### 单元测试
1. ✅ 数据库连接测试
2. ✅ API端点测试
3. ✅ 错误处理测试

### 集成测试
1. ✅ Android App与后端通信
2. ✅ 数据库查询性能
3. ✅ 并发请求处理

### 用户验收测试
1. ✅ 数据准确显示
2. ✅ 刷新功能正常
3. ✅ 错误提示友好

## 📞 支持

### 获取帮助
1. 检查后端控制台输出
2. 查看Android Logcat日志
3. 测试API端点：`curl http://localhost:3000/api/stats/class/1`

### 日志位置
- **后端日志**: 控制台输出
- **Android日志**: Logcat中搜索"Retrofit"或"IOException"
- **数据库日志**: MySQL错误日志

### 调试技巧
```bash
# 开启详细日志
export DEBUG=express:*

# 查看网络请求
adb logcat | grep -E "(Retrofit|OkHttp|IOException)"

# 测试API响应时间
time curl http://localhost:3000/api/stats/class/1
```

## 🎉 完成检查

完成以下检查，确保集成成功：

- [ ] 后端服务器运行正常
- [ ] 数据库连接成功
- [ ] API返回正确JSON格式
- [ ] Android App BASE_URL配置正确
- [ ] 班级数据显示正常
- [ ] 刷新功能工作
- [ ] 错误处理正常

现在你的Android App应该显示真实的数据库数据了！