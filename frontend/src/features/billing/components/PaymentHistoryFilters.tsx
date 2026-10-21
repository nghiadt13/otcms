"use client";

import { DateRangeFilter } from "@/components/admin/DateRangeFilter";
import { FilterBar } from "@/components/admin/FilterBar";
import { SearchField } from "@/components/admin/SearchField";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { formatAppMessage } from "@/lib/messages/appMessages";
import {
  PAYMENT_METHOD_LABELS,
  type PaymentMethod,
} from "../types/payment";

type PaymentHistoryFiltersProps = {
  from: string;
  to: string;
  /** Mã lỗi khoảng ngày (MSG-54) hoặc null. */
  rangeError: "MSG-54" | null;
  onRangeChange: (range: { from: string; to: string }) => void;
  searchText: string;
  onSearchChange: (value: string) => void;
  method: PaymentMethod | "";
  onMethodChange: (method: PaymentMethod | "") => void;
};

const ALL = "ALL";

export function PaymentHistoryFilters({
  from,
  to,
  rangeError,
  onRangeChange,
  searchText,
  onSearchChange,
  method,
  onMethodChange,
}: PaymentHistoryFiltersProps) {
  return (
    <FilterBar>
      <DateRangeFilter
        from={from}
        to={to}
        onChange={onRangeChange}
        error={rangeError ? formatAppMessage(rangeError) : undefined}
      />
      <SearchField
        value={searchText}
        onChange={onSearchChange}
        placeholder="Tên bệnh nhân hoặc số phiếu thu"
        label="Tìm thanh toán"
      />
      <ToggleGroup
        variant="outline"
        size="sm"
        spacing={0}
        aria-label="Phương thức"
        value={[method || ALL]}
        onValueChange={(values) => {
          // Bấm lại mục đang chọn trả mảng rỗng: giữ nguyên lựa chọn hiện tại.
          const next = values[0];
          if (!next) return;
          onMethodChange(next === ALL ? "" : (next as PaymentMethod));
        }}
      >
        <ToggleGroupItem value={ALL}>Tất cả</ToggleGroupItem>
        <ToggleGroupItem value="CASH">{PAYMENT_METHOD_LABELS.CASH}</ToggleGroupItem>
        <ToggleGroupItem value="VNPAY">{PAYMENT_METHOD_LABELS.VNPAY}</ToggleGroupItem>
      </ToggleGroup>
    </FilterBar>
  );
}
