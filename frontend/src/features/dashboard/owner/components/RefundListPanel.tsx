"use client";

import { useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import {
  NativeSelect,
  NativeSelectOption,
} from "@/components/ui/native-select";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { formatDate, formatDateTime } from "@/lib/format/formatDate";
import type { RefundRow } from "../types/ownerFinance";
import {
  filterRefunds,
  personKey,
  roleLabel,
  UNKNOWN_PERSON,
  type RefundStatusFilter,
} from "../lib/ownerFinanceFormat";
import { OwnerPanel } from "./OwnerPanel";
import { RankedTable, type RankedColumn } from "./RankedTable";

const STATUS_FILTERS: { value: RefundStatusFilter; label: string }[] = [
  { value: "ALL", label: "Tất cả" },
  { value: "REFUND_PENDING", label: "Chờ hoàn" },
  { value: "REFUNDED", label: "Đã hoàn" },
];

function StatusMark({ status }: { status: RefundRow["status"] }) {
  // Chữ luôn đi kèm màu, không chỉ dựa vào màu.
  return status === "REFUND_PENDING" ? (
    <span className="font-medium text-coral-strong">Chờ hoàn</span>
  ) : (
    <span className="font-medium text-primary">Đã hoàn</span>
  );
}

const columns: RankedColumn<RefundRow>[] = [
  {
    key: "patient",
    label: "Khoản hoàn",
    sortValue: (r) => r.patientName ?? "",
    cell: (r) => (
      <div className="whitespace-normal [overflow-wrap:anywhere]">
        <span className="font-medium">
          {r.patientName ?? "Không rõ bệnh nhân"}
        </span>
        <p className="text-xs text-muted-foreground">
          {r.description} · {r.invoiceNumber}
        </p>
        {/* Màn hẹp: lý do và người hoàn chuyển xuống dưới. */}
        <p className="text-xs text-muted-foreground sm:hidden">
          {r.status === "REFUNDED"
            ? `${r.refundedByName ?? UNKNOWN_PERSON}${r.refundedAt ? `, ${formatDateTime(r.refundedAt)}` : ""}`
            : "Chờ hoàn"}
        </p>
      </div>
    ),
  },
  {
    key: "reason",
    label: "Lý do",
    hideBelowSm: true,
    sortValue: (r) => r.reason ?? "",
    cell: (r) => (
      <span className="whitespace-normal text-muted-foreground">
        {r.reason ?? "—"}
      </span>
    ),
  },
  {
    key: "paidAt",
    label: "Ngày thu",
    hideBelowSm: true,
    sortValue: (r) => r.paidAt ?? "",
    cell: (r) => (
      <span className="tabular-nums">
        {r.paidAt ? formatDate(r.paidAt) : "—"}
      </span>
    ),
  },
  {
    key: "person",
    label: "Người hoàn",
    hideBelowSm: true,
    sortValue: (r) => r.refundedByName ?? "",
    cell: (r) =>
      r.status === "REFUND_PENDING" ? (
        <span className="text-muted-foreground">Chưa có</span>
      ) : (
        <div className="whitespace-normal [overflow-wrap:anywhere]">
          <span>{r.refundedByName ?? UNKNOWN_PERSON}</span>
          <p className="text-xs text-muted-foreground">
            {roleLabel(r.refundedByRole)}
          </p>
        </div>
      ),
  },
  {
    key: "refundedAt",
    label: "Giờ hoàn",
    hideBelowSm: true,
    sortValue: (r) => r.refundedAt ?? "",
    cell: (r) => (
      <span className="tabular-nums">
        {r.refundedAt ? formatDateTime(r.refundedAt) : "—"}
      </span>
    ),
  },
  {
    key: "amount",
    label: "Số tiền",
    align: "right",
    sortValue: (r) => r.amount,
    cell: (r) => (
      <div>
        <div className="font-medium tabular-nums">
          {formatCurrencyVnd(r.amount)}
        </div>
        <div className="text-xs">
          <StatusMark status={r.status} />
        </div>
      </div>
    ),
  },
];

/** Từng khoản hoàn tiền: chờ hoàn xếp trước (lâu nhất trước), rồi các khoản đã hoàn. Lọc theo trạng thái và người hoàn. */
export function RefundListPanel({
  refunds,
  people,
}: {
  refunds: RefundRow[];
  people: { name: string | null; role: string | null }[];
}) {
  const [status, setStatus] = useState<RefundStatusFilter>("ALL");
  const [person, setPerson] = useState<string>("");
  const shown = useMemo(
    () => filterRefunds(refunds, status, person || null),
    [refunds, status, person],
  );

  return (
    <OwnerPanel
      title="Các khoản hoàn"
      description="Khoản chờ hoàn của mọi thời điểm, và khoản đã hoàn trong kỳ"
      actions={
        <div className="flex flex-wrap items-center gap-2">
          <div
            role="group"
            aria-label="Lọc theo trạng thái"
            className="flex gap-1"
          >
            {STATUS_FILTERS.map((f) => (
              <Button
                key={f.value}
                type="button"
                size="sm"
                variant={status === f.value ? "default" : "outline"}
                aria-pressed={status === f.value}
                onClick={() => setStatus(f.value)}
              >
                {f.label}
              </Button>
            ))}
          </div>
          <NativeSelect
            size="sm"
            aria-label="Lọc theo người hoàn"
            value={person}
            onChange={(event) => setPerson(event.target.value)}
          >
            <NativeSelectOption value="">Mọi người hoàn</NativeSelectOption>
            {people.map((p) => (
              <NativeSelectOption
                key={personKey(p.name, p.role)}
                value={personKey(p.name, p.role)}
              >
                {`${p.name ?? UNKNOWN_PERSON}${p.role ? ` (${roleLabel(p.role)})` : ""}`}
              </NativeSelectOption>
            ))}
          </NativeSelect>
        </div>
      }
    >
      {shown.length === 0 ? (
        <p className="py-6 text-center text-sm text-muted-foreground">
          Không có khoản hoàn nào khớp bộ lọc.
        </p>
      ) : (
        <RankedTable
          rows={shown}
          columns={columns}
          rowKey={(r) => r.itemId}
          defaultSortKey="amount"
          caption="Danh sách khoản chờ hoàn và đã hoàn tiền"
        />
      )}
    </OwnerPanel>
  );
}
