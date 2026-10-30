"use client";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Table,
  TableHeader,
  TableRow,
  TableHead,
  TableBody,
  TableCell,
} from "@/components/ui/table";
import { useState } from "react";
import { DEMO_DATE } from "../api/imagingMockData";
import { Badge } from "./Badge";
import { Empty } from "./Empty";
import { Icon } from "./Icon";
import { Info } from "./Info";
import { PageHeading } from "./PageHeading";
import { Panel } from "./Panel";

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
