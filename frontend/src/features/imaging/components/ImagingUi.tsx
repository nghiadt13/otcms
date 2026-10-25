"use client";

import Link from "next/link";
import type { ReactNode } from "react";
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
import { PageHeader } from "@/components/PageHeader";
import { StatusBadge, type StatusTone } from "@/components/admin/StatusBadge";
import { Button, buttonVariants } from "@/components/ui/button";
import { Alert, AlertDescription } from "@/components/ui/alert";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { cn } from "@/lib/utils";

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
export function PageHeading({
  title,
  description,
  children,
}: {
  eyebrow?: string;
  title: string;
  description: string;
  children?: ReactNode;
}) {
  return (
    <div className="im-page-heading" title={description}>
      <PageHeader title={title} actions={children} />
    </div>
  );
}
export function Panel({
  title,
  subtitle,
  children,
  action,
  className = "",
}: {
  title?: string;
  subtitle?: string;
  children: ReactNode;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <section
      className={cn(
        "im-panel rounded-lg border bg-card text-card-foreground",
        className,
      )}
    >
      {title && (
        <header className="im-panel-heading">
          <h2 className="text-lg font-semibold" title={subtitle}>
            {title}
          </h2>
          {action}
        </header>
      )}
      {children}
    </section>
  );
}
export function SectionTabs({
  value,
  items,
  onChange,
}: {
  value: string;
  items: { id: string; label: string }[];
  onChange: (value: string) => void;
}) {
  return (
    <Tabs value={value} onValueChange={(next) => onChange(String(next))}>
      <TabsList aria-label="Nhóm thông tin">
        {items.map((item) => (
          <TabsTrigger key={item.id} value={item.id}>
            {item.label}
          </TabsTrigger>
        ))}
      </TabsList>
    </Tabs>
  );
}
const tones: Record<string, StatusTone> = {
  blue: "info",
  purple: "info",
  green: "success",
  amber: "warning",
  red: "danger",
  hero: "info",
};
export function Badge({
  children,
  tone = "neutral",
}: {
  children: ReactNode;
  tone?: string;
}) {
  return (
    <StatusBadge className="im-badge" tone={tones[tone] ?? "neutral"}>
      {children}
    </StatusBadge>
  );
}
export function Empty({
  title = "Không có dữ liệu phù hợp",
  description = "Thử thay đổi bộ lọc hoặc kiểm tra lại sau.",
  children,
}: {
  title?: string;
  description?: string;
  children?: ReactNode;
}) {
  return (
    <div className="im-empty">
      <Search className="size-6 text-muted-foreground" aria-hidden />
      <h3 className="font-medium">{title}</h3>
      <p className="text-muted-foreground">{description}</p>
      {children}
    </div>
  );
}
export function ActionLink({
  href,
  children,
  secondary = false,
}: {
  href: string;
  children: ReactNode;
  secondary?: boolean;
}) {
  return (
    <Link
      href={href}
      className={cn(
        buttonVariants({ variant: secondary ? "outline" : "default" }),
        "im-action-link",
      )}
    >
      {children}
    </Link>
  );
}
export function Modal({
  title,
  children,
  close,
}: {
  title: string;
  children: ReactNode;
  close: () => void;
}) {
  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) close();
      }}
    >
      <DialogContent className="max-h-[calc(100dvh-2rem)] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle className="text-lg font-semibold">{title}</DialogTitle>
          <DialogDescription className="sr-only">{title}</DialogDescription>
        </DialogHeader>
        {children}
      </DialogContent>
    </Dialog>
  );
}
export function Pagination({
  page,
  total,
  size = 5,
  onChange,
}: {
  page: number;
  total: number;
  size?: number;
  onChange: (page: number) => void;
}) {
  const pages = Math.max(1, Math.ceil(total / size));
  const current = Math.min(page, pages);
  return (
    <footer className="im-pagination">
      <span>
        {total
          ? `${(current - 1) * size + 1}–${Math.min(current * size, total)} trong ${total} mục`
          : "0 mục"}
      </span>
      <div>
        <Button
          variant="outline"
          size="sm"
          disabled={current <= 1}
          onClick={() => onChange(current - 1)}
        >
          Trước
        </Button>
        <span>
          {current} / {pages}
        </span>
        <Button
          variant="outline"
          size="sm"
          disabled={current >= pages}
          onClick={() => onChange(current + 1)}
        >
          Sau
        </Button>
      </div>
    </footer>
  );
}
export function Info({
  children,
  tone = "blue",
}: {
  children: ReactNode;
  tone?: string;
}) {
  const warning = tone === "amber";
  const danger = tone === "red";
  return (
    <Alert
      variant={danger ? "destructive" : "default"}
      className={cn(
        "im-info",
        warning && "border-warning/30 bg-warning-soft text-warning",
        !warning && !danger && "bg-secondary/50",
      )}
    >
      <Icon name={danger || warning ? "alert" : "shield"} />
      <AlertDescription
        className={cn(
          warning
            ? "text-warning"
            : danger
              ? "text-destructive"
              : "text-muted-foreground",
        )}
      >
        {children}
      </AlertDescription>
    </Alert>
  );
}
export function OrderBadge({ status }: { status: string }) {
  const labels: Record<string, string> = {
    "Pending Payment": "Chờ thanh toán",
    Paid: "Đã thanh toán",
    "Results Available": "Đã có kết quả",
    Cancelled: "Đã hủy",
  };
  return (
    <Badge
      tone={
        status === "Paid"
          ? "blue"
          : status === "Results Available"
            ? "green"
            : status === "Cancelled"
              ? "red"
              : "amber"
      }
    >
      {labels[status] || status}
    </Badge>
  );
}
export function PatientAvatar({ name }: { name: string }) {
  return (
    <span className="im-patient-avatar" aria-hidden>
      {name
        .split(" ")
        .slice(-2)
        .map((n) => n[0])
        .join("")}
    </span>
  );
}
