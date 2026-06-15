interface HudRankIconProps {
  rank: number;
  size?: number;
}

const COLORS = {
  1: "#FFD700",
  2: "#C0C0C0",
  3: "#CD7F32",
} as const;

const DEFAULT_COLOR = "#64748B";

function Hexagon({ cx, cy, r, color }: { cx: number; cy: number; r: number; color: string }) {
  const pts = Array.from({ length: 6 }, (_, i) => {
    const a = (Math.PI / 3) * i - Math.PI / 6;
    return `${cx + r * Math.cos(a)},${cy + r * Math.sin(a)}`;
  }).join(" ");
  return (
    <g>
      <polygon points={pts} fill={`${color}15`} stroke={color} strokeWidth={1.5} />
      <circle cx={cx} cy={cy} r={r * 0.35} fill={`${color}40`} />
    </g>
  );
}

function CircleRank({ cx, cy, r, color }: { cx: number; cy: number; r: number; color: string }) {
  return (
    <g>
      <circle cx={cx} cy={cy} r={r} fill={`${color}15`} stroke={color} strokeWidth={1.5} />
      <circle cx={cx} cy={cy} r={r * 0.35} fill={`${color}40`} />
    </g>
  );
}

function Triangle({ cx, cy, r, color }: { cx: number; cy: number; r: number; color: string }) {
  const pts = [
    `${cx},${cy + r}`,
    `${cx - r * 0.87},${cy - r * 0.5}`,
    `${cx + r * 0.87},${cy - r * 0.5}`,
  ].join(" ");
  return (
    <polygon points={pts} fill={`${color}15`} stroke={color} strokeWidth={1.5} />
  );
}

function Diamond({ cx, cy, r, color }: { cx: number; cy: number; r: number; color: string }) {
  const pts = [
    `${cx},${cy - r}`,
    `${cx + r * 0.7},${cy}`,
    `${cx},${cy + r}`,
    `${cx - r * 0.7},${cy}`,
  ].join(" ");
  return (
    <polygon points={pts} fill={`${color}10`} stroke={color} strokeWidth={1.2} />
  );
}

export default function HudRankIcon({ rank, size = 20 }: HudRankIconProps) {
  const color = COLORS[rank as keyof typeof COLORS] ?? DEFAULT_COLOR;
  const cx = size / 2;
  const cy = size / 2;
  const r = size / 2 - 1.5;

  return (
    <svg width={size} height={size} style={{ flexShrink: 0 }}>
      {rank === 1 && <Hexagon cx={cx} cy={cy} r={r} color={color} />}
      {rank === 2 && <CircleRank cx={cx} cy={cy} r={r} color={color} />}
      {rank === 3 && <Triangle cx={cx} cy={cy} r={r} color={color} />}
      {rank >= 4 && <Diamond cx={cx} cy={cy} r={r} color={color} />}

      <text
        x={cx}
        y={cy + size * 0.08}
        textAnchor="middle"
        dominantBaseline="central"
        fill={color}
        fontSize={size * 0.42}
        fontFamily="'Fira Code', monospace"
        fontWeight={600}
      >
        {rank}
      </text>
    </svg>
  );
}
