import { beforeEach, describe, expect, it } from "vitest";
import { checkInPatient } from "./checkInApi";
import { resetReceptionMockStore } from "./receptionMockStore";
import { rejectionOf } from "./checkInTestErrors";
import { getReceptionQueue } from "./receptionQueueApi";
import {
  forceNextReceptionQueueFailure,
  resetReceptionQueueMockControls,
  setReceptionQueueForcedEmpty,
} from "./receptionQueueMockControls";
import { addTestEntry } from "./receptionQueueTestEntries";
import type { ReceptionQueueStatus } from "../types/receptionQueue";

// Kho giả mặc định (S04b) hôm nay: Phòng khám 1 có #4 và #8 đang chờ, #6 vắng mặt; Phòng khám 2 có
// #3 đang phục vụ, #5, #7, #11 đang chờ. Hai bản ghi Xong không bao giờ hiện.

const ALL: ReceptionQueueStatus[] = ["WAITING", "BEING_SERVED", "ABSENT"];

beforeEach(() => {
  resetReceptionMockStore();
  resetReceptionQueueMockControls();
});

describe("getReceptionQueue", () => {
  it("mặc định Đang chờ + Vắng mặt: 6 dòng, không có Đang phục vụ", async () => {
    const response = await getReceptionQueue();
    expect(response.items).toHaveLength(6);
    expect(response.items.some((row) => row.status === "BEING_SERVED")).toBe(false);
    expect(response.items.filter((row) => row.status === "ABSENT")).toHaveLength(1);
  });

  it("chọn Tất cả thêm Đang phục vụ, dòng này không có vị trí", async () => {
    const response = await getReceptionQueue({ status: ALL });
    expect(response.items).toHaveLength(7);
    expect(response.items).toContainEqual(
      expect.objectContaining({
        queueNumber: 3,
        roomName: "Phòng khám 2",
        status: "BEING_SERVED",
        position: null,
      }),
    );
  });

  it("vị trí là thứ tự trong hàng của từng phòng, theo giờ vào hàng", async () => {
    const response = await getReceptionQueue();
    const waitingOf = (roomName: string) =>
      response.items
        .filter((row) => row.roomName === roomName && row.status === "WAITING")
        .map((row) => [row.queueNumber, row.position]);
    expect(waitingOf("Phòng khám 2")).toEqual([[5, 1], [7, 2], [11, 3]]);
    expect(waitingOf("Phòng khám 1")).toEqual([[4, 1], [8, 2]]);
  });

  it("nhóm ưu tiên đứng trước, rồi tới giờ vào hàng; bản ghi mới xếp đúng chỗ", async () => {
    addTestEntry({ patientName: "Người đến sau", roomName: "Phòng khám 2", time: "09:20" });
    addTestEntry({
      patientName: "Người ưu tiên",
      roomName: "Phòng khám 2",
      time: "09:30",
      priorityLabel: "Người từ 75 tuổi",
    });
    const response = await getReceptionQueue();
    const room2 = response.items
      .filter((row) => row.roomName === "Phòng khám 2" && row.status === "WAITING")
      .map((row) => [row.queueNumber, row.position]);
    expect(room2).toEqual([[13, 1], [5, 2], [7, 3], [11, 4], [12, 5]]);
  });

  it("đếm theo cả ngày (kể cả đã xong), không đổi theo bộ lọc phòng và trạng thái", async () => {
    const all = await getReceptionQueue();
    const filtered = await getReceptionQueue({ roomId: "room-k2", status: ["WAITING"] });
    // 9 đã tiếp nhận hôm nay (kể cả 2 đã xong), 5 đang chờ, 1 vắng mặt.
    expect(all.counts).toEqual({ total: 9, waiting: 5, absent: 1 });
    expect(filtered.counts).toEqual(all.counts);
  });

  it("lọc theo phòng chỉ trả dòng của phòng đó; danh sách phòng luôn đủ", async () => {
    const response = await getReceptionQueue({ roomId: "room-k2", status: ALL });
    expect(response.items).toHaveLength(4);
    expect(response.items.every((row) => row.roomId === "room-k2")).toBe(true);
    expect(response.rooms).toEqual([
      { id: "room-k1", name: "Phòng khám 1" },
      { id: "room-k2", name: "Phòng khám 2" },
    ]);
  });

  it("phòng lạ, mã phòng rỗng, trạng thái lạ hoặc danh sách trạng thái rỗng → 400 MSG-39", async () => {
    const cases = [
      { roomId: "room-k9" },
      { roomId: "  " },
      { status: ["DONE" as ReceptionQueueStatus] },
      { status: [] },
    ];
    for (const query of cases) {
      expect(await rejectionOf(getReceptionQueue(query))).toMatchObject({
        status: 400,
        code: "MSG-39",
      });
    }
  });

  it("hàng dịch vụ không hiện; hàng đọc kết quả hiện với đúng loại", async () => {
    addTestEntry({ patientName: "Chỉ dịch vụ", roomName: "Phòng khám 1", time: "09:40", queueType: "SERVICE" });
    const before = await getReceptionQueue({ status: ALL });
    expect(before.items.some((row) => row.patientName === "Chỉ dịch vụ")).toBe(false);

    await checkInPatient({
      patientId: "pat-cuong",
      appointmentId: null,
      visitId: "vis-6101",
      serviceId: null,
      priorityGroupCode: null,
      examFeePaymentId: null,
    });
    const after = await getReceptionQueue({ status: ALL });
    expect(after.items).toContainEqual(
      expect.objectContaining({
        patientName: "Lê Văn Cường",
        roomName: "Phòng khám 2",
        queueType: "RESULT_REVIEW",
        appointmentStartAt: null,
      }),
    );
  });

  it("kết quả là bản sao: sửa danh sách trả về không đổi kho", async () => {
    const first = await getReceptionQueue();
    first.items.length = 0;
    expect((await getReceptionQueue()).items).toHaveLength(6);
  });
});

describe("trạng thái lỗi và trống của danh sách (cờ test)", () => {
  it("lỗi đã hẹn chỉ áp dụng cho lần gọi kế tiếp: 403 MSG-38, 5xx MSG-04", async () => {
    forceNextReceptionQueueFailure("queue", 403);
    expect(await rejectionOf(getReceptionQueue())).toMatchObject({ status: 403, code: "MSG-38" });
    expect((await getReceptionQueue()).items).toHaveLength(6);

    forceNextReceptionQueueFailure("queue", 500);
    expect(await rejectionOf(getReceptionQueue())).toMatchObject({ status: 500, code: "MSG-04" });
  });

  it("hàng đợi trống: không có dòng, đếm bằng 0, danh sách phòng vẫn đủ", async () => {
    setReceptionQueueForcedEmpty(true);
    const response = await getReceptionQueue({ status: ALL });
    expect(response.items).toEqual([]);
    expect(response.counts).toEqual({ total: 0, waiting: 0, absent: 0 });
    expect(response.rooms).toHaveLength(2);
  });
});
