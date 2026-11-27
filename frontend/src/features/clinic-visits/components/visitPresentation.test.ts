import { describe, expect, it } from "vitest";
import type { VisitProgress } from "../types/visitProgress";
import {
  queueAheadText,
  visitDateLabel,
  visitPatientLabel,
} from "./visitPresentation";

describe("visitDateLabel", () => {
  it("ghi thứ theo ngày, không lệch theo múi giờ máy", () => {
    expect(visitDateLabel("2026-10-23")).toBe("Thứ sáu 23/10/2026");
    expect(visitDateLabel("2026-10-26")).toBe("Thứ hai 26/10/2026");
  });
});

describe("visitPatientLabel", () => {
  it("luôn ghi người khám và quan hệ", () => {
    expect(
      visitPatientLabel({
        id: "p1",
        fullName: "Nguyễn Thị Lan",
        relationship: "PARENT",
      }),
    ).toBe("Nguyễn Thị Lan · Cha/Mẹ");
  });

  it("liên kết cũ chưa có quan hệ thì chỉ ghi tên", () => {
    expect(
      visitPatientLabel({
        id: "p2",
        fullName: "Trần Văn Bình",
        relationship: null,
      }),
    ).toBe("Trần Văn Bình");
  });
});

const position = (
  patch: Partial<Pick<VisitProgress, "beingCalled" | "peopleAhead">>,
) => ({ beingCalled: false, peopleAhead: 3, ...patch });

describe("queueAheadText", () => {
  it("đang chờ: ghi số người phía trước bằng chữ", () => {
    expect(queueAheadText(position({ peopleAhead: 3 }))).toBe(
      "Còn 3 người trước bạn.",
    );
  });

  it("không còn ai trước: báo đến lượt kế tiếp", () => {
    expect(queueAheadText(position({ peopleAhead: 0 }))).toBe(
      "Bạn là người tiếp theo.",
    );
  });

  it("đang được gọi: ghi 'Đang gọi bạn' dù còn số người trước", () => {
    expect(
      queueAheadText(position({ beingCalled: true, peopleAhead: 0 })),
    ).toBe("Đang gọi bạn");
  });

  it("chưa vào hàng (không có số người trước): trả chuỗi rỗng", () => {
    expect(queueAheadText(position({ peopleAhead: null }))).toBe("");
  });
});
