"use client";

import { FieldError } from "@/components/admin/FieldError";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Skeleton } from "@/components/ui/skeleton";
import { AF1_LINES } from "../schema/appointmentMessages";
import type { EligibleDoctor } from "../types/appointment";

type ReassignCandidateListProps = {
  loading: boolean;
  /** Thông báo lỗi 5xx khi tải ứng viên. */
  error: string | null;
  doctors: EligibleDoctor[] | null;
  selectedId: string;
  onSelect: (doctorId: string) => void;
  fieldError?: string;
  disabled: boolean;
  onRetry: () => void;
};

/** Ba nhánh trong Dialog: đang tải, lỗi, không có ứng viên (AF1), có ứng viên. */
export function ReassignCandidateList({
  loading,
  error,
  doctors,
  selectedId,
  onSelect,
  fieldError,
  disabled,
  onRetry,
}: ReassignCandidateListProps) {
  if (loading)
    return (
      <div className="grid gap-3" aria-hidden>
        <Skeleton className="h-5 w-2/3" />
        <Skeleton className="h-5 w-1/2" />
        <Skeleton className="h-5 w-3/5" />
      </div>
    );
  if (error)
    return (
      <div>
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
        <Button variant="outline" size="sm" className="mt-3" onClick={onRetry}>
          Thử lại
        </Button>
      </div>
    );
  if (!doctors || doctors.length === 0)
    return (
      <div className="grid gap-1 text-sm">
        {AF1_LINES.map((line) => (
          <p key={line}>{line}</p>
        ))}
      </div>
    );
  return (
    <div>
      <p id="reassign-candidates-label" className="mb-2 text-sm font-medium">
        Chọn bác sĩ có ca vào cùng giờ:
      </p>
      <RadioGroup
        aria-labelledby="reassign-candidates-label"
        value={selectedId}
        onValueChange={(value) => onSelect(String(value))}
        disabled={disabled}
        className="max-h-60 overflow-y-auto"
      >
        {doctors.map((doctor) => (
          <Label
            key={doctor.id}
            className="flex items-center gap-2 rounded-md border px-3 py-2 font-normal"
          >
            <RadioGroupItem value={doctor.id} />
            {doctor.fullName}
          </Label>
        ))}
      </RadioGroup>
      <FieldError message={fieldError} />
    </div>
  );
}
