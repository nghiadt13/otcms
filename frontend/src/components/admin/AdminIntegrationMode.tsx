"use client";
import { useState, type ReactNode } from "react";
import { NativeSelect } from "@/components/ui/native-select";
import { Label } from "@/components/ui/label";
export function AdminIntegrationMode({
  live,
  prototype,
}: {
  live: ReactNode;
  prototype: ReactNode;
}) {
  const [mode, setMode] = useState("live");
  return (
    <>
      <div className="mb-5 space-y-2">
        <Label htmlFor="integration-mode">Nguồn dữ liệu</Label>
        <NativeSelect
          id="integration-mode"
          value={mode}
          onChange={(e) => setMode(e.target.value)}
        >
          <option value="live">Bác sĩ — dữ liệu backend</option>
          <option value="prototype">
            Bản mẫu nhân viên / phòng — chưa nối backend
          </option>
        </NativeSelect>
        {mode === "prototype" && (
          <p role="status" className="rounded-lg border bg-muted p-3 text-sm">
            Đây là bản mẫu với dữ liệu giả, bao gồm cả bác sĩ mẫu. Thao tác ở
            đây không lưu vào database. Hồ sơ và ca theo phòng của lễ tân, kỹ
            thuật viên đang chờ backend.
          </p>
        )}
      </div>
      {mode === "live" ? live : prototype}
    </>
  );
}
