"use client";

import { motion, useSpring, useTransform } from "framer-motion";
import { TrendingUp, TrendingDown } from "lucide-react";

interface MetricCardProps {
  label: string;
  value: number;
  unit?: string;
  color?: string;
  icon?: React.ReactNode;
  delay?: number;
  trend?: { value: string; positive: boolean };
}

export default function MetricCard({
  label,
  value,
  unit = "",
  color = "#00D4FF",
  icon,
  delay = 0,
  trend,
}: MetricCardProps) {
  const spring = useSpring(0, { stiffness: 60, damping: 15 });
  const displayValue = useTransform(spring, (v) => Math.round(v).toLocaleString());

  spring.set(value);

  return (
    <motion.div
      className="relative rounded-xl p-3 flex flex-col gap-1 min-w-0"
      style={{
        background: "rgba(10,14,39,0.75)",
        backdropFilter: "blur(16px)",
        WebkitBackdropFilter: "blur(16px)",
        border: "1px solid rgba(0,212,255,0.10)",
      }}
      initial={{ opacity: 0, scale: 0.85, y: 20 }}
      animate={{ opacity: 1, scale: 1, y: 0 }}
      transition={{ duration: 0.5, delay, ease: [0.25, 0.1, 0.25, 1] }}
      whileHover={{ scale: 1.03, borderColor: "rgba(0,212,255,0.25)" }}
    >
      {/* Glow border effect */}
      <motion.div
        className="absolute inset-0 rounded-xl pointer-events-none"
        animate={{ opacity: [0.3, 0.7, 0.3] }}
        transition={{ duration: 2, repeat: Infinity, ease: "easeInOut", delay }}
        style={{
          boxShadow: `inset 0 0 15px ${color}22, 0 0 15px ${color}11`,
        }}
      />

      <div className="relative z-10 flex items-center justify-between">
        {icon && <div className="flex items-center gap-1.5">{icon}</div>}
        {trend && (
          <div
            className="flex items-center gap-0.5 text-[10px] font-medium ml-auto"
            style={{ color: trend.positive ? "#10B981" : "#EF4444" }}
          >
            {trend.positive ? (
              <TrendingUp size={10} strokeWidth={2.5} />
            ) : (
              <TrendingDown size={10} strokeWidth={2.5} />
            )}
            <span className="tabular-nums">{trend.value}</span>
          </div>
        )}
      </div>

      <motion.span
        className="relative z-10 text-2xl font-bold tabular-nums leading-none"
        style={{ fontFamily: "'Fira Code', monospace", color }}
      >
        <motion.span>{displayValue}</motion.span>
        {unit && (
          <span className="text-sm ml-0.5" style={{ color: `${color}bb` }}>
            {unit}
          </span>
        )}
      </motion.span>

      <span
        className="relative z-10 text-[10px] uppercase tracking-[1.5px]"
        style={{ fontFamily: "'Fira Sans', sans-serif", color: "#94A3B8", fontWeight: 300 }}
      >
        {label}
      </span>
    </motion.div>
  );
}
