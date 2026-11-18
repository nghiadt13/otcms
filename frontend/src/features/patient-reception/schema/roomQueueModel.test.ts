import { describe, expect, it } from "vitest";
import type { ExamQueueItem } from "@/lib/api/examQueueApi";
import type { RoomQueueEntry } from "../api/receptionLiveApi";
import {
  itemsByVisit,
  roomOptionsOf,
  splitRoomEntries,
} from "./roomQueueModel";

const item = (overrides: Partial<ExamQueueItem>): ExamQueueItem => ({
  visitId: "v",
  queueNumber: 1,
  examStatus: "WAITING",
  source: "SCHEDULED",
  appointmentId: null,
  patientProfileId: "p",
  patientName: "Bệnh nhân",
  doctorProfileId: "d",
  doctorName: "BS",
  roomId: "r1",
  roomLabel: "Phòng 2",
  serviceId: null,
  queuedAt: null,
  startedAt: null,
  finishedAt: null,
  ...overrides,
});

const entry = (
  id: string,
  status: string,
  sortOrder: number,
): RoomQueueEntry => ({
  id,
  visitId: `visit-${id}`,
  queueNumber: sortOrder,
  stage: "EXAM",
  status,
  sortOrder,
});

describe("roomOptionsOf", () => {
  it("lấy các phòng có lượt, bỏ lượt chưa gắn phòng, xếp theo tên phòng", () => {
    const rooms = roomOptionsOf([
      item({ roomId: "r2", roomLabel: "Phòng 10" }),
      item({ roomId: "r1", roomLabel: "Phòng 2" }),
      item({ roomId: "r2", roomLabel: "Phòng 10" }),
      item({ roomId: null, roomLabel: null }),
    ]);
    expect(rooms).toEqual([
      { id: "r1", label: "Phòng 2" },
      { id: "r2", label: "Phòng 10" },
    ]);
  });
});

describe("splitRoomEntries", () => {
  it("tách hàng theo nhóm và giữ thứ tự BE; trạng thái khác không hiện", () => {
    const groups = splitRoomEntries([
      entry("a", "CALLED", 1),
      entry("b", "WAITING", 2),
      entry("c", "ABSENT", 3),
      entry("d", "WAITING", 4),
      entry("e", "AWAITING_CONCLUSION", 5),
      entry("f", "COMPLETED", 6),
      entry("g", "PENDING_PAYMENT", 7),
    ]);
    expect(groups.serving.map((e) => e.id)).toEqual(["a"]);
    expect(groups.waiting.map((e) => e.id)).toEqual(["b", "d"]);
    expect(groups.absent.map((e) => e.id)).toEqual(["c"]);
    expect(groups.awaiting.map((e) => e.id)).toEqual(["e"]);
  });

  it("đang khám cũng thuộc nhóm đang phục vụ", () => {
    expect(
      splitRoomEntries([entry("a", "IN_EXAMINATION", 1)]).serving.map(
        (e) => e.id,
      ),
    ).toEqual(["a"]);
  });
});

describe("itemsByVisit", () => {
  it("tra lượt theo mã lượt khám", () => {
    const map = itemsByVisit([item({ visitId: "v1", patientName: "An" })]);
    expect(map.get("v1")?.patientName).toBe("An");
    expect(map.get("missing")).toBeUndefined();
  });
});
