import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import { describeMissing, type CheckInMissingItem } from "../schema/checkInReadiness";

interface CheckInReadinessProps {
  present: boolean;
  onPresentChange: (present: boolean) => void;
  missing: CheckInMissingItem[];
  submitting: boolean;
  onSubmit: () => void;
}

const MISSING_ID = "checkin-missing";

// Ba điều kiện của BR-26 ngay cạnh nút: ô có mặt, dòng "Còn thiếu" và nút Tiếp nhận (nút chính
// duy nhất của màn). Nút chỉ bật khi không còn gì thiếu.
export function CheckInReadiness({
  present,
  onPresentChange,
  missing,
  submitting,
  onSubmit,
}: CheckInReadinessProps) {
  const ready = missing.length === 0;
  return (
    <section aria-label="Xác nhận tiếp nhận" className="space-y-3 border-t pt-5">
      <div className="flex items-start gap-3">
        <Checkbox
          id="checkin-present"
          checked={present}
          onCheckedChange={(checked) => onPresentChange(checked === true)}
          disabled={submitting}
          className="mt-0.5"
        />
        <Label htmlFor="checkin-present" className="font-normal">
          Đã kiểm tra đúng người và bệnh nhân có mặt
        </Label>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p id={MISSING_ID} aria-live="polite" className="text-sm text-muted-foreground">
          {ready ? null : describeMissing(missing)}
        </p>
        <Button disabled={!ready || submitting} aria-describedby={MISSING_ID} onClick={onSubmit}>
          {submitting ? "Đang tiếp nhận..." : "Tiếp nhận"}
        </Button>
      </div>
    </section>
  );
}
