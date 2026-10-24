import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import { Skeleton } from "@/components/ui/skeleton";
import type { PriorityGroup } from "../types/checkIn";

interface PriorityGroupSelectProps {
  groups: PriorityGroup[];
  loading: boolean;
  error: unknown;
  /** Mã nhóm; null là "Không ưu tiên". */
  value: string | null;
  onChange: (code: string | null) => void;
  onRetry: () => void;
  disabled: boolean;
}

const SELECT_ID = "checkin-priority";
const ERROR_ID = "checkin-priority-error";

// Nhóm ưu tiên lấy từ cấu hình BE (BR-31), mặc định "Không ưu tiên". Lỗi tải: khóa ô, nói rõ và
// cho thử lại.
export function PriorityGroupSelect({
  groups,
  loading,
  error,
  value,
  onChange,
  onRetry,
  disabled,
}: PriorityGroupSelectProps) {
  const failed = Boolean(error);
  return (
    <div className="grid gap-2">
      <Label htmlFor={SELECT_ID}>Nhóm ưu tiên</Label>
      {loading ? (
        <Skeleton className="h-8 w-full max-w-xs" />
      ) : (
        <NativeSelect
          id={SELECT_ID}
          className="w-full max-w-xs"
          value={value ?? ""}
          disabled={disabled || failed}
          aria-describedby={failed ? ERROR_ID : undefined}
          onChange={(event) =>
            onChange(event.target.value === "" ? null : event.target.value)
          }
        >
          <NativeSelectOption value="">Không ưu tiên</NativeSelectOption>
          {groups.map((group) => (
            <NativeSelectOption key={group.code} value={group.code}>
              {group.label}
            </NativeSelectOption>
          ))}
        </NativeSelect>
      )}
      {failed ? (
        <div className="flex flex-wrap items-center gap-3">
          <p id={ERROR_ID} role="alert" className="text-sm text-destructive">
            Không tải được nhóm ưu tiên.
          </p>
          <Button variant="outline" size="sm" onClick={onRetry}>
            Thử lại
          </Button>
        </div>
      ) : null}
    </div>
  );
}
