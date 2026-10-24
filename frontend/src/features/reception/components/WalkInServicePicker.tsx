import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import type { CheckInServiceOption } from "../types/checkIn";

interface WalkInServicePickerProps {
  services: CheckInServiceOption[];
  selectedId: string | null;
  onChange: (serviceId: string) => void;
  disabled: boolean;
}

// Vãng lai: lễ tân chọn dịch vụ Khám để biết phí. Không chọn thì chưa thu được (BR-27: phòng và
// bác sĩ do BE xếp, FE không chọn).
export function WalkInServicePicker({
  services,
  selectedId,
  onChange,
  disabled,
}: WalkInServicePickerProps) {
  return (
    <div className="space-y-2">
      <p id="checkin-service-label" className="text-sm font-medium">
        Dịch vụ khám
      </p>
      <RadioGroup
        aria-labelledby="checkin-service-label"
        value={selectedId}
        onValueChange={(value) => onChange(String(value))}
        disabled={disabled}
        className="gap-2"
      >
        {services.map((service) => (
          <Label
            key={service.id}
            className="flex min-h-11 items-center justify-between gap-3 rounded-md border px-3 py-2 font-normal"
          >
            <span className="flex items-center gap-3">
              <RadioGroupItem value={service.id} />
              {service.name}
            </span>
            <span className="text-sm font-medium tabular-nums">
              {formatCurrencyVnd(service.price)}
            </span>
          </Label>
        ))}
      </RadioGroup>
      <p className="text-xs text-muted-foreground">
        Phòng và bác sĩ do hệ thống xếp theo phòng ít người chờ nhất.
      </p>
    </div>
  );
}
