#!/bin/bash

# ============================================
# 阿里云ECS部署脚本 - StudyApp后端服务
# 功能：自动部署Node.js后端和MySQL数据库到ECS
# 使用：chmod +x deploy-ecs.sh && ./deploy-ecs.sh
# ============================================

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# ============================================
# 1. 系统检查和更新
# ============================================
log_info "开始部署StudyApp后端到阿里云ECS"
log_info "系统信息: $(uname -a)"
log_info "当前用户: $(whoami)"

# 检查是否为root用户
if [[ $EUID -ne 0 ]]; then
    log_error "请使用root用户运行此脚本"
    exit 1
fi

# 更新系统包
log_info "更新系统包..."
if command -v apt &> /dev/null; then
    apt update && apt upgrade -y
elif command -v yum &> /dev/null; then
    yum update -y
elif command -v dnf &> /dev/null; then
    dnf update -y
else
    log_warn "无法识别的包管理器，跳过系统更新"
fi

# ============================================
# 2. 安装必要软件
# ============================================
log_info "安装必要软件..."

# 安装MySQL
if ! command -v mysql &> /dev/null; then
    log_info "安装MySQL..."
    if command -v apt &> /dev/null; then
        apt install -y mysql-server mysql-client
    elif command -v yum &> /dev/null; then
        yum install -y mysql-server mysql
    elif command -v dnf &> /dev/null; then
        dnf install -y mysql-server mysql
    else
        log_error "无法安装MySQL，请手动安装"
        exit 1
    fi
else
    log_info "MySQL已安装: $(mysql --version)"
fi

# 安装Node.js和npm
if ! command -v node &> /dev/null; then
    log_info "安装Node.js..."
    curl -fsSL https://deb.nodesource.com/setup_18.x | bash -
    apt install -y nodejs
else
    log_info "Node.js已安装: $(node --version)"
fi

if ! command -v npm &> /dev/null; then
    log_info "安装npm..."
    apt install -y npm
else
    log_info "npm已安装: $(npm --version)"
fi

# 安装Git
if ! command -v git &> /dev/null; then
    log_info "安装Git..."
    apt install -y git
else
    log_info "Git已安装: $(git --version)"
fi

# 安装PM2（进程管理）
log_info "安装PM2..."
npm install -g pm2

# ============================================
# 3. 配置MySQL数据库
# ============================================
log_info "配置MySQL数据库..."

# 启动MySQL服务
systemctl start mysql
systemctl enable mysql

# 检查MySQL状态
MYSQL_STATUS=$(systemctl is-active mysql)
if [ "$MYSQL_STATUS" != "active" ]; then
    log_error "MySQL服务启动失败"
    systemctl status mysql
    exit 1
fi
log_info "MySQL服务状态: $MYSQL_STATUS"

# 安全配置MySQL（设置root密码）
log_info "配置MySQL安全设置..."
# 生成随机密码
MYSQL_ROOT_PASSWORD="StudyApp@2026$(date +%s | tail -c 4)"
log_info "生成的MySQL root密码: $MYSQL_ROOT_PASSWORD"

# 运行安全脚本
mysql -e "ALTER USER 'root'@'localhost' IDENTIFIED BY '$MYSQL_ROOT_PASSWORD';"
mysql -e "DELETE FROM mysql.user WHERE User='';"
mysql -e "DELETE FROM mysql.user WHERE User='root' AND Host NOT IN ('localhost', '127.0.0.1', '::1');"
mysql -e "DROP DATABASE IF EXISTS test;"
mysql -e "DELETE FROM mysql.db WHERE Db='test' OR Db='test\\_%';"
mysql -e "FLUSH PRIVILEGES;"

# 创建应用数据库
log_info "创建studyapp_database数据库..."
mysql -u root -p"$MYSQL_ROOT_PASSWORD" -e "CREATE DATABASE IF NOT EXISTS studyapp_database CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p"$MYSQL_ROOT_PASSWORD" -e "SHOW DATABASES;"

# 创建应用专用用户（可选）
log_info "创建应用数据库用户..."
mysql -u root -p"$MYSQL_ROOT_PASSWORD" -e "CREATE USER IF NOT EXISTS 'studyapp_user'@'localhost' IDENTIFIED BY 'StudyAppUser2026';"
mysql -u root -p"$MYSQL_ROOT_PASSWORD" -e "GRANT ALL PRIVILEGES ON studyapp_database.* TO 'studyapp_user'@'localhost';"
mysql -u root -p"$MYSQL_ROOT_SSWORD" -e "FLUSH PRIVILEGES;"

# 保存密码到文件（仅用于迁移）
echo "MYSQL_ROOT_PASSWORD=$MYSQL_ROOT_PASSWORD" > /root/mysql_password.txt
chmod 600 /root/mysql_password.txt
log_info "MySQL密码已保存到 /root/mysql_password.txt"

