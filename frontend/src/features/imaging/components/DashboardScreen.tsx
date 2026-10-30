"use client";

import { Button } from "@/components/ui/button";
import Link from "next/link";
import { useImaging } from "../hooks/useImaging";
import { CURRENT_ROOM, ROOM_NAME } from "../api/imagingMockData";
import { ActionLink } from "./ActionLink";
import { Badge } from "./Badge";
import { Empty } from "./Empty";
import { Icon } from "./Icon";
import { PageHeading } from "./PageHeading";
import { Panel } from "./Panel";
import { PatientAvatar } from "./PatientAvatar";

export function DashboardScreen() {
  const { state, callNext } = useImaging();
  const orders = state.orders.filter((o) => o.room === CURRENT_ROOM);
  const waiting = state.queue
    .filter((q) => q.room === CURRENT_ROOM && q.status === "Waiting")
    .sort(
      (a, b) => a.priority - b.priority || a.joinedAt.localeCompare(b.joinedAt),
    );
  const serving = state.queue.find(
    (q) => q.room === CURRENT_ROOM && q.status === "Being Served",
  );
  const stats = [
    {
      label: "Bệnh nhân đang chờ",
      value: waiting.length,
      icon: "queue",
      tone: "blue",
      sub: "Trong hàng đợi của phòng",
    },
    {
      label: "Đang phục vụ",
      value: serving ? 1 : 0,
      icon: "user",
      tone: "purple",
      sub: serving?.number || "Sẵn sàng gọi bệnh nhân",
    },
    {
      label: "Chỉ định đã có kết quả",
      value: orders.filter((o) => o.status === "Results Available").length,
      icon: "check",
      tone: "green",
      sub: "Study đã được gửi",
    },
    {
      label: "Bệnh nhân vắng mặt",
      value: state.queue.filter(
        (q) => q.room === CURRENT_ROOM && q.status === "Absent",
      ).length,
      icon: "clock",
      tone: "amber",
      sub: "Xem lại trong danh sách chờ",
    },
  ];
  return (
    <>
      <PageHeading
        eyebrow="THỨ BẢY, 24 THÁNG 10, 2026"
        title="Tổng quan công việc"
        description="Theo dõi công việc và tiếp nhận bệnh nhân tại phòng của bạn."
      >
        <ActionLink href="/imaging/worklist">
          <Icon name="queue" />
          Mở danh sách chờ
        </ActionLink>
      </PageHeading>
      <div className="im-hero">
        <div className="im-hero-summary">
          <Badge tone="hero">CA SÁNG · 07:00 – 12:00</Badge>
          <h2>{ROOM_NAME}</h2>
          <p>
            {serving
              ? `Đang phục vụ: ${serving.number} · ${state.visits.find((v) => v.id === serving.visitId)?.patient}`
              : "Sẵn sàng nhận bệnh nhân"}
          </p>
        </div>
        <div className="im-hero-actions">
          <Button
            variant="outline"
            className="im-button im-button-white"
            onClick={callNext}
          >
            <Icon name="user" />
            Gọi bệnh nhân tiếp theo
          </Button>
          {serving ? (
            <Link
              href={`/imaging/orders/${orders.find((o) => o.visitId === serving.visitId && o.status === "Paid")?.id || "CDHA-001"}`}
            >
              Mở chỉ định <Icon name="arrow" size={16} />
            </Link>
          ) : (
            <Link href="/imaging/schedule">
              Xem lịch làm việc <Icon name="arrow" size={16} />
            </Link>
          )}
        </div>
      </div>
      <div className="im-stats">
        {stats.map((s) => (
          <article className="im-stat" key={s.label}>
            <div>
              <span>{s.label}</span>
              <strong>{s.value.toString().padStart(2, "0")}</strong>
            </div>
            <span className={`im-stat-icon im-stat-${s.tone}`}>
              <Icon name={s.icon} size={23} />
            </span>
          </article>
        ))}
      </div>
      <div className="im-two-column">
        <Panel
          title="Bệnh nhân tiếp theo"
          subtitle="Theo nhóm ưu tiên và thời điểm vào hàng đợi"
          action={
            <Link className="im-text-button" href="/imaging/worklist">
              Xem tất cả <Icon name="arrow" size={15} />
            </Link>
          }
        >
          {waiting.length ? (
            <div className="im-patient-list">
              {waiting.slice(0, 3).map((q) => {
                const o = orders.find((o) => o.visitId === q.visitId);
                return (
                  o && (
                    <Link
                      className="im-patient-row"
                      key={q.id}
                      href={`/imaging/orders/${o.id}`}
                    >
                      <span className="im-queue-number">{q.number}</span>
                      <PatientAvatar name={o.patient} />
                      <div className="im-patient-row-info">
                        <strong>{o.patient}</strong>
                        <span>
                          {o.service} · {q.joinedAt}
                        </span>
                      </div>
                      <Badge tone={q.priority < 9 ? "amber" : "neutral"}>
                        {q.priorityLabel}
                      </Badge>
                      <Icon name="arrow" size={16} />
                    </Link>
                  )
                );
              })}
            </div>
          ) : (
            <Empty
              title="Hàng đợi đã trống"
              description="Bạn đã xử lý hết bệnh nhân đang chờ tại phòng."
            />
          )}
        </Panel>
        <Panel title="Công việc của bạn" subtitle="Truy cập nhanh">
          <div className="im-quick-links">
            {[
              [
                "/imaging/orders",
                "upload",
                "Tải và gửi study DICOM",
                "Chọn chỉ định, đối chiếu và gửi bộ ảnh",
              ],
              [
                "/imaging/status",
                "activity",
                "Theo dõi trạng thái",
                "Kiểm tra các chỉ định của phòng",
              ],
              [
                "/imaging/rooms",
                "room",
                "Thông tin phòng dịch vụ",
                "Loại phòng và tình trạng hoạt động",
              ],
            ].map(([href, icon, title, sub]) => (
              <Link key={href} href={href}>
                <span className="im-quick-icon">
                  <Icon name={icon} />
                </span>
                <div>
                  <strong>{title}</strong>
                  <span>{sub}</span>
                </div>
                <Icon name="arrow" size={16} />
              </Link>
            ))}
          </div>
        </Panel>
      </div>
    </>
  );
}
