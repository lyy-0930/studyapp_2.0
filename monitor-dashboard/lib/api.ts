const BASE_URL = "http://120.55.72.75:3001";

// ─── Types ───

export interface OnlineUser {
  id: number;
  username: string;
  role: string;
  last_active_at: string;
  is_online: boolean;
}

export interface OnlineUsersData {
  stats: { online_count: number; total_users: number; online_rate: number };
  online_users: OnlineUser[];
}

export interface CourseItem {
  rank: number;
  course_id: number;
  course_name: string;
  total_students: number;
  average_progress: number;
  average_completion_rate: number;
}

export interface CourseMasteryData {
  stats: { total_courses: number; total_students: number; average_progress: number };
  courses: CourseItem[];
  mastery_distribution: Record<string, number>;
}

export interface StudentItem {
  rank: number;
  student_id: number;
  username: string;
  total_watch_time: number;
  average_progress: number;
}

export interface LearningStatsData {
  stats: { total_students: number; total_watch_time: number; average_progress: number };
  students: StudentItem[];
}

export interface ActivityRankingData {
  stats: {
    activity_distribution: { high: number; medium: number; low: number };
    total_users: number;
  };
  ranking: Array<{ rank: number; username: string; activity_score: number }>;
}

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

// ─── Fetcher ───

async function fetchApi<T>(path: string): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, { cache: "no-store" });
  const json: ApiResponse<T> = await res.json();
  if (!json.success) throw new Error(json.message || "API Error");
  return json.data;
}

// ─── Hooks-compatible API ───

export const api = {
  getOnlineUsers: () => fetchApi<OnlineUsersData>("/admin/online-users"),
  getCourseMasteryStats: (limit = 20) =>
    fetchApi<CourseMasteryData>(`/admin/course-mastery-stats?limit=${limit}`),
  getLearningStats: (limit = 20) =>
    fetchApi<LearningStatsData>(`/admin/learning-stats?limit=${limit}`),
  getActivityRanking: (days = 7, limit = 20) =>
    fetchApi<ActivityRankingData>(`/admin/activity-ranking?days=${days}&limit=${limit}`),
};
