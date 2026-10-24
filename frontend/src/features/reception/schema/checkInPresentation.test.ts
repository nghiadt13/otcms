import { describe, expect, it } from "vitest";
import type { CheckInResult, IneligibleAppointment } from "../types/checkIn";
import {
  ageInYears,
  formatCountdown,
  formatPhone,
  ineligibleMessage,
  optionDetail,
  optionTitle,
  peopleAheadText,
  queueSummary,
} from "./checkInPresentation";

const TODAY = "2026-10-24";

describe("tuổi và số điện thoại", () => {
  it("tuổi tròn: sinh nhật chưa tới thì chưa tính (Nguyễn Thị Lan, sinh 14/03/1936)", () => {
    expect(ageInYears("1936-03-14", "2026-03-13")).toBe(89);
    expect(ageInYears("1936-03-14", "2026-03-14")).toBe(90);
    expect(ageInYears("1936-03-14", TODAY)).toBe(90);
  });

  it("số 10 chữ số tách nhóm 4-3-3, số khác giữ nguyên", () => {
    expect(formatPhone("0912345678")).toBe("0912 345 678");
    expect(formatPhone("12345")).toBe("12345");
  });
});

describe("dòng lựa chọn tiếp nhận", () => {
  it("lịch hẹn ghi giờ, dịch vụ, loại lượt; bác sĩ và phòng ở dòng phụ", () => {
    const option = {
      kind: "APPOINTMENT" as const,
      appointmentId: "apt-1001",
      startAt: `${TODAY}T08:30:00+07:00`,
      serviceName: "Khám chỉnh hình tổng quát",
      visitType: "FOLLOW_UP" as const,
      doctorName: "BS. Phạm Thu Hà",
      roomName: "Phòng khám 2",
      examFee: { amount: 200000, status: "PENDING_PAYMENT" as const, paidOnline: false },
    };
    expect(optionTitle(option)).toBe(
      "Lịch hẹn 08:30 · Khám chỉnh hình tổng quát · Tái khám",
    );
    expect(optionDetail(option)).toBe("BS. Phạm Thu Hà · Phòng khám 2");
  });

  it("quay lại ghi ngày x/7 và nơi vào hàng đợi; vãng lai nói rõ không theo lịch", () => {
    const revisit = {
      kind: "RETURN_VISIT" as const,
      visitId: "vis-6101",
      firstCheckInDate: "2026-10-21",
      dayOfPeriod: 4,
      nextQueue: { type: "RESULT_REVIEW" as const, roomName: "Phòng khám 2", doctorName: "BS. Phạm Thu Hà" },
      examFee: { amount: 0, status: "NOT_REQUIRED" as const, paidOnline: false },
    };
    expect(optionTitle(revisit)).toBe("Tiếp tục lượt khám ngày 21/10/2026 · Ngày 4/7");
    expect(optionDetail(revisit)).toBe("Vào hàng đợi đọc kết quả · Phòng khám 2 · BS. Phạm Thu Hà");
    expect(optionTitle({ kind: "WALK_IN", services: [] })).toBe("Khám vãng lai (không theo lịch)");
  });
});

describe("lý do lịch không tiếp nhận được", () => {
  const base = { appointmentId: "apt-x", startAt: `${TODAY}T08:30:00+07:00` };

  it("đã tiếp nhận: nêu giờ tiếp nhận, số và phòng (không hiện mã)", () => {
    const item: IneligibleAppointment = {
      ...base,
      reason: "ALREADY_CHECKED_IN",
      checkedIn: { at: `${TODAY}T08:12:00+07:00`, queueNumber: 7, roomName: "Phòng khám 2" },
    };
    expect(ineligibleMessage(item, TODAY)).toBe(
      "Lịch hẹn 08:30 hôm nay đã được tiếp nhận lúc 08:12 · Số 7 · Phòng khám 2.",
    );
  });

  it("đã hủy, không đến, và lịch không phải hôm nay (kèm ngày hôm qua hoặc ngày cụ thể)", () => {
    expect(ineligibleMessage({ ...base, reason: "CANCELLED" }, TODAY)).toBe(
      "Lịch hẹn 08:30 hôm nay đã bị hủy, không tiếp nhận được.",
    );
    expect(
      ineligibleMessage({ ...base, startAt: "2026-10-23T09:00:00+07:00", reason: "NO_SHOW" }, TODAY),
    ).toBe("Lịch hẹn 09:00 hôm qua đã quá giờ nên được ghi nhận là không đến.");
    expect(
      ineligibleMessage({ ...base, startAt: "2026-10-20T09:00:00+07:00", reason: "NOT_TODAY" }, TODAY),
    ).toBe("Lịch hẹn 09:00 ngày 20/10/2026 chỉ tiếp nhận được trong ngày hẹn.");
  });
});

describe("phiếu số thứ tự và đếm ngược", () => {
  const result: CheckInResult = {
    visitId: "vis-7001",
    queueEntryId: "q-3012",
    queueNumber: 12,
    queueType: "EXAMINATION",
    roomName: "Phòng khám 2",
    doctorName: "BS. Phạm Thu Hà",
    priorityLabel: "Người từ 75 tuổi",
    peopleAhead: 3,
  };

  it("tóm tắt hàng đợi: loại, ưu tiên, số người phía trước", () => {
    expect(queueSummary(result)).toBe(
      "Hàng đợi khám · Ưu tiên: Người từ 75 tuổi · Có 3 người phía trước",
    );
    expect(queueSummary({ ...result, priorityLabel: null, peopleAhead: 0 })).toBe(
      "Hàng đợi khám · Chưa có ai đang chờ trước",
    );
  });

  it("số người phía trước và đếm ngược mm:ss", () => {
    expect(peopleAheadText(1)).toBe("Có 1 người phía trước");
    expect(formatCountdown(872)).toBe("14:32");
    expect(formatCountdown(5)).toBe("00:05");
  });
});
