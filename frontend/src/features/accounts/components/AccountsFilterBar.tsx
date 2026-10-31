"use client";

import { FilterBar } from "@/components/admin/FilterBar";
import { SearchField } from "@/components/admin/SearchField";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { STATUS_LABELS } from "../accountDisplay";
import type { AccountStatus } from "../types/account";

type AccountsFilterBarProps = {
  searchText: string;
  onSearchChange: (value: string) => void;
  status: AccountStatus | undefined;
  onStatusChange: (status: AccountStatus | undefined) => void;
};

const ALL = "ALL";

export function AccountsFilterBar({
  searchText,
  onSearchChange,
  status,
  onStatusChange,
}: AccountsFilterBarProps) {
  return (
    <FilterBar>
      <SearchField
        value={searchText}
        onChange={onSearchChange}
        placeholder="Tìm email hoặc họ tên bệnh nhân"
        label="Tìm tài khoản"
      />
      <ToggleGroup
        variant="outline"
        size="sm"
        spacing={0}
        aria-label="Trạng thái"
        value={[status ?? ALL]}
        onValueChange={(values) => {
          // Bấm lại mục đang chọn trả mảng rỗng: giữ nguyên lựa chọn hiện tại.
          const next = values[0];
          if (!next) return;
          onStatusChange(next === ALL ? undefined : (next as AccountStatus));
        }}
      >
        <ToggleGroupItem value={ALL}>Tất cả</ToggleGroupItem>
        <ToggleGroupItem value="ACTIVE">{STATUS_LABELS.ACTIVE}</ToggleGroupItem>
        <ToggleGroupItem value="INACTIVE">
          {STATUS_LABELS.INACTIVE}
        </ToggleGroupItem>
      </ToggleGroup>
    </FilterBar>
  );
}
