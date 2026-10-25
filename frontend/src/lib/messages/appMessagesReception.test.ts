import { describe, expect, it } from "vitest";
import { APP_MESSAGES, formatAppMessage } from "./appMessages";

// Mã MSG của khu Lễ tân (tiếp nhận, hàng đợi, lịch hẹn). Tách file riêng để không lẫn với
// test của khu bệnh nhân.
describe("mã MSG khu Lễ tân", () => {
  it.each(["MSG-18", "MSG-37", "MSG-38", "MSG-39", "MSG-48", "MSG-94"])(
    "%s có câu chữ trong bảng",
    (code) => {
      expect(APP_MESSAGES[code]).toBeTruthy();
    },
  );

  it("MSG-94 thay {number} và {room} khi tiếp nhận xong", () => {
    expect(
      formatAppMessage("MSG-94", { number: 12, room: "Phòng khám 2" }),
    ).toBe("Đã tiếp nhận bệnh nhân. Số thứ tự 12, phòng Phòng khám 2.");
  });

  it("MSG-48 thay {amount} bằng số tiền vừa ghi nhận", () => {
    expect(formatAppMessage("MSG-48", { amount: "150.000 ₫" })).toBe(
      "Đã ghi nhận thanh toán 150.000 ₫. Phiếu thu đã được tạo.",
    );
  });

  it("MSG-94 thiếu tham số thì giữ nguyên {number} để dễ thấy", () => {
    expect(formatAppMessage("MSG-94", { room: "Phòng 1" })).toBe(
      "Đã tiếp nhận bệnh nhân. Số thứ tự {number}, phòng Phòng 1.",
    );
  });

  it("MSG-18, 39 không có tham số và giữ nguyên câu chữ R3 Drive", () => {
    expect(formatAppMessage("MSG-39")).toBe(
      "Thông tin chưa hợp lệ hoặc chưa đầy đủ. Vui lòng kiểm tra và thử lại.",
    );
    expect(formatAppMessage("MSG-18")).toBe(
      "Chưa xác định được đúng hồ sơ bệnh nhân. Vui lòng hỏi thêm họ tên hoặc số CCCD/định danh.",
    );
  });
});
