"use client";

import {
  LayoutDashboard,
  ListOrdered,
  Search,
  FileText,
  Upload,
  Clock,
  Check,
  Bell,
  CalendarDays,
  Hospital,
  UserRound,
  ArrowRight,
  ArrowLeft,
  LogOut,
  Menu,
  X,
  ShieldCheck,
  Activity,
  RefreshCw,
  TriangleAlert,
  Layers,
  LockKeyhole,
} from "lucide-react";

const icons = {
  grid: LayoutDashboard,
  queue: ListOrdered,
  search: Search,
  file: FileText,
  upload: Upload,
  clock: Clock,
  check: Check,
  bell: Bell,
  calendar: CalendarDays,
  room: Hospital,
  user: UserRound,
  arrow: ArrowRight,
  back: ArrowLeft,
  logout: LogOut,
  menu: Menu,
  close: X,
  shield: ShieldCheck,
  activity: Activity,
  refresh: RefreshCw,
  alert: TriangleAlert,
  layers: Layers,
  lock: LockKeyhole,
};

export function Icon({ name, size = 16 }: { name: string; size?: number }) {
  const Glyph = icons[name as keyof typeof icons] ?? FileText;
  return <Glyph size={size} aria-hidden />;
}
