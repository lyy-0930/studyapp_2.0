"use client";

import { useState, useMemo } from "react";
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from "recharts";

type Granularity = "日" | "周" | "月";

const FALLBACK_DATA: Record<Granularity, { date: string; count: number }[]> = {
  日: [
    { date: "00:00", count: 12 }, { date: "04:00", count: 8 },
    { date: "08:00", count: 45 }, { date: "10:00", count: 78 },
    { date: "12:00", count: 92 }, { date: "14:00", count: 110 },
    { date: "16:00", count: 135 }, { date: "18:00", count: 120 },
    { date: "20:00", count: 88 }, { date: "22:00", count: 55 },
  ],
  周: [
    { date: "周一", count: 320 }, { date: "周二", count: 280 },
    { date: "周三", count: 350 }, { date: "周四", count: 410 },
    { date: "周五", count: 380 }, { date: "周六", count: 290 },
    { date: "周日", count: 220 },
  ],
  月: [
    { date: "W1", count: 1200 }, { date: "W2", count: 1450 },
    { date: "W3", count: 1320 }, { date: "W4", count: 1580 },
  ],
};

interface ActiveUsersChartProps {
  data?: Record<Granularity, { date: string; count: number }[]>;
}

function CustomTooltip({ active, payload, label }: any) {
  if (!active || !payload?.length) return null;
  return (
    <div
      className="rounded-lg px-3 py-2 text-xs"
      style={{
        background: "rgba(10,14,39,0.95)",
        border: "1px solid rgba(0,212,255,0.2)",
        backdropFilter: "blur(8px)",
      }}
    >
      <span style={{ color: "#94A3B8" }}>{label}</span>
      <span className="ml-2 font-bold" style={{ color: "#00D4FF" }}>
        {payload[0].value}
      </span>
    </div>
  );
}

export default function ActiveUsersChart({ data }: ActiveUsersChartProps) {
  const [granularity, setGranularity] = useState<Granularity>("日");

  const chartData = useMemo(
    () => data?.[granularity] ?? FALLBACK_DATA[granularity],
    [data, granularity]
  );

  return (
    <div
      className="relative rounded-xl p-4"
      style={{
        background: "rgba(255,255,255,0.04)",
        backdropFilter: "blur(16px)",
        WebkitBackdropFilter: "blur(16px)",
        border: "1px solid rgba(0,212,255,0.10)",
      }}
    >
      {/* Header */}
      <div className="flex items-center justify-between mb-3">
        <span
          className="text-xs font-medium tracking-wide"
          style={{ color: "#F8FAFC", fontFamily: "'Fira Sans', sans-serif" }}
        >
          活跃用户分布
        </span>
        <div className="flex gap-1">
          {(["日", "周", "月"] as Granularity[]).map((g) => (
            <button
              key={g}
              onClick={() => setGranularity(g)}
              className="text-[10px] px-2 py-0.5 rounded cursor-pointer transition-colors duration-200"
              style={{
                color: granularity === g ? "#00D4FF" : "#64748B",
                background:
                  granularity === g
                    ? "rgba(0,212,255,0.12)"
                    : "transparent",
                fontFamily: "'Fira Code', monospace",
              }}
            >
              {g}
            </button>
          ))}
        </div>
      </div>

      {/* Chart */}
      <div className="min-w-0" style={{ height: 180, position: "relative" }}>
        <div style={{ position: "absolute", inset: 0, minWidth: 0, minHeight: 0 }}>
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={chartData} margin={{ top: 4, right: 4, bottom: 0, left: -20 }}>
              <defs>
                <linearGradient id="activeUsersGradient" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="#00D4FF" stopOpacity={0.3} />
                  <stop offset="100%" stopColor="#00D4FF" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid
                strokeDasharray="3 3"
                stroke="rgba(0,212,255,0.06)"
                vertical={false}
              />
              <XAxis
                dataKey="date"
                axisLine={false}
                tickLine={false}
                tick={{ fill: "#64748B", fontSize: 9, fontFamily: "'Fira Code', monospace" }}
              />
              <YAxis
                axisLine={false}
                tickLine={false}
                tick={{ fill: "#64748B", fontSize: 9, fontFamily: "'Fira Code', monospace" }}
              />
              <Tooltip content={<CustomTooltip />} />
              <Area
                type="monotone"
                dataKey="count"
                stroke="#00D4FF"
                strokeWidth={1.5}
                fill="url(#activeUsersGradient)"
              />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      </div>
    </div>
  );
}
