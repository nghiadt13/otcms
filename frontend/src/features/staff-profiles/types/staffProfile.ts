import type { StaffRole } from "@/lib/domain/clinicRoles";

export type StaffProfileStatus = "ACTIVE" | "REMOVED";
// "Xem" luôn có, không nằm trong allowedActions.
export type StaffProfileAction = "edit" | "remove" | "togglePublic";

/** Chỉ có khi vai trò là ORTHOPEDIC_DOCTOR. */
export type DoctorInfo = {
  specialties: string[];
  qualifications: string;
  biography: string;
  consultationInfo: string;
  isPublic: boolean;
};

/** Dòng danh sách (màn 91). */
export type StaffProfileListItem = {
  id: string;
  accountId: string;
  username: string;
  role: StaffRole;
  displayName: string;
  photoUrl: string | null;
  workContact: string;
  status: StaffProfileStatus;
  /** null = không phải bác sĩ, hiện "—". */
  isPublic: boolean | null;
  allowedActions: StaffProfileAction[];
};

/** Chi tiết (màn 92). */
export type StaffProfileDetail = Omit<StaffProfileListItem, "isPublic"> & {
  doctor: DoctorInfo | null;
  /** Chỉ có khi bác sĩ đang công khai. */
  publicUrl: string | null;
};

/** Tài khoản nhân viên Active chưa có hồ sơ (combobox màn 92). */
export type EligibleStaffAccount = {
  accountId: string;
  username: string;
  fullName: string;
  role: StaffRole;
};

/** Phần JSON của part `data` (multipart). */
export type StaffProfileInput = {
  /** Chỉ khi tạo. */
  accountId?: string;
  displayName: string;
  workContact: string;
  /** Chỉ khi vai trò bác sĩ. */
  doctor?: DoctorInfo;
};

/** Bộ lọc màn 91. Rỗng thì bỏ khỏi query; `isPublic` đổi thành `public`. */
export type StaffProfileFilters = {
  q: string;
  role: StaffRole | "";
  status: StaffProfileStatus | "";
  isPublic: "" | "true" | "false";
};

/** Bác sĩ hiện ở trang chủ công khai. */
export type FeaturedDoctor = {
  id: string;
  displayName: string;
  photoUrl: string | null;
  specialties: string[];
  qualifications: string;
};
