import { FilterBar } from "@/components/admin/FilterBar";
import { SearchField } from "@/components/admin/SearchField";
import {
  NativeSelect,
  NativeSelectOption,
} from "@/components/ui/native-select";
import { ROLE_LABELS, STAFF_ROLES } from "@/lib/domain/clinicRoles";
import type { StaffProfileFilters as Filters } from "../types/staffProfile";

type StaffProfileFiltersProps = {
  search: string;
  onSearchChange: (value: string) => void;
  filters: Filters;
  onFilterChange: <K extends keyof Filters>(key: K, value: Filters[K]) => void;
};

/** Thanh lọc màn 91. Lọc Công khai chỉ áp cho bác sĩ nên khóa vai trò ở bác sĩ. */
export function StaffProfileFilters({
  search,
  onSearchChange,
  filters,
  onFilterChange,
}: StaffProfileFiltersProps) {
  return (
    <FilterBar>
      <SearchField
        value={search}
        onChange={onSearchChange}
        placeholder="Tìm họ tên, tên đăng nhập"
        label="Tìm hồ sơ nhân viên"
      />
      <NativeSelect
        aria-label="Vai trò"
        className="w-full sm:w-auto"
        value={filters.role}
        disabled={filters.isPublic !== ""}
        onChange={(event) =>
          onFilterChange("role", event.target.value as Filters["role"])
        }
      >
        <NativeSelectOption value="">Vai trò: Tất cả</NativeSelectOption>
        {STAFF_ROLES.map((role) => (
          <NativeSelectOption key={role} value={role}>
            {ROLE_LABELS[role]}
          </NativeSelectOption>
        ))}
      </NativeSelect>
      <NativeSelect
        aria-label="Hồ sơ"
        className="w-full sm:w-auto"
        value={filters.status}
        onChange={(event) =>
          onFilterChange("status", event.target.value as Filters["status"])
        }
      >
        <NativeSelectOption value="">Hồ sơ: Tất cả</NativeSelectOption>
        <NativeSelectOption value="ACTIVE">Đang hoạt động</NativeSelectOption>
        <NativeSelectOption value="REMOVED">Đã gỡ</NativeSelectOption>
      </NativeSelect>
      <NativeSelect
        aria-label="Công khai"
        className="w-full sm:w-auto"
        value={filters.isPublic}
        onChange={(event) =>
          onFilterChange("isPublic", event.target.value as Filters["isPublic"])
        }
      >
        <NativeSelectOption value="">Công khai: Tất cả</NativeSelectOption>
        <NativeSelectOption value="true">Có</NativeSelectOption>
        <NativeSelectOption value="false">Không</NativeSelectOption>
      </NativeSelect>
    </FilterBar>
  );
}
