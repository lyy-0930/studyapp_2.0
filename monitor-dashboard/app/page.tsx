"use client";

import { motion } from "framer-motion";
import {
  Users,
  GraduationCap,
  BookOpen,
  Activity,
  RefreshCw,
  Clock,
  Award,
  BarChart3,
} from "lucide-react";

import { useMonitorData } from "@/hooks/useMonitorData";
import MetricCard from "@/components/cards/MetricCard";
import RankingPanel from "@/components/ranking/RankingPanel";
import ActiveUsersChart from "@/components/charts/ActiveUsersChart";

function DataBadge({
  icon,
  label,
  value,
  color,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  color?: string;
}) {
  return (
    <motion.div
      className="flex items-center gap-2.5 rounded-lg px-4 py-2.5 min-w-[200px]"
      style={{
        background: "rgba(10,14,39,0.7)",
        backdropFilter: "blur(12px)",
        WebkitBackdropFilter: "blur(12px)",
        border: "1px solid rgba(0,212,255,0.12)",
      }}
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5 }}
    >
      <div style={{ color: color ?? "#00D4FF" }}>{icon}</div>
      <div>
        <div
          className="text-[10px] tracking-wide"
          style={{
            color: "#64748B",
            fontFamily: "'Fira Sans', sans-serif",
          }}
        >
          {label}
        </div>
        <div
          className="text-sm font-bold tabular-nums"
          style={{
            color: "#F8FAFC",
            fontFamily: "'Fira Code', monospace",
          }}
        >
          {value}
        </div>
      </div>
    </motion.div>
  );
}

const cardColors = {
  students: "#00D4FF",
  teachers: "#10B981",
  courses: "#7C3AED",
  active: "#F59E0B",
} as const;

const trends = {
  students: { value: "12.5%", positive: true },
  teachers: { value: "8.3%", positive: true },
  courses: { value: "3.2%", positive: true },
  active: { value: "15.7%", positive: true },
};

export default function Home() {
  const {
    onlineStudents,
    onlineTeachers,
    totalCourses,
    activeUsers,
    totalWatchTime,
    examPassRate,
    courseCompletionRate,
    studentRankings,
    courseRankings,
    loading,
    error,
    refresh,
  } = useMonitorData(30000);

  return (
    <>
      {/* Background image */}
      <div
        className="fixed inset-0 z-0 bg-cover bg-center bg-no-repeat"
        style={{
          backgroundImage: "url(/监控.jpg)",
        }}
      />
      <div className="fixed inset-0 z-[1] bg-[#0A0E27]/60" />

      {/* Main 3-column layout */}
      <div className="fixed inset-0 z-10 flex">
        {/* ─── Left Column ─── */}
        <aside
          className="w-[260px] shrink-0 overflow-y-auto p-4 space-y-3"
          style={{
            borderRight: "1px solid rgba(0,212,255,0.06)",
            background: "rgba(10,14,39,0.4)",
          }}
        >
          <div className="flex items-center justify-between mb-1">
            <span
              className="text-xs font-semibold tracking-wider uppercase"
              style={{
                color: "#F8FAFC",
                fontFamily: "'Fira Sans', sans-serif",
              }}
            >
              数据概览
            </span>
            <motion.button
              onClick={refresh}
              className="cursor-pointer"
              whileTap={{ scale: 0.85, rotate: 180 }}
              transition={{ duration: 0.3 }}
            >
              <RefreshCw size={13} color="#64748B" />
            </motion.button>
          </div>

          <MetricCard
            label="在线学生"
            value={onlineStudents}
            color={cardColors.students}
            icon={<Users size={14} color={cardColors.students} />}
            delay={0}
            trend={trends.students}
          />
          <MetricCard
            label="在线教师"
            value={onlineTeachers}
            color={cardColors.teachers}
            icon={<GraduationCap size={14} color={cardColors.teachers} />}
            delay={0.1}
            trend={trends.teachers}
          />
          <MetricCard
            label="总课程数"
            value={totalCourses}
            color={cardColors.courses}
            icon={<BookOpen size={14} color={cardColors.courses} />}
            delay={0.2}
            trend={trends.courses}
          />
          <MetricCard
            label="活跃用户"
            value={activeUsers}
            color={cardColors.active}
            icon={<Activity size={14} color={cardColors.active} />}
            delay={0.3}
            trend={trends.active}
          />

          <RankingPanel
            title="选课人数排行"
            items={courseRankings}
            barColor="#0080FF"
            icon="users"
            delay={0.2}
          />
        </aside>

        {/* ─── Center Column ─── */}
        <main className="flex-1 relative overflow-hidden flex items-center justify-center">
          <div className="flex flex-col gap-3 z-10">
            <DataBadge
              icon={<Clock size={16} />}
              label="总学习时长"
              value={totalWatchTime || "—"}
            />
            <DataBadge
              icon={<Award size={16} />}
              label="考试通过率"
              value={examPassRate}
              color="#10B981"
            />
            <DataBadge
              icon={<BarChart3 size={16} />}
              label="课程完成率"
              value={courseCompletionRate}
              color="#7C3AED"
            />
          </div>
        </main>

        {/* ─── Right Column ─── */}
        <aside
          className="w-[320px] shrink-0 overflow-y-auto p-4 space-y-3"
          style={{
            borderLeft: "1px solid rgba(0,212,255,0.06)",
            background: "rgba(10,14,39,0.4)",
          }}
        >
          <RankingPanel
            title="完成率排行"
            items={studentRankings}
            barColor="#00D4FF"
            icon="trending"
            delay={0.2}
          />

          <ActiveUsersChart />

          <div className="text-center pt-2">
            <span
              className="text-[9px] tracking-[2px] uppercase"
              style={{
                fontFamily: "'Fira Code', monospace",
                color: "#334155",
              }}
            >
              StudyApp · 实时监控
            </span>
          </div>
        </aside>
      </div>

      {/* Error banner */}
      {error && (
        <motion.div
          className="fixed bottom-4 left-1/2 -translate-x-1/2 z-50 rounded-lg px-4 py-2 text-xs"
          style={{
            background: "rgba(239,68,68,0.15)",
            border: "1px solid rgba(239,68,68,0.25)",
            color: "#EF4444",
            fontFamily: "'Fira Code', monospace",
            backdropFilter: "blur(12px)",
          }}
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
        >
          {error}
        </motion.div>
      )}

      {/* Loading overlay */}
      {loading && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#0A0E27]/80">
          <motion.div
            className="w-8 h-8 rounded-full border-2 border-transparent"
            style={{ borderTopColor: "#00D4FF" }}
            animate={{ rotate: 360 }}
            transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
          />
        </div>
      )}
    </>
  );
}
