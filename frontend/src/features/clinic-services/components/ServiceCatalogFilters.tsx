"use client";

import { FilterBar } from "@/components/admin/FilterBar";
import { SearchField } from "@/components/admin/SearchField";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import {
  CATEGORY_LABELS,
  SERVICE_CATEGORIES,
  type ServiceCategory,
} from "@/lib/domain/clinicCategories";
import { SERVICE_STATUS_LABELS, type ServiceStatus } from "../types/serviceCatalog";

type ServiceCatalogFiltersProps = {
  searchText: string;
  onSearchTextChange: (value: string) => void;
  type: ServiceCategory | "";
  onTypeChange: (value: ServiceCategory | "") => void;
  status: ServiceStatus | "";
  onStatusChange: (value: ServiceStatus | "") => void;
};

export function ServiceCatalogFilters(props: ServiceCatalogFiltersProps) {
  return (
    <FilterBar>
      <SearchField
        value={props.searchText}
        onChange={props.onSearchTextChange}
        placeholder="Tìm theo tên dịch vụ"
        label="Tìm theo tên dịch vụ"
      />
      <NativeSelect
        aria-label="Lọc theo loại"
        className="w-full sm:w-52"
        value={props.type}
        onChange={(event) => props.onTypeChange(event.target.value as ServiceCategory | "")}
      >
        <NativeSelectOption value="">Mọi loại</NativeSelectOption>
        {SERVICE_CATEGORIES.map((category) => (
          <NativeSelectOption key={category} value={category}>
            {CATEGORY_LABELS[category]}
          </NativeSelectOption>
        ))}
      </NativeSelect>
      <NativeSelect
        aria-label="Lọc theo trạng thái"
        className="w-full sm:w-44"
        value={props.status}
        onChange={(event) => props.onStatusChange(event.target.value as ServiceStatus | "")}
      >
        <NativeSelectOption value="">Mọi trạng thái</NativeSelectOption>
        {(Object.keys(SERVICE_STATUS_LABELS) as ServiceStatus[]).map((status) => (
          <NativeSelectOption key={status} value={status}>
            {SERVICE_STATUS_LABELS[status]}
          </NativeSelectOption>
        ))}
      </NativeSelect>
    </FilterBar>
  );
}
