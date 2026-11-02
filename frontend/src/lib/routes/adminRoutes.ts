// Đường dẫn các màn admin để liên kết giữa màn (feature không được import adminNavigation).
export const ADMIN_ROUTES = {
  dashboard: "/admin",
  accounts: "/admin/accounts",
  staffProfiles: "/admin/staff-profiles",
  staffShifts: "/admin/staff-shifts",
  appointments: "/admin/appointments",
  rooms: "/admin/rooms",
  services: "/admin/services",
  reviews: "/admin/reviews",
  websiteContent: "/admin/website-content",
  announcements: "/admin/announcements",
  payments: "/admin/payments",
  unpaidItems: "/admin/unpaid-items",
  reports: "/admin/reports",
  activityLogs: "/admin/activity-logs",
  account: "/admin/account",
  notifications: "/admin/notifications",
} as const;
