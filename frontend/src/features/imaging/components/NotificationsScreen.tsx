"use client";

import { Button } from "@/components/ui/button";
import Link from "next/link";
import { useState } from "react";
import { useImaging } from "../hooks/useImaging";
import { ActionLink } from "./ActionLink";
import { Badge } from "./Badge";
import { Empty } from "./Empty";
import { Icon } from "./Icon";
import { PageHeading } from "./PageHeading";
import { Panel } from "./Panel";

export function NotificationsScreen({ id }: { id?: string }) {
  const { state, markRead } = useImaging();
  const [filter, setFilter] = useState("all");
  if (id) {
    const n = state.notifications.find((n) => n.id === id);
    if (!n)
      return (
        <Empty title="Thông báo không khả dụng">
          <ActionLink href="/imaging/notifications">
            Về danh sách thông báo
          </ActionLink>
        </Empty>
      );
    return (
      <>
        <Link href="/imaging/notifications" className="im-back-link">
          <Icon name="back" size={16} />
          Danh sách thông báo
        </Link>
        <PageHeading
          eyebrow="THÔNG BÁO CỦA TÔI"
          title="Chi tiết thông báo"
          description="Nội dung gửi riêng đến tài khoản của bạn."
        />
        <Panel>
          <article className="im-notification-detail">
            <div className="im-inline-between">
              <Badge tone="blue">{n.category}</Badge>
              <Badge tone={n.read ? "neutral" : "amber"}>
                {n.read ? "Đã đọc" : "Chưa đọc"}
              </Badge>
            </div>
            <h2>{n.title}</h2>
            <p className="im-muted im-small">24/10/2026 · {n.time}</p>
            <p>{n.text}</p>
            <div className="im-row-actions">
              {n.href && (
                <ActionLink secondary href={n.href}>
                  Xem thông tin liên quan <Icon name="arrow" size={15} />
                </ActionLink>
              )}
              {!n.read && (
                <Button className="im-button" onClick={() => markRead(n.id)}>
                  <Icon name="check" />
                  Đánh dấu đã đọc
                </Button>
              )}
            </div>
          </article>
        </Panel>
      </>
    );
  }
  const matches = state.notifications.filter(
    (n) => filter === "all" || (filter === "unread" ? !n.read : n.read),
  );
  return (
    <>
      <PageHeading
        eyebrow="THÔNG BÁO CỦA TÔI"
        title="Thông báo"
        description="Lịch làm việc, thông tin phòng và các cập nhật liên quan đến bạn."
      />
      <Panel>
        <div className="im-tabs" role="tablist" aria-label="Lọc thông báo">
          {[
            ["all", "Tất cả"],
            ["unread", "Chưa đọc"],
            ["read", "Đã đọc"],
          ].map(([value, label]) => (
            <Button
              variant="ghost"
              role="tab"
              aria-selected={filter === value}
              className={filter === value ? "im-tab-active" : ""}
              key={value}
              onClick={() => setFilter(value)}
            >
              {label}
              <span>
                {
                  state.notifications.filter(
                    (n) =>
                      value === "all" ||
                      (value === "unread" ? !n.read : n.read),
                  ).length
                }
              </span>
            </Button>
          ))}
        </div>
        <div className="im-notification-list">
          {matches.map((n) => (
            <Link
              className={!n.read ? "im-unread" : ""}
              key={n.id}
              href={`/imaging/notifications/${n.id}`}
            >
              <span className="im-quick-icon">
                <Icon
                  name={n.category === "Lịch làm việc" ? "calendar" : "bell"}
                />
              </span>
              <div>
                <strong>
                  {n.title}
                  {!n.read && <i />}
                </strong>
                <p>{n.text}</p>
                <small>
                  {n.category} · 24/10/2026, {n.time}
                </small>
              </div>
              <Icon name="arrow" size={16} />
            </Link>
          ))}
        </div>
        {!matches.length && (
          <Empty
            title="Không có thông báo"
            description="Các thông báo thuộc bộ lọc này sẽ hiển thị tại đây."
          />
        )}
      </Panel>
    </>
  );
}
