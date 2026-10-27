// Hình JSON khớp 1-1 với contract `/api/v1/admin/shifts` (phase-04 mục 7).
import type { ServiceCategory } from "@/lib/domain/clinicCategories";
import type { StaffRole } from "@/lib/domain/clinicRoles";

export type ShiftAction = "edit" | "delete" | "view";

export interface Shift {
  id: string;
  /** "2026-10-20" */
  date: string;
  /** "08:00" */
  startTime: string;
  endTime: string;
  staffId: string;
  staffName: string;
  role: StaffRole;
  roomId: string;
  roomName: string;
  roomCategory: ServiceCategory;
  allowedActions: ShiftAction[];
}

export interface ShiftListQuery {
  from: string;
  to: string;
  staffId?: string;
  role?: StaffRole;
  roomId?: string;
}
export interface ShiftListResponse {
  items: Shift[];
}
export interface ShiftInput {
  staffId: string;
  date: string;
  startTime: string;
  endTime: string;
  roomId: string;
}
export type ShiftUpdateInput = Omit<ShiftInput, "staffId">;

export interface StaffOption {
  staffId: string;
  fullName: string;
  role: StaffRole;
}
export interface StaffOptionsResponse {
  items: StaffOption[];
}
export interface RoomOption {
  roomId: string;
  name: string;
  category: ServiceCategory;
}
export interface RoomOptionsResponse {
  items: RoomOption[];
}

export interface AffectedAppointment {
  appointmentId: string;
  /** ISO có offset, "2026-10-20T08:30:00+07:00" */
  startAt: string;
  patientName: string;
  serviceName: string;
}
export interface AffectedAppointmentsResponse {
  shiftId: string;
  totalItems: number;
  items: AffectedAppointment[];
}

// Trạng thái UI, không phải API
export type ShiftPeriod = "week" | "day";
export type ShiftView = "grid" | "list";
