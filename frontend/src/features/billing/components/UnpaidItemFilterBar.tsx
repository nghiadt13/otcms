"use client";

import { useEffect, useState } from "react";
import { DateRangeFilter } from "@/components/admin/DateRangeFilter";
import { FilterBar } from "@/components/admin/FilterBar";
import { SearchField } from "@/components/admin/SearchField";
import {
  NativeSelect,
  NativeSelectOption,
} from "@/components/ui/native-select";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { isBillingDateRangeValid } from "../schema/billingDateRangeRules";
import {
  UNPAID_ITEM_KIND_LABELS,
  type ServiceOption,
  type UnpaidItemFilter,
  type UnpaidItemKind,
} from "../types/unpaidItem";

type UnpaidItemFilterBarProps = {
  filters: UnpaidItemFilter;
  onChange: (next: Partial<UnpaidItemFilter>) => void;
  serviceOptions: ServiceOption[];
  /** Tải danh sách dịch vụ lỗi: khóa ô Dịch vụ, các ô còn lại vẫn dùng được. */
  serviceOptionsFailed: boolean;
};

const ALL = "ALL";

/**
 * Bộ lọc giữ bản nháp khoảng ngày và từ khóa; chỉ đẩy giá trị hợp lệ lên `onChange`.
 * Khoảng ngày sai: hiện câu lỗi dưới ô, không gọi API, bảng giữ kết quả lần lọc hợp lệ trước.
 */
export function UnpaidItemFilterBar({
  filters,
  onChange,
  serviceOptions,
  serviceOptionsFailed,
}: UnpaidItemFilterBarProps) {
  const [range, setRange] = useState({ from: "", to: "" });
  const [searchText, setSearchText] = useState("");
  const debounced = useDebouncedValue(searchText.trim());
  const currentQ = filters.q ?? "";
  const rangeValid = isBillingDateRangeValid(range.from, range.to);

  useEffect(() => {
    if (debounced !== currentQ) onChange({ q: debounced || undefined });
  }, [debounced, currentQ, onChange]);

  const changeRange = (next: { from: string; to: string }) => {
    setRange(next);
    if (isBillingDateRangeValid(next.from, next.to))
      onChange({ from: next.from || undefined, to: next.to || undefined });
  };

  const isFee = filters.kind === "CONSULTATION_FEE";

  return (
    <FilterBar>
      <DateRangeFilter
        from={range.from}
        to={range.to}
        onChange={changeRange}
        error={rangeValid ? undefined : formatAppMessage("MSG-54")}
      />
      <SearchField
        value={searchText}
        onChange={setSearchText}
        placeholder="Tìm tên hoặc SĐT bệnh nhân"
        label="Tìm bệnh nhân"
      />
      <NativeSelect
        aria-label="Dịch vụ"
        value={isFee ? ALL : (filters.serviceId ?? ALL)}
        // Phí khám không gắn với dịch vụ nào: chọn cả hai cho kết quả rỗng nên khóa ô này.
        disabled={serviceOptionsFailed || isFee}
        onChange={(event) =>
          onChange({
            serviceId: event.target.value === ALL ? undefined : event.target.value,
          })
        }
      >
        <NativeSelectOption value={ALL}>Tất cả dịch vụ</NativeSelectOption>
        {serviceOptions.map((service) => (
          <NativeSelectOption key={service.id} value={service.id}>
            {service.name}
          </NativeSelectOption>
        ))}
      </NativeSelect>
      <NativeSelect
        aria-label="Loại khoản"
        value={filters.kind ?? ALL}
        onChange={(event) => {
          const kind =
            event.target.value === ALL
              ? undefined
              : (event.target.value as UnpaidItemKind);
          onChange({
            kind,
            ...(kind === "CONSULTATION_FEE" ? { serviceId: undefined } : {}),
          });
        }}
      >
        <NativeSelectOption value={ALL}>Tất cả loại khoản</NativeSelectOption>
        {(Object.keys(UNPAID_ITEM_KIND_LABELS) as UnpaidItemKind[]).map((kind) => (
          <NativeSelectOption key={kind} value={kind}>
            {UNPAID_ITEM_KIND_LABELS[kind]}
          </NativeSelectOption>
        ))}
      </NativeSelect>
    </FilterBar>
  );
}
