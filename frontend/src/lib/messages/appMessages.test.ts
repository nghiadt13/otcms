import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import {
  APP_MESSAGES,
  formatAppMessage,
  messageFromError,
} from "./appMessages";

describe("formatAppMessage", () => {
  it("thay {doctor} bằng tham số của MSG-27", () => {
    expect(formatAppMessage("MSG-27", { doctor: "BS. Minh" })).toBe(
      "Đã chuyển lịch hẹn sang bác sĩ BS. Minh. Bệnh nhân sẽ nhận được thông báo.",
    );
  });

  it("thiếu tham số thì giữ nguyên {tham số} để dễ thấy", () => {
    expect(formatAppMessage("MSG-27")).toBe(
      "Đã chuyển lịch hẹn sang bác sĩ {doctor}. Bệnh nhân sẽ nhận được thông báo.",
    );
  });

  it("MSG-51 theo R3 không có tham số: câu nguyên văn", () => {
    expect(formatAppMessage("MSG-51")).toBe(
      "Thanh toán đã được xác nhận. Phiếu thu đã được tạo.",
    );
  });

  it("MSG-11 thay {accountRole} và {username} theo R3", () => {
    expect(
      formatAppMessage("MSG-11", {
        accountRole: "Bác sĩ chỉnh hình",
        username: "bacsi.moi",
      }),
    ).toBe(
      "Đã tạo tài khoản Bác sĩ chỉnh hình với tên đăng nhập bacsi.moi. Mật khẩu khởi tạo chỉ hiển thị một lần.",
    );
  });

  it("MSG-96 dùng tên tham số đã chọn, MSG-48 dùng {amount} như R3", () => {
    expect(
      formatAppMessage("MSG-96", {
        date: "25/10/2026",
        time: "09:00",
        patientName: "Nguyễn Văn A",
        doctor: "BS. Minh",
      }),
    ).toBe(
      "Lịch hẹn ngày 25/10/2026 lúc 09:00 của Nguyễn Văn A được chuyển sang bác sĩ BS. Minh.",
    );
    expect(formatAppMessage("MSG-48", { amount: "150.000 ₫" })).toBe(
      "Đã ghi nhận thanh toán 150.000 ₫. Phiếu thu đã được tạo.",
    );
  });

  it("mã không có trong bảng rơi về MSG-04", () => {
    expect(formatAppMessage("MSG-999")).toBe(APP_MESSAGES["MSG-04"]);
  });
});

describe("messageFromError", () => {
  it("ApiError có mã MSG trong bảng dùng câu của bảng", () => {
    const error = new ApiError(422, { code: "MSG-17" });
    expect(messageFromError(error)).toBe(
      "Thông tin hồ sơ chưa hợp lệ. Vui lòng kiểm tra và nhập lại.",
    );
  });

  it("mã của khu tài khoản (MSG-13) dùng chung bảng, không rơi về MSG-04", () => {
    const error = new ApiError(400, { code: "MSG-13" });
    expect(messageFromError(error)).toBe(formatAppMessage("MSG-13"));
    expect(messageFromError(error)).not.toBe(APP_MESSAGES["MSG-04"]);
  });

  it("ApiError có params thì thay tham số vào câu của bảng", () => {
    const error = new ApiError(409, {
      code: "MSG-27",
      params: { doctor: "BS. Minh" },
    });
    expect(messageFromError(error)).toBe(
      "Đã chuyển lịch hẹn sang bác sĩ BS. Minh. Bệnh nhân sẽ nhận được thông báo.",
    );
  });

  it("lỗi không phải ApiError hoặc không có mã dùng MSG-04", () => {
    expect(messageFromError(new Error("network"))).toBe(APP_MESSAGES["MSG-04"]);
  });
});

describe("mã MSG khu bệnh nhân", () => {
  it.each([
    "MSG-17",
    "MSG-21",
    "MSG-26",
    "MSG-28",
    "MSG-30",
    "MSG-31",
    "MSG-40",
    "MSG-41",
    "MSG-49",
    "MSG-51",
    "MSG-52",
    "MSG-53",
    "MSG-55",
    "MSG-82",
    "MSG-90",
  ])("%s có câu hiển thị không rỗng và không lộ mã", (code) => {
    const text = APP_MESSAGES[code];
    expect(text).toBeTruthy();
    expect(text).not.toMatch(/MSG-|UC\d|BR-/);
  });
});
