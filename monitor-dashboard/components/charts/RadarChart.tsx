"use client";

import {
  Radar,
  RadarChart as RechartsRadar,
  PolarGrid,
  PolarAngleAxis,
  PolarRadiusAxis,
  ResponsiveContainer,
} from "recharts";

interface RadarChartProps {
  data: Array<{ subject: string; value: number }>;
}

export default function RadarChartComponent({ data }: RadarChartProps) {
  return (
    <ResponsiveContainer width="100%" height={160}>
      <RechartsRadar data={data} cx="50%" cy="50%" outerRadius="65%">
        <PolarGrid stroke="rgba(0,255,255,0.1)" />
        <PolarAngleAxis
          dataKey="subject"
          tick={{ fill: "#94A3B8", fontSize: 10, fontFamily: "'Fira Sans', sans-serif" }}
        />
        <PolarRadiusAxis
          tick={false}
          axisLine={false}
          tickCount={3}
        />
        <Radar
          name="数据"
          dataKey="value"
          stroke="#00FFFF"
          fill="#00FFFF"
          fillOpacity={0.1}
          strokeWidth={1}
        />
      </RechartsRadar>
    </ResponsiveContainer>
  );
}
