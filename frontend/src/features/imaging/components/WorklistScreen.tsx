"use client";

import Link from "next/link";
import { useState } from "react";
import { useImaging } from "../ImagingProvider";
import {
  CURRENT_ROOM,
  CURRENT_USER,
  DEMO_DATE,
  queueLabels,
  ROOM_NAME,
} from "../demoData";
import {
  Badge,
  Empty,
  Icon,
  PageHeading,
  Pagination,
  Panel,
  PatientAvatar,
} from "./ImagingUi";

export function WorklistScreen() {
  const { state, callNext, markAbsent, returnPatient } = useImaging();
  const [tab, setTab] = useState("active");
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(1);
  const all = state.queue.filter(
    (q) =>
      q.room === CURRENT_ROOM &&
      state.orders.some(
        (o) =>
          o.visitId === q.visitId &&
          o.room === CURRENT_ROOM &&
          ["Paid", "Results Available"].includes(o.status),
      ),
  );
  const tabs = [
    { id: "active", label: "Đang chờ & phục vụ" },
    { id: "Absent", label: "Vắng mặt" },
    { id: "Done", label: "Đã hoàn tất" },
  ];
  const matches = all
    .filter(
      (q) =>
        (tab === "active"
          ? ["Waiting", "Being Served"].includes(q.status)
          : q.status === tab) &&
        `${q.number} ${state.visits.find((v) => v.id === q.visitId)?.patient} ${state.visits.find((v) => v.id === q.visitId)?.patientId}`
          .toLowerCase()
          .includes(search.toLowerCase()),
    )
    .sort(
      (a, b) =>
        Number(b.status === "Being Served") -
          Number(a.status === "Being Served") ||
        a.priority - b.priority ||
        a.joinedAt.localeCompare(b.joinedAt),
    );
  const current = Math.min(page, Math.max(1, Math.ceil(matches.length / 5)));
  return (
    <>
      <PageHeading
        eyebrow="TIẾP NHẬN TẠI PHÒNG"
        title="Danh sách chờ chẩn đoán hình ảnh"
        description="Chỉ hiển thị bệnh nhân và chỉ định đã thanh toán thuộc phòng của bạn."
      >
        <button className="im-button" onClick={callNext}>
          <Icon name="user" />
          Gọi tiếp theo
        </button>
      </PageHeading>
      <div className="im-room-banner">
        <span className="im-room-icon">
          <Icon name="room" />
        </span>
        <div>
          <strong>
            {ROOM_NAME} <span>XR-01</span>
          </strong>
          <p>Ca sáng 07:00 – 12:00 · KTV. Nguyễn Minh Anh</p>
        </div>
        <Badge tone="green">Đang nhận bệnh nhân</Badge>
      </div>
      <Panel>
        <div className="im-tabs" role="tablist" aria-label="Nhóm hàng đợi">
          {tabs.map((t) => (
            <button
              key={t.id}
              role="tab"
              aria-selected={tab === t.id}
              className={tab === t.id ? "im-tab-active" : ""}
              onClick={() => {
                setTab(t.id);
                setPage(1);
              }}
            >
              {t.label}
              <span>
                {
                  all.filter((q) =>
                    t.id === "active"
                      ? ["Waiting", "Being Served"].includes(q.status)
                      : q.status === t.id,
                  ).length
                }
              </span>
            </button>
          ))}
        </div>
        <div className="im-toolbar">
          <label className="im-search">
            <Icon name="search" />
            <input
              aria-label="Tìm bệnh nhân trong hàng đợi"
              placeholder="Tìm tên, mã bệnh nhân hoặc số thứ tự…"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(1);
              }}
            />
          </label>
          <span className="im-muted im-small">
            Ưu tiên → thời gian vào hàng đợi
          </span>
        </div>
        <div className="im-table-scroll">
          <table className="im-table">
            <thead>
              <tr>
                <th>Số thứ tự</th>
                <th>Bệnh nhân</th>
                <th>Chỉ định đã thanh toán</th>
                <th>Nhóm ưu tiên</th>
                <th>Trạng thái</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {matches.slice((current - 1) * 5, current * 5).map((q) => {
                const orders = state.orders.filter(
                  (o) =>
                    o.visitId === q.visitId &&
                    o.room === CURRENT_ROOM &&
                    ["Paid", "Results Available"].includes(o.status),
                );
                const o = orders[0];
                return (
                  <tr
                    key={q.id}
                    className={
                      q.status === "Being Served" ? "im-row-serving" : ""
                    }
                  >
                    <td>
                      <strong className="im-number-text">{q.number}</strong>
                      <small>Vào lúc {q.joinedAt}</small>
                    </td>
                    <td>
                      <div className="im-patient-cell">
                        <PatientAvatar name={o.patient} />
                        <div>
                          <strong>{o.patient}</strong>
                          <small>
                            {o.patientId} · {o.age} tuổi · {o.sex}
                          </small>
                        </div>
                      </div>
                    </td>
                    <td>
                      {orders.map((x) => (
                        <Link
                          key={x.id}
                          className="im-order-line"
                          href={`/imaging/orders/${x.id}`}
                        >
                          {x.service}
                        </Link>
                      ))}
                      <small>
                        {orders.length} chỉ định · {o.visitId}
                      </small>
                    </td>
                    <td>
                      <Badge tone={q.priority < 9 ? "amber" : "neutral"}>
                        {q.priorityLabel}
                      </Badge>
                    </td>
                    <td>
                      <Badge
                        tone={
                          q.status === "Being Served"
                            ? "blue"
                            : q.status === "Done"
                              ? "green"
                              : q.status === "Absent"
                                ? "amber"
                                : "neutral"
                        }
                      >
                        {queueLabels[q.status]}
                      </Badge>
                    </td>
                    <td>
                      <div className="im-row-actions">
                        <Link
                          href={`/imaging/orders/${orders.find((x) => x.status === "Paid")?.id || o.id}`}
                        >
                          Chi tiết <Icon name="arrow" size={14} />
                        </Link>
                        {q.status === "Being Served" &&
                          q.calledBy === CURRENT_USER &&
                          q.calledOn === DEMO_DATE && (
                            <button
                              className="im-text-button im-danger"
                              onClick={() => markAbsent(q.id)}
                            >
                              Đánh dấu vắng
                            </button>
                          )}
                        {q.status === "Absent" &&
                          (q.calledBy === CURRENT_USER &&
                          q.calledOn === DEMO_DATE ? (
                            <button
                              className="im-text-button"
                              onClick={() => returnPatient(q.id)}
                            >
                              Trở lại hàng đợi
                            </button>
                          ) : (
                            <small>Không thuộc quyền trả lại trong ngày</small>
                          ))}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        {!matches.length && (
          <Empty
            title={
              tab === "Absent"
                ? "Không có bệnh nhân vắng mặt"
                : "Không có bệnh nhân phù hợp"
            }
          />
        )}
        <Pagination page={current} total={matches.length} onChange={setPage} />
      </Panel>
      <p className="im-footnote">
        <Icon name="shield" size={16} />
        Thứ tự được xếp theo nhóm ưu tiên, sau đó theo thời điểm vào hàng đợi.
        Kỹ thuật viên không thay đổi vị trí thủ công.
      </p>
    </>
  );
}
