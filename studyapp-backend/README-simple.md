# StudyApp 简单版后端 - 用户注册登录系统

## 功能概述
1. **用户注册**：支持 student、teacher、admin 三种身份
2. **用户登录**：验证用户名密码，返回用户信息
3. **数据存储**：使用 JSON 文件存储，无需数据库

## 系统要求
- Node.js 14.0 或更高版本
- npm 或 yarn 包管理器

## 项目结构
```
studyapp-backend/
├── server-simple.js      # 主程序文件
├── users.json           # 用户数据文件（自动创建）
├── users-example.json   # 示例用户数据
├── package.json         # 项目依赖配置
├── README-simple.md     # 本说明文件
└── node_modules/        # 依赖包目录
```

## 安装和运行步骤

### 步骤1：进入后端目录
```bash
cd studyapp-backend
```

### 步骤2：安装依赖（如果尚未安装）
```bash
npm install
```

注意：如果已经安装过依赖，可以跳过此步骤。

### 步骤3：启动服务器
```bash
node server-simple.js
```

或者使用 nodemon（自动重启）：
```bash
npx nodemon server-simple.js
```

### 步骤4：验证服务器运行
看到以下输出表示启动成功：
```
==================================================
✅ StudyApp简单版后端服务器已启动
📡 访问地址: http://localhost:3000
📚 API文档: http://localhost:3000/
💾 数据存储: [完整路径]/users.json
==================================================

测试账号：
  学生: student001 / 123456
  教师: teacher001 / 123456
  管理员: admin001 / 123456

按 Ctrl+C 停止服务器
```

## API接口说明

### 1. 根路径（API文档）
- **URL**: `GET http://localhost:3000/`
- **功能**: 查看API文档和测试账号
- **响应示例**:
```json
{
  "message": "StudyApp简单版后端API运行正常",
  "version": "1.0.0",
  "description": "使用JSON文件存储数据的用户注册登录系统",
  "endpoints": [
    "POST /register - 用户注册",
    "POST /login - 用户登录"
  ],
  "testAccounts": [
    { "username": "student001", "password": "123456", "role": "student" },
    { "username": "teacher001", "password": "123456", "role": "teacher" },
    { "username": "admin001", "password": "123456", "role": "admin" }
  ],
  "note": "密码未加密，仅用于演示目的"
}
```

### 2. 用户注册接口
- **URL**: `POST http://localhost:3000/register`
- **请求体** (JSON格式):
```json
{
  "username": "newuser",
  "password": "mypassword",
  "role": "student"  // 必须是 "student", "teacher" 或 "admin"
}
```
- **成功响应** (HTTP 201):
```json
{
  "success": true,
  "message": "注册成功",
  "user": {
    "id": 4,
    "username": "newuser",
    "role": "student",
    "createdAt": "2026-04-19T12:30:00.000Z"
  }
}
```
- **错误响应**:
  - 400: 参数缺失、角色不合法、用户名已存在
  - 500: 服务器内部错误

### 3. 用户登录接口
- **URL**: `POST http://localhost:3000/login`
- **请求体** (JSON格式):
```json
{
  "username": "student001",
  "password": "123456"
}
```
- **成功响应**:
```json
{
  "success": true,
  "message": "登录成功",
  "user": {
    "id": 1,
    "username": "student001",
    "role": "student",
    "createdAt": "2026-04-19T12:00:00.000Z"
  }
}
```
- **错误响应**:
  - 400: 参数缺失
  - 401: 用户名或密码错误

### 4. 获取所有用户接口（仅测试用）
- **URL**: `GET http://localhost:3000/users`
- **功能**: 获取所有用户信息（不含密码）
- **响应**:
```json
{
  "success": true,
  "count": 3,
  "users": [
    {
      "id": 1,
      "username": "student001",
      "role": "student",
      "createdAt": "2026-04-19T12:00:00.000Z"
    },
    // ... 其他用户
  ]
}
```

## 数据文件说明

### users.json
程序自动创建和维护的用户数据文件，格式如下：
```json
{
  "users": [
    {
      "id": 1,
      "username": "student001",
      "password": "123456",
      "role": "student",
      "createdAt": "2026-04-19T12:00:00.000Z"
    }
  ]
}
```

**注意**: 实际应用中密码应该加密存储，这里为了简化演示使用明文。

## 常见问题

### 1. 端口已被占用怎么办？
如果3000端口已被占用，可以修改`server-simple.js`中的`PORT`变量，改为其他端口（如3001、8080等）。

### 2. 如何重置用户数据？
删除`users.json`文件，重启服务器会自动创建包含初始测试账号的新文件。

### 3. 跨域问题？
已配置CORS中间件，允许所有跨域请求，方便Android App调用。

### 4. 如何添加更多接口？
在`server-simple.js`中参考现有接口格式添加新的`app.post()`或`app.get()`路由。

## 停止服务器
在终端中按 `Ctrl + C` 停止服务器运行。