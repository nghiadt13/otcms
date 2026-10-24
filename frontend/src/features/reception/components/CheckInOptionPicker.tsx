import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { optionKey } from "../schema/checkInDraft";
import { optionDetail, optionTitle } from "../schema/checkInPresentation";
import type { CheckInOption } from "../types/checkIn";

interface CheckInOptionPickerProps {
  options: CheckInOption[];
  selectedKey: string | null;
  onChange: (key: string) => void;
  disabled: boolean;
}

// Cách tiếp nhận: lịch hẹn, quay lại trong 7 ngày hoặc vãng lai. BE đã quyết định có những
// lựa chọn nào; màn chỉ hiện đúng danh sách nhận được.
export function CheckInOptionPicker({
  options,
  selectedKey,
  onChange,
  disabled,
}: CheckInOptionPickerProps) {
  return (
    <div className="space-y-2">
      <p id="checkin-option-label" className="text-sm font-medium">
        Tiếp nhận theo
      </p>
      <RadioGroup
        aria-labelledby="checkin-option-label"
        value={selectedKey}
        onValueChange={(value) => onChange(String(value))}
        disabled={disabled}
        className="gap-2"
      >
        {options.map((option) => {
          const key = optionKey(option);
          return (
            <Label
              key={key}
              className="flex min-h-11 items-start gap-3 rounded-md border px-3 py-2 font-normal"
            >
              <RadioGroupItem value={key} className="mt-0.5" />
              <span className="grid gap-0.5">
                <span className="text-sm font-medium">{optionTitle(option)}</span>
                <span className="text-xs text-muted-foreground">{optionDetail(option)}</span>
              </span>
            </Label>
          );
        })}
      </RadioGroup>
    </div>
  );
}
