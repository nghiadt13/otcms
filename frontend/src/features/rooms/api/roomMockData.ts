import type { ServiceCategory } from "@/lib/domain/clinicCategories";
import type { Room, RoomServiceOption } from "../types/room";

// Fixture riêng của feature rooms (không import clinic-services). Khi nối BE thì bỏ.

const svcId = (n: number) => `c1a0e7b2-4f3d-4b8e-9a61-0d2f5e7c1a0${n}`;
const roomId = (n: number) =>
  `3b8e1f5a-2c4d-4e7a-9b1f-6a2d8c0e${String(1100 + n).padStart(4, "0")}`;

export const MOCK_SERVICES: RoomServiceOption[] = [
  { id: svcId(1), name: "Khám chỉnh hình", category: "EXAMINATION" },
  { id: svcId(2), name: "Tái khám", category: "EXAMINATION" },
  { id: svcId(3), name: "Khám nội tổng quát", category: "EXAMINATION" },
  { id: svcId(4), name: "X-quang", category: "IMAGING" },
  { id: svcId(5), name: "Siêu âm", category: "IMAGING" },
  { id: svcId(6), name: "CT", category: "IMAGING" },
  { id: svcId(7), name: "Xét nghiệm máu", category: "LABORATORY" },
  { id: svcId(8), name: "Xét nghiệm nước tiểu", category: "LABORATORY" },
  { id: svcId(9), name: "Sinh hóa máu", category: "LABORATORY" },
];

export const MOCK_MISSING_ROOM_ID = "00000000-0000-4000-8000-00000000dead";
/** Phòng đang dùng: đặt Tạm ngưng bị từ chối 409 MSG-64. Cờ nội bộ, không lộ ra API. */
export const MOCK_IN_USE_ROOM_NAME = "CĐHA-02";

type SeedRoom = {
  name: string;
  type: ServiceCategory;
  description: string;
  services: number[];
  unavailable?: boolean;
};

const SEED: SeedRoom[] = [
  {
    name: "P101",
    type: "EXAMINATION",
    description: "Phòng khám chỉnh hình, 3 giường khám.",
    services: [1, 2],
  },
  {
    name: "P102",
    type: "EXAMINATION",
    description: "Phòng khám chỉnh hình, có máy đo lực cơ.",
    services: [1, 2],
  },
  {
    name: "P103",
    type: "EXAMINATION",
    description: "Phòng khám nội tổng quát, 2 giường.",
    services: [3],
  },
  { name: "P104", type: "EXAMINATION", description: "", services: [1] },
  {
    name: "P105",
    type: "EXAMINATION",
    description: "Phòng tái khám.",
    services: [2],
  },
  {
    name: "P106",
    type: "EXAMINATION",
    description: "Phòng khám chỉnh hình trẻ em.",
    services: [1, 2, 3],
  },
  {
    name: "P107",
    type: "EXAMINATION",
    description: "Đang sửa điều hòa.",
    services: [1],
    unavailable: true,
  },
  { name: "P108", type: "EXAMINATION", description: "", services: [] },
  {
    name: "P109",
    type: "EXAMINATION",
    description: "Phòng khám nội.",
    services: [3],
  },
  {
    name: "P110",
    type: "EXAMINATION",
    description: "Phòng khám dự phòng.",
    services: [1, 2],
  },
  {
    name: "CĐHA-01",
    type: "IMAGING",
    description: "Phòng X-quang số hóa.",
    services: [4, 5, 6],
  },
  {
    name: "CĐHA-02",
    type: "IMAGING",
    description: "Phòng siêu âm và X-quang.",
    services: [4, 5],
  },
  { name: "CĐHA-03", type: "IMAGING", description: "Phòng CT.", services: [6] },
  {
    name: "CĐHA-04",
    type: "IMAGING",
    description: "Đang bảo trì máy.",
    services: [4],
    unavailable: true,
  },
  { name: "CĐHA-05", type: "IMAGING", description: "", services: [5] },
  {
    name: "CĐHA-06",
    type: "IMAGING",
    description: "Phòng X-quang di động.",
    services: [4],
  },
  { name: "CĐHA-07", type: "IMAGING", description: "", services: [4, 5] },
  {
    name: "CĐHA-08",
    type: "IMAGING",
    description: "Phòng siêu âm khớp.",
    services: [5],
  },
  {
    name: "XN-01",
    type: "LABORATORY",
    description: "Phòng lấy mẫu và xét nghiệm máu.",
    services: [7, 9],
  },
  {
    name: "XN-02",
    type: "LABORATORY",
    description: "Xét nghiệm nước tiểu.",
    services: [8],
  },
  { name: "XN-03", type: "LABORATORY", description: "", services: [7, 8, 9] },
  {
    name: "XN-04",
    type: "LABORATORY",
    description: "Ngừng nhận mẫu tạm thời.",
    services: [7],
    unavailable: true,
  },
  { name: "XN-05", type: "LABORATORY", description: "", services: [9] },
];

const serviceRef = (n: number) => {
  const s = MOCK_SERVICES[n - 1];
  return { id: s.id, name: s.name };
};

export function buildSeedRooms(): Room[] {
  return SEED.map((seed, index) => ({
    id: roomId(index + 1),
    name: seed.name,
    type: seed.type,
    description: seed.description,
    status: seed.unavailable ? "UNAVAILABLE" : "AVAILABLE",
    supportedServices: seed.services.map(serviceRef),
    allowedActions: ["edit", "changeStatus"],
  }));
}
