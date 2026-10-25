"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState, type ReactNode } from "react";
import { ImagingProvider, useImaging } from "../ImagingProvider";
import { ROOM_NAME } from "../demoData";
import { Icon } from "./ImagingUi";
import "../imaging.css";

const sections = [
  {
    title: "KHÔNG GIAN LÀM VIỆC",
    links: [
      ["/imaging", "Tổng quan", "grid"],
      ["/imaging/worklist", "Danh sách chờ", "queue"],
      ["/imaging/orders", "Chỉ định hình ảnh", "file"],
      ["/imaging/status", "Trạng thái chỉ định", "activity"],
      ["/imaging/visits", "Tiến trình lượt khám", "layers"],
    ],
  },
  {
    title: "THÔNG TIN CÔNG VIỆC",
    links: [
      ["/imaging/rooms", "Phòng dịch vụ", "room"],
      ["/imaging/schedule", "Lịch làm việc", "calendar"],
      ["/imaging/notifications", "Thông báo", "bell"],
    ],
  },
  {
    title: "CÁ NHÂN",
    links: [
      ["/imaging/profile", "Tài khoản của tôi", "user"],
      ["/imaging/change-password", "Đổi mật khẩu", "lock"],
    ],
  },
];
function ShellContent({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const { state, toast, reset, mustChangePassword, ready } = useImaging();
  const [menuOpen, setMenuOpen] = useState(false);
  const [resetOpen, setResetOpen] = useState(false);
  const unread = state.notifications.filter((n) => !n.read).length;
  if (!ready)
    return (
      <div className="imaging">
        <main className="im-content" role="status">
          Đang mở không gian làm việc…
        </main>
      </div>
    );
  if (pathname === "/imaging/login")
    return <div className="imaging">{children}</div>;
  if (mustChangePassword && pathname !== "/imaging/change-password")
    return (
      <div className="imaging">
        <main className="im-content">
          <h1>Cần đổi mật khẩu được cấp</h1>
          <p>Hoàn tất đổi mật khẩu trước khi sử dụng không gian làm việc.</p>
          <Link className="im-button" href="/imaging/change-password">
            Đổi mật khẩu
          </Link>
        </main>
      </div>
    );
  const active = (href: string) =>
    href === "/imaging" ? pathname === href : pathname.startsWith(href);
  const currentItem = sections
    .flatMap((s) => s.links)
    .filter(([href]) => active(href))
    .at(-1);
  const current = currentItem?.[1] || "Chỉ định hình ảnh";
  return (
    <div className="imaging">
      <a className="im-skip" href="#im-content">
        Chuyển đến nội dung
      </a>
      {menuOpen && (
        <button
          className="im-sidebar-overlay"
          aria-label="Đóng menu"
          onClick={() => setMenuOpen(false)}
        />
      )}
      <aside className={`im-sidebar ${menuOpen ? "im-sidebar-open" : ""}`}>
        <Link href="/imaging" className="im-brand">
          <span className="im-brand-mark">+</span>
          <div>
            <strong>
              OTCMS<span>CARE MANAGEMENT</span>
            </strong>
          </div>
        </Link>
        <div className="im-role">
          <span className="im-role-icon">
            <Icon name="layers" />
          </span>
          <div>
            <strong>Chẩn đoán hình ảnh</strong>
            <span>Không gian kỹ thuật viên</span>
          </div>
        </div>
        <nav aria-label="Điều hướng kỹ thuật viên">
          {sections.map((section) => (
            <div className="im-nav-section" key={section.title}>
              <p>{section.title}</p>
              {section.links.map(([href, label, icon]) => (
                <Link
                  key={href}
                  href={href}
                  className={`im-nav-link ${active(href) ? "im-nav-active" : ""}`}
                  aria-current={active(href) ? "page" : undefined}
                  onClick={() => setMenuOpen(false)}
                >
                  <Icon name={icon} />
                  <span>{label}</span>
                  {href.endsWith("notifications") && unread > 0 && (
                    <b>{unread}</b>
                  )}
                </Link>
              ))}
            </div>
          ))}
        </nav>
        <div className="im-sidebar-bottom">
          <div className="im-room-dot" />
          <div>
            <strong>{ROOM_NAME}</strong>
            <span>Ca sáng · 07:00 – 12:00</span>
          </div>
        </div>
        <Link className="im-logout" href="/imaging/login">
          <Icon name="logout" />
          Đăng xuất
        </Link>
      </aside>
      <div className="im-main-shell">
        <header className="im-topbar">
          <div className="im-breadcrumb">
            <button
              className="im-icon-button im-mobile-menu"
              aria-label="Mở menu"
              onClick={() => setMenuOpen(true)}
            >
              <Icon name="menu" />
            </button>
            <span>Không gian làm việc</span>
            <span>/</span>
            <Link
              href={currentItem?.[0] || "/imaging/orders"}
              aria-label={`Quay lại ${current.toLowerCase()}`}
            >
              {current}
            </Link>
          </div>
          <div className="im-topbar-actions">
            <span className="im-preview-pill">Dữ liệu minh họa</span>
            <Link
              href="/imaging/orders"
              className="im-icon-button"
              aria-label="Tra cứu chỉ định"
            >
              <Icon name="search" />
            </Link>
            <Link
              className="im-icon-button im-notification-bell"
              href="/imaging/notifications"
              aria-label={`Thông báo, ${unread} chưa đọc`}
            >
              <Icon name="bell" />
              {unread > 0 && <i />}
            </Link>
            <div className="im-topbar-divider" />
            <Link href="/imaging/profile" className="im-account">
              <span className="im-avatar">
                {state.profile.avatar ? (
                  <Image
                    src={state.profile.avatar}
                    alt="Ảnh đại diện"
                    width={38}
                    height={38}
                    unoptimized
                  />
                ) : (
                  "MA"
                )}
              </span>
              <div>
                <strong>{state.profile.name}</strong>
                <span>Kỹ thuật viên CĐHA</span>
              </div>
            </Link>
          </div>
        </header>
        <main id="im-content" className="im-content">
          {children}
        </main>
        <footer className="im-footer">
          <span>© 2026 OTCMS · Phòng khám chấn thương chỉnh hình</span>
          <button
            onClick={() => setResetOpen(!resetOpen)}
            className="im-text-button"
          >
            Khôi phục dữ liệu mẫu
          </button>
          {resetOpen && (
            <span className="im-reset-confirm">
              Các thao tác xem thử sẽ được xóa.{" "}
              <button
                className="im-text-button"
                onClick={() => {
                  reset();
                  setResetOpen(false);
                }}
              >
                Xác nhận
              </button>
              <button
                className="im-text-button"
                onClick={() => setResetOpen(false)}
              >
                Giữ lại
              </button>
            </span>
          )}
        </footer>
      </div>
      {toast && (
        <div className="im-toast" role="status" aria-live="polite">
          <Icon name="check" />
          <span>{toast}</span>
        </div>
      )}
    </div>
  );
}
export function ImagingShell({ children }: { children: ReactNode }) {
  return (
    <ImagingProvider>
      <ShellContent>{children}</ShellContent>
    </ImagingProvider>
  );
}
