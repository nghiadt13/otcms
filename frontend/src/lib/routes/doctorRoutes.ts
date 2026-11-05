// Đường dẫn khu Bác sĩ chỉnh hình (/doctor) để liên kết giữa các màn mà không phải
// import `doctor-shell` (feature khác không được import lẫn nhau). Mục menu và mô tả
// nằm ở `features/doctor-shell/doctorNavigation.ts`.
export const DOCTOR_ROUTES = {
  dashboard: "/doctor",
  todayPatients: "/doctor/today",
  /** Gốc workspace khám: `/doctor/visits/<visitId>[/<màn>]`. */
  visits: "/doctor/visits",
  imagingStudies: "/doctor/imaging",
  schedule: "/doctor/schedule",
  account: "/doctor/account",
  notifications: "/doctor/notifications",
} as const;

/** Đường dẫn một lượt khám trong workspace bác sĩ; `section` rỗng là tab tổng quan. */
export function doctorVisitHref(visitId: string, section = ""): string {
  const base = `${DOCTOR_ROUTES.visits}/${encodeURIComponent(visitId)}`;
  return section ? `${base}/${section}` : base;
}
