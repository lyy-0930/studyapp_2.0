"use client";

import { useState, useEffect, useCallback } from "react";
import { api, OnlineUsersData, CourseMasteryData, LearningStatsData, ActivityRankingData } from "@/lib/api";

export interface MonitorData {
  onlineStudents: number;
  onlineTeachers: number;
  totalCourses: number;
  activeUsers: number;
  totalWatchTime: string;
  examPassRate: string;
  courseCompletionRate: string;
  studentRankings: { rank: number; name: string; value: string; progress: number }[];
  courseRankings: { rank: number; name: string; value: string; progress: number }[];
  lastRefresh: string;
  raw: {
    online?: OnlineUsersData;
    mastery?: CourseMasteryData;
    learning?: LearningStatsData;
    activity?: ActivityRankingData;
  };
  loading: boolean;
  error: string | null;
}

const initialData: MonitorData = {
  onlineStudents: 0,
  onlineTeachers: 0,
  totalCourses: 0,
  activeUsers: 0,
  totalWatchTime: "",
  examPassRate: "—",
  courseCompletionRate: "—",
  studentRankings: [],
  courseRankings: [],
  lastRefresh: "",
  raw: {},
  loading: true,
  error: null,
};

function now() {
  return new Date().toLocaleTimeString("zh-CN", { hour12: false });
}

export function useMonitorData(refreshMs = 30000) {
  const [data, setData] = useState<MonitorData>(initialData);

  const load = useCallback(async () => {
    try {
      const [online, mastery, learning, activity] = await Promise.all([
        api.getOnlineUsers().catch(() => null),
        api.getCourseMasteryStats(20).catch(() => null),
        api.getLearningStats(20).catch(() => null),
        api.getActivityRanking(7, 20).catch(() => null),
      ]);

      const students = online?.online_users?.filter((u) => u.role === "student").length ?? 0;
      const teachers = online?.online_users?.filter((u) => u.role === "teacher").length ?? 0;
      const courses = mastery?.stats?.total_courses ?? 0;
      const active = activity?.stats?.activity_distribution?.high ?? 0;

      // derived rates
      const completionRate = mastery?.stats?.average_progress
        ? `${mastery.stats.average_progress.toFixed(1)}%`
        : "—";
      const examRate = learning?.stats?.average_progress
        ? `${learning.stats.average_progress.toFixed(1)}%`
        : "—";

      // watch time formatting
      const totalMin = learning?.stats?.total_watch_time ?? 0;
      const watchTime =
        totalMin >= 60
          ? `${Math.floor(totalMin / 60)}h${totalMin % 60}m`
          : `${totalMin}分钟`;

      // course rankings
      const courseRanks = (mastery?.courses ?? [])
        .sort((a, b) => b.total_students - a.total_students)
        .slice(0, 5)
        .map((c, i) => ({
          rank: i + 1,
          name: c.course_name,
          value: `${c.total_students}人`,
          progress: c.total_students / (mastery?.courses?.[0]?.total_students || 1),
        }));

      // student rankings
      const studentRanks = (learning?.students ?? [])
        .sort((a, b) => b.average_progress - a.average_progress)
        .slice(0, 5)
        .map((s, i) => ({
          rank: i + 1,
          name: s.username,
          value: `${s.average_progress.toFixed(1)}%`,
          progress: s.average_progress / 100,
        }));

      setData({
        onlineStudents: students,
        onlineTeachers: teachers,
        totalCourses: courses,
        activeUsers: active,
        totalWatchTime: watchTime,
        examPassRate: examRate,
        courseCompletionRate: completionRate,
        studentRankings: studentRanks,
        courseRankings: courseRanks,
        lastRefresh: now(),
        raw: { online: online ?? undefined, mastery: mastery ?? undefined, learning: learning ?? undefined, activity: activity ?? undefined },
        loading: false,
        error: null,
      });
    } catch (e) {
      setData((prev) => ({ ...prev, loading: false, error: (e as Error).message }));
    }
  }, []);

  useEffect(() => {
    load();
    const interval = setInterval(load, refreshMs);
    return () => clearInterval(interval);
  }, [load, refreshMs]);

  return { ...data, refresh: load };
}
