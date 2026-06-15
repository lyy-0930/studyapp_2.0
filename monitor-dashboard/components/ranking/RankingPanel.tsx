"use client";

import { motion } from "framer-motion";
import RankingRow from "./RankingRow";
import { Users, TrendingUp } from "lucide-react";

interface RankItem {
  rank: number;
  name: string;
  value: string;
  progress: number;
}

interface RankingPanelProps {
  title: string;
  items: RankItem[];
  barColor: string;
  icon?: "users" | "trending";
  delay?: number;
}

const icons = {
  users: Users,
  trending: TrendingUp,
};

export default function RankingPanel({ title, items, barColor, icon = "trending", delay = 0 }: RankingPanelProps) {
  const IconComp = icons[icon];

  return (
    <motion.div
      className="rounded-xl p-3 w-full"
      style={{
        background: "rgba(10,14,39,0.75)",
        backdropFilter: "blur(16px)",
        WebkitBackdropFilter: "blur(16px)",
        border: "1px solid rgba(0,212,255,0.10)",
      }}
      initial={{ opacity: 0, y: 30 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5, delay, ease: [0.25, 0.1, 0.25, 1] }}
    >
      {/* Header */}
      <div className="flex items-center gap-2 mb-2">
        <div
          className="w-[3px] h-3 rounded-full flex-shrink-0"
          style={{ background: barColor }}
        />
        <IconComp size={12} color={barColor} className="flex-shrink-0" />
        <span
          className="text-xs font-bold uppercase tracking-[1px]"
          style={{ fontFamily: "'Fira Code', monospace", color: "#94A3B8" }}
        >
          {title}
        </span>
      </div>

      {/* Rows */}
      {items.length === 0 ? (
        <span
          className="text-xs"
          style={{ fontFamily: "'Fira Sans', sans-serif", color: "#64748B" }}
        >
          暂无数据
        </span>
      ) : (
        <div className="flex flex-col gap-1.5">
          {items.map((item) => (
            <RankingRow
              key={`${item.rank}-${item.name}`}
              rank={item.rank}
              name={item.name}
              value={item.value}
              progress={item.progress}
              barColor={barColor}
            />
          ))}
        </div>
      )}
    </motion.div>
  );
}
