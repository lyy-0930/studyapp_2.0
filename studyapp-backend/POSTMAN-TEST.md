# Postman 测试指南 - StudyApp 用户注册登录系统

## 准备工作
1. 确保后端服务器已启动（运行 `node server-simple.js`）
2. 打开 Postman 应用程序
3. 服务器地址：`http://localhost:3000`

## 测试接口列表

### 1. 查看API文档
- **请求方法**: GET
- **URL**: `http://localhost:3000/`
- **描述**: 查看所有可用接口和测试账号
- **预期响应**: 200 OK，显示API文档

**请求截图**:
```
GET http://localhost:3000/
```

**响应示例**:
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

### 2. 用户注册测试

#### 测试用例1: 成功注册新学生
- **请求方法**: POST
- **URL**: `http://localhost:3000/register`
- **Headers**:
  ```
  Content-Type: application/json
  ```
- **请求体** (Body -> raw -> JSON):
```json
{
  "username": "student_test",
  "password": "123456",
  "role": "student"
}
```

- **预期响应**: 201 Created
```json
{
  "success": true,
  "message": "注册成功",
  "user": {
    "id": 4,
    "username": "student_test",
    "role": "student",
    "createdAt": "2026-04-19T12:30:00.000Z"
  }
}
```

#### 测试用例2: 注册教师账号
- **请求方法**: POST
- **URL**: `http://localhost:3000/register`
- **请求体**:
```json
{
  "username": "teacher_test",
  "password": "654321",
  "role": "teacher"
}
```

#### 测试用例3: 注册管理员账号
- **请求方法**: POST
- **URL**: `http://localhost:3000/register`
- **请求体**:
```json
{
  "username": "admin_test",
  "password": "admin123",
  "role": "admin"
}
```

#### 测试用例4: 错误测试 - 用户名已存在
- **请求方法**: POST
- **URL**: `http://localhost:3000/register`
- **请求体**:
```json
{
  "username": "student001",  // 已存在的用户名
  "password": "123456",
  "role": "student"
}
```
- **预期响应**: 400 Bad Request
```json
{
  "success": false,
  "message": "用户名已存在，请选择其他用户名"
}
```

#### 测试用例5: 错误测试 - 角色不合法
- **请求方法**: POST
- **URL**: `http://localhost:3000/register`
- **请求体**:
```json
{
  "username": "testuser",
  "password": "123456",
  "role": "manager"  // 错误角色，只能是 student/teacher/admin
}
```
- **预期响应**: 400 Bad Request
```json
{
  "success": false,
  "message": "角色只能是 student、teacher 或 admin"
}
```

#### 测试用例6: 错误测试 - 参数缺失
- **请求方法**: POST
- **URL**: `http://localhost:3000/register`
- **请求体**:
```json
{
  "username": "testuser"
  // 缺少 password 和 role
}
```
- **预期响应**: 400 Bad Request
```json
{
  "success": false,
  "message": "用户名、密码和角色都不能为空"
}
```

### 3. 用户登录测试

#### 测试用例1: 使用已有账号登录
- **请求方法**: POST
- **URL**: `http://localhost:3000/login`
- **Headers**:
  ```
  Content-Type: application/json
  ```
- **请求体**:
```json
{
  "username": "student001",
  "password": "123456"
}
```

- **预期响应**: 200 OK
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

#### 测试用例2: 登录教师账号
- **请求方法**: POST
- **URL**: `http://localhost:3000/login`
- **请求体**:
```json
{
  "username": "teacher001",
  "password": "123456"
}
```

#### 测试用例3: 登录管理员账号
- **请求方法**: POST
- **URL**: `http://localhost:3000/login`
- **请求体**:
```json
{
  "username": "admin001",
  "password": "123456"
}
```

