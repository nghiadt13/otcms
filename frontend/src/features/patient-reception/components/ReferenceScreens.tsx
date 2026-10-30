"use client";

import { useState } from "react";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { normalizeText, addDays } from "../schema/receptionRules";
import { useReception } from "./ReceptionProvider";
import {
  DataTable,
  dateText,
  FormModal,
  money,
  RouteLink,
  SelectField,
  TextField,
  timeText,
} from "./ReceptionUi";

const categories: Record<string, string> = {
  exam: "Khám chuyên khoa",
  imaging: "Chẩn đoán hình ảnh",
  lab: "Xét nghiệm",
};
export function ReferenceScreen({
  type,
}: {
  type: "rooms" | "services" | "schedule";
}) {
  const { data: s } = useReception();
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("");
  const [week, setWeek] = useState(s!.clock.slice(0, 10));
  const title =
    type === "rooms"
      ? "Phòng và tình trạng"
      : type === "services"
        ? "Danh mục dịch vụ"
        : "Ca làm việc của tôi";
  const description =
    type === "rooms"
      ? "Xem tình trạng và các dịch vụ phòng có thể thực hiện."
      : type === "services"
        ? "Giá tham khảo hiện tại. Phí của lịch hẹn và chỉ định đã tạo được giữ nguyên."
        : "Lịch làm việc riêng của bạn do quản trị viên phân công.";
  const rows =
    type === "rooms"
      ? s!.rooms
          .filter(
            (r) =>
              (!category || r.category === category) &&
              normalizeText(r.name).includes(normalizeText(search)),
          )
          .map((r) => ({
            id: r.id,
            cells: [
              <span key="room" className="font-medium">
                {r.name}
              </span>,
              categories[r.category],
              r.serviceIds
                .map((id) => s!.services.find((v) => v.id === id)!.name)
                .join(", "),
              <StatusBadge
                key="status"
                tone={r.available ? "success" : "warning"}
              >
                {r.available ? "Đang hoạt động" : "Tạm ngừng"}
              </StatusBadge>,
              s!.queues.filter(
                (q) =>
                  q.roomId === r.id &&
                  q.date === s!.clock.slice(0, 10) &&
                  q.status === "waiting",
              ).length,
              <RouteLink key="queue" href={`/reception/queues?roomId=${r.id}`}>
                Hàng đợi
              </RouteLink>,
            ],
          }))
      : type === "services"
        ? s!.services
            .filter(
              (v) =>
                (!category || v.category === category) &&
                normalizeText(v.name).includes(normalizeText(search)),
            )
            .map((v) => ({
              id: v.id,
              cells: [
                <span key="name" className="font-medium">
                  {v.name}
                </span>,
                categories[v.category],
                <span key="price" className="whitespace-nowrap tabular-nums">
                  {money(v.price)}
                </span>,
                <StatusBadge
                  key="status"
                  tone={v.active ? "success" : "neutral"}
                >
                  {v.active ? "Đang cung cấp" : "Ngừng cung cấp"}
                </StatusBadge>,
              ],
            }))
        : s!.shifts
            .filter(
              (shift) =>
                shift.staffId === s!.account.id &&
                shift.date >= week &&
                shift.date <= addDays(week || s!.clock.slice(0, 10), 6),
            )
            .map((shift) => ({
              id: shift.id,
              cells: [
                dateText(shift.date),
                shift.start < "12:00" ? "Ca sáng" : "Ca chiều",
                `${shift.start}–${shift.end}`,
                "Quầy tiếp nhận",
                s!.account.name,
              ],
            }));
  return (
    <>
      <PageHeader title={title} description={description} />
      <div className="mb-4 grid gap-3 sm:grid-cols-2">
        {type === "schedule" ? (
          <div className="space-y-2">
            <TextField
              label="Ngày bắt đầu xem lịch"
              type="date"
              value={week}
              onChange={(v) => {
                if (v) setWeek(v);
              }}
            />
            <p className="text-sm text-muted-foreground">
              Hiển thị từ {dateText(week)} đến {dateText(addDays(week, 6))} (7
              ngày, tính cả ngày bắt đầu).
            </p>
          </div>
        ) : (
          <>
            <TextField
              label={type === "rooms" ? "Tên phòng" : "Tên dịch vụ"}
              value={search}
              onChange={setSearch}
            />
            <SelectField
              label="Nhóm"
              value={category}
              onChange={setCategory}
              options={[
                { value: "", label: "Tất cả nhóm" },
                ...Object.entries(categories).map(([value, label]) => ({
                  value,
                  label,
                })),
              ]}
            />
          </>
        )}
      </div>
      <DataTable
        key={`${search}-${category}-${week}`}
        label={title}
        headers={
          type === "rooms"
            ? [
                "Phòng",
                "Nhóm",
                "Dịch vụ hỗ trợ",
                "Tình trạng",
                "Đang chờ",
                "Thao tác",
              ]
            : type === "services"
              ? ["Dịch vụ", "Nhóm", "Đơn giá", "Trạng thái"]
              : ["Ngày", "Ca", "Thời gian", "Vị trí", "Nhân viên"]
        }
        rows={rows}
        empty={
          type === "schedule"
            ? "Bạn chưa có ca được phân công trong khoảng này."
            : "Không có mục phù hợp với bộ lọc."
        }
      />
      <p className="mt-5 text-xs text-muted-foreground">
        Thông tin do quản trị viên quản lý; lễ tân chỉ tra cứu.
      </p>
    </>
  );
}
export function NoticeScreen() {
  const { data: s, run, pending } = useReception();
  const [filter, setFilter] = useState("all");
  const [selected, setSelected] = useState("");
  const notice = s!.notices.find((n) => n.id === selected);
  const list = s!.notices.filter((n) => filter === "all" || !n.read);
  return (
    <>
      <PageHeader
        title="Thông báo"
        description="Các thông báo liên quan đến tài khoản lễ tân và công việc trong ca."
      />
      <div className="mb-4 max-w-xs">
        <SelectField
          label="Hiển thị"
          value={filter}
          onChange={setFilter}
          options={[
            { value: "all", label: "Tất cả thông báo" },
            { value: "unread", label: "Chưa đọc" },
          ]}
        />
      </div>
      <div className="divide-y rounded-lg border bg-card">
        {list.length ? (
          list.map((n) => (
            <div
              key={n.id}
              className="flex flex-wrap items-center justify-between gap-4 p-4"
            >
              <div className="min-w-0">
                <p className="font-medium">{n.title}</p>
                <p className="mt-1 text-xs text-muted-foreground">
                  {timeText(n.createdAt)} · {n.read ? "Đã đọc" : "Chưa đọc"}
                </p>
              </div>
              <Button
                variant="outline"
                disabled={pending}
                onClick={async () => {
                  if (n.read || (await run({ type: "read-notice", id: n.id })))
                    setSelected(n.id);
                }}
              >
                Đọc thông báo
              </Button>
            </div>
          ))
        ) : (
          <p className="p-8 text-center text-muted-foreground">
            Không còn thông báo chưa đọc.
          </p>
        )}
      </div>
      {notice && (
        <FormModal
          title={notice.title}
          description={timeText(notice.createdAt)}
          onClose={() => setSelected("")}
        >
          <p className="leading-relaxed">{notice.body}</p>
          {notice.href && (
            <RouteLink href={notice.href}>Mở nội dung liên quan</RouteLink>
          )}
        </FormModal>
      )}
    </>
  );
}
