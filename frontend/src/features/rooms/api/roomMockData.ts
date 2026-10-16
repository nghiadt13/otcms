import type { Room } from "../types/room";

export const initialRooms: Room[] = [
  {
    id: "room-001",
    name: "Phòng khám 101",
    type: "examination",
    description: "Khám chỉnh hình tổng quát, tầng 1.",
    supportedServices: ["Khám chỉnh hình", "Tái khám"],
    status: "available",
  },
  {
    id: "room-002",
    name: "Phòng khám 102",
    type: "examination",
    description: "Khám cột sống và khớp gối, tầng 1.",
    supportedServices: ["Khám chỉnh hình", "Tái khám", "Tiêm khớp"],
    status: "available",
  },
  {
    id: "room-003",
    name: "Phòng X-quang",
    type: "imaging",
    description: "Máy X-quang kỹ thuật số.",
    supportedServices: ["X-quang"],
    status: "available",
  },
  {
    id: "room-004",
    name: "Phòng MRI",
    type: "imaging",
    description: "Máy MRI 1.5T. Đang bảo trì định kỳ.",
    supportedServices: ["MRI"],
    status: "unavailable",
  },
  {
    id: "room-005",
    name: "Phòng thủ thuật",
    type: "procedure",
    description: "Bó bột, nẹp và tiêm khớp.",
    supportedServices: ["Bó bột", "Tiêm khớp"],
    status: "available",
  },
];
