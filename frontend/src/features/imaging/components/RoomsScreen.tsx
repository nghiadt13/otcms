"use client";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { NativeSelect } from "@/components/ui/native-select";
import { useState } from "react";
import { CURRENT_ROOM, rooms } from "../api/imagingMockData";
import { Badge } from "./Badge";
import { Empty } from "./Empty";
import { Icon } from "./Icon";
import { Modal } from "./Modal";
import { PageHeading } from "./PageHeading";
import { Panel } from "./Panel";

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
