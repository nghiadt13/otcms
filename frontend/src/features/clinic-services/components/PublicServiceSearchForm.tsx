import { Search } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  NativeSelect,
  NativeSelectOption,
} from "@/components/ui/native-select";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { formatAppMessage } from "@/lib/messages/appMessages";
import {
  CATEGORY_LABELS,
  SERVICE_CATEGORIES,
} from "@/lib/domain/clinicCategories";
import { cn } from "@/lib/utils";
import type { ServiceListError } from "../schema/serviceListQuery";

// Form tìm và lọc dịch vụ: GET tới /services nên hoạt động cả khi chưa có JavaScript.
// Ô và ô chọn cao h-12 chữ text-base cho người lớn tuổi; tiêu chí sai hiện MSG-54 bên dưới.
export function PublicServiceSearchForm({
  input,
  error,
  className,
}: {
  input: { q: string; type: string };
  error: ServiceListError | null;
  className?: string;
}) {
  const errorId = "service-search-error";
  return (
    <form
      action="/services"
      className={cn("flex flex-col gap-4 sm:flex-row sm:items-end", className)}
      method="get"
      role="search"
    >
      <div className="grid gap-2 sm:flex-1">
        <Label className="text-base" htmlFor="service-search-q">
          Tên dịch vụ
        </Label>
        <Input
          aria-describedby={error ? errorId : undefined}
          aria-invalid={error?.field === "q" || undefined}
          className="h-12 text-base md:text-base"
          defaultValue={input.q}
          id="service-search-q"
          name="q"
          placeholder="Ví dụ: X-quang, khớp gối"
        />
      </div>

      <div className="grid gap-2 sm:w-64">
        <Label className="text-base" htmlFor="service-search-type">
          Loại dịch vụ
        </Label>
        <NativeSelect
          className="w-full [&>select]:h-12 [&>select]:text-base"
          defaultValue={input.type}
          id="service-search-type"
          name="type"
        >
          <NativeSelectOption value="">Tất cả loại</NativeSelectOption>
          {SERVICE_CATEGORIES.map((category) => (
            <NativeSelectOption key={category} value={category}>
              {CATEGORY_LABELS[category]}
            </NativeSelectOption>
          ))}
        </NativeSelect>
      </div>

      <Button className={publicButtonClass("primary")} type="submit">
        <Search aria-hidden />
        Tìm dịch vụ
      </Button>

      {error ? (
        <p
          className="text-base text-destructive sm:basis-full"
          id={errorId}
          role="alert"
        >
          {formatAppMessage(error.code)}
        </p>
      ) : null}
    </form>
  );
}
