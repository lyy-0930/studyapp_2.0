# Windows ECS部署指南

## 当前状况
- ECS实例：120.55.72.75（Windows Server）
- 目标：部署StudyApp Node.js后端和MySQL数据库
- 本地环境：Node.js + MySQL 3307端口

## 部署前检查

### 1. 查看Windows版本
在VNC终端中运行：
```cmd
systeminfo | findstr /B /C:"OS Name" /C:"OS Version"
```
或
```cmd
winver
```

### 2. 检查现有软件
```cmd
# 检查Node.js
node --version

# 检查npm
npm --version

# 检查MySQL
mysql --version
```

## 第一阶段：安装必要软件

### 1. 安装MySQL for Windows
**下载地址**: https://dev.mysql.com/downloads/installer/

**安装步骤**:
1. 下载MySQL Installer for Windows
2. 运行安装程序，选择"Custom"自定义安装
3. 选择MySQL Server 8.0+
4. **重要**: 配置端口为`3307`（非默认3306）
5. 设置root密码（建议使用强密码）
6. 完成安装并启动MySQL服务

**PowerShell验证**:
```powershell
# 检查MySQL服务
Get-Service -Name MySQL*

# 测试连接
mysql -u root -P 3307 -p
```

### 2. 安装Node.js for Windows
**下载地址**: https://nodejs.org/en/download/

**安装步骤**:
1. 下载Node.js Windows Installer (.msi)
2. 运行安装程序，选择默认选项
3. 确保勾选"Add to PATH"

**PowerShell验证**:
```powershell
node --version
npm --version
```

### 3. 安装Git for Windows（可选，用于代码克隆）
**下载地址**: https://git-scm.com/download/win

## 第二阶段：准备应用目录

### 1. 创建应用目录
```powershell
# 以管理员身份运行PowerShell
New-Item -ItemType Directory -Path "C:\StudyApp-Backend" -Force
Set-Location "C:\StudyApp-Backend"
```

### 2. 上传后端代码
将本地`studyapp-backend`目录下的所有文件上传到`C:\StudyApp-Backend`。

**上传方法**:
- 使用阿里云控制台的文件上传功能
- 或使用SCP工具（如WinSCP）
- 或从Git仓库克隆

## 第三阶段：配置数据库连接

### 1. 修改db.js配置
打开`C:\StudyApp-Backend\db.js`，修改连接配置：
```javascript
const dbConfig = {
    host: '127.0.0.1',       // Windows本地
    port: 3307,              // MySQL端口（注意：3307不是默认3306）
    user: 'root',            // 数据库用户名
    password: '您的MySQL密码', // 安装时设置的密码
    database: 'studyapp_database',
    // ... 其他配置保持不变
};
```

### 2. 创建数据库
```powershell
# 连接MySQL（使用3307端口）
mysql -u root -P 3307 -p

# 在MySQL命令行中执行
CREATE DATABASE IF NOT EXISTS studyapp_database CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
EXIT;
```

## 第四阶段：安装依赖和启动服务

### 1. 安装Node.js依赖
```powershell
cd C:\StudyApp-Backend
npm install
```

### 2. 安装PM2 for Windows（进程管理）
```powershell
npm install -g pm2
npm install -g pm2-windows-startup
```

### 3. 启动后端服务
```powershell
# 使用PM2启动
pm2 start server.js --name "studyapp-backend"

# 保存PM2配置
pm2 save

# 配置PM2开机自启
pm2-startup install
```

### 4. 使用Windows服务（替代PM2方案）
```powershell
# 安装nssm（Windows服务管理器）
choco install nssm -y

# 创建Windows服务
nssm install StudyAppBackend "C:\Program Files\nodejs\node.exe" "C:\StudyApp-Backend\server.js"
nssm set StudyAppBackend AppDirectory "C:\StudyApp-Backend"
nssm set StudyAppBackend AppStdout "C:\StudyApp-Backend\app.log"
nssm set StudyAppBackend AppStderr "C:\StudyApp-Backend\error.log"

# 启动服务
Start-Service StudyAppBackend
```

## 第五阶段：配置防火墙

### 开放3001端口（API服务）
```powershell
# 开放入站端口
New-NetFirewallRule -DisplayName "StudyApp API Port 3001" -Direction Inbound -Protocol TCP -LocalPort 3001 -Action Allow

# 验证规则
Get-NetFirewallRule -DisplayName "StudyApp API Port 3001"
```

### 开放3307端口（仅本地访问，可选）
```powershell
# 限制为本地访问（127.0.0.1）
New-NetFirewallRule -DisplayName "MySQL 3307 Local Only" -Direction Inbound -Protocol TCP -LocalPort 3307 -RemoteAddress 127.0.0.1 -Action Allow
```

## 第六阶段：数据库迁移

### 1. 本地导出数据
```bash
# 在您的本地计算机上执行
mysqldump -u root -p123456 --port=3307 studyapp_database > studyapp_backup.sql
```

### 2. 上传备份文件到ECS
使用阿里云控制台文件上传或WinSCP将`studyapp_backup.sql`上传到ECS。

### 3. ECS导入数据
```powershell
# 在ECS的PowerShell中执行
mysql -u root -P 3307 -p studyapp_database < C:\路径\studyapp_backup.sql
```

### 4. 验证数据
```powershell
mysql -u root -P 3307 -p -e "SHOW TABLES FROM studyapp_database;"
```

