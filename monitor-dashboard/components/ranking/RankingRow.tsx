"use client";

import { motion } from "framer-motion";
import HudRankIcon from "./HudRankIcon";

interface RankingRowProps {
  rank: number;
  name: string;
  value: string;
  progress: number;
  barColor: string;
}

export default function RankingRow({ rank, name, value, progress, barColor }: RankingRowProps) {
  return (
    <div className="flex flex-col gap-0.5">
      <div className="flex items-center gap-1.5">
        <HudRankIcon rank={rank} size={18} />
        <span
          className="flex-1 truncate text-xs"
          style={{ fontFamily: "'Fira Sans', sans-serif", color: "#F8FAFCC8" }}
        >
          {name}
        </span>
        <span
          className="text-xs font-bold tabular-nums"
          style={{ fontFamily: "'Fira Code', monospace", color: barColor }}
        >
          {value}
        </span>
      </div>
      <div
        className="w-full h-[2px] rounded-full overflow-hidden"
        style={{ background: "rgba(255,255,255,0.06)" }}
      >
        <motion.div
          className="h-full rounded-full"
          style={{
            background: `linear-gradient(90deg, ${barColor}88, ${barColor})`,
          }}
          initial={{ width: 0 }}
          animate={{ width: `${Math.max(progress * 100, 2)}%` }}
          transition={{ duration: 0.8, ease: "easeOut", delay: 0.1 }}
        />
      </div>
    </div>
  );
}
