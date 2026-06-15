# Android应用配置更新指南

## 更新目的
将Android应用从连接本地后端（`10.0.2.2:3001`）切换为连接阿里云ECS后端（`120.55.72.75:3001`）。

## 需要修改的文件

### 1. RetrofitClient.kt
**文件路径**: `app/src/main/java/com/example/studyapp/api/RetrofitClient.kt`

**修改位置**: 第19行
```kotlin
// 修改前：
private const val BASE_URL = "http://10.0.2.2:3001/"

// 修改后：
private const val BASE_URL = "http://120.55.72.75:3001/"
```

### 2. ApiService.kt  
**文件路径**: `app/src/main/java/com/example/studyapp/manager/ApiService.kt`

**修改位置**: 第293行
```kotlin
// 修改前：
private const val BASE_URL = "http://10.0.2.2:3001"

// 修改后：
private const val BASE_URL = "http://120.55.72.75:3001"
```

## 修改步骤

### 方法一：手动编辑
1. 打开Android Studio项目
2. 导航到 `app/src/main/java/com/example/studyapp/api/RetrofitClient.kt`
3. 修改第19行的BASE_URL
4. 导航到 `app/src/main/java/com/example/studyapp/manager/ApiService.kt`
5. 修改第293行的BASE_URL
6. 重新构建项目：`./gradlew clean build`

### 方法二：使用脚本（推荐）
创建更新脚本 `update-android-config.sh`：

```bash
#!/bin/bash

echo "更新Android应用配置..."

# 备份原始文件
cp app/src/main/java/com/example/studyapp/api/RetrofitClient.kt app/src/main/java/com/example/studyapp/api/RetrofitClient.kt.backup
cp app/src/main/java/com/example/studyapp/manager/ApiService.kt app/src/main/java/com/example/studyapp/manager/ApiService.kt.backup

# 更新RetrofitClient.kt
sed -i 's|http://10.0.2.2:3001/|http://120.55.72.75:3001/|g' app/src/main/java/com/example/studyapp/api/RetrofitClient.kt

# 更新ApiService.kt
sed -i 's|http://10.0.2.2:3001|http://120.55.72.75:3001|g' app/src/main/java/com/example/studyapp/manager/ApiService.kt

echo "配置更新完成！"
echo "请重新构建项目：./gradlew clean build"
```

运行脚本：
```bash
chmod +x update-android-config.sh
./update-android-config.sh
```

### 方法三：使用环境变量（高级配置）
对于更灵活的配置，可以修改为从环境变量读取：

**RetrofitClient.kt修改**：
```kotlin
private const val BASE_URL = BuildConfig.API_BASE_URL ?: "http://120.55.72.75:3001/"
```

**在app/build.gradle.kts中添加**：
```kotlin
android {
    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", '"http://120.55.72.75:3001/"')
        }
        release {
            buildConfigField("String", "API_BASE_URL", '"http://120.55.72.75:3001/"')
        }
    }
}
```

## 验证修改

### 1. 检查修改
```bash
grep -n "BASE_URL" app/src/main/java/com/example/studyapp/api/RetrofitClient.kt
grep -n "BASE_URL" app/src/main/java/com/example/studyapp/manager/ApiService.kt
```

预期输出：
```
RetrofitClient.kt:19:private const val BASE_URL = "http://120.55.72.75:3001/"
ApiService.kt:293:private const val BASE_URL = "http://120.55.72.75:3001"
```

### 2. 构建测试
```bash
./gradlew clean assembleDebug
```

### 3. 安装测试
将APK安装到设备或模拟器，测试连接：
1. 启动应用
2. 尝试登录/注册
3. 检查网络请求是否发送到 `120.55.72.75:3001`

## 注意事项

### 1. 网络权限
确保AndroidManifest.xml中包含网络权限：
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### 2. 安全配置
如果ECS使用HTTPS，需要更新为：
```kotlin
private const val BASE_URL = "https://120.55.72.75:3001/"
```

### 3. 域名配置（可选）
建议为ECS配置域名，便于维护：
```kotlin
private const val BASE_URL = "https://api.studyapp.yourdomain.com/"
```

### 4. 调试模式
开发阶段可以在RetrofitClient中启用详细日志：
```kotlin
.addInterceptor(HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.BODY
})
```

## 故障排除

### 1. 连接失败
- 检查ECS安全组是否开放3001端口
- 检查ECS防火墙配置
- 在浏览器访问 `http://120.55.72.75:3001` 测试API

### 2. SSL证书问题
如果使用HTTPS且证书不可信，可以配置OkHttpClient信任所有证书（仅限开发环境）：
```kotlin
val trustAllCerts = arrayOf<TrustManager>(...)
val sslContext = SSLContext.getInstance("SSL")
```

### 3. 跨域问题
确保后端CORS配置允许Android应用访问：
```javascript
app.use(cors({
    origin: '*', // 生产环境应限制为特定域名
    methods: ['GET', 'POST', 'PUT', 'DELETE'],
    allowedHeaders: ['Content-Type', 'Authorization']
}));
```

## 回滚方案
如果新配置有问题，可以恢复备份：
```bash
cp app/src/main/java/com/example/studyapp/api/RetrofitClient.kt.backup app/src/main/java/com/example/studyapp/api/RetrofitClient.kt
cp app/src/main/java/com/example/studyapp/manager/ApiService.kt.backup app/src/main/java/com/example/studyapp/manager/ApiService.kt
```

## 后续优化

### 1. 多环境配置
建议配置不同的构建变体：
- debug: 开发环境
- staging: 测试环境  
- release: 生产环境

### 2. 动态配置
考虑从服务器获取配置，避免硬编码IP地址。

### 3. 监控和日志
添加网络请求监控和错误日志，便于问题排查。

---

**完成配置更新后，请测试应用功能确保一切正常。**