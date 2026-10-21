import { useId } from "react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { FieldError } from "./FieldError";

type DateRangeFilterProps = {
  /** yyyy-MM-dd hoặc "" */
  from: string;
  to: string;
  onChange: (range: { from: string; to: string }) => void;
  /** Màn truyền câu lỗi (MSG-72 ở 01/13, MSG-54 ở màn lọc khác); component không tự gán mã. */
  error?: string;
};

export function DateRangeFilter({
  from,
  to,
  onChange,
  error,
}: DateRangeFilterProps) {
  const id = useId();
  return (
    <div>
      <div className="flex flex-wrap items-end gap-3">
        <div className="grid gap-1.5">
          <Label htmlFor={`${id}-from`}>Từ ngày</Label>
          <Input
            id={`${id}-from`}
            type="date"
            value={from}
            max={to || undefined}
            aria-invalid={error ? true : undefined}
            onChange={(event) => onChange({ from: event.target.value, to })}
            className="w-40"
          />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor={`${id}-to`}>Đến ngày</Label>
          <Input
            id={`${id}-to`}
            type="date"
            value={to}
            min={from || undefined}
            aria-invalid={error ? true : undefined}
            onChange={(event) => onChange({ from, to: event.target.value })}
            className="w-40"
          />
        </div>
      </div>
      {error ? <FieldError message={error} /> : null}
    </div>
  );
}
