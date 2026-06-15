# Express + MySQL 用户认证API

基于Node.js Express和MySQL的用户注册登录API，使用mysql2连接池。

## 📋 功能特性

- ✅ 用户注册 (POST /register)
- ✅ 用户登录 (POST /login) 
- ✅ MySQL连接池管理
- ✅ 完整的错误处理
- ✅ CORS跨域支持
- ✅ 健康检查端点

## 🚀 快速开始

### 1. 安装依赖
```bash
cd backend_integration
npm install
```

### 2. 配置MySQL数据库

确保MySQL服务正在运行（端口3307），并创建数据库：

```sql
CREATE DATABASE studyapp_database;
```

### 3. 修改数据库配置（如果需要）

编辑 `db.js` 文件，确认以下配置正确：

```javascript
const dbConfig = {
    host: '127.0.0.1',    // MySQL主机
    port: 3307,           // MySQL端口
    user: 'root',         // MySQL用户名
    password: '123456',   // MySQL密码
    database: 'studyapp_database'  // 数据库名
};
```

### 4. 启动服务器
```bash
# 开发模式（使用nodemon）
npm run dev

# 生产模式
npm start
```

服务器启动后访问：http://localhost:3000

## 📊 API文档

### 健康检查
```
GET /
GET /health
```

**响应示例**:
```json
{
  "success": true,
  "message": "StudyApp用户认证API",
  "version": "1.0.0",
  "endpoints": {
    "register": "POST /register",
    "login": "POST /login",
    "health": "GET /health"
  }
}
```

### 用户注册
```
POST /register
Content-Type: application/json

{
  "username": "testuser",
  "password": "test123",
  "role": "student"  // 可选，默认"student"
}
```

**成功响应 (201)**:
```json
{
  "success": true,
  "message": "注册成功",
  "data": {
    "id": 1,
    "username": "testuser",
    "role": "student",
    "createdAt": "2024-01-01T00:00:00.000Z"
  }
}
```

**错误响应**:
- 400: 缺少必要字段
- 409: 用户名已存在
- 500: 服务器内部错误

### 用户登录
```
POST /login
Content-Type: application/json

{
  "username": "testuser",
  "password": "test123"
}
```

**成功响应**:
```json
{
  "success": true,
  "message": "登录成功",
  "data": {
    "id": 1,
    "username": "testuser",
    "role": "student",
    "created_at": "2024-01-01 00:00:00"
  }
}
```

**错误响应**:
- 400: 缺少必要字段
- 401: 用户名或密码错误
- 500: 服务器内部错误

## 🔧 数据库表结构

自动创建的users表结构：

```sql
CREATE TABLE users (
    id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(100) NOT NULL,
    role VARCHAR(20) DEFAULT 'student',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## 🐛 故障排除

### 1. MySQL连接失败
**错误信息**: `ER_ACCESS_DENIED_ERROR` 或 `ECONNREFUSED`

**解决方案**:
```bash
# 检查MySQL服务状态
sudo service mysql status  # Linux/Mac
# 或
net start MySQL           # Windows

# 检查端口3307是否监听
netstat -an | grep 3307   # Linux/Mac
netstat -an | findstr 3307 # Windows
```

### 2. 数据库不存在
**错误信息**: `ER_BAD_DB_ERROR`

**解决方案**:
```sql
-- 登录MySQL
mysql -u root -p

-- 创建数据库
CREATE DATABASE studyapp_database;
```

### 3. 端口被占用
**错误信息**: `EADDRINUSE`

**解决方案**:
```bash
# 查找占用3000端口的进程
lsof -i :3000  # Mac/Linux
netstat -ano | findstr :3000  # Windows

# 修改server.js中的PORT
const PORT = 4000;
```

## 📁 项目结构

```
backend_integration/
├── package.json        # 项目依赖配置
├── db.js              # MySQL数据库连接配置
├── server.js          # Express服务器主文件
├── README.md          # 本说明文件
└── node_modules/      # 依赖包（安装后生成）
```

## 🔐 安全注意事项

⚠️ **重要**: 当前版本存储明文密码，仅适用于开发和测试环境！

**生产环境建议**:
1. 使用bcrypt对密码进行哈希处理
2. 启用HTTPS
3. 添加请求速率限制
4. 实现JWT令牌认证
5. 使用环境变量存储敏感配置

**密码哈希示例**:
```javascript
const bcrypt = require('bcrypt');
const saltRounds = 10;

// 注册时哈希密码
const hashedPassword = await bcrypt.hash(password, saltRounds);

// 登录时验证密码
const match = await bcrypt.compare(password, user.password);
```

## 📞 支持

### 测试API
```bash
# 测试注册
curl -X POST http://localhost:3000/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123","role":"student"}'

# 测试登录
curl -X POST http://localhost:3000/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123"}'
```

### 查看日志
- 服务器控制台输出所有请求日志
- MySQL查询错误会记录到控制台
- 使用 `npm run dev` 时自动重启

### 获取帮助
1. 检查MySQL服务是否运行
2. 验证数据库配置（用户名、密码、端口）
3. 查看服务器控制台错误信息
4. 确保数据库已创建

## 🚀 下一步

### 集成到现有项目
1. 将 `db.js` 和 `server.js` 复制到你的项目
2. 安装依赖: `npm install express mysql2 cors`
3. 修改数据库配置
4. 集成到现有Express路由

### 添加更多功能
- 用户信息更新
- 密码重置
- 邮箱验证
- 用户列表查询
- 角色权限管理

现在你的Express项目已经集成了MySQL用户认证功能！