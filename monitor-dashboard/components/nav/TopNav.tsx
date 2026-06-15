"use client";

import { useState } from "react";
import { motion } from "framer-motion";
import { Bell } from "lucide-react";

const tabs = ["数据概览", "用户管理", "活跃排行", "学习统计", "课程掌握", "成绩分析"];

export default function TopNav() {
  const [active, setActive] = useState("数据概览");

  return (
    <nav
      className="fixed top-0 left-0 right-0 z-50 flex items-center h-14 px-6"
      style={{
        background: "rgba(10,14,39,0.85)",
        backdropFilter: "blur(20px)",
        WebkitBackdropFilter: "blur(20px)",
        borderBottom: "1px solid rgba(0,212,255,0.08)",
      }}
    >
      {/* Logo */}
      <div className="flex items-center gap-2 mr-10">
        <div
          className="w-8 h-8 rounded-lg flex items-center justify-center text-sm font-bold"
          style={{
            background: "linear-gradient(135deg, #00D4FF, #7C3AED)",
            color: "#fff",
            fontFamily: "'Fira Code', monospace",
          }}
        >
          S
        </div>
        <span
          className="text-sm font-semibold tracking-wide"
          style={{ fontFamily: "'Fira Sans', sans-serif", color: "#F8FAFC" }}
        >
          StudyApp
        </span>
      </div>

      {/* Nav tabs */}
      <div className="flex items-center gap-1">
        {tabs.map((tab) => (
          <button
            key={tab}
            onClick={() => setActive(tab)}
            className="relative px-3 py-1.5 text-xs font-medium rounded-md transition-colors duration-200 cursor-pointer"
            style={{
              color: active === tab ? "#00D4FF" : "#64748B",
              fontFamily: "'Fira Sans', sans-serif",
              letterSpacing: "0.3px",
            }}
          >
            {tab}
            {active === tab && (
              <motion.div
                layoutId="nav-underline"
                className="absolute bottom-0 left-2 right-2 h-0.5 rounded-full"
                style={{ background: "#00D4FF" }}
                transition={{ type: "spring", stiffness: 300, damping: 30 }}
              />
            )}
          </button>
        ))}
      </div>

      {/* Spacer */}
      <div className="flex-1" />

      {/* Right side */}
      <div className="flex items-center gap-4">
        <button className="relative cursor-pointer">
          <Bell size={16} color="#94A3B8" />
          <span
            className="absolute -top-1 -right-1 w-2 h-2 rounded-full"
            style={{ background: "#00D4FF" }}
          />
        </button>
        <div
          className="w-7 h-7 rounded-full flex items-center justify-center text-[10px] font-medium"
          style={{
            background: "linear-gradient(135deg, #7C3AED, #00D4FF)",
            color: "#fff",
          }}
        >
          A
        </div>
      </div>
    </nav>
  );
}