## 第七阶段：Android应用更新

### 修改Android应用配置
更新以下文件中的BASE_URL：

1. `app/src/main/java/com/example/studyapp/api/RetrofitClient.kt` 第19行：
```kotlin
private const val BASE_URL = "http://120.55.72.75:3001/"
```

2. `app/src/main/java/com/example/studyapp/manager/ApiService.kt` 第293行：
```kotlin
private const val BASE_URL = "http://120.55.72.75:3001"
```

### 构建Android应用
```bash
./gradlew clean assembleDebug
```

## 第八阶段：验证部署

### 1. 检查服务状态
```powershell
# 检查PM2状态
pm2 status

# 检查Windows服务状态
Get-Service -Name StudyAppBackend

# 检查端口监听
netstat -ano | findstr :3001
```

### 2. 测试API连接
```powershell
# 在ECS本地测试
curl http://localhost:3001/

# 或使用浏览器访问
# http://120.55.72.75:3001
```

### 3. 测试数据库连接
```powershell
# 测试MySQL连接
mysql -u root -P 3307 -p -e "SELECT 1;"
```

### 4. 端到端测试
1. 安装Android APK到手机
2. 测试登录/注册功能
3. 验证数据同步

## 故障排除

### 常见问题1：MySQL连接失败
```powershell
# 检查MySQL服务状态
Get-Service -Name MySQL*

# 检查端口监听
netstat -ano | findstr :3307

# 检查MySQL错误日志（通常在C:\ProgramData\MySQL\MySQL Server 8.0\Data\<hostname>.err）
```

### 常见问题2：Node.js服务无法启动
```powershell
# 查看PM2日志
pm2 logs studyapp-backend

# 检查Node.js错误
cd C:\StudyApp-Backend
node server.js
```

### 常见问题3：防火墙阻止连接
```powershell
# 检查防火墙规则
Get-NetFirewallRule -DisplayName "*StudyApp*"

# 临时禁用防火墙测试
Set-NetFirewallProfile -Profile Domain,Public,Private -Enabled False
# 测试后记得重新启用
```

## 安全配置建议

### 1. 修改ECS密码
- 立即修改root/administrator密码
- 使用强密码策略

### 2. 配置远程桌面限制
- 限制RDP访问IP范围
- 启用网络级别身份验证

### 3. MySQL安全加固
```sql
-- 创建应用专用用户
CREATE USER 'studyapp_user'@'localhost' IDENTIFIED BY '强密码';
GRANT ALL PRIVILEGES ON studyapp_database.* TO 'studyapp_user'@'localhost';
FLUSH PRIVILEGES;
```

### 4. 定期更新
- 启用Windows自动更新
- 定期更新Node.js和npm包

## 备份策略

### 1. 数据库备份
```powershell
# 创建备份脚本 backup-db.ps1
$backupDir = "C:\Backups\MySQL"
$date = Get-Date -Format "yyyy-MM-dd"
mysqldump -u root -P 3307 -p密码 studyapp_database > "$backupDir\studyapp_$date.sql"

# 添加到计划任务（每天凌晨2点）
$action = New-ScheduledTaskAction -Execute "PowerShell.exe" -Argument "-File C:\Scripts\backup-db.ps1"
$trigger = New-ScheduledTaskTrigger -Daily -At 2am
Register-ScheduledTask -TaskName "StudyApp DB Backup" -Action $action -Trigger $trigger -User "SYSTEM"
```

### 2. 代码备份
- 使用Git版本控制
- 定期推送到远程仓库

## 监控和维护

### 1. 资源监控
```powershell
# 查看系统资源
Get-Process | Sort-Object CPU -Descending | Select-Object -First 10
Get-Process | Sort-Object WorkingSet -Descending | Select-Object -First 10
```

### 2. 日志监控
- PM2日志：`pm2 logs studyapp-backend`
- Windows事件查看器
- MySQL错误日志

---

## 快速开始脚本（PowerShell）

创建文件 `C:\deploy-studyapp.ps1`：
```powershell
# StudyApp Windows部署脚本
Write-Host "开始部署StudyApp到Windows ECS..." -ForegroundColor Green

# 1. 创建应用目录
New-Item -ItemType Directory -Path "C:\StudyApp-Backend" -Force
Set-Location "C:\StudyApp-Backend"

# 2. 安装Node.js依赖
Write-Host "安装Node.js依赖..." -ForegroundColor Yellow
npm install

# 3. 安装PM2
Write-Host "安装PM2..." -ForegroundColor Yellow
npm install -g pm2
npm install -g pm2-windows-startup

# 4. 启动服务
Write-Host "启动StudyApp后端服务..." -ForegroundColor Yellow
pm2 start server.js --name "studyapp-backend"
pm2 save
pm2-startup install

# 5. 配置防火墙
Write-Host "配置防火墙规则..." -ForegroundColor Yellow
New-NetFirewallRule -DisplayName "StudyApp API Port 3001" -Direction Inbound -Protocol TCP -LocalPort 3001 -Action Allow

Write-Host "部署完成！" -ForegroundColor Green
Write-Host "API地址: http://120.55.72.75:3001" -ForegroundColor Cyan
```

**运行脚本**：
```powershell
# 以管理员身份运行PowerShell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope Process -Force
.\deploy-studyapp.ps1
```

---

**重要提示**：完成部署后，请立即修改所有默认密码，配置适当的安全策略。