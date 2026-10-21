import { FilterBar } from "@/components/admin/FilterBar";
import { SearchField } from "@/components/admin/SearchField";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import {
  WEBSITE_CONTENT_TYPES,
  WEBSITE_CONTENT_TYPE_LABELS,
  type WebsiteContentType,
} from "../types/websiteContentAdmin";

type Props = {
  search: string;
  onSearchChange: (value: string) => void;
  type: WebsiteContentType | "";
  onTypeChange: (value: WebsiteContentType | "") => void;
};

export function WebsiteContentFilters({
  search,
  onSearchChange,
  type,
  onTypeChange,
}: Props) {
  return (
    <FilterBar>
      <SearchField
        value={search}
        onChange={onSearchChange}
        placeholder="Tìm theo tiêu đề"
        label="Tìm nội dung theo tiêu đề"
      />
      <label className="grid w-full gap-1 text-xs text-muted-foreground sm:w-auto">
        Loại nội dung
        <NativeSelect
          className="w-full sm:w-52"
          value={type}
          onChange={(event) =>
            onTypeChange(event.target.value as WebsiteContentType | "")
          }
        >
          <NativeSelectOption value="">Tất cả</NativeSelectOption>
          {WEBSITE_CONTENT_TYPES.map((value) => (
            <NativeSelectOption key={value} value={value}>
              {WEBSITE_CONTENT_TYPE_LABELS[value]}
            </NativeSelectOption>
          ))}
        </NativeSelect>
      </label>
    </FilterBar>
  );
}
