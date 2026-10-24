import { beforeEach, describe, expect, it } from "vitest";
import { checkInPatient } from "./checkInApi";
import { at, MOCK_TODAY } from "./checkInMockData";
import { resetReceptionMockStore } from "./receptionMockStore";
import { rejectionOf } from "./checkInTestErrors";
import { getReceptionVisitSummary } from "./receptionQueueApi";
import {
  forceNextReceptionQueueFailure,
  resetReceptionQueueMockControls,
} from "./receptionQueueMockControls";
import { addTestEntry, queueIdOf } from "./receptionQueueTestEntries";

// Tóm tắt lượt khám của lớp API giả: vị trí và người phía trước, trạng thái lượt, và các mã lỗi
// 404/410 (đã rời hàng). Kho giả mặc định như trong receptionQueueApi.test.ts.

beforeEach(() => {
  resetReceptionMockStore();
  resetReceptionQueueMockControls();
});

describe("getReceptionVisitSummary", () => {
  it("đang chờ: người phía trước = vị trí - 1; chỉ hành chính và hàng đợi", async () => {
    const summary = await getReceptionVisitSummary(queueIdOf("apt-1020"));
    expect(summary).toEqual({
      queueEntryId: queueIdOf("apt-1020"),
      queueNumber: 7,
      roomName: "Phòng khám 2",
      patient: { fullName: "Đặng Thị Xuân", patientCode: "BN-000313", sex: "FEMALE", age: expect.any(Number) },
      checkedInAt: at(MOCK_TODAY, "08:12"),
      appointmentStartAt: at(MOCK_TODAY, "08:30"),
      visitType: "FIRST_VISIT",
      responsibleDoctorName: "BS. Phạm Thu Hà",
      priorityLabel: null,
      queueType: "EXAMINATION",
      queueStatus: "WAITING",
      peopleAhead: 1,
      visitStatus: "WAITING_FOR_EXAMINATION",
    });
  });

  it("vắng mặt không có người phía trước; đang phục vụ là đang khám", async () => {
    const absent = await getReceptionVisitSummary(queueIdOf("apt-1019"));
    expect(absent).toMatchObject({
      queueStatus: "ABSENT",
      peopleAhead: null,
      visitStatus: "WAITING_FOR_EXAMINATION",
    });
    const serving = await getReceptionVisitSummary(queueIdOf("apt-1016"));
    expect(serving).toMatchObject({
      queueStatus: "BEING_SERVED",
      peopleAhead: null,
      visitStatus: "IN_EXAMINATION",
    });
  });

  it("bản ghi vãng lai mới: không có lịch, người phía trước tính theo hàng mới", async () => {
    const entry = addTestEntry({ patientName: "Vãng lai mới", roomName: "Phòng khám 2", time: "09:20" });
    const summary = await getReceptionVisitSummary(entry.id);
    expect(summary).toMatchObject({ appointmentStartAt: null, visitType: "FIRST_VISIT", peopleAhead: 3 });
  });

  it("quay lại đọc kết quả: loại Đọc kết quả, tái khám, trạng thái chờ kết luận", async () => {
    const result = await checkInPatient({
      patientId: "pat-cuong",
      appointmentId: null,
      visitId: "vis-6101",
      serviceId: null,
      priorityGroupCode: null,
      examFeePaymentId: null,
    });
    const summary = await getReceptionVisitSummary(result.queueEntryId);
    expect(summary).toMatchObject({
      queueType: "RESULT_REVIEW",
      visitType: "FOLLOW_UP",
      visitStatus: "AWAITING_CONCLUSION",
      appointmentStartAt: null,
    });
  });

  it("bản ghi đã xong hoặc hàng dịch vụ → 410 MSG-38; id không có → 404 MSG-38; id trống → 400 MSG-39", async () => {
    expect(await rejectionOf(getReceptionVisitSummary(queueIdOf("apt-1015")))).toMatchObject({
      status: 410,
      code: "MSG-38",
    });
    const service = addTestEntry({ patientName: "Dịch vụ", roomName: "Phòng khám 1", time: "09:40", queueType: "SERVICE" });
    expect(await rejectionOf(getReceptionVisitSummary(service.id))).toMatchObject({ status: 410, code: "MSG-38" });
    expect(await rejectionOf(getReceptionVisitSummary("q-gone"))).toMatchObject({ status: 404, code: "MSG-38" });
    expect(await rejectionOf(getReceptionVisitSummary("  "))).toMatchObject({ status: 400, code: "MSG-39" });
  });

  it("lỗi đã hẹn cho tóm tắt: 404 MSG-38 đúng một lần rồi trả bình thường", async () => {
    forceNextReceptionQueueFailure("summary", 404);
    const id = queueIdOf("apt-1020");
    expect(await rejectionOf(getReceptionVisitSummary(id))).toMatchObject({ status: 404, code: "MSG-38" });
    expect((await getReceptionVisitSummary(id)).queueNumber).toBe(7);
  });
});