# ============================================
# 4. 部署后端代码
# ============================================
log_info "部署后端代码..."

# 创建应用目录
APP_DIR="/opt/studyapp-backend"
log_info "应用目录: $APP_DIR"

if [ -d "$APP_DIR" ]; then
    log_warn "应用目录已存在，备份后重新创建..."
    BACKUP_DIR="/opt/studyapp-backend-backup-$(date +%Y%m%d-%H%M%S)"
    mv "$APP_DIR" "$BACKUP_DIR"
    log_info "已备份到: $BACKUP_DIR"
fi

# 创建目录结构
mkdir -p "$APP_DIR"
cd "$APP_DIR"

# 复制后端代码（假设代码已上传到ECS）
log_info "复制后端代码..."
# 这里假设代码在 /tmp/studyapp-backend 目录中
# 实际部署时可能需要从Git仓库克隆或使用scp上传

# 创建配置文件
log_info "创建数据库配置文件..."

cat > db.js << EOF
/**
 * 学习应用后端 - MySQL数据库连接模块
 * 功能：提供MySQL数据库连接池，支持连接复用和高效查询
 * 使用mysql2/promise库，支持async/await语法
 * 配置：ECS本地MySQL数据库
 */

// ================================
// 1. 引入必要的模块
// ================================
const mysql = require('mysql2/promise');

// ================================
// 2. 数据库连接配置
// ================================
const dbConfig = {
    host: '127.0.0.1',       // MySQL服务器地址（本地）
    port: 3306,              // MySQL服务器端口（默认3306）
    user: 'root',            // 数据库用户名
    password: '$MYSQL_ROOT_PASSWORD', // 使用生成的密码
    database: 'studyapp_database', // 数据库名称
    waitForConnections: true,     // 等待连接（连接池满时）
    connectionLimit: 10,          // 连接池最大连接数
    queueLimit: 0,                // 排队连接数限制（0表示不限制）
    enableKeepAlive: true,        // 保持连接活跃
    keepAliveInitialDelay: 0      // 保持连接初始延迟
};

// ================================
// 3. 创建数据库连接池
// ================================
let pool;

/**
 * 初始化数据库连接池
 * @returns {Promise<mysql.Pool>} 数据库连接池
 */
async function initPool() {
    try {
        // 创建连接池
        pool = mysql.createPool(dbConfig);

        // 测试连接
        const connection = await pool.getConnection();
        console.log('✅ MySQL数据库连接成功');
        connection.release(); // 释放连接回连接池
        return pool;
    } catch (error) {
        console.error('❌ MySQL数据库连接失败:', error.message);
        console.log('💡 请检查：');
        console.log('   1. MySQL服务是否启动');
        console.log('   2. 数据库配置是否正确（用户名、密码、端口）');
        console.log('   3. studyapp_database数据库是否存在');
        throw error; // 抛出错误，让调用者处理
    }
}

/**
 * 获取数据库连接池（单例模式）
 * @returns {Promise<mysql.Pool>} 数据库连接池
 */
async function getPool() {
    if (!pool) {
        pool = await initPool();
    }
    return pool;
}

/**
 * 执行SQL查询（带参数）
 * @param {string} sql - SQL查询语句，使用?作为占位符
 * @param {Array} params - 查询参数数组
 * @returns {Promise<Object>} 查询结果
 * @example
 * // 查询用户
 * const users = await executeQuery('SELECT * FROM users WHERE id = ?', [1]);
 * // 插入数据
 * const result = await executeQuery('INSERT INTO users (username, password) VALUES (?, ?)', ['test', '123']);
 */
async function executeQuery(sql, params = []) {
    try {
        const pool = await getPool();
        const [rows] = await pool.execute(sql, params);
        return rows;
    } catch (error) {
        console.error('❌ SQL查询执行失败:', error.message);
        console.error('📋 SQL语句:', sql);
        console.error('📋 参数:', params);
        throw error; // 抛出错误，让上层处理
    }
}

/**
 * 开启事务
 * @returns {Promise<mysql.PoolConnection>} 事务连接对象
 */
async function beginTransaction() {
    try {
        const pool = await getPool();
        const connection = await pool.getConnection();
        await connection.beginTransaction();
        return connection;
    } catch (error) {
        console.error('❌ 开启事务失败:', error.message);
        throw error;
    }
}

/**
 * 提交事务
 * @param {mysql.PoolConnection} connection - 事务连接对象
 */
async function commitTransaction(connection) {
    try {
        await connection.commit();
        connection.release();
    } catch (error) {
        console.error('❌ 提交事务失败:', error.message);
        throw error;
    }
}

/**
 * 回滚事务
 * @param {mysql.PoolConnection} connection - 事务连接对象
 */
async function rollbackTransaction(connection) {
    try {
        await connection.rollback();
        connection.release();
    } catch (error) {
        console.error('❌ 回滚事务失败:', error.message);
        throw error;
    }
}

