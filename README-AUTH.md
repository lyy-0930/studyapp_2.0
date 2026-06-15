# StudyApp 用户注册登录系统 - 使用指南

## 系统概述
已实现完整的用户注册登录系统，支持三种身份：
- **学生** (student)
- **教师** (teacher)  
- **管理员** (admin)

## 文件清单

### 后端文件 (studyapp-backend/)
- `server-simple.js` - 简单版后端，使用JSON文件存储数据
- `users.json` - 用户数据文件（自动创建）
- `users-example.json` - 示例用户数据
- `README-simple.md` - 后端详细说明
- `POSTMAN-TEST.md` - Postman测试指南

### Android文件
- `RegisterActivity.kt` - 注册页面
- `activity_register.xml` - 注册页面布局
- 已更新 `LoginActivity.kt` - 添加注册链接
- 已更新 `activity_login.xml` - 添加注册链接
- 已更新 `ApiService.kt` - 添加register方法
- 已更新 `LoginResponse.kt` - 添加RegisterResponse和UserData.createdAt字段
- 已更新 `AndroidManifest.xml` - 注册RegisterActivity
- 已更新 `strings.xml` - 添加注册相关字符串

## 快速开始

### 第一步：启动后端服务器
```bash
cd studyapp-backend
npm install                 # 如果尚未安装依赖
node server-simple.js
```

看到以下输出表示启动成功：
```
✅ StudyApp简单版后端服务器已启动
📡 访问地址: http://localhost:3000
📚 API文档: http://localhost:3000/
```

### 第二步：测试后端接口
使用Postman或浏览器测试：
1. 打开浏览器访问 `http://localhost:3000/` 查看API文档
2. 使用Postman测试注册和登录接口（详见 `POSTMAN-TEST.md`）

**测试账号**：
- 学生：`student001` / `123456`
- 教师：`teacher001` / `123456`
- 管理员：`admin001` / `123456`

### 第三步：运行Android应用
1. 打开Android Studio
2. 构建并运行应用
3. 在登录页面点击"注册"链接跳转到注册页面
4. 注册新用户或使用测试账号登录

## 功能说明

### 注册功能
- 输入用户名、密码、确认密码
- 选择角色（学生/教师/管理员）
- 用户名唯一性检查（后端验证）
- 密码一致性检查
- 注册成功后跳转到登录页面

### 登录功能
- 输入用户名、密码
- 验证成功后根据角色跳转到不同页面：
  - 学生 → StudentActivity
  - 教师 → TeacherActivity
  - 管理员 → AdminActivity
- 保存登录状态（SharedPreferences）

### 网络配置
- **Android模拟器**：使用 `http://10.0.2.2:3000`
- **真机测试**：需要修改 `ApiService.kt` 中的 `BASE_URL` 为电脑的IP地址，如 `http://192.168.1.100:3000`

## 代码结构

### 后端API接口
```javascript
POST /register    // 用户注册
POST /login       // 用户登录
GET  /            // API文档
GET  /users       // 获取所有用户（测试用）
```

### Android网络请求
```kotlin
// 注册请求
apiService.register(username, password, role)

// 登录请求  
apiService.login(username, password)
```

### 数据模型
```kotlin
// 用户数据
data class UserData(
    val id: Int,
    val username: String,
    val role: String,
    val createdAt: String?
)

// 注册响应
data class RegisterResponse(
    val success: Boolean,
    val message: String,
    val user: UserData?
)

// 登录响应
data class LoginResponse(
    val success: Boolean,
    val message: String,
    val user: UserData?
)
```

## 测试流程

### 1. 后端测试
```bash
# 测试注册
curl -X POST http://localhost:3000/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456","role":"student"}'

# 测试登录
curl -X POST http://localhost:3000/login \
  -H "Content-Type: application/json" \
  -d '{"username":"student001","password":"123456"}'
```

### 2. Android测试
1. 运行Android应用
2. 点击"注册"链接
3. 填写注册信息：
   - 用户名：testuser
   - 密码：123456
   - 确认密码：123456
   - 选择角色：学生
4. 点击注册按钮
5. 注册成功后自动跳转到登录页面
6. 使用新账号登录
7. 验证跳转到正确的角色页面

## 常见问题

### 1. 端口冲突
如果3000端口已被占用，修改 `server-simple.js` 中的 `PORT` 变量。

### 2. 网络连接失败
- 检查后端服务器是否运行
- Android模拟器使用 `10.0.2.2`
- 真机确保手机和电脑在同一网络

### 3. 用户名已存在
注册时用户名不能重复，请尝试其他用户名。

### 4. 角色不合法
角色只能是 `student`、`teacher` 或 `admin`。

### 5. JSON解析错误
确保请求体格式正确，字段名与示例一致。

## 扩展建议

### 后端扩展
1. 添加密码加密（bcrypt）
2. 添加JWT令牌认证
3. 添加更多用户信息字段
4. 添加输入验证（邮箱、手机号）

### Android扩展
1. 添加记住密码功能
2. 添加忘记密码功能
3. 添加用户信息编辑
4. 添加Token自动刷新

## 文件说明
- `USER-AUTH-SUMMARY.md` - 完整实现总结
- `ANDROID-EXAMPLES.md` - Android详细示例代码
- `studyapp-backend/README-simple.md` - 后端详细说明

## 注意事项
1. 密码未加密存储，仅用于演示目的
2. 使用JSON文件存储，不适合高并发场景
3. 重启服务器后数据不会丢失（保存在users.json中）
4. 确保AndroidManifest.xml已添加网络权限

系统已完整实现，可以直接使用或根据需求扩展。