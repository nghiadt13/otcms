"use client";

import { useEffect, useState } from "react";
import {
  ArrowRight,
  AtSign,
  Beaker,
  Bell,
  CalendarDays,
  ClipboardList,
  FlaskConical,
  Mail,
  ShieldCheck,
  UserRound,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { apiRequest } from "@/lib/api/client";
import {
  fetchCurrentAccount,
  type CurrentAccount,
} from "@/lib/auth/currentAccountApi";

type Room = {
  id: string;
  roomCode: string;
  displayLabel: string;
  type: string;
  active: boolean;
};
type Notice = {
  id: string;
  title: string;
  content: string;
  createdAt: string;
  read: boolean;
};
type Screen = "Phòng dịch vụ" | "Thông báo" | "Tài khoản của tôi";

function formatNoticeDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat("vi-VN", {
        dateStyle: "medium",
        timeStyle: "short",
      }).format(date);
}

export function LaboratoryLiveInfo({
  screen,
  onOpenQueue,
  onOpenSchedule,
}: {
  screen: Screen;
  onOpenQueue?: () => void;
  onOpenSchedule?: () => void;
}) {
  const [rooms, setRooms] = useState<Room[]>([]);
  const [notices, setNotices] = useState<Notice[]>([]);
  const [account, setAccount] = useState<CurrentAccount | null>(null);
  const [error, setError] = useState(false);
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    const request =
      screen === "Phòng dịch vụ"
        ? apiRequest<{ items: Room[] }>("/api/v1/staff/rooms", {
            signal: controller.signal,
          }).then((data) => {
            if (active)
              setRooms(data.items.filter((room) => room.type === "LABORATORY"));
          })
        : screen === "Thông báo"
          ? apiRequest<{ items: Notice[] }>(
              "/api/v1/me/notifications?page=0&size=100",
              { signal: controller.signal },
            ).then((data) => {
              if (active) setNotices(data.items);
            })
          : fetchCurrentAccount().then((data) => {
              if (active) setAccount(data);
            });
    request
      .catch(() => {
        if (active) setError(true);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [screen, revision]);

  if (loading) return <p role="status">Đang tải thông tin...</p>;
  if (error)
    return (
      <section className="flex flex-wrap items-center justify-between gap-4 rounded-xl border bg-card p-5" role="alert">
        <div>
          <h2 className="font-semibold">Không thể tải thông tin</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Kiểm tra kết nối rồi thử tải lại.
          </p>
        </div>
        <Button
          variant="outline"
          onClick={() => {
            setLoading(true);
            setError(false);
            setRevision((value) => value + 1);
          }}
        >
          Thử tải lại
        </Button>
      </section>
    );
  if (screen === "Phòng dịch vụ")
    return (
      <div className="space-y-5">
        <section className="rounded-xl border bg-card p-5 md:p-6">
          <div className="flex flex-col gap-5 md:flex-row md:items-center md:justify-between">
            <div className="flex gap-4">
              <div className="flex size-12 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
                <FlaskConical aria-hidden className="size-6" />
              </div>
              <div className="max-w-2xl">
                <h2 className="font-semibold">Danh sách phòng xét nghiệm</h2>
                <p className="mt-1 text-sm leading-6 text-muted-foreground">
                  Tra cứu các phòng và trạng thái hoạt động. Xem lịch để biết
                  phòng được phân công theo ca; mở danh sách chờ để xử lý chỉ
                  định trong ca làm việc.
                </p>
              </div>
            </div>
            <div className="flex flex-wrap gap-2 md:shrink-0">
              {onOpenSchedule && (
                <Button variant="outline" onClick={onOpenSchedule}>
                  <CalendarDays aria-hidden /> Lịch làm việc
                </Button>
              )}
              {onOpenQueue && (
                <Button onClick={onOpenQueue}>
                  <ClipboardList aria-hidden /> Danh sách chờ
                  <ArrowRight aria-hidden />
                </Button>
              )}
            </div>
          </div>
        </section>

        <section
          aria-label="Tổng quan phòng xét nghiệm"
          className="grid gap-3 sm:grid-cols-3"
        >
          <div className="rounded-xl border bg-card p-4">
            <p className="text-sm text-muted-foreground">Tổng số phòng</p>
            <p className="mt-1 text-2xl font-semibold">{rooms.length}</p>
          </div>
          <div className="rounded-xl border bg-card p-4">
            <p className="text-sm text-muted-foreground">Đang hoạt động</p>
            <p className="mt-1 text-2xl font-semibold">
              {rooms.filter((room) => room.active).length}
            </p>
          </div>
          <div className="rounded-xl border bg-card p-4">
            <p className="text-sm text-muted-foreground">Ngừng hoạt động</p>
            <p className="mt-1 text-2xl font-semibold">
              {rooms.filter((room) => !room.active).length}
            </p>
          </div>
        </section>

        <section aria-labelledby="laboratory-rooms-heading" className="space-y-3">
          <div>
            <h2 id="laboratory-rooms-heading" className="font-semibold">
              Các phòng hiện có
            </h2>
            <p className="mt-1 text-sm text-muted-foreground">
              Trạng thái được cung cấp từ thông tin phòng trên hệ thống.
            </p>
          </div>
          {rooms.length ? (
            <ul className="grid gap-3 md:grid-cols-2">
              {rooms.map((room) => (
                <li key={room.id} className="rounded-xl border bg-card p-5">
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex min-w-0 gap-3">
                      <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-muted text-muted-foreground">
                        <Beaker aria-hidden className="size-5" />
                      </div>
                      <div className="min-w-0">
                        <h3 className="truncate font-semibold">
                          {room.displayLabel}
                        </h3>
                        <p className="mt-1 text-sm text-muted-foreground">
                          Mã phòng: {room.roomCode}
                        </p>
                      </div>
                    </div>
                    <Badge variant={room.active ? "secondary" : "outline"}>
                      {room.active ? "Đang hoạt động" : "Ngừng hoạt động"}
                    </Badge>
                  </div>
                </li>
              ))}
            </ul>
          ) : (
            <div className="rounded-xl border border-dashed bg-card p-8 text-center">
              <FlaskConical
                aria-hidden
                className="mx-auto size-8 text-muted-foreground"
              />
              <h3 className="mt-3 font-semibold">Chưa có phòng xét nghiệm</h3>
              <p className="mx-auto mt-1 max-w-lg text-sm text-muted-foreground">
                Danh sách sẽ hiển thị khi hệ thống có phòng xét nghiệm. Nếu bạn
                cần được phân công phòng, hãy xem lịch làm việc hoặc liên hệ
                quản trị viên.
              </p>
              {onOpenSchedule && (
                <Button
                  className="mt-4"
                  variant="outline"
                  onClick={onOpenSchedule}
                >
                  Xem lịch làm việc
                </Button>
              )}
            </div>
          )}
        </section>
      </div>
    );
  if (screen === "Thông báo")
    return (
      <div className="space-y-5">
        <section className="flex flex-col gap-4 rounded-xl border bg-card p-5 sm:flex-row sm:items-center sm:justify-between md:p-6">
          <div className="flex gap-4">
            <div className="flex size-12 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
              <Bell aria-hidden className="size-6" />
            </div>
            <div>
              <h2 className="font-semibold">Cập nhật dành cho bạn</h2>
              <p className="mt-1 max-w-2xl text-sm leading-6 text-muted-foreground">
                Xem nội dung và thời điểm nhận thông báo. Trạng thái giúp bạn
                nhận biết thông báo nào đã được đọc.
              </p>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3 sm:min-w-52">
            <div className="rounded-lg bg-muted/60 p-3">
              <p className="text-xs text-muted-foreground">Tổng thông báo</p>
              <p className="mt-1 text-xl font-semibold">{notices.length}</p>
            </div>
            <div className="rounded-lg bg-muted/60 p-3">
              <p className="text-xs text-muted-foreground">Chưa đọc</p>
              <p className="mt-1 text-xl font-semibold">
                {notices.filter((notice) => !notice.read).length}
              </p>
            </div>
          </div>
        </section>

        <section aria-labelledby="notifications-heading" className="space-y-3">
          <div>
            <h2 id="notifications-heading" className="font-semibold">
              Danh sách thông báo
            </h2>
            <p className="mt-1 text-sm text-muted-foreground">
              Thông báo mới nhất được hiển thị trước.
            </p>
          </div>
          {notices.length ? (
            <ul className="space-y-3">
              {[...notices]
                .sort(
                  (first, second) =>
                    new Date(second.createdAt).getTime() -
                    new Date(first.createdAt).getTime(),
                )
                .map((notice) => (
                  <li
                    key={notice.id}
                    className={`rounded-xl border bg-card p-5 ${notice.read ? "" : "border-primary/40"}`}
                  >
                    <div className="flex items-start gap-3">
                      <div
                        className={`mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-lg ${notice.read ? "bg-muted text-muted-foreground" : "bg-primary/10 text-primary"}`}
                      >
                        <Bell aria-hidden className="size-4" />
                      </div>
                      <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <h3 className="font-semibold">{notice.title}</h3>
                          <Badge variant={notice.read ? "outline" : "secondary"}>
                            {notice.read ? "Đã đọc" : "Chưa đọc"}
                          </Badge>
                        </div>
                        <p className="mt-2 whitespace-pre-wrap text-sm leading-6">
                          {notice.content}
                        </p>
                        <p className="mt-3 text-xs text-muted-foreground">
                          {formatNoticeDate(notice.createdAt)}
                        </p>
                      </div>
                    </div>
                  </li>
                ))}
            </ul>
          ) : (
            <div className="rounded-xl border border-dashed bg-card p-8 text-center">
              <Bell
                aria-hidden
                className="mx-auto size-8 text-muted-foreground"
              />
              <h3 className="mt-3 font-semibold">Bạn chưa có thông báo</h3>
              <p className="mx-auto mt-1 max-w-lg text-sm text-muted-foreground">
                Khi có thông báo gửi đến tài khoản, nội dung và thời điểm nhận
                sẽ xuất hiện tại đây.
              </p>
            </div>
          )}
        </section>
      </div>
    );
  return (
    <div className="space-y-5">
      <section className="rounded-xl border bg-card p-5 md:p-6">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
          <div className="flex size-14 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary">
            <UserRound aria-hidden className="size-7" />
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-sm text-muted-foreground">Tài khoản đang đăng nhập</p>
            <h2 className="mt-1 truncate text-xl font-semibold">
              {account?.displayName || account?.username || "Tài khoản"}
            </h2>
            <p className="mt-1 text-sm text-muted-foreground">
              {account?.email || "Chưa có email được cung cấp"}
            </p>
          </div>
          <Badge variant="secondary">
            <ShieldCheck aria-hidden /> Kỹ thuật viên xét nghiệm
          </Badge>
        </div>
      </section>

      <section aria-labelledby="account-details-heading" className="space-y-3">
        <div>
          <h2 id="account-details-heading" className="font-semibold">
            Thông tin tài khoản
          </h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Thông tin hồ sơ hiện có trong tài khoản của bạn.
          </p>
        </div>
        <dl className="grid gap-3 sm:grid-cols-2">
          <div className="rounded-xl border bg-card p-5">
            <dt className="flex items-center gap-2 text-sm text-muted-foreground">
              <UserRound aria-hidden className="size-4" /> Họ tên hiển thị
            </dt>
            <dd className="mt-2 font-medium">
              {account?.displayName || "Chưa có thông tin"}
            </dd>
          </div>
          <div className="rounded-xl border bg-card p-5">
            <dt className="flex items-center gap-2 text-sm text-muted-foreground">
              <AtSign aria-hidden className="size-4" /> Tên đăng nhập
            </dt>
            <dd className="mt-2 font-medium">
              {account?.username || "Chưa có thông tin"}
            </dd>
          </div>
          <div className="rounded-xl border bg-card p-5">
            <dt className="flex items-center gap-2 text-sm text-muted-foreground">
              <Mail aria-hidden className="size-4" /> Email
            </dt>
            <dd className="mt-2 break-all font-medium">
              {account?.email || "Chưa có thông tin"}
            </dd>
          </div>
          <div className="rounded-xl border bg-card p-5">
            <dt className="flex items-center gap-2 text-sm text-muted-foreground">
              <ShieldCheck aria-hidden className="size-4" /> Vai trò
            </dt>
            <dd className="mt-2 font-medium">Kỹ thuật viên xét nghiệm</dd>
          </div>
        </dl>
      </section>
    </div>
  );
}
