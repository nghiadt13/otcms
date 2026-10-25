"use client";

import Link from "next/link";
import { useEffect, useRef, type ReactNode } from "react";

const paths: Record<string, string> = {
  grid: "M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z",
  queue: "M8 6h13 M8 12h13 M8 18h13 M3 6h.01 M3 12h.01 M3 18h.01",
  search: "M21 21l-5-5 M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0",
  file: "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z M14 2v6h6 M8 13h8 M8 17h5",
  upload: "M12 16V3 M7 8l5-5 5 5 M4 16v4h16v-4",
  clock: "M12 8v5l3 2 M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0",
  check: "M5 12l4 4L19 6",
  bell: "M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9 M10 21h4",
  calendar: "M4 5h16v16H4z M4 10h16 M8 2v6 M16 2v6",
  room: "M3 21h18 M6 21V3h12v18 M10 7h4 M10 11h4 M10 21v-6h4v6",
  user: "M20 21v-2a7 7 0 0 0-14 0v2 M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0",
  arrow: "M5 12h14 M13 6l6 6-6 6",
  back: "M19 12H5 M11 6l-6 6 6 6",
  logout: "M9 3H4v18h5 M10 12h11 M17 8l4 4-4 4",
  menu: "M3 6h18 M3 12h18 M3 18h18",
  close: "M6 6l12 12 M18 6 6 18",
  shield: "M12 2l9 4v6c0 5-9 10-9 10S3 17 3 12V6z M8 12l3 3 5-6",
  activity: "M2 12h5l3-9 4 18 3-9h5",
  refresh: "M20 7V2l-4 4 M20 7A9 9 0 1 0 21 16",
  alert: "M12 3 2 21h20z M12 9v5 M12 18h.01",
  layers: "M12 3 2 8l10 5 10-5z M2 12l10 5 10-5 M2 16l10 5 10-5",
  lock: "M5 10h14v12H5z M8 10V6a4 4 0 0 1 8 0v4",
};
export function Icon({ name, size = 20 }: { name: string; size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d={paths[name] || paths.file} />
    </svg>
  );
}
export function PageHeading({
  eyebrow,
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
    <header className="im-page-heading">
      <div>
        <h1 title={eyebrow ? `${eyebrow} · ${description}` : description}>
          {title}
        </h1>
      </div>
      {children && <div className="im-heading-actions">{children}</div>}
    </header>
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
    <section className={`im-panel ${className}`}>
      {title && (
        <header className="im-panel-heading">
          <div>
            <h2 title={subtitle}>{title}</h2>
          </div>
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
    <nav className="im-section-tabs" aria-label="Nhóm thông tin">
      {items.map((item) => (
        <button
          type="button"
          key={item.id}
          aria-pressed={value === item.id}
          className={value === item.id ? "im-section-active" : ""}
          onClick={() => onChange(item.id)}
        >
          {item.label}
        </button>
      ))}
    </nav>
  );
}
export function Badge({
  children,
  tone = "neutral",
}: {
  children: ReactNode;
  tone?: string;
}) {
  return <span className={`im-badge im-badge-${tone}`}>{children}</span>;
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
      <span>
        <Icon name="search" size={28} />
      </span>
      <h3>{title}</h3>
      <p>{description}</p>
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
      className={`im-button ${secondary ? "im-button-secondary" : ""}`}
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
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const dialog = ref.current;
    dialog?.showModal();
    return () => dialog?.close();
  }, []);
  return (
    <dialog ref={ref} className="im-modal" onCancel={close}>
      <header>
        <h2>{title}</h2>
        <button
          className="im-icon-button"
          aria-label="Đóng hộp thoại"
          onClick={close}
        >
          <Icon name="close" />
        </button>
      </header>
      {children}
    </dialog>
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
        <button
          className="im-button im-button-secondary im-button-small"
          disabled={current <= 1}
          onClick={() => onChange(current - 1)}
        >
          Trước
        </button>
        <span>
          {current} / {pages}
        </span>
        <button
          className="im-button im-button-secondary im-button-small"
          disabled={current >= pages}
          onClick={() => onChange(current + 1)}
        >
          Sau
        </button>
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
  return (
    <div className={`im-info im-info-${tone}`}>
      <Icon name={tone === "red" || tone === "amber" ? "alert" : "shield"} />
      <div>{children}</div>
    </div>
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
    <span className="im-patient-avatar" aria-hidden="true">
      {name
        .split(" ")
        .slice(-2)
        .map((n) => n[0])
        .join("")}
    </span>
  );
}
