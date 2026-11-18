"use client";

import { Input } from "@/components/ui/input";
import { NativeSelect } from "@/components/ui/native-select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { messageFromError } from "@/lib/messages/appMessages";
import {
  listAssignedImagingUploadWork,
  type AssignedImagingUploadWork,
} from "../api/imagingIngestApi";
import { listImagingRooms } from "../api/imagingRoomApi";
import { Badge } from "./Badge";
import { Empty } from "./Empty";
import { Icon } from "./Icon";
import { PageHeading } from "./PageHeading";
import { Panel } from "./Panel";
import { PatientAvatar } from "./PatientAvatar";

/** UC-52 (chụp): trạng thái từng chỉ định của phòng từ API thật. Chỉ định chưa thanh toán không vào phòng (D7). */
const statusLabels: Record<AssignedImagingUploadWork["status"], string> = {
  PENDING: "Đã chỉ định",
  IN_PROGRESS: "Đang thực hiện",
  RESULT_AVAILABLE: "Đã có kết quả",
};

const statusTones: Record<AssignedImagingUploadWork["status"], string> = {
  PENDING: "amber",
  IN_PROGRESS: "blue",
  RESULT_AVAILABLE: "green",
};

function dayOf(value: string) {
  return value.slice(0, 10);
}

export function OrderListScreen({ statusMode = false }: { statusMode?: boolean }) {
  const [items, setItems] = useState<AssignedImagingUploadWork[]>([]);
  const [roomNames, setRoomNames] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("all");
  const [room, setRoom] = useState("all");
  const [day, setDay] = useState("");

  useEffect(() => {
    const controller = new AbortController();
    Promise.all([
      listAssignedImagingUploadWork(0, 100, controller.signal),
      listImagingRooms(controller.signal).catch(() => []),
    ])
      .then(([page, rooms]) => {
        if (controller.signal.aborted) return;
        setItems(page.items);
        setRoomNames(Object.fromEntries(rooms.map((item) => [item.id, item.name])));
      })
      .catch((cause: unknown) => {
        if (!controller.signal.aborted) setError(messageFromError(cause));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, []);

  const matches = useMemo(
    () =>
      items.filter(
        (item) =>
          `${item.requestId} ${item.patient.fullName} ${item.serviceName ?? ""}`
            .toLowerCase()
            .includes(search.toLowerCase()) &&
          (status === "all" || item.status === status) &&
          (room === "all" || item.roomId === room) &&
          (!day || dayOf(item.createdAt) === day),
      ),
    [items, search, status, room, day],
  );
  const rooms = [...new Set(items.map((item) => item.roomId))];

  return (
    <>
      <PageHeading
        title={statusMode ? "Trạng thái chỉ định" : "Chỉ định chẩn đoán hình ảnh"}
        description="Theo dõi từng chỉ định của phòng từ khi vào phòng đến khi có kết quả. Chỉ định chưa thanh toán không xuất hiện ở đây."
      />
      <Panel title="Theo dõi chỉ định của phòng" subtitle="Dữ liệu trực tiếp từ hệ thống">
        <div className="im-toolbar im-toolbar-wrap">
          <label className="im-search">
            <Icon name="search" />
            <Input
              aria-label="Tìm chỉ định"
              placeholder="Mã chỉ định, tên bệnh nhân hoặc dịch vụ…"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </label>
          <label className="im-filter">
            <span>Trạng thái</span>
            <NativeSelect aria-label="Lọc trạng thái" value={status} onChange={(event) => setStatus(event.target.value)}>
              <option value="all">Tất cả trạng thái</option>
              {Object.entries(statusLabels).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </NativeSelect>
          </label>
          <label className="im-filter">
            <span>Phòng</span>
            <NativeSelect aria-label="Lọc phòng" value={room} onChange={(event) => setRoom(event.target.value)}>
              <option value="all">Tất cả phòng</option>
              {rooms.map((id) => (
                <option key={id} value={id}>
                  {roomNames[id] ?? id}
                </option>
              ))}
            </NativeSelect>
          </label>
          <label className="im-filter">
            <span>Ngày</span>
            <Input aria-label="Lọc ngày" type="date" value={day} onChange={(event) => setDay(event.target.value)} />
          </label>
        </div>
        {loading ? (
          <p role="status" className="p-5">
            Đang tải trạng thái chỉ định…
          </p>
        ) : error ? (
          <p role="alert" className="im-form-error p-5">
            {error}
          </p>
        ) : matches.length === 0 ? (
          <Empty />
        ) : (
          <div className="im-table-scroll">
            <Table className="im-table">
              <TableHeader>
                <TableRow>
                  <TableHead>Mã chỉ định</TableHead>
                  <TableHead>Bệnh nhân</TableHead>
                  <TableHead>Dịch vụ</TableHead>
                  <TableHead>Thanh toán</TableHead>
                  <TableHead>Trạng thái</TableHead>
                  <TableHead>Thao tác</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {matches.map((item) => (
                  <TableRow key={item.requestId}>
                    <TableCell>
                      <span className="im-id-link">{item.requestId.slice(0, 8)}</span>
                      <small>{new Date(item.createdAt).toLocaleString("vi-VN")}</small>
                    </TableCell>
                    <TableCell>
                      <div className="im-patient-cell">
                        <PatientAvatar name={item.patient.fullName} />
                        <strong>{item.patient.fullName}</strong>
                      </div>
                    </TableCell>
                    <TableCell>
                      <strong>{item.serviceName ?? item.imagingType}</strong>
                      <small>{roomNames[item.roomId] ?? ""}</small>
                    </TableCell>
                    <TableCell>
                      <Badge tone="green">Đã thanh toán</Badge>
                    </TableCell>
                    <TableCell>
                      <Badge tone={statusTones[item.status]}>{statusLabels[item.status]}</Badge>
                    </TableCell>
                    <TableCell>
                      <Link className="im-text-button" href={`/imaging/orders/${encodeURIComponent(item.requestId)}`}>
                        Chi tiết <Icon name="arrow" size={14} />
                      </Link>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
      </Panel>
    </>
  );
}
