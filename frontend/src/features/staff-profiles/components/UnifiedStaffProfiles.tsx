"use client";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import type { StaffRole } from "@/lib/domain/clinicRoles";
import { AdminDoctorsLive } from "./AdminDoctorsLive";
import { StaffProfileManagement } from "./StaffProfileManagement";
const roles: [StaffRole, string][] = [
  ["ORTHOPEDIC_DOCTOR", "Bác sĩ"],
  ["RECEPTIONIST", "Lễ tân"],
  ["IMAGING_TECHNICIAN", "KTV hình ảnh"],
  ["LAB_TECHNICIAN", "KTV xét nghiệm"],
];
export function UnifiedStaffProfiles() {
  const [role, setRole] = useState<StaffRole>("ORTHOPEDIC_DOCTOR");
  return (
    <>
      <div
        role="tablist"
        aria-label="Vai trò nhân viên"
        className="mb-5 flex flex-wrap gap-2"
      >
        {roles.map(([value, label]) => (
          <Button
            key={value}
            id={"profile-tab-" + value}
            role="tab"
            aria-selected={role === value}
            aria-controls="staff-profile-panel"
            variant={role === value ? "secondary" : "ghost"}
            onClick={() => setRole(value)}
          >
            {label}
          </Button>
        ))}
      </div>
      <div
        id="staff-profile-panel"
        role="tabpanel"
        aria-labelledby={"profile-tab-" + role}
      >
        {role === "ORTHOPEDIC_DOCTOR" ? (
          <AdminDoctorsLive />
        ) : (
          <StaffProfileManagement key={role} role={role} />
        )}
      </div>
    </>
  );
}
