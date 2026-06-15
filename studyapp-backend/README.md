# StudyApp 后端API服务

这是一个为Android学习应用提供后端支持的Node.js API服务。

## 功能特性

- ✅ 用户登录认证
- ✅ 课程管理
- ✅ 视频数据管理
- ✅ 用户选课关系
- ✅ RESTful API接口
- ✅ SQLite数据库

## 快速开始

### 1. 安装依赖

确保已安装 [Node.js](https://nodejs.org/) (版本14.0或更高)

```bash
# 进入项目目录
cd studyapp-backend

# 安装依赖包
npm install
```

### 2. 初始化数据库

```bash
# 创建数据库表并插入测试数据
npm run init-db
```

### 3. 启动服务器

```bash
# 启动后端API服务器
npm start
```

服务器启动后，访问 http://localhost:3000 查看API文档。

## API接口

### 1. 用户登录
- **URL**: `POST /login`
- **参数**: `{ "username": "student001", "password": "123456" }`
- **响应**: 用户信息和登录状态

### 2. 获取用户课程
- **URL**: `GET /getCourses?userId=1`
- **参数**: `userId` (用户ID)
- **响应**: 用户已选的课程列表

### 3. 获取课程视频
- **URL**: `GET /getVideosByCourse?courseId=1`
- **参数**: `courseId` (课程ID)
- **响应**: 课程的视频列表

## 测试账号

| 用户名 | 密码 | 角色 | 说明 |
|--------|------|------|------|
| student001 | 123456 | 学生 | 选了3门课程 |
| student002 | 123456 | 学生 | 选了1门课程 |
| teacher001 | 123456 | 教师 | 可上传视频 |

## 数据库结构

### 用户表 (users)
- `id`: 用户ID (主键)
- `username`: 用户名 (唯一)
- `password`: 密码
- `role`: 角色 (student/teacher)
- `created_at`: 创建时间

### 课程表 (courses)
- `id`: 课程ID (主键)
- `name`: 课程名称
- `description`: 课程描述
- `teacher`: 授课教师
- `credit`: 学分
- `created_at`: 创建时间

### 视频表 (videos)
- `id`: 视频ID (主键)
- `course_id`: 课程ID (外键)
- `title`: 视频标题
- `description`: 视频描述
- `video_url`: 视频URL (阿里云OSS地址)
- `teacher`: 上传教师
- `upload_time`: 上传时间

### 用户-课程关联表 (user_courses)
- `user_id`: 用户ID (外键)
- `course_id`: 课程ID (外键)
- `selected_at`: 选课时间
- 复合主键: (user_id, course_id)

## Android客户端集成

### 1. 添加网络权限
在 `AndroidManifest.xml` 中添加:
```xml
<uses-permission android:name="android.permission.INTERNET" />
```

### 2. 添加依赖
在 `app/build.gradle` 中添加:
```gradle
dependencies {
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    implementation 'com.google.code.gson:gson:2.10.1'
}
```

### 3. API服务类
参考 `ApiService.kt` 示例代码进行API调用。

### 4. 网络配置
- **Android模拟器**: 使用 `http://10.0.2.2:3000`
- **真机测试**: 使用电脑的IP地址，如 `http://192.168.1.100:3000`

## 开发命令

```bash
# 安装依赖
npm install

# 初始化数据库
npm run init-db

# 启动开发服务器
npm start

# 使用nodemon热重载（需要先安装）
npm install -g nodemon
npm run dev
```

## 故障排除

### 1. 端口被占用
如果3000端口被占用，可以在 `server.js` 中修改 `PORT` 常量。

### 2. 数据库连接失败
确保 `db/` 目录存在且可写。

### 3. Android无法连接
- 检查防火墙是否允许3000端口
- 确保手机和电脑在同一个网络
- 模拟器使用 `10.0.2.2`，真机使用电脑IP

### 4. 跨域问题
已在代码中配置CORS，如果仍有问题检查CORS配置。

## 后续扩展建议

1. **安全性增强**
   - 密码加密存储 (bcrypt)
   - JWT令牌认证
   - 输入验证和消毒

2. **功能扩展**
   - 用户注册
   - 课程搜索
   - 视频上传接口
   - 学习进度跟踪

3. **性能优化**
   - 数据库索引
   - API响应缓存
   - 连接池管理

## 许可证

MIT License