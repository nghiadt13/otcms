"use client";

import { Switch } from "@/components/ui/switch";
import { canToggleDoctorDemo, setDoctorDemoMode } from "../api/doctorDemoMode";
import { useDoctorDemoMode } from "../hooks/useDoctorData";

// Công tắc "Dữ liệu mẫu" ở header khu bác sĩ. Chỉ hiện khi môi trường cho phép (không phải
// production, không bị build ép). Đổi chế độ thì tải lại trang để mọi màn đọc lại từ nguồn mới.

export function DoctorDemoSwitch() {
  const on = useDoctorDemoMode();
  if (!canToggleDoctorDemo()) return null;
  return (
    <label className="flex items-center gap-2 text-xs text-muted-foreground">
      <Switch
        size="sm"
        checked={on}
        onCheckedChange={(checked) => {
          setDoctorDemoMode(checked);
          window.location.reload();
        }}
        aria-label="Dùng dữ liệu mẫu"
      />
      <span className="hidden sm:inline">Dữ liệu mẫu</span>
    </label>
  );
}
