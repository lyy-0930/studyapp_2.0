# StudyApp 班级统计后端API示例

这个示例提供了完整的Node.js/Express后端API，用于对接Android App中的班级统计功能。

## 📋 功能特性

- ✅ 完整的Express.js服务器
- ✅ CORS支持（允许Android App跨域访问）
- ✅ RESTful API设计
- ✅ 模拟数据，方便测试
- ✅ 错误处理和404页面

## 🚀 快速开始

### 1. 安装依赖
```bash
cd backend_example
npm install
```

### 2. 启动服务器
```bash
# 开发模式（使用nodemon，代码变更自动重启）
npm run dev

# 生产模式
npm start
```

### 3. 测试API
服务器启动后，访问以下URL测试API：

- **健康检查**: http://localhost:3000
- **班级1统计**: http://localhost:3000/api/stats/class/1
- **班级2统计**: http://localhost:3000/api/stats/class/2
- **班级3统计**: http://localhost:3000/api/stats/class/3
- **所有班级**: http://localhost:3000/api/stats/all

## 📊 API文档

### GET /api/stats/class/:classId
获取指定班级的统计数据。

**路径参数**:
- `classId` - 班级ID（示例: 1, 2, 3）

**响应示例** (HTTP 200):
```json
{
  "totalStudents": 45,
  "activeStudents": 38,
  "averageWatchTime": 125,
  "completionRate": 78,
  "topStudents": [
    { "name": "张三", "progress": 95 },
    { "name": "李四", "progress": 88 }
  ]
}
```

**错误响应** (HTTP 404):
```json
{
  "success": false,
  "message": "班级ID 999 不存在",
  "error": "CLASS_NOT_FOUND"
}
```

## 🔧 集成到现有后端

如果你已经有Node.js/Express后端，只需添加以下路由：

```javascript
// 在你的Express应用中添加这个路由
app.get('/api/stats/class/:classId', (req, res) => {
  const { classId } = req.params;
  
  // TODO: 从你的数据库查询实际数据
  // 这里用模拟数据代替
  
  const classStats = {
    totalStudents: 45,
    activeStudents: 38,
    averageWatchTime: 125,
    completionRate: 78,
    topStudents: [
      { name: '张三', progress: 95 },
      { name: '李四', progress: 88 }
    ]
  };
  
  res.json(classStats);
});
```

## 📱 Android App配置

### 1. 修改服务器地址
根据你的运行环境，修改Android App中的服务器地址：

**文件位置**: `app/src/main/java/com/example/studyapp/api/RetrofitClient.kt`

```kotlin
// 修改BASE_URL为你实际的服务器地址
private const val BASE_URL = "http://10.0.2.2:3000"  // Android模拟器访问本地主机
// private const val BASE_URL = "http://192.168.1.100:3000"  // 真机测试，使用电脑IP
// private const val BASE_URL = "https://your-domain.com"   // 生产环境
```

**不同环境的配置**:

| 环境 | BASE_URL | 说明 |
|------|----------|------|
| Android模拟器 | `http://10.0.2.2:3000` | 模拟器访问本地主机 |
| 真机（同一WiFi） | `http://<电脑IP>:3000` | 替换`<电脑IP>`为你的电脑IP地址 |
| 生产服务器 | `https://your-domain.com` | 部署到云服务器的地址 |

### 2. 获取电脑IP地址（真机测试需要）

**Windows**:
```cmd
ipconfig
# 查找"IPv4 地址"，通常是 192.168.x.x
```

**Mac/Linux**:
```bash
ifconfig | grep "inet "
```

### 3. 防火墙设置
确保防火墙允许3000端口的入站连接：
- **Windows**: Windows Defender防火墙 -> 允许应用通过防火墙
- **Mac**: 系统偏好设置 -> 安全性与隐私 -> 防火墙

## 🧪 测试数据

示例服务器包含3个班级的模拟数据：

### 班级1 (ID: 1)
- 总学生: 45人
- 活跃学生: 38人
- 平均观看时长: 125分钟
- 完成率: 78%
- 优秀学生: 5名

### 班级2 (ID: 2)
- 总学生: 32人
- 活跃学生: 28人
- 平均观看时长: 98分钟
- 完成率: 85%
- 优秀学生: 5名

### 班级3 (ID: 3)
- 总学生: 28人
- 活跃学生: 25人
- 平均观看时长: 145分钟
- 完成率: 92%
- 优秀学生: 5名

## 🔄 自定义数据

修改`server.js`中的`classStatsDatabase`对象来自定义数据：

```javascript
const classStatsDatabase = {
  '你的班级ID': {
    totalStudents: 50,
    activeStudents: 42,
    averageWatchTime: 150,
    completionRate: 85,
    topStudents: [
      { name: '学生1', progress: 95 },
      { name: '学生2', progress: 90 }
    ]
  }
  // 添加更多班级...
};
```

## 🐛 常见问题

### 1. Android App连接失败
**可能原因**: 
- 服务器未启动
- IP地址错误
- 端口被防火墙阻止

**解决方案**:
1. 确认服务器正在运行: `http://localhost:3000`
2. 检查Android中的BASE_URL设置
3. 确保电脑和手机在同一WiFi网络
4. 关闭防火墙或添加3000端口例外

### 2. 跨域错误 (CORS)
如果出现CORS错误，确保：
- 服务器已启用CORS (`app.use(cors())`)
- 响应头包含`Access-Control-Allow-Origin: *`

### 3. 真机无法连接
**步骤**:
1. 获取电脑IP地址
2. 修改BASE_URL为`http://<电脑IP>:3000`
3. 确保手机和电脑在同一网络
4. 可能需要重启路由器和设备

## 📁 项目结构

```
backend_example/
├── package.json          # 项目依赖配置
├── server.js            # 主服务器文件
├── README.md           # 本说明文件
└── node_modules/       # 依赖包（安装后生成）
```

## 📞 支持

如果在集成过程中遇到问题：
1. 检查控制台错误信息
2. 确保服务器正在运行
3. 验证API端点可访问
4. 检查Android Logcat中的网络错误

现在你可以启动后端服务器，然后在Android App中查看真实的班级统计数据了！