import type { UserAccount } from "../types/account";

// Sử dụng ngày gần thời điểm hiện tại (2026-10-19) để hiển thị relative time
export const initialAccounts: UserAccount[] = [
  {
    id: "user-01",
    fullName: "BS. Nguyễn Văn Long",
    email: "long.nv@otcms.vn",
    phone: "0912345678",
    role: "orthopedic_doctor",
    status: "active",
    createdAt: "12/03/2026",
    lastSignInAt: "2026-10-19T08:30:00Z", // Hôm nay (vài giờ trước)
  },
  {
    id: "user-02",
    fullName: "BS. Trần Thị Mai",
    email: "mai.tt@otcms.vn",
    phone: "0923456789",
    role: "orthopedic_doctor",
    status: "active",
    createdAt: "15/03/2026",
    lastSignInAt: "2026-10-18T14:15:00Z", // Hôm qua
  },
  {
    id: "user-03",
    fullName: "Lê Thị Thu Hà",
    email: "ha.reception@otcms.vn",
    phone: "0988776655",
    role: "receptionist",
    status: "active",
    createdAt: "20/03/2026",
    lastSignInAt: "2026-10-17T09:00:00Z", // 2 ngày trước
  },
  {
    id: "user-04",
    fullName: "Phạm Văn Hoàng",
    email: "hoang.rad@otcms.vn",
    phone: "0934567890",
    role: "radiology_technician",
    status: "active",
    createdAt: "25/03/2026",
    lastSignInAt: "2026-10-14T16:20:00Z", // 5 ngày trước
  },
  {
    id: "user-05",
    fullName: "BS. Vũ Quốc Bảo",
    email: "bao.vq@otcms.vn",
    phone: "0945678901",
    role: "orthopedic_doctor",
    status: "pending_invitation",
    createdAt: "18/10/2026",
    lastSignInAt: null, // Chưa đăng nhập
  },
  {
    id: "user-06",
    fullName: "Bùi Minh Tâm",
    email: "tam.bm@otcms.vn",
    phone: "0956789012",
    role: "receptionist",
    status: "inactive",
    createdAt: "10/02/2026",
    lastSignInAt: "2026-09-01T17:00:00Z",
    lockReason: "Nhân viên tạm nghỉ việc theo nguyện vọng cá nhân",
    lockedBy: "Admin OTCMS",
    lockedAt: "2026-09-02T10:00:00Z",
  },
  {
    id: "user-07",
    fullName: "Hoàng Đức An",
    email: "an.hoang@gmail.com",
    phone: "0967890123",
    role: "patient",
    status: "active",
    createdAt: "01/04/2026",
    lastSignInAt: "2026-10-15T14:10:00Z", // 4 ngày trước
  },
  {
    id: "user-08",
    fullName: "Lê Thị Thanh",
    email: "thanh.le@gmail.com",
    phone: "0978901234",
    role: "patient",
    status: "active",
    createdAt: "05/04/2026",
    lastSignInAt: "2026-10-12T10:05:00Z", // 1 tuần trước
  },
  {
    id: "user-09",
    fullName: "Admin OTCMS",
    email: "admin@otcms.local",
    phone: "0900000001",
    role: "admin",
    status: "active",
    createdAt: "01/01/2026",
    lastSignInAt: "2026-10-19T11:45:00Z",
  },
];
