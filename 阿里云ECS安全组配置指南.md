# 阿里云ECS安全组配置指南

## 问题诊断
SSH连接被拒绝（`Connection refused`），通常是因为安全组未开放22端口或ECS未启动SSH服务。

## 配置步骤

### 1. 登录阿里云控制台
- 访问 https://ecs.console.aliyun.com
- 使用您的阿里云账号登录

### 2. 进入ECS实例管理
1. 在左侧导航栏选择 **实例与镜像 > 实例**
2. 找到您的ECS实例：`120.55.72.75`
3. 点击实例ID进入详情页

### 3. 配置安全组规则
1. 在实例详情页，点击 **安全组** 标签页
2. 点击安全组ID链接进入安全组管理

**方法一：快速配置（推荐）**
1. 点击 **配置规则**
2. 点击 **快速添加**
3. 勾选 **SSH(22)**
4. 授权对象：`0.0.0.0/0`（允许所有IP访问）
5. 点击 **确定**

**方法二：手动添加规则**
1. 点击 **手动添加**
2. 规则方向：**入方向**
3. 授权策略：**允许**
4. 协议类型：**SSH(22)**
5. 端口范围：**22/22**
6. 授权对象：**0.0.0.0/0**
7. 优先级：**1**（数字越小优先级越高）
8. 描述：**SSH远程访问**
9. 点击 **保存**

### 4. 检查ECS系统防火墙
如果配置安全组后仍无法连接，可能需要检查ECS内部防火墙：

**Linux系统常用命令：**
```bash
# 查看防火墙状态
systemctl status firewalld
# 或
systemctl status ufw

# 如果使用firewalld，开放22端口
firewall-cmd --zone=public --add-port=22/tcp --permanent
firewall-cmd --reload

# 如果使用ufw（Ubuntu）
ufw allow 22/tcp
ufw reload
```

### 5. 检查SSH服务状态
```bash
# 检查SSH服务是否运行
systemctl status sshd
# 或
systemctl status ssh

# 启动SSH服务
systemctl start sshd
systemctl enable sshd
```

### 6. 验证连接
配置完成后，在本地终端测试：
```bash
ssh -o "StrictHostKeyChecking=no" root@120.55.72.75
```

## 可选：使用阿里云VNC连接
如果无法通过SSH连接，可以使用控制台VNC：

1. 在实例详情页点击 **远程连接**
2. 选择 **VNC连接**
3. 输入root密码：`Wrn20061228`
4. 通过VNC配置SSH服务

## 下一步操作
安全组配置完成后，我将：
1. 通过SSH连接到ECS
2. 检查系统环境
3. 安装必要软件（MySQL、Node.js等）
4. 部署后端代码
5. 配置数据库
6. 更新Android应用连接配置

## 安全建议
- 配置完成后，建议修改root密码
- 考虑使用SSH密钥认证替代密码
- 可以设置安全组只允许特定IP访问（如您的办公IP）
- 定期更新系统和软件

## 常见问题
1. **连接超时**：检查ECS是否运行、网络是否正常
2. **密码错误**：确认root密码正确，可通过VNC重置
3. **端口被占用**：检查22端口是否被其他程序占用
4. **SELinux限制**：临时禁用 `setenforce 0` 或配置SELinux规则

完成安全组配置后，请告诉我，我将继续部署工作。