/**
 * 关闭数据库连接池（在应用退出时调用）
 */
async function closePool() {
    if (pool) {
        await pool.end();
        console.log('✅ 数据库连接池已关闭');
    }
}

// ================================
// 4. 导出函数
// ================================
module.exports = {
    getPool,               // 获取连接池
    executeQuery,          // 执行查询（主要使用这个）
    beginTransaction,      // 开启事务
    commitTransaction,     // 提交事务
    rollbackTransaction,   // 回滚事务
    closePool              // 关闭连接池
};
EOF

# 复制其他后端文件（这里需要实际的后端代码）
log_info "请确保后端代码文件已上传到 $APP_DIR"
log_info "需要的文件：server.js, package.json, init-mysql.js 等"

# ============================================
# 5. 安装Node.js依赖
# ============================================
log_info "安装Node.js依赖..."

# 创建package.json如果不存在
if [ ! -f "package.json" ]; then
    cat > package.json << EOF
{
  "name": "studyapp-backend",
  "version": "1.0.0",
  "description": "StudyApp后端API服务",
  "main": "server.js",
  "scripts": {
    "start": "node server.js",
    "dev": "nodemon server.js",
    "test": "echo \"Error: no test specified\" && exit 1"
  },
  "dependencies": {
    "express": "^4.18.2",
    "mysql2": "^3.6.0",
    "cors": "^2.8.5",
    "body-parser": "^1.20.2"
  },
  "devDependencies": {
    "nodemon": "^3.0.1"
  }
}
EOF
fi

# 安装依赖
npm install

# ============================================
# 6. 初始化数据库
# ============================================
log_info "初始化数据库表结构..."
# 这里需要运行数据库初始化脚本
if [ -f "init-mysql.js" ]; then
    node init-mysql.js
elif [ -f "init-mysql.sql" ]; then
    mysql -u root -p"$MYSQL_ROOT_PASSWORD" studyapp_database < init-mysql.sql
else
    log_warn "未找到数据库初始化脚本，请手动创建表"
fi

# ============================================
# 7. 配置防火墙
# ============================================
log_info "配置防火墙..."

# 开放端口：22(SSH), 3001(API), 3306(MySQL)
if command -v ufw &> /dev/null; then
    ufw allow 22/tcp
    ufw allow 3001/tcp
    ufw --force enable
    log_info "UFW防火墙已配置"
elif command -v firewall-cmd &> /dev/null; then
    firewall-cmd --permanent --add-port=22/tcp
    firewall-cmd --permanent --add-port=3001/tcp
    firewall-cmd --permanent --add-port=3306/tcp
    firewall-cmd --reload
    log_info "FirewallD已配置"
else
    log_warn "未找到防火墙工具，请手动配置端口"
fi

# ============================================
# 8. 配置系统服务
# ============================================
log_info "配置系统服务..."

# 使用PM2管理Node.js应用
pm2 start server.js --name "studyapp-backend"
pm2 save
pm2 startup

# 创建systemd服务（备用）
cat > /etc/systemd/system/studyapp-backend.service << EOF
[Unit]
Description=StudyApp Backend API Service
After=network.target mysql.service

[Service]
Type=simple
User=root
WorkingDirectory=$APP_DIR
Environment=NODE_ENV=production
ExecStart=/usr/bin/node server.js
Restart=always
RestartSec=10
StandardOutput=syslog
StandardError=syslog
SyslogIdentifier=studyapp-backend

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable studyapp-backend.service
systemctl start studyapp-backend.service

# ============================================
# 9. 验证部署
# ============================================
log_info "验证部署..."

# 检查服务状态
API_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:3001/health || echo "unreachable")
MYSQL_CHECK=$(mysql -u root -p"$MYSQL_ROOT_PASSWORD" -e "SELECT 1" 2>/dev/null && echo "connected" || echo "failed")

log_info "API服务状态: $API_STATUS"
log_info "MySQL连接状态: $MYSQL_CHECK"

# 显示部署摘要
echo ""
echo "=============================================="
echo "           部署完成 - 摘要信息"
echo "=============================================="
echo "应用目录: $APP_DIR"
echo "MySQL root密码: $MYSQL_ROOT_PASSWORD"
echo "API服务地址: http://$(curl -s ifconfig.me):3001"
echo "MySQL端口: 3306"
echo "SSH端口: 22"
echo ""
echo "重要文件:"
echo "  - MySQL密码: /root/mysql_password.txt"
echo "  - 应用日志: pm2 logs studyapp-backend"
echo "  - 服务管理: systemctl status studyapp-backend"
echo ""
echo "下一步:"
echo "  1. 配置阿里云安全组开放3001端口"
echo "  2. 更新Android应用中的API地址"
echo "  3. 导入现有数据到新数据库"
echo "=============================================="

log_info "部署脚本执行完成！"