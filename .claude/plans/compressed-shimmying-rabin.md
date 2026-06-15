# 修复数据统计无法加载的问题

## Context

管理员界面的仪表盘统计数据（活跃度排行、学习统计、课程掌握度）和教师界面的数据统计都显示加载失败。经过排查，确认问题出在**后端服务器**，Android 代码本身没有问题。

## Root Cause

后端服务器 `http://120.55.72.75:3001` 的以下 API 端点返回 `{"success": false}`：

| 端点 | 响应 |
|---|---|
| `GET /admin/course-mastery-stats` | `{"success":false,"message":"获取课程掌握度统计失败"}` |
| `GET /admin/activity-ranking` | `{"success":false,"message":"获取活跃度排行榜失败"}` |
| `GET /admin/learning-stats` | `{"success":false,"message":"获取学生学习统计失败"}` |
| `GET /teacher/stats?username=...` | `{"success":false,"message":"获取教师统计数据失败"}` |

唯一正常工作的端点：`GET /admin/online-users`

所有失败的端点都需要查询 `study_records`、`course_enrollments`、`quiz_attempts` 等数据库表，而正常的端点只查询 `users` 表。这意味着**生产环境的 MySQL 数据库可能缺失这些表**。

## Android 端状态（无需修改）

- `AdminActivity.kt` 的 `loadDashboardStats()` 方法正确调用 API，处理失败时显示 `--`
- `StatisticsFragment.kt` 正确调用 Retrofit API，失败时显示"数据加载失败"
- `ApiService.kt` 正确检查 `apiResponse.success` 并返回 `Result.failure`
- **之前删除监控面板的改动与此问题无关**

## 排查步骤

1. **检查后端服务器日志**：在服务器运行 `server.js` 的终端查看错误输出，定位具体 SQL 错误
2. **连接到 MySQL 数据库**（`127.0.0.1:3307`，数据库 `studyapp_database`，用户 `root`）：
   ```sql
   SHOW TABLES;
   ```
   确认是否存在 `study_records`、`course_enrollments`、`quiz_attempts` 表
3. **如缺少表，创建对应的表结构**，参考 `database.js` 中的 SQLite 表定义
4. **重启后端服务器**
5. **验证**：重新运行 Android 应用，检查管理员和教师界面的数据统计

## 无需修改的文件

- `app/src/main/java/com/example/studyapp/AdminActivity.kt`
- `app/src/main/java/com/example/studyapp/StatisticsFragment.kt`
- `app/src/main/java/com/example/studyapp/manager/ApiService.kt`
- `app/src/main/res/layout/activity_admin.xml`