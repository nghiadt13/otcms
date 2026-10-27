"use client";

import { FilterBar } from "@/components/admin/FilterBar";
import { SearchField } from "@/components/admin/SearchField";
import {
  NativeSelect,
  NativeSelectOption,
} from "@/components/ui/native-select";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import {
  ACCOUNT_TYPE_LABELS,
  type AccountType,
} from "@/lib/domain/clinicRoles";
import { STATUS_LABELS } from "../accountDisplay";
import type { AccountStatus } from "../types/account";

type AccountsFilterBarProps = {
  searchText: string;
  onSearchChange: (value: string) => void;
  role: AccountType | undefined;
  onRoleChange: (role: AccountType | undefined) => void;
  status: AccountStatus | undefined;
  onStatusChange: (status: AccountStatus | undefined) => void;
};

const ALL = "ALL";

export function AccountsFilterBar({
  searchText,
  onSearchChange,
  role,
  onRoleChange,
  status,
  onStatusChange,
}: AccountsFilterBarProps) {
  return (
    <FilterBar>
      <SearchField
        value={searchText}
        onChange={onSearchChange}
        placeholder="Tìm tên đăng nhập, email, họ tên"
        label="Tìm tài khoản"
      />
      <NativeSelect
        aria-label="Loại tài khoản"
        value={role ?? ALL}
        onChange={(event) =>
          onRoleChange(
            event.target.value === ALL
              ? undefined
              : (event.target.value as AccountType),
          )
        }
      >
        <NativeSelectOption value={ALL}>
          Tất cả loại tài khoản
        </NativeSelectOption>
        {(Object.keys(ACCOUNT_TYPE_LABELS) as AccountType[]).map((type) => (
          <NativeSelectOption key={type} value={type}>
            {ACCOUNT_TYPE_LABELS[type]}
          </NativeSelectOption>
        ))}
      </NativeSelect>
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
