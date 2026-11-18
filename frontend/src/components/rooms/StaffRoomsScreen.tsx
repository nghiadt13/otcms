"use client";

import { useEffect, useMemo, useState } from "react";
import { PageHeader } from "@/components/PageHeader";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { NativeSelect } from "@/components/ui/native-select";
import { getStaffRoom, listStaffRooms, type StaffRoom } from "@/lib/api/staffRoomsApi";
import { messageFromError } from "@/lib/messages/appMessages";

const typeLabels: Record<string, string> = {
  EXAMINATION: "Phòng khám",
  IMAGING: "Chẩn đoán hình ảnh",
  LABORATORY: "Xét nghiệm",
};

/**
 * UC-64: phòng và tình trạng dùng chung cho lễ tân, bác sĩ, kỹ thuật viên (và shell xét nghiệm của Long).
 * Danh sách đã được BE lọc theo vai trò nên màn hình không tự lọc quyền.
 */
export function StaffRoomsScreen({ embedded = false }: { embedded?: boolean }) {
  const [rooms, setRooms] = useState<StaffRoom[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);
  const [search, setSearch] = useState("");
  const [type, setType] = useState("all");
  const [availability, setAvailability] = useState("all");
  const [detail, setDetail] = useState<StaffRoom | null>(null);
  const [detailError, setDetailError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    listStaffRooms(controller.signal)
      .then((items) => {
        if (controller.signal.aborted) return;
        setRooms(items);
        setError(null);
      })
      .catch((cause: unknown) => {
        if (!controller.signal.aborted) setError(messageFromError(cause));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [reload]);

  const matches = useMemo(
    () =>
      rooms.filter(
        (room) =>
          `${room.name} ${room.code} ${room.capabilities.join(" ")}`.toLowerCase().includes(search.toLowerCase()) &&
          (type === "all" || room.type === type) &&
          (availability === "all" || (availability === "active") === room.active),
      ),
    [rooms, search, type, availability],
  );

  async function openDetail(room: StaffRoom) {
    setDetailError(null);
    setDetail(room);
    try {
      setDetail(await getStaffRoom(room.id));
    } catch (cause) {
      setDetail(null);
      setDetailError(messageFromError(cause));
    }
  }

  return (
    <div className="space-y-4">
      {embedded ? (
        <h2 className="text-lg font-semibold">Phòng và tình trạng</h2>
      ) : (
        <PageHeader title="Phòng và tình trạng" description="Phòng thuộc phạm vi công việc của bạn, loại phòng và tình trạng hoạt động." />
      )}
      <div className="flex flex-wrap gap-3">
        <Input
          aria-label="Tìm phòng hoặc dịch vụ"
          className="max-w-sm"
          placeholder="Tìm phòng, mã phòng hoặc khả năng…"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
        <NativeSelect aria-label="Loại phòng" value={type} onChange={(event) => setType(event.target.value)}>
          <option value="all">Tất cả loại phòng</option>
          {[...new Set(rooms.map((room) => room.type))].map((value) => (
            <option key={value} value={value}>
              {typeLabels[value] ?? value}
            </option>
          ))}
        </NativeSelect>
        <NativeSelect aria-label="Tình trạng" value={availability} onChange={(event) => setAvailability(event.target.value)}>
          <option value="all">Tất cả tình trạng</option>
          <option value="active">Hoạt động</option>
          <option value="inactive">Tạm ngưng</option>
        </NativeSelect>
      </div>
      {loading ? (
        <p role="status">Đang tải danh sách phòng…</p>
      ) : error ? (
        <div role="alert" className="space-y-2">
          <p>{error}</p>
          <Button
            variant="outline"
            onClick={() => {
              setLoading(true);
              setReload((value) => value + 1);
            }}
          >
            Thử lại
          </Button>
        </div>
      ) : matches.length === 0 ? (
        <p role="status" className="text-muted-foreground">
          Không có phòng phù hợp.
        </p>
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {matches.map((room) => (
            <li key={room.id} className="space-y-2 rounded-lg border bg-card p-4">
              <div className="flex items-start justify-between gap-2">
                <h2 className="font-medium">{room.name}</h2>
                <StatusBadge tone={room.active ? "success" : "warning"}>{room.active ? "Hoạt động" : "Tạm ngưng"}</StatusBadge>
              </div>
              <p className="text-sm text-muted-foreground">
                {room.code} · {typeLabels[room.type] ?? room.type}
              </p>
              {room.capabilities.length > 0 && <p className="text-sm">{room.capabilities.join(", ")}</p>}
              <Button variant="ghost" size="sm" onClick={() => void openDetail(room)}>
                Chi tiết
              </Button>
            </li>
          ))}
        </ul>
      )}
      {detailError && (
        <p role="alert" className="text-destructive">
          {detailError}
        </p>
      )}
      {detail && (
        <section aria-label="Chi tiết phòng" className="space-y-1 rounded-lg border bg-card p-4">
          <h2 className="font-medium">{detail.name}</h2>
          <dl className="grid grid-cols-[10rem_1fr] gap-1 text-sm">
            <dt>Mã phòng</dt>
            <dd>{detail.code}</dd>
            <dt>Loại phòng</dt>
            <dd>{typeLabels[detail.type] ?? detail.type}</dd>
            <dt>Mô tả</dt>
            <dd>{detail.description || "Chưa cập nhật"}</dd>
            <dt>Dịch vụ hỗ trợ</dt>
            <dd>{detail.supportedServiceCount}</dd>
            <dt>Tình trạng</dt>
            <dd>{detail.active ? "Hoạt động" : "Tạm ngưng"}</dd>
          </dl>
          <Button variant="outline" size="sm" onClick={() => setDetail(null)}>
            Đóng
          </Button>
        </section>
      )}
    </div>
  );
}
