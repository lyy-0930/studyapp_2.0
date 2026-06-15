# 学习统计系统测试指南

## 1. 环境准备

### 1.1 安装Node.js（如未安装）
- 访问 https://nodejs.org/ 下载安装Node.js 18+版本
- 安装完成后验证：
  ```bash
  node --version
  npm --version
  ```

### 1.2 安装MySQL（如未安装）
- 确保MySQL服务运行在端口3307（可在db.js中修改配置）
- 创建数据库：
  ```sql
  CREATE DATABASE IF NOT EXISTS studyapp_database;
  ```

## 2. 数据库初始化

### 方法A：使用Node.js脚本（推荐）
```bash
cd studyapp-backend
npm install
npm run init-mysql       # 创建数据库和users表
npm run init-tables      # 创建其他表
```

### 方法B：手动执行SQL
1. 运行 `init-mysql.sql` 创建数据库和users表
2. 运行 `init-tables.sql` 创建其他表

## 3. 启动后端服务器

```bash
cd studyapp-backend
npm install   # 如果尚未安装依赖
npm start     # 或 npm run dev（使用nodemon热重载）
```

服务器启动后：
- 访问 http://localhost:3000/ 查看API文档
- 访问 http://localhost:3000/health 检查健康状态

## 4. 创建测试数据

### 4.1 创建教师账号
```bash
curl -X POST http://localhost:3000/register \
  -H "Content-Type: application/json" \
  -d '{"username":"teacher_test","password":"123456","role":"teacher"}'
```

### 4.2 创建学生账号
```bash
curl -X POST http://localhost:3000/register \
  -H "Content-Type: application/json" \
  -d '{"username":"student_test","password":"123456","role":"student"}'
```

### 4.3 创建课程
```bash
curl -X POST http://localhost:3000/courses \
  -H "Content-Type: application/json" \
  -d '{
    "name":"测试课程",
    "description":"课程描述",
    "teacherId":1,
    "teacherName":"teacher_test",
    "credit":2
  }'
```

### 4.4 学生选课
```bash
curl -X POST http://localhost:3000/courses/1/enroll \
  -H "Content-Type: application/json" \
  -d '{
    "studentId":2,
    "studentName":"student_test"
  }'
```

### 4.5 记录学习进度
```bash
curl -X POST http://localhost:3000/study/record \
  -H "Content-Type: application/json" \
  -d '{
    "courseId":1,
    "studentId":2,
    "watchTime":30,
    "progress":50
  }'
```

## 5. 测试教师统计API

### 5.1 通过教师ID查询
```bash
curl "http://localhost:3000/teacher/stats?teacherId=1"
```

### 5.2 通过用户名查询
```bash
curl "http://localhost:3000/teacher/stats?username=teacher_test"
```

预期响应格式：
```json
{
  "success": true,
  "message": "获取教师统计数据成功",
  "data": {
    "teacherId": 1,
    "teacherName": "teacher_test",
    "totalCourses": 1,
    "totalStudents": 1,
    "totalWatchTime": 30,
    "averageWatchDuration": 30.0,
    "averageProgress": 50.0,
    "courses": [...]
  }
}
```

## 6. Android应用测试

### 6.1 确保后端服务器运行
- 服务器地址：`http://10.0.2.2:3000`（Android模拟器）
- 真机测试使用电脑IP地址

### 6.2 测试流程
1. 教师账号登录
2. 进入"数据统计"功能
3. 预期行为：
   - 有课程：显示真实统计数据
   - 无课程：显示"未上传教学视频"提示

### 6.3 故障排除

#### 问题：连接失败
- 检查服务器是否运行：`curl http://localhost:3000/health`
- 检查Android网络权限：`<uses-permission android:name="android.permission.INTERNET" />`
- 模拟器使用 `10.0.2.2` 访问本地主机

#### 问题：数据库错误
- 检查MySQL服务状态
- 确认数据库表已创建
- 查看服务器日志

#### 问题：API返回错误
- 检查请求参数格式
- 查看服务器控制台输出

## 7. 验证完整功能

### 7.1 教师创建课程 → 统计数据更新
1. 教师创建新课程
2. 学生选课
3. 学生学习记录
4. 教师统计数据实时更新

### 7.2 多教师场景
- 每位教师只能看到自己的课程数据
- 统计数据基于各自课程计算

## 8. 清理测试数据

```sql
-- 删除测试数据
DELETE FROM study_records;
DELETE FROM course_enrollments;
DELETE FROM courses;
DELETE FROM users WHERE username LIKE '%_test';
```

## 支持

遇到问题请检查：
1. 服务器控制台错误信息
2. Android Logcat网络请求日志
3. 数据库连接状态