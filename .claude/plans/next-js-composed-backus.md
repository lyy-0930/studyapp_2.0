# Next.js 科幻监控面板 — 完整实现方案

## Context

基于 ui-ux-pro-max 设计系统（OLED Dark + HUD/FUI），用 Next.js + Framer Motion + Three.js + Noise Texture 构建一个真正炫酷的数据监控面板。后端 API 已存在（`http://120.55.72.75:3001`），4 个管理接口可直接调用。

## 设计规范

| 项目 | 规范 |
|------|------|
| 风格 | Dark Mode (OLED) + Glassmorphism |
| 主色 | 霓虹青 `#00FFFF`、全息蓝 `#0080FF` |
| 背景 | `#020617`（深空黑） |
| 标题字体 | Fira Code（等宽） |
| 正文字体 | Fira Sans |
| 效果 | text-shadow 辉光、backdrop-blur 玻璃态、细线边框 |
| 反模式 | 禁止 emoji 图标、禁止无过渡的突变 |

## 技术栈

```
Next.js 14 (App Router) + TypeScript
├── Tailwind CSS — HUD 主题系统
├── Framer Motion — 所有动画（入场/数字/辉光）
├── Three.js — 粒子星空背景
│   ├── @react-three/fiber — React 集成
│   ├── @react-three/drei — 辅助工具
│   └── simplex-noise — 粒子噪声漂移算法
├── Recharts — 环形图 + 雷达图
└── Heroicons/Lucide — SVG 图标（禁止 emoji）
```

## 文件结构

```
monitor-dashboard/
├── src/
│   ├── app/
│   │   ├── layout.tsx           # 根布局（字体、metadata）
│   │   ├── page.tsx             # 主监控页面（组件组装）
│   │   └── globals.css          # HUD 主题变量
│   ├── components/
│   │   ├── background/
│   │   │   └── ThreeScene.tsx   # Three.js 粒子系统（dynamic import）
│   │   ├── hud/
│   │   │   ├── HUDGrid.tsx      # 极坐标网格（SVG）
│   │   │   ├── ScanLines.tsx    # CRT 扫描线（CSS）
│   │   │   ├── HudCorner.tsx    # 四角括号装饰
│   │   │   └── HudDivider.tsx   # 发光分割线
│   │   ├── cards/
│   │   │   └── MetricCard.tsx   # 玻璃态指标卡片（Framer Motion）
│   │   ├── charts/
│   │   │   ├── DonutChart.tsx   # 环形进度图
│   │   │   └── RadarChart.tsx   # 雷达图
│   │   ├── ranking/
│   │   │   ├── RankingPanel.tsx # 排行榜面板
│   │   │   ├── RankingRow.tsx   # 单行（HUD图标 + 进度条）
│   │   │   └── HudRankIcon.tsx  # SVG 排名图标（非 emoji）
│   │   └── ui/
│   │       └── GlowText.tsx     # 辉光文字
│   ├── hooks/
│   │   └── useMonitorData.ts   # 数据获取 + 自动轮询
│   └── lib/
│       └── api.ts              # API 客户端
├── tailwind.config.ts
├── next.config.ts
└── package.json
```

## API 接口

| 接口 | 数据 |
|------|------|
| `GET /admin/online-users` | 在线学生/教师数 |
| `GET /admin/course-mastery-stats?limit=20` | 总课程数、选课排行 |
| `GET /admin/learning-stats?limit=20` | 完成率排行、总观看时长 |
| `GET /admin/activity-ranking?days=7&limit=20` | 活跃用户数、分布 |

## 实现步骤

### Step 1: 创建项目
```bash
npx create-next-app@latest monitor-dashboard --typescript --tailwind --app --src-dir=true
cd monitor-dashboard
npm install framer-motion three @react-three/fiber @react-three/drei simplex-noise recharts lucide-react
npm install -D @types/three
```

### Step 2: Tailwind HUD 主题
`tailwind.config.ts` 添加：
- `colors.hud-cyan: '#00FFFF'`
- `colors.hud-blue: '#0080FF'`
- `colors.hud-green: '#00FF88'`
- `colors.hud-bg: '#020617'`
- `colors.hud-surface: '#0F172A'`
- `colors.hud-glass: 'rgba(255,255,255,0.06)'`
- `colors.hud-border: 'rgba(0,255,255,0.2)'`

### Step 3: Three.js 粒子背景（核心视觉）
- 5000 粒子，球体分布
- `simplex-noise` 驱动每个粒子的 XYZ 漂移
- 粒子颜色：青色渐变到透明
- 相机缓慢自转
- `next/dynamic` 动态导入，`ssr: false`

