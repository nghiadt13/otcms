"use client";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
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
import { useImaging } from "../ImagingProvider";
import { CURRENT_ROOM, DEMO_DATE, rooms, visitLabels } from "../demoData";
import {
  ActionLink,
  Badge,
  Empty,
  Icon,
  Info,
  Modal,
  OrderBadge,
  PageHeading,
  Panel,
  SectionTabs,
} from "./ImagingUi";

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

export function RoomsScreen() {
  const [search, setSearch] = useState("");
  const [type, setType] = useState("all");
  const [status, setStatus] = useState("all");
  const [selected, setSelected] = useState<string | null>(null);
  const matches = rooms.filter(
    (r) =>
      `${r.name} ${r.id} ${r.services.join(" ")}`
        .toLowerCase()
        .includes(search.toLowerCase()) &&
      (type === "all" || r.type === type) &&
      (status === "all" || r.availability === status),
  );
  const detail = rooms.find((r) => r.id === selected);
  return (
    <>
      <PageHeading
        eyebrow="THÔNG TIN CÔNG VIỆC"
        title="Phòng dịch vụ"
        description="Tra cứu loại phòng, dịch vụ hỗ trợ và tình trạng hoạt động."
      />
      <Panel>
        <div className="im-toolbar im-toolbar-wrap">
          <label className="im-search">
            <Icon name="search" />
            <Input
              value={search}
              aria-label="Tìm phòng hoặc dịch vụ"
              placeholder="Tìm phòng, mã phòng hoặc dịch vụ…"
              onChange={(e) => setSearch(e.target.value)}
            />
          </label>
          <label className="im-filter">
            <span>Loại phòng</span>
            <NativeSelect
              value={type}
              onChange={(e) => setType(e.target.value)}
            >
              <option value="all">Tất cả loại phòng</option>
              {[...new Set(rooms.map((r) => r.type))].map((t) => (
                <option key={t}>{t}</option>
              ))}
            </NativeSelect>
          </label>
          <label className="im-filter">
            <span>Tình trạng</span>
            <NativeSelect
              value={status}
              onChange={(e) => setStatus(e.target.value)}
            >
              <option value="all">Tất cả tình trạng</option>
              <option>Hoạt động</option>
              <option>Tạm ngưng</option>
            </NativeSelect>
          </label>
        </div>
      </Panel>
      <div className="im-room-grid">
        {matches.map((r) => (
          <Panel
            key={r.id}
            className={r.id === CURRENT_ROOM ? "im-room-own" : ""}
          >
            <div className="im-panel-body">
              <div className="im-inline-between">
                <span className="im-room-card-icon">
                  <Icon name="room" size={25} />
                </span>
                <Badge
                  tone={r.availability === "Hoạt động" ? "green" : "amber"}
                >
                  {r.availability}
                </Badge>
              </div>
              <h2 className="im-room-card-title">{r.name}</h2>
              <p className="im-muted im-small">
                {r.id} · {r.location}
              </p>
              <div className="im-room-service-tags">
                {r.services.map((s) => (
                  <span key={s}>{s}</span>
                ))}
              </div>
              <div className="im-inline-between">
                <span className="im-small im-muted">
                  {r.id === CURRENT_ROOM
                    ? "Phòng được phân công"
                    : "Thông tin tham khảo"}
                </span>
                <Button
                  variant="ghost"
                  className="im-text-button"
                  onClick={() => setSelected(r.id)}
                >
                  Chi tiết <Icon name="arrow" size={15} />
                </Button>
              </div>
            </div>
          </Panel>
        ))}
      </div>
      {!matches.length && (
        <Panel>
          <Empty />
        </Panel>
      )}
      <p className="im-footnote">
        <Icon name="shield" size={16} />
        Kỹ thuật viên xem thông tin phòng; Administrator quản lý tình trạng hoạt
        động.
      </p>
      {detail && (
        <Modal title={detail.name} close={() => setSelected(null)}>
          <dl className="im-details">
            <div>
              <dt>Mã phòng</dt>
              <dd>{detail.id}</dd>
            </div>
            <div>
              <dt>Loại phòng</dt>
              <dd>{detail.type}</dd>
            </div>
            <div>
              <dt>Vị trí</dt>
              <dd>{detail.location}</dd>
            </div>
            <div>
              <dt>Tình trạng</dt>
              <dd>{detail.availability}</dd>
            </div>
            <div>
              <dt>Dịch vụ hỗ trợ</dt>
              <dd>{detail.services.join(", ")}</dd>
            </div>
          </dl>
          <footer>
            <Button
              variant="outline"
              className="im-button im-button-secondary"
              onClick={() => setSelected(null)}
            >
              Đóng
            </Button>
          </footer>
        </Modal>
      )}
    </>
  );
}

