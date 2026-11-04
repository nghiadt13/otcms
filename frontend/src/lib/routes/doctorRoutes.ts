// Đường dẫn khu Bác sĩ chỉnh hình (/doctor) để liên kết giữa các màn mà không phải
// import `doctor-shell` (feature khác không được import lẫn nhau). Mục menu và mô tả
// nằm ở `features/doctor-shell/doctorNavigation.ts`.
export const DOCTOR_ROUTES = {
  dashboard: "/doctor",
  todayPatients: "/doctor/today",
  imagingStudies: "/doctor/imaging",
  schedule: "/doctor/schedule",
  rooms: "/doctor/rooms",
  account: "/doctor/account",
  notifications: "/doctor/notifications",
} as const;