### Step 4: HUD 装饰层
- `HUDGrid` — SVG 同心圆 + 径向射线，`opacity: 0.06`
- `ScanLines` — CSS `repeating-linear-gradient` 水平线覆盖，`pointer-events: none`
- `HudCorner` — 四角 L 形括号，CSS `opacity` 呼吸动画
- `HudDivider` — 中间带亮点的细线

### Step 5: MetricCard 组件
- `backdrop-filter: blur(16px)` 玻璃态背景
- 边框 1px `rgba(0,255,255,0.15)` 呼吸发光
- Framer Motion: `fadeIn + scale` 入场（100ms 依次延迟）
- 数字 `useSpring` 动画 0 → target
- 禁止 emoji，用 `lucide-react` 图标

### Step 6: 排名系统
- `HudRankIcon` — SVG path 绘制几何徽章（6边形/圆形/三角/菱形）
- `RankingRow` — Framer Motion `animate` 宽度进度条
- `RankingPanel` — 半透明面板 + 标题装饰

### Step 7: 图表
- `DonutChart` — Recharts 环形图（掌握度分布：精通/良好/中等/基础/入门）
- `RadarChart` — Recharts 雷达图（5 维度对比）

### Step 8: 数据层
- `api.ts` — `fetch` + 类型定义
- `useMonitorData.ts` — 并行请求 4 接口，30s 轮询，loading/error 状态

### Step 9: 主页面
```
┌──────────────────────────────────────────────┐
│ Three.js 粒子背景（全屏，pointer-events: none）│
│  HUD 网格 + 扫描线覆盖                         │
│  ┌ 标题栏 ─────────────────────────────┐      │
│  │ ◀ 监控中心 v2.0            [A]      │      │
│  ├─────────────────────────────────────┤      │
│  │ ┌──────┐┌──────┐┌──────┐┌──────┐  │      │
│  │ │在线学 ││在线教 ││课程总 ││活跃用 │  │      │
│  │ │生 45  ││师 12  ││数 8   ││户 36  │  │      │
│  │ └──────┘└──────┘└──────┘└──────┘  │      │
│  ├────────────┬───────────────────────┤      │
│  │ 选课人数排行│ 完成率排行           │      │
│  │ ┌────────┐│ ┌────────────────────┐│      │
│  │ │#1 课程A ││ │ #1 学生X   95.2%  ││      │
│  │ │▓▓▓▓▓▓▓░││ │ ▓▓▓▓▓▓▓▓▓▓░░░░   ││      │
│  │ │#2 课程B ││ │ #2 学生Y   88.5%  ││      │
│  │ │▓▓▓▓▓░░░││ │ ▓▓▓▓▓▓▓▓░░░░░░   ││      │
│  │ └────────┘│ └────────────────────┘│      │
│  ├─────────────────────────────────────┤      │
│  │ 最后刷新: 14:32:15  总计: 128h30m  │      │
│  └─────────────────────────────────────┘      │
│  加载状态: skeleton 动画 (content-jumping 防) │
└──────────────────────────────────────────────┘
```

### Step 10: 动效清单

| 元素 | Framer Motion | 触发 |
|------|--------------|------|
| 粒子漂移 | Three.js + simplex-noise | mount |
| 卡片入场 | `fadeIn + scale(0.9→1)` 四张依次 100ms 延迟 | data loaded |
| 数字 | `useSpring { stiffness: 80, damping: 15 }` | 数据更新 |
| 边框辉光 | `animate opacity 0.3↔0.9` 无限循环 | 持续 |
| 排行面板 | `slideInUp` | data loaded |
| 进度条 | `animate width 0→target` `easeOut` | visible |
| HUD 括号 | `animate opacity 0.15↔0.35` 2.5s | 持续 |
| 分割线 | `animate opacity 0.2↔0.5` 1.5s | 持续 |

## 性能考虑
- `ThreeScene` 使用 `next/dynamic` + `ssr: false`
- 图表组件 `dynamic` 按需加载
- 粒子数量限制 5000（桌面流畅）
- 轮询间隔 30s 避免频繁请求
- Skeleton 占位防止 layout shift

## 验证
1. `cd monitor-dashboard && npm run dev`
2. 浏览器打开 `http://localhost:3000`
3. 确认 Three.js 粒子星空背景正常
4. 确认 Framer Motion 入场动画流畅
5. 确认 API 数据正确显示
6. 确认排名图标为 SVG 非 emoji
7. 确认字体为 Fira Code / Fira Sans
8. 确认 `prefers-reduced-motion` 被尊重
