import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { resetReceptionMockStore } from "./receptionMockStore";
import { rejectionOf } from "./checkInTestErrors";
import { checkInPatient, collectExamFee, getCheckInContext } from "./checkInApi";
import { listCheckInQueueEntries } from "./receptionMockStore";

beforeEach(() => resetReceptionMockStore());
afterEach(() => vi.useRealTimers());

describe("checkInPatient", () => {
  it("lịch đã thu phí: cấp số tiếp theo của phòng, vào kho hàng đợi, không ưu tiên khi lễ tân không chọn", async () => {
    const paid = await collectExamFee({ patientId: "pat-an", appointmentId: "apt-1001", serviceId: null, method: "CASH" });
    const result = await checkInPatient({
      patientId: "pat-an",
      appointmentId: "apt-1001",
      visitId: null,
      serviceId: null,
      priorityGroupCode: null,
      examFeePaymentId: paid.id,
    });
    expect(result).toMatchObject({
      queueNumber: 12,
      queueType: "EXAMINATION",
      roomName: "Phòng khám 2",
      doctorName: "BS. Phạm Thu Hà",
      priorityLabel: null,
      peopleAhead: 3,
    });

    const entry = listCheckInQueueEntries().find((item) => item.id === result.queueEntryId);
    expect(entry).toMatchObject({ appointmentId: "apt-1001", queueNumber: 12, status: "WAITING" });
    expect((await getCheckInContext({ appointmentId: "apt-1001" })).ineligible[0]).toMatchObject({
      reason: "ALREADY_CHECKED_IN",
      checkedIn: { queueNumber: 12 },
    });
  });

  it("người từ 75 tuổi (Nguyễn Thị Lan, vãng lai đã thu phí): nhóm ưu tiên đi vào hàng và lên kết quả", async () => {
    const paid = await collectExamFee({ patientId: "pat-lan", appointmentId: null, serviceId: "svc-kham-tq", method: "CASH" });
    const result = await checkInPatient({
      patientId: "pat-lan",
      appointmentId: null,
      visitId: null,
      serviceId: "svc-kham-tq",
      priorityGroupCode: "AGE_75_PLUS",
      examFeePaymentId: paid.id,
    });
    expect(result).toMatchObject({ queueType: "EXAMINATION", priorityLabel: "Người từ 75 tuổi" });

    const entry = listCheckInQueueEntries().find((item) => item.id === result.queueEntryId);
    expect(entry).toMatchObject({ priorityLabel: "Người từ 75 tuổi", status: "WAITING" });
  });

  it("chưa thu phí khám: 409 MSG-49 và không cấp số", async () => {
    const before = listCheckInQueueEntries().length;
    expect(
      await rejectionOf(
        checkInPatient({
          patientId: "pat-an",
          appointmentId: "apt-1001",
          visitId: null,
          serviceId: null,
          priorityGroupCode: null,
          examFeePaymentId: null,
        }),
      ),
    ).toMatchObject({ status: 409, code: "MSG-49" });
    expect(listCheckInQueueEntries()).toHaveLength(before);
  });

  it("lịch đã bị lễ tân khác tiếp nhận (apt-1009): 409 MSG-37, rồi lần tải sau thấy đã tiếp nhận", async () => {
    expect(
      await rejectionOf(
        checkInPatient({
          patientId: "pat-p09",
          appointmentId: "apt-1009",
          visitId: null,
          serviceId: null,
          priorityGroupCode: null,
          examFeePaymentId: null,
        }),
      ),
    ).toMatchObject({ status: 409, code: "MSG-37" });
    const context = await getCheckInContext({ appointmentId: "apt-1009" });
    expect(context.ineligible[0]).toMatchObject({ reason: "ALREADY_CHECKED_IN" });
  });

  it("vãng lai: vào phòng ít người chờ nhất (Phòng khám 1, số 9) với bác sĩ của phòng", async () => {
    const paid = await collectExamFee({ patientId: "pat-moi", appointmentId: null, serviceId: "svc-kham-tq", method: "CASH" });
    const result = await checkInPatient({
      patientId: "pat-moi",
      appointmentId: null,
      visitId: null,
      serviceId: "svc-kham-tq",
      priorityGroupCode: null,
      examFeePaymentId: paid.id,
    });
    expect(result).toMatchObject({ roomName: "Phòng khám 1", doctorName: "BS. Lê Quang Minh", queueNumber: 9 });
  });

  it("vãng lai chưa có khoản thu cho dịch vụ đó: 409 MSG-49", async () => {
    expect(
      await rejectionOf(
        checkInPatient({
          patientId: "pat-moi",
          appointmentId: null,
          visitId: null,
          serviceId: "svc-kham-tq",
          priorityGroupCode: null,
          examFeePaymentId: null,
        }),
      ),
    ).toMatchObject({ status: 409, code: "MSG-49" });
  });

  it("quay lại trong 7 ngày: vào hàng đợi đọc kết quả, không cần thu phí", async () => {
    const result = await checkInPatient({
      patientId: "pat-cuong",
      appointmentId: null,
      visitId: "vis-6101",
      serviceId: null,
      priorityGroupCode: null,
      examFeePaymentId: null,
    });
    expect(result).toMatchObject({ visitId: "vis-6101", queueType: "RESULT_REVIEW", roomName: "Phòng khám 2" });
  });

  it("đầu vào không hợp lệ: hai lựa chọn cùng lúc hoặc nhóm ưu tiên lạ thì 400 MSG-39", async () => {
    expect(
      await rejectionOf(
        checkInPatient({
          patientId: "pat-an",
          appointmentId: "apt-1001",
          visitId: null,
          serviceId: "svc-kham-tq",
          priorityGroupCode: null,
          examFeePaymentId: null,
        }),
      ),
    ).toMatchObject({ status: 400, code: "MSG-39" });
    expect(
      await rejectionOf(
        checkInPatient({
          patientId: "pat-an",
          appointmentId: "apt-1001",
          visitId: null,
          serviceId: null,
          priorityGroupCode: "KHONG_CO",
          examFeePaymentId: null,
        }),
      ),
    ).toMatchObject({ status: 400, code: "MSG-39" });
  });
});

