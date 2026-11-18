"use client";

import { Button } from "@/components/ui/button";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { formatAppMessage, messageFromError } from "@/lib/messages/appMessages";
import {
  listAssignedImagingUploadWork,
  type AssignedImagingUploadWork,
} from "../api/imagingIngestApi";
import {
  callNextService,
  getServiceWorklist,
  type ServiceWorklistEntry,
} from "../api/imagingQueueApi";
import { listImagingRooms, type ImagingRoom } from "../api/imagingRoomApi";
import { ActionLink } from "./ActionLink";
import { Badge } from "./Badge";
import { Empty } from "./Empty";
import { Icon } from "./Icon";
import { PageHeading } from "./PageHeading";
import { Panel } from "./Panel";
import { PatientAvatar } from "./PatientAvatar";

/** Tổng quan công việc của phòng chụp từ hàng chờ và chỉ định thật (UC-46, UC-52). */
export function DashboardScreen() {
  const [room, setRoom] = useState<ImagingRoom | null>(null);
  const [entries, setEntries] = useState<ServiceWorklistEntry[]>([]);
  const [work, setWork] = useState<AssignedImagingUploadWork[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async (signal?: AbortSignal) => {
    try {
      const rooms = (await listImagingRooms(signal)).filter(
        (item) => item.availability === "Hoạt động",
      );
      const first = rooms[0] ?? null;
      const [worklist, assigned] = await Promise.all([
        first
          ? getServiceWorklist(first.id, signal)
          : Promise.resolve({ roomId: "", entries: [] }),
        listAssignedImagingUploadWork(0, 100, signal),
      ]);
      if (signal?.aborted) return;
      setRoom(first);
      setEntries(worklist.entries);
      setWork(assigned.items);
      setError(null);
    } catch (cause) {
      if (!signal?.aborted) setError(messageFromError(cause));
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    queueMicrotask(() => void load(controller.signal));
    return () => controller.abort();
  }, [load]);

  async function callNext() {
    if (!room) return;
    setNotice(null);
    try {
      await callNextService(room.id);
      setNotice(formatAppMessage("MSG-43"));
      await load();
    } catch (cause) {
      setError(messageFromError(cause));
    }
  }

  const waiting = entries.filter((entry) => entry.status === "WAITING");
  const serving = entries.find(
    (entry) => entry.status === "CALLED" || entry.status === "IN_EXAMINATION",
  );
  const absent = entries.filter((entry) => entry.status === "ABSENT");
  const today = new Date().toDateString();
  const resultsToday = work.filter(
    (item) =>
      item.status === "RESULT_AVAILABLE" &&
      new Date(item.updatedAt).toDateString() === today,
  ).length;
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
      sub:
        serving?.queueNumber != null
          ? `Số ${serving.queueNumber}`
          : "Sẵn sàng gọi bệnh nhân",
    },
    {
      label: "Chỉ định có kết quả hôm nay",
      value: resultsToday,
      icon: "check",
      tone: "green",
      sub: "Study đã được gửi",
    },
    {
      label: "Bệnh nhân vắng mặt",
      value: absent.length,
      icon: "clock",
      tone: "amber",
      sub: "Xem lại trong danh sách chờ",
    },
  ];

  return (
    <>
      <PageHeading
        title="Tổng quan công việc"
        description="Theo dõi công việc và tiếp nhận bệnh nhân tại phòng của bạn."
      >
        <ActionLink href="/imaging/worklist">
          <Icon name="queue" />
          Mở danh sách chờ
        </ActionLink>
      </PageHeading>
      {error && (
        <p role="alert" className="im-form-error">
          {error}
        </p>
      )}
      {notice && <p role="status">{notice}</p>}
      {loading ? (
        <p role="status">Đang tải tổng quan…</p>
      ) : (
        <>
          <div className="im-hero">
            <div className="im-hero-summary">
              <h2>{room ? room.name : "Chưa được phân công phòng"}</h2>
              <p>
                {serving
                  ? `Đang phục vụ: ${serving.queueNumber ?? ""} · ${serving.patient.fullName ?? ""}`
                  : "Sẵn sàng nhận bệnh nhân"}
              </p>
            </div>
            <div className="im-hero-actions">
              <Button
                variant="outline"
                className="im-button im-button-white"
                disabled={!room}
                onClick={() => void callNext()}
              >
                <Icon name="user" />
                Gọi bệnh nhân tiếp theo
              </Button>
              {serving?.orders[0] && (
                <Link href={`/imaging/orders/${serving.orders[0].orderId}`}>
                  Mở chỉ định <Icon name="arrow" size={16} />
                </Link>
              )}
            </div>
          </div>
          <div className="im-stats">
            {stats.map((stat) => (
              <article className="im-stat" key={stat.label}>
                <div>
                  <span>{stat.label}</span>
                  <strong>{stat.value.toString().padStart(2, "0")}</strong>
                  <small>{stat.sub}</small>
                </div>
                <span className={`im-stat-icon im-stat-${stat.tone}`}>
                  <Icon name={stat.icon} size={23} />
                </span>
              </article>
            ))}
          </div>
          <div className="im-two-column">
            <Panel
              title="Bệnh nhân tiếp theo"
              subtitle="Theo thứ tự vào hàng đợi"
              action={
                <Link className="im-text-button" href="/imaging/worklist">
                  Xem tất cả <Icon name="arrow" size={15} />
                </Link>
              }
            >
              {waiting.length ? (
                <div className="im-patient-list">
                  {waiting.slice(0, 3).map((entry) => (
                    <Link
                      className="im-patient-row"
                      key={entry.entryId}
                      href={
                        entry.orders[0]
                          ? `/imaging/orders/${entry.orders[0].orderId}`
                          : "/imaging/worklist"
                      }
                    >
                      <span className="im-queue-number">
                        {entry.queueNumber}
                      </span>
                      <PatientAvatar name={entry.patient.fullName ?? "?"} />
                      <div className="im-patient-row-info">
                        <strong>
                          {entry.patient.fullName ?? "Không rõ tên"}
                        </strong>
                        <span>
                          {entry.orders
                            .map((order) => order.serviceName)
                            .join(", ")}
                        </span>
                      </div>
                      <Badge tone="neutral">Đang chờ</Badge>
                      <Icon name="arrow" size={16} />
                    </Link>
                  ))}
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
      )}
    </>
  );
}