function monday(value: string) {
  const d = new Date(`${value}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() - ((d.getUTCDay() + 6) % 7));
  return d;
}
export function ScheduleScreen() {
  const [selected, setSelected] = useState(DEMO_DATE);
  const [view, setView] = useState("week");
  const start = monday(selected);
  const days = Array.from({ length: 7 }, (_, i) => {
    const d = new Date(start);
    d.setUTCDate(d.getUTCDate() + i);
    return d;
  });
  const dayLabel = (d: Date) => d.toISOString().slice(0, 10);
  function moveWeek(direction: number) {
    const d = new Date(start);
    d.setUTCDate(d.getUTCDate() + 7 * direction);
    setSelected(dayLabel(d));
  }
  const hasShift = (d: Date) =>
    d.getUTCDay() !== 0 &&
    dayLabel(d) >= "2026-10-19" &&
    dayLabel(d) <= "2026-10-31";
  return (
    <>
      <PageHeading
        eyebrow="LỊCH CÁ NHÂN"
        title="Lịch làm việc"
        description="Xem ca làm việc và phòng được phân công của bạn."
      />
      <div className="im-room-banner">
        <span className="im-room-icon">
          <Icon name="calendar" />
        </span>
        <div>
          <strong>KTV. Nguyễn Minh Anh</strong>
          <p>Mã nhân viên KTV-001 · Chẩn đoán hình ảnh</p>
        </div>
        <Badge tone="blue">Lịch của tôi</Badge>
      </div>
      <Panel>
        <div className="im-toolbar im-toolbar-wrap">
          <div className="im-inline-between im-week-navigation">
            <Button
              variant="ghost"
              className="im-icon-button"
              aria-label="Tuần trước"
              onClick={() => moveWeek(-1)}
            >
              <Icon name="back" size={18} />
            </Button>
            <strong>
              {days[0].toLocaleDateString("vi-VN", { timeZone: "UTC" })} –{" "}
              {days[6].toLocaleDateString("vi-VN", { timeZone: "UTC" })}
            </strong>
            <Button
              variant="ghost"
              className="im-icon-button"
              aria-label="Tuần sau"
              onClick={() => moveWeek(1)}
            >
              <Icon name="arrow" size={18} />
            </Button>
          </div>
          <label className="im-filter">
            <span>Chọn ngày</span>
            <Input
              aria-label="Ngày xem lịch"
              type="date"
              value={selected}
              onChange={(e) => {
                if (e.target.value) setSelected(e.target.value);
              }}
            />
          </label>
          <Button
            variant="outline"
            className="im-button im-button-secondary im-button-small"
            onClick={() => setSelected(DEMO_DATE)}
          >
            Tuần hiện tại
          </Button>
          <div className="im-segment-control">
            <Button
              variant="ghost"
              className={view === "week" ? "im-selected" : ""}
              onClick={() => setView("week")}
            >
              Tuần
            </Button>
            <Button
              variant="ghost"
              className={view === "list" ? "im-selected" : ""}
              onClick={() => setView("list")}
            >
              Danh sách
            </Button>
          </div>
        </div>
        {view === "week" ? (
          <div className="im-calendar-scroll">
            <div className="im-calendar">
              {days.map((d, i) => (
                <div
                  key={dayLabel(d)}
                  className={
                    dayLabel(d) === DEMO_DATE ? "im-calendar-today" : ""
                  }
                >
                  <header>
                    <span>
                      {
                        [
                          "THỨ HAI",
                          "THỨ BA",
                          "THỨ TƯ",
                          "THỨ NĂM",
                          "THỨ SÁU",
                          "THỨ BẢY",
                          "CHỦ NHẬT",
                        ][i]
                      }
                    </span>
                    <strong>{d.getUTCDate()}</strong>
                  </header>
                  <div>
                    {hasShift(d) ? (
                      <article className="im-shift">
                        <span>CA SÁNG</span>
                        <strong>07:00 – 12:00</strong>
                        <p>Phòng X-quang 01</p>
                        <small>XR-01 · Chẩn đoán hình ảnh</small>
                      </article>
                    ) : (
                      <p className="im-calendar-off">Không có ca</p>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        ) : (
          <div className="im-table-scroll">
            <Table className="im-table">
              <TableHeader>
                <TableRow>
                  <TableHead>Ngày</TableHead>
                  <TableHead>Ca làm việc</TableHead>
                  <TableHead>Thời gian</TableHead>
                  <TableHead>Phòng</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {days.filter(hasShift).map((d) => (
                  <TableRow key={dayLabel(d)}>
                    <TableCell>
                      {d.toLocaleDateString("vi-VN", { timeZone: "UTC" })}
                    </TableCell>
                    <TableCell>Ca sáng</TableCell>
                    <TableCell>07:00 – 12:00</TableCell>
                    <TableCell>Phòng X-quang 01 · XR-01</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            {!days.some(hasShift) && (
              <Empty title="Chưa có ca trong tuần này" />
            )}
          </div>
        )}
      </Panel>
      <Info>
        Lịch làm việc và phân công phòng do Administrator quản lý. Liên hệ quản
        trị viên nếu cần điều chỉnh ca.
      </Info>
    </>
  );
}

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
