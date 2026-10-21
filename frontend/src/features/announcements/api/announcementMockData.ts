import type {
  AnnouncementAudience,
  AnnouncementChannel,
  AnnouncementDeliveryStatus,
  AnnouncementDetail,
  AnnouncementOptions,
} from "../types/announcement";

// Kho dữ liệu giả của UC74. Khi nối BE chỉ cần bỏ file này và đổi thân hàm trong announcementApi.ts.

export const MOCK_ANNOUNCEMENT_OPTIONS: AnnouncementOptions = {
  audiences: [
    "PUBLIC",
    "PATIENT",
    "RECEPTIONIST",
    "ORTHOPEDIC_DOCTOR",
    "IMAGING_TECHNICIAN",
    "LAB_TECHNICIAN",
  ],
  channels: ["EMAIL", "IN_APP"],
  allowedActions: ["create"],
};

/** Cấu hình độ trễ giả; test đặt về 0. */
export const mockConfig = { latencyMs: 200 };

const LONG_TITLE =
  "Thông báo về việc điều chỉnh lịch tiếp nhận bệnh nhân, thời gian chụp X-quang, " +
  "thời gian lấy mẫu xét nghiệm và quy trình thanh toán tại quầy trong tuần lễ tổng vệ sinh " +
  "toàn bộ phòng khám để bảo đảm an toàn cho người bệnh";

const AUDIENCES: AnnouncementAudience[] = [
  "PATIENT",
  "PUBLIC",
  "RECEPTIONIST",
  "ORTHOPEDIC_DOCTOR",
  "IMAGING_TECHNICIAN",
  "LAB_TECHNICIAN",
];
const CHANNEL_SETS: AnnouncementChannel[][] = [
  ["EMAIL", "IN_APP"],
  ["EMAIL"],
  ["IN_APP"],
];
const STATUSES: (AnnouncementDeliveryStatus | null)[] = [
  "SENT",
  "SENT",
  "PARTIAL_FAILURE",
  "NOT_SUPPORTED",
  null,
];

const pad = (n: number) => String(n).padStart(2, "0");
const uuid = (n: number) =>
  `00000000-0000-4000-8000-${String(n).padStart(12, "0")}`;

function buildSeed(): AnnouncementDetail[] {
  const base: AnnouncementDetail[] = [
    {
      id: uuid(1),
      title: "Nghỉ khám dịp Quốc khánh 2/9",
      body: "Phòng khám nghỉ khám ngày 02/09/2026.\nCấp cứu vẫn tiếp nhận 24/7.",
      audience: "PATIENT",
      channels: ["EMAIL", "IN_APP"],
      publishedAt: "2026-08-28T09:00:00+07:00",
      publishedByName: "Nguyễn Quản Trị",
      deliveryStatus: "SENT",
      allowedActions: ["view"],
    },
    {
      id: uuid(2),
      title: "Thay đổi giờ chụp X-quang thứ Bảy",
      body: "Từ 15/10, ca chụp X-quang thứ Bảy bắt đầu lúc 8 giờ.\nKTV vui lòng có mặt trước 15 phút.",
      audience: "IMAGING_TECHNICIAN",
      channels: ["EMAIL"],
      publishedAt: "2026-10-15T16:20:00+07:00",
      publishedByName: "Nguyễn Quản Trị",
      deliveryStatus: "PARTIAL_FAILURE",
      allowedActions: ["view"],
    },
    {
      id: uuid(3),
      title: "Giới thiệu gói khám sức khỏe tổng quát",
      body: "Gói khám sức khỏe tổng quát mới đã có tại phòng khám.\nXem chi tiết ở trang Dịch vụ.",
      audience: "PUBLIC",
      channels: ["IN_APP"],
      publishedAt: "2026-10-10T08:00:00+07:00",
      publishedByName: "Nguyễn Quản Trị",
      deliveryStatus: "NOT_SUPPORTED",
      allowedActions: ["view"],
    },
    {
      id: uuid(4),
      title: LONG_TITLE,
      body: "Chi tiết lịch điều chỉnh sẽ được gửi riêng cho từng bộ phận.",
      audience: "PUBLIC",
      channels: ["EMAIL", "IN_APP"],
      publishedAt: "2026-10-12T10:30:00+07:00",
      publishedByName: "Nguyễn Quản Trị",
      deliveryStatus: "SENT",
      allowedActions: ["view"],
    },
  ];
  const generated: AnnouncementDetail[] = Array.from({ length: 24 }, (_, i) => {
    const n = i + 5;
    const month = 8 + (i % 3);
    const day = 1 + ((i * 3) % 18);
    const status = STATUSES[i % STATUSES.length];
    const item: AnnouncementDetail = {
      id: uuid(n),
      title: `Thông báo vận hành số ${n}`,
      body: `Nội dung thông báo số ${n}.\nVui lòng đọc kỹ và thực hiện đúng hướng dẫn.`,
      audience: AUDIENCES[i % AUDIENCES.length],
      channels: CHANNEL_SETS[i % CHANNEL_SETS.length],
      publishedAt: `2026-${pad(month)}-${pad(day)}T${pad(8 + (i % 9))}:${pad((i * 7) % 60)}:00+07:00`,
      publishedByName: "Nguyễn Quản Trị",
      allowedActions: ["view"],
    };
    // Một bản ghi vắng hẳn field deliveryStatus (BE chưa trả).
    if (i !== 0) item.deliveryStatus = status;
    return item;
  });
  return [...base, ...generated].sort((a, b) =>
    b.publishedAt.localeCompare(a.publishedAt),
  );
}

export const mockStore: {
  announcements: AnnouncementDetail[];
  options: AnnouncementOptions;
  nextId: number;
} = {
  announcements: buildSeed(),
  options: structuredClone(MOCK_ANNOUNCEMENT_OPTIONS),
  nextId: 1000,
};

/** Đặt lại kho giả (dùng trong test); `overrides` thay từng phần. */
export function resetAnnouncementMock(
  overrides: {
    announcements?: AnnouncementDetail[];
    options?: Partial<AnnouncementOptions>;
  } = {},
) {
  mockStore.announcements = overrides.announcements ?? buildSeed();
  mockStore.options = {
    ...structuredClone(MOCK_ANNOUNCEMENT_OPTIONS),
    ...overrides.options,
  };
  mockStore.nextId = 1000;
}