#### 测试用例4: 错误测试 - 用户名密码错误
- **请求方法**: POST
- **URL**: `http://localhost:3000/login`
- **请求体**:
```json
{
  "username": "student001",
  "password": "wrongpassword"  // 错误密码
}
```
- **预期响应**: 401 Unauthorized
```json
{
  "success": false,
  "message": "用户名或密码错误"
}
```

#### 测试用例5: 错误测试 - 用户不存在
- **请求方法**: POST
- **URL**: `http://localhost:3000/login`
- **请求体**:
```json
{
  "username": "nonexistent",
  "password": "123456"
}
```
- **预期响应**: 401 Unauthorized
```json
{
  "success": false,
  "message": "用户名或密码错误"
}
```

#### 测试用例6: 错误测试 - 参数缺失
- **请求方法**: POST
- **URL**: `http://localhost:3000/login`
- **请求体**:
```json
{
  "username": "student001"
  // 缺少 password
}
```
- **预期响应**: 400 Bad Request
```json
{
  "success": false,
  "message": "用户名和密码不能为空"
}
```

### 4. 查看所有用户（测试用）

- **请求方法**: GET
- **URL**: `http://localhost:3000/users`
- **描述**: 查看所有已注册用户（不含密码）
- **预期响应**: 200 OK
```json
{
  "success": true,
  "count": 4,
  "users": [
    {
      "id": 1,
      "username": "student001",
      "role": "student",
      "createdAt": "2026-04-19T12:00:00.000Z"
    },
    {
      "id": 2,
      "username": "teacher001",
      "role": "teacher",
      "createdAt": "2026-04-19T12:00:00.000Z"
    },
    {
      "id": 3,
      "username": "admin001",
      "role": "admin",
      "createdAt": "2026-04-19T12:00:00.000Z"
    },
    {
      "id": 4,
      "username": "student_test",
      "role": "student",
      "createdAt": "2026-04-19T12:30:00.000Z"
    }
  ]
}
```

## Postman 环境配置（可选）

### 1. 创建环境变量
1. 点击右上角的 "Environments"
2. 点击 "Add" 创建新环境
3. 添加变量：
   - `base_url`: `http://localhost:3000`
4. 保存并选择该环境

### 2. 使用环境变量
在请求URL中使用：`{{base_url}}/login`

### 3. 创建测试集合
1. 点击 "Collections" 标签
2. 点击 "New Collection"
3. 命名为 "StudyApp API Tests"
4. 添加文件夹：
   - 注册测试
   - 登录测试
   - 其他测试

## 自动化测试（可选）

### 在Postman中编写测试脚本
在 "Tests" 标签页中编写JavaScript测试：

```javascript
// 测试注册接口
pm.test("注册成功 - 状态码应为201", function () {
    pm.response.to.have.status(201);
});

pm.test("注册成功 - 响应包含success字段", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData.success).to.be.true;
});

// 测试登录接口
pm.test("登录成功 - 状态码应为200", function () {
    pm.response.to.have.status(200);
});

pm.test("登录成功 - 返回用户信息", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData.user).to.be.an('object');
    pm.expect(jsonData.user.username).to.eql("student001");
});
```

## 常见问题排查

### 1. 服务器未启动
**症状**: Connection refused
**解决方案**: 运行 `node server-simple.js`

### 2. 端口冲突
**症状**: Address already in use
**解决方案**: 修改 server-simple.js 中的 PORT 变量

### 3. JSON格式错误
**症状**: 400 Bad Request
**解决方案**: 检查请求体JSON格式是否正确

### 4. 跨域问题
**症状**: CORS error
**解决方案**: 服务器已配置CORS，确保URL正确

## 测试流程建议

1. 首先测试 `GET /` 确认服务器运行正常
2. 测试注册接口，创建几个测试账号
3. 测试登录接口，验证注册的账号
4. 测试错误情况，确保边界条件处理正确
5. 最后测试 `GET /users` 查看所有用户

## 导出测试集合
1. 在集合上点击右键
2. 选择 "Export"
3. 选择 v2.1 格式
4. 保存为 JSON 文件