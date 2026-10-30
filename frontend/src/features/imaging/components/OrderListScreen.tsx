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
import { useState } from "react";
import { useImaging } from "../hooks/useImaging";
import { CURRENT_ROOM, statusLabels } from "../api/imagingMockData";
import { Empty } from "./Empty";
import { Icon } from "./Icon";
import { OrderBadge } from "./OrderBadge";
import { PageHeading } from "./PageHeading";
import { Pagination } from "./Pagination";
import { Panel } from "./Panel";
import { PatientAvatar } from "./PatientAvatar";

export function OrderListScreen({
  statusMode = false,
}: {
  statusMode?: boolean;
}) {
  const { state } = useImaging();
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("all");
  const [service, setService] = useState("all");
  const [page, setPage] = useState(1);
  const own = state.orders.filter(
    (o) => o.room === CURRENT_ROOM && o.kind === "imaging",
  );
  const matches = own.filter(
    (o) =>
      `${o.id} ${o.patient} ${o.patientId}`
        .toLowerCase()
        .includes(search.toLowerCase()) &&
      (status === "all" || o.status === status) &&
      (service === "all" || o.service === service),
  );
  const current = Math.min(page, Math.max(1, Math.ceil(matches.length / 5)));
  return (
    <>
      <PageHeading
        eyebrow="CHẨN ĐOÁN HÌNH ẢNH"
        title={
          statusMode ? "Trạng thái chỉ định" : "Chỉ định chẩn đoán hình ảnh"
        }
        description={
          statusMode
            ? "Theo dõi từng chỉ định của phòng từ thanh toán đến khi có kết quả."
            : "Tra cứu chỉ định theo mã hoặc tên bệnh nhân trước khi thực hiện dịch vụ."
        }
      />
      <div className="im-summary-strip">
        {Object.entries(statusLabels).map(([value, label]) => (
          <Button
            variant="ghost"
            key={value}
            className={status === value ? "im-summary-active" : ""}
            onClick={() => {
              setStatus(status === value ? "all" : value);
              setPage(1);
            }}
          >
            <span>{label}</span>
            <strong>
              {own
                .filter((o) => o.status === value)
                .length.toString()
                .padStart(2, "0")}
            </strong>
          </Button>
        ))}
      </div>
      <Panel
        title={
          statusMode ? "Theo dõi chỉ định của phòng" : "Danh sách chỉ định"
        }
        subtitle="Phòng X-quang 01 · XR-01"
      >
        <div className="im-toolbar im-toolbar-wrap">
          <label className="im-search">
            <Icon name="search" />
            <Input
              aria-label="Tìm chỉ định"
              placeholder="Mã chỉ định, tên hoặc mã bệnh nhân…"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(1);
              }}
            />
          </label>
          <label className="im-filter">
            <span>Trạng thái</span>
            <NativeSelect
              value={status}
              onChange={(e) => {
                setStatus(e.target.value);
                setPage(1);
              }}
            >
              <option value="all">Tất cả trạng thái</option>
              {Object.entries(statusLabels).map(([v, l]) => (
                <option key={v} value={v}>
                  {l}
                </option>
              ))}
            </NativeSelect>
          </label>
          <label className="im-filter">
            <span>Dịch vụ</span>
            <NativeSelect
              value={service}
              onChange={(e) => {
                setService(e.target.value);
                setPage(1);
              }}
            >
              <option value="all">Tất cả dịch vụ</option>
              {[...new Set(own.map((o) => o.service))].map((s) => (
                <option key={s}>{s}</option>
              ))}
            </NativeSelect>
          </label>
          {(search || status !== "all" || service !== "all") && (
            <Button
              variant="ghost"
              className="im-text-button"
              onClick={() => {
                setSearch("");
                setStatus("all");
                setService("all");
                setPage(1);
              }}
            >
              Xóa bộ lọc
            </Button>
          )}
        </div>
        <div className="im-table-scroll">
          <Table className="im-table">
            <TableHeader>
              <TableRow>
                <TableHead>Mã chỉ định</TableHead>
                <TableHead>Bệnh nhân</TableHead>
                <TableHead>Dịch vụ</TableHead>
                <TableHead>
                  {statusMode ? "Thời điểm" : "Bác sĩ yêu cầu"}
                </TableHead>
                <TableHead>Trạng thái</TableHead>
                <TableHead>Thao tác</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {matches.slice((current - 1) * 5, current * 5).map((o) => (
                <TableRow key={o.id}>
                  <TableCell>
                    <Link
                      className="im-id-link"
                      href={`/imaging/orders/${o.id}`}
                    >
                      {o.id}
                    </Link>
                    <small>
                      {o.visitId} · Đợt {o.round}
                    </small>
                  </TableCell>
                  <TableCell>
                    <div className="im-patient-cell">
                      <PatientAvatar name={o.patient} />
                      <div>
                        <strong>{o.patient}</strong>
                        <small>
                          {o.patientId} · {o.age} tuổi · {o.sex}
                        </small>
                      </div>
                    </div>
                  </TableCell>
                  <TableCell>
                    <strong>{o.service}</strong>
                    <small>{o.room}</small>
                  </TableCell>
                  <TableCell>
                    {statusMode ? (
                      <>
                        <span>Tạo: {o.createdAt}</span>
                        <small>
                          {o.resultAt
                            ? `Có kết quả: ${o.resultAt}`
                            : o.paidAt
                              ? `Thanh toán: ${o.paidAt}`
                              : "Chưa thanh toán"}
                        </small>
                      </>
                    ) : (
                      o.doctor
                    )}
                  </TableCell>
                  <TableCell>
                    <OrderBadge status={o.status} />
                  </TableCell>
                  <TableCell>
                    <Link
                      className="im-text-button"
                      href={`/imaging/orders/${o.id}`}
                    >
                      Chi tiết <Icon name="arrow" size={14} />
                    </Link>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
        {!matches.length && <Empty />}
        <Pagination page={current} total={matches.length} onChange={setPage} />
      </Panel>
    </>
  );
}
