import { describe, expect, it } from "vitest";
import {
  appointmentCell,
  peopleAheadPhrase,
  refreshedAtText,
  summaryCheckInLine,
  summaryQueueLine,
} from "./receptionQueuePresentation";

// Chữ của màn Hàng đợi tiếp nhận: giờ theo múi Việt Nam, không có mã UC/BR/MSG.

describe("appointmentCell", () => {
  it("có lịch thì ghi giờ hẹn; vãng lai hoặc quay lại thì ghi Vãng lai", () => {
    expect(appointmentCell("2026-10-24T08:30:00+07:00")).toBe("08:30");
    expect(appointmentCell(null)).toBe("Vãng lai");
  });
});

describe("peopleAheadPhrase", () => {
  it("0 người, 1 người và nhiều người; null khi không đang chờ", () => {
    expect(peopleAheadPhrase(0)).toBe("Không có ai phía trước");
    expect(peopleAheadPhrase(1)).toBe("1 người phía trước");
    expect(peopleAheadPhrase(3)).toBe("3 người phía trước");
    expect(peopleAheadPhrase(null)).toBeNull();
  });
});

describe("summaryQueueLine", () => {
  it("đang chờ ghi đủ loại, trạng thái và số người phía trước", () => {
    expect(
      summaryQueueLine({ queueType: "EXAMINATION", queueStatus: "WAITING", peopleAhead: 1 }),
    ).toBe("Khám · Đang chờ · 1 người phía trước");
  });

  it("vắng mặt không ghi số người phía trước", () => {
    expect(
      summaryQueueLine({ queueType: "RESULT_REVIEW", queueStatus: "ABSENT", peopleAhead: null }),
    ).toBe("Đọc kết quả · Vắng mặt");
  });
});

describe("summaryCheckInLine", () => {
  it("có lịch hẹn và tái khám", () => {
    expect(
      summaryCheckInLine({
        checkedInAt: "2026-10-24T08:12:00+07:00",
        appointmentStartAt: "2026-10-24T08:30:00+07:00",
        visitType: "FOLLOW_UP",
      }),
    ).toBe("08:12 · Lịch hẹn 08:30 · Tái khám");
  });

  it("vãng lai khám lần đầu", () => {
    expect(
      summaryCheckInLine({
        checkedInAt: "2026-10-24T08:12:00+07:00",
        appointmentStartAt: null,
        visitType: "FIRST_VISIT",
      }),
    ).toBe("08:12 · Vãng lai · Khám lần đầu");
  });
});

describe("refreshedAtText", () => {
  it("ghi giờ BE trả, theo múi Việt Nam", () => {
    expect(refreshedAtText("2026-10-24T08:41:00+07:00")).toBe("Cập nhật 08:41");
  });
});
