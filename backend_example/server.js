const express = require('express');
const cors = require('cors');

const app = express();
const PORT = process.env.PORT || 3000;

// 启用CORS，允许所有来源（开发环境）
app.use(cors());

// 解析JSON请求体
app.use(express.json());

// 模拟数据库中的班级统计数据
const classStatsDatabase = {
  '1': {
    totalStudents: 45,
    activeStudents: 38,
    averageWatchTime: 125, // 分钟
    completionRate: 78, // 百分比
    topStudents: [
      { name: '张三', progress: 95 },
      { name: '李四', progress: 88 },
      { name: '王五', progress: 82 },
      { name: '赵六', progress: 79 },
      { name: '孙七', progress: 76 }
    ]
  },
  '2': {
    totalStudents: 32,
    activeStudents: 28,
    averageWatchTime: 98,
    completionRate: 85,
    topStudents: [
      { name: '周八', progress: 98 },
      { name: '吴九', progress: 92 },
      { name: '郑十', progress: 87 },
      { name: '钱一', progress: 83 },
      { name: '孙二', progress: 80 }
    ]
  },
  '3': {
    totalStudents: 28,
    activeStudents: 25,
    averageWatchTime: 145,
    completionRate: 92,
    topStudents: [
      { name: '李小明', progress: 99 },
      { name: '张小红', progress: 96 },
      { name: '王大刚', progress: 94 },
      { name: '刘小花', progress: 91 },
      { name: '陈小明', progress: 89 }
    ]
  }
};

// 健康检查端点
app.get('/', (req, res) => {
  res.json({
    message: 'StudyApp后端API服务器正在运行',
    version: '1.0.0',
    endpoints: {
      stats: 'GET /api/stats/class/:classId',
      allClasses: 'GET /api/stats/all'
    }
  });
});

// 获取所有班级的统计信息（用于测试）
app.get('/api/stats/all', (req, res) => {
  res.json({
    success: true,
    message: '所有班级统计信息',
    data: classStatsDatabase
  });
});

// 获取指定班级的统计数据 - 主要API端点
app.get('/api/stats/class/:classId', (req, res) => {
  const { classId } = req.params;

  if (!classStatsDatabase[classId]) {
    return res.status(404).json({
      success: false,
      message: `班级ID ${classId} 不存在`,
      error: 'CLASS_NOT_FOUND'
    });
  }

  res.json(classStatsDatabase[classId]);
});

// 404处理
app.use((req, res) => {
  res.status(404).json({
    success: false,
    message: 'API端点不存在',
    error: 'ENDPOINT_NOT_FOUND'
  });
});

// 错误处理中间件
app.use((err, req, res, next) => {
  console.error('服务器错误:', err);
  res.status(500).json({
    success: false,
    message: '服务器内部错误',
    error: 'INTERNAL_SERVER_ERROR'
  });
});

// 启动服务器
app.listen(PORT, () => {
  console.log(`✅ StudyApp后端服务器正在运行`);
  console.log(`📡 访问地址: http://localhost:${PORT}`);
  console.log(`📊 API端点: http://localhost:${PORT}/api/stats/class/1`);
  console.log(`📋 示例班级ID: 1, 2, 3`);
  console.log('='.repeat(50));
});

module.exports = app;