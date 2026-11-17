"use client";

import { useEffect, useState } from "react";
import { apiRequest } from "@/lib/api/client";
import { fetchCurrentAccount, type CurrentAccount } from "@/lib/auth/currentAccountApi";

type Room = { id: string; roomCode: string; displayLabel: string; type: string; active: boolean };
type Notice = { id: string; title: string; content: string; createdAt: string; read: boolean };
type Screen = "Phòng dịch vụ" | "Thông báo" | "Tài khoản của tôi";

export function LaboratoryLiveInfo({ screen }: { screen: Screen }) {
  const [rooms, setRooms] = useState<Room[]>([]);
  const [notices, setNotices] = useState<Notice[]>([]);
  const [account, setAccount] = useState<CurrentAccount | null>(null);
  const [error, setError] = useState(false);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    const request = screen === "Phòng dịch vụ"
      ? apiRequest<{ items: Room[] }>("/api/v1/staff/rooms", { signal: controller.signal }).then((data) => { if (active) setRooms(data.items.filter((room) => room.type === "LABORATORY")); })
      : screen === "Thông báo"
        ? apiRequest<{ items: Notice[] }>("/api/v1/me/notifications?page=0&size=100", { signal: controller.signal }).then((data) => { if (active) setNotices(data.items); })
        : fetchCurrentAccount().then((data) => { if (active) setAccount(data); });
    request.catch(() => { if (active) setError(true); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; controller.abort(); };
  }, [screen]);

  if (loading) return <p role="status">Đang tải thông tin...</p>;
  if (error) return <p role="alert">Không thể tải thông tin. Vui lòng thử lại sau.</p>;
  if (screen === "Phòng dịch vụ") return <section className="divide-y rounded-lg border bg-card">{rooms.length ? rooms.map((room) => <div key={room.id} className="p-4"><strong>{room.displayLabel}</strong><p className="text-sm text-muted-foreground">{room.roomCode} · {room.active ? "Đang hoạt động" : "Ngừng hoạt động"}</p></div>) : <p className="p-4">Chưa có phòng xét nghiệm.</p>}</section>;
  if (screen === "Thông báo") return <section className="divide-y rounded-lg border bg-card">{notices.length ? notices.map((notice) => <div key={notice.id} className="p-4"><strong>{notice.title}</strong><p>{notice.content}</p><p className="text-sm text-muted-foreground">{new Date(notice.createdAt).toLocaleString("vi-VN")} · {notice.read ? "Đã đọc" : "Chưa đọc"}</p></div>) : <p className="p-4">Chưa có thông báo.</p>}</section>;
  return <section className="space-y-2 rounded-lg border bg-card p-5"><p>Họ tên: {account?.displayName || "Chưa có"}</p><p>Tên đăng nhập: {account?.username || "Chưa có"}</p><p>Email: {account?.email || "Chưa có"}</p><p>Vai trò: Kỹ thuật viên xét nghiệm</p></section>;
}
