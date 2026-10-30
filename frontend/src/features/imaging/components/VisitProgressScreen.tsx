"use client";

import { NativeSelect } from "@/components/ui/native-select";
import {
  Table,
  TableHeader,
  TableRow,
  TableHead,
  TableBody,
  TableCell,
} from "@/components/ui/table";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useImaging } from "../hooks/useImaging";
import { CURRENT_ROOM, rooms, visitLabels } from "../api/imagingMockData";
import { ActionLink } from "./ActionLink";
import { Badge } from "./Badge";
import { Empty } from "./Empty";
import { Icon } from "./Icon";
import { OrderBadge } from "./OrderBadge";
import { PageHeading } from "./PageHeading";
import { Panel } from "./Panel";
import { SectionTabs } from "./SectionTabs";

export function VisitProgressScreen({ id = "LK-001" }: { id?: string }) {
  const { state } = useImaging();
  const router = useRouter();
  const [section, setSection] = useState("overview");
  const permitted = state.visits.filter((v) =>
    state.orders.some((o) => o.visitId === v.id && o.room === CURRENT_ROOM),
  );
  const visit = permitted.find((v) => v.id === id);
  if (!visit)
    return (
      <Empty
        title="Không thể mở lượt khám"
        description="Lượt khám không tồn tại hoặc không liên quan đến công việc của phòng."
      >
        <ActionLink href="/imaging/visits">Quay lại tiến trình</ActionLink>
      </Empty>
    );
  const orders = state.orders.filter((o) => o.visitId === id);
  const latest = orders.filter(
    (o) => o.round === visit.latestRound && o.status !== "Cancelled",
  );
  const complete = latest.filter(
    (o) => o.status === "Results Available",
  ).length;
  const deadline = new Date(`${visit.firstCheckIn}T00:00:00Z`);
  deadline.setUTCDate(deadline.getUTCDate() + 6);
  return (
    <>
      <PageHeading
        eyebrow="THEO DÕI QUA CÁC PHÒNG"
        title="Tiến trình lượt khám"
        description="Theo dõi giai đoạn, hàng đợi và trạng thái chỉ định trong phạm vi công việc."
      />
      <Panel>
        <div className="im-toolbar">
          <label className="im-field im-inline-field">
            Lượt khám
            <NativeSelect
              aria-label="Chọn lượt khám"
              value={id}
              onChange={(e) => router.push(`/imaging/visits/${e.target.value}`)}
            >
              {permitted.map((v) => (
                <option key={v.id} value={v.id}>
                  {v.id} · {v.patient}
                </option>
              ))}
            </NativeSelect>
          </label>
          <Badge
            tone={
              visit.status === "Completed"
                ? "green"
                : visit.status === "Incomplete"
                  ? "amber"
                  : "blue"
            }
          >
            {visitLabels[visit.status]}
          </Badge>
        </div>
      </Panel>
      <SectionTabs
        value={section}
        onChange={setSection}
        items={[
          { id: "overview", label: "Tổng quan & phòng" },
          { id: "orders", label: `Chỉ định (${orders.length})` },
        ]}
      />
      {section === "overview" && (
        <>
          <div className="im-two-column">
            <Panel
              title={visit.patient}
              subtitle={`${visit.patientId} · ${visit.id}`}
            >
              <dl className="im-details">
                <div>
                  <dt>Bác sĩ phụ trách</dt>
                  <dd>{visit.doctor}</dd>
                </div>
                <div>
                  <dt>Phòng đọc kết quả</dt>
                  <dd>{visit.doctorRoom}</dd>
                </div>
                <div>
                  <dt>Check-in lần đầu</dt>
                  <dd>{visit.firstCheckIn.split("-").reverse().join("/")}</dd>
                </div>
                <div>
                  <dt>Hạn 7 ngày của lượt khám</dt>
                  <dd>
                    Hết ngày{" "}
                    {deadline.toLocaleDateString("vi-VN", { timeZone: "UTC" })}
                  </dd>
                </div>
                <div>
                  <dt>Đợt chỉ định hiện tại</dt>
                  <dd>Đợt {visit.latestRound}</dd>
                </div>
              </dl>
            </Panel>
            <Panel title="Tổng hợp đợt chỉ định hiện tại">
              <div className="im-panel-body">
                <div className="im-progress-summary">
                  <strong>
                    {complete}
                    <small> / {latest.length}</small>
                  </strong>
                  <span>chỉ định không bị hủy đã có kết quả</span>
                </div>
                <progress
                  className="im-progress-bar"
                  max={Math.max(latest.length, 1)}
                  value={complete}
                />
                <div className="im-review-state">
                  <Icon name={visit.reviewQueued ? "check" : "clock"} />
                  <div>
                    <strong>
                      {visit.status === "Completed"
                        ? "Lượt khám đã hoàn tất"
                        : visit.status === "Incomplete"
                          ? "Lượt khám đã hết thời hạn"
                          : visit.reviewQueued
                            ? "Đã vào hàng đợi đọc kết quả"
                            : "Chưa vào hàng đợi đọc kết quả"}
                    </strong>
                    <p>
                      {visit.reviewQueued
                        ? `Phòng ${visit.doctorRoom} · đã ghi nhận thông báo bệnh nhân (minh họa).`
                        : "Hệ thống kiểm tra kết quả của tất cả chỉ định không bị hủy trong đợt mới nhất."}
                    </p>
                  </div>
                </div>
              </div>
            </Panel>
          </div>
          <Panel
            title="Tiến trình theo phòng"
            subtitle="Chỉ hiển thị số thứ tự và trạng thái, không hiển thị nội dung kết quả"
          >
            <div className="im-room-progress">
              {[...new Set(orders.map((o) => o.room))].map((room) => {
                const q = state.queue.find(
                  (q) => q.visitId === id && q.room === room,
                );
                const roomOrders = orders.filter((o) => o.room === room);
                const done = roomOrders.every((o) =>
                  ["Results Available", "Cancelled"].includes(o.status),
                );
                return (
                  <article key={room}>
                    <span
                      className={`im-room-progress-icon ${done ? "im-check-ok" : ""}`}
                    >
                      <Icon name={done ? "check" : "room"} />
                    </span>
                    <div>
                      <strong>
                        {rooms.find((r) => r.id === room)?.name || room}
                      </strong>
                      <p>
                        {q
                          ? `Số thứ tự ${q.number}`
                          : "Chỉ định dịch vụ liên quan"}
                      </p>
                    </div>
                    <Badge
                      tone={
                        done
                          ? "green"
                          : q?.status === "Being Served"
                            ? "blue"
                            : "neutral"
                      }
                    >
                      {done
                        ? "Đã có kết quả"
                        : q?.status === "Being Served"
                          ? "Đang được gọi"
                          : q?.status === "Absent"
                            ? "Vắng mặt"
                            : "Đang chờ"}
                    </Badge>
                    {q?.status === "Waiting" && (
                      <small>
                        Số người phía trước:{" "}
                        {
                          state.queue.filter(
                            (x) =>
                              x.room === room &&
                              x.status === "Waiting" &&
                              (x.priority < q.priority ||
                                (x.priority === q.priority &&
                                  x.joinedAt < q.joinedAt)),
                          ).length
                        }
                      </small>
                    )}
                  </article>
                );
              })}
            </div>
          </Panel>
        </>
      )}
      {section === "orders" && (
        <Panel title="Trạng thái từng chỉ định">
          <div className="im-table-scroll">
            <Table className="im-table">
              <TableHeader>
                <TableRow>
                  <TableHead>Mã chỉ định</TableHead>
                  <TableHead>Dịch vụ</TableHead>
                  <TableHead>Phòng</TableHead>
                  <TableHead>Đợt</TableHead>
                  <TableHead>Trạng thái</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {orders.map((o) => (
                  <TableRow key={o.id}>
                    <TableCell>
                      {o.room === CURRENT_ROOM ? (
                        <Link
                          className="im-id-link"
                          href={`/imaging/orders/${o.id}`}
                        >
                          {o.id}
                        </Link>
                      ) : (
                        o.id
                      )}
                    </TableCell>
                    <TableCell>{o.service}</TableCell>
                    <TableCell>{o.room}</TableCell>
                    <TableCell>Đợt {o.round}</TableCell>
                    <TableCell>
                      <OrderBadge status={o.status} />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </Panel>
      )}
    </>
  );
}
