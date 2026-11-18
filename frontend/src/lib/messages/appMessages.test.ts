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

  it("MSG-77 và MSG-82 đúng nguyên văn R3", () => {
    expect(formatAppMessage("MSG-77")).toBe(
      "Mã OTP không đúng hoặc đã hết hạn. Vui lòng nhập lại hoặc yêu cầu mã mới.",
    );
    expect(formatAppMessage("MSG-82")).toBe(
      "Lượt khám ngày {date} của {patient name} đã đóng ở trạng thái chưa hoàn tất. Ảnh và kết quả xét nghiệm đã được công bố; các chỉ định chưa thực hiện đã bị hủy; khoản đã thanh toán không được hoàn.",
    );
  });

  it("MSG-83 đúng nguyên văn R3 với {patientCode} và {change}", () => {
    expect(
      formatAppMessage("MSG-83", { patientCode: "BN-001", change: "chuyển sang bác sĩ Minh" }),
    ).toBe("Lượt khám của bệnh nhân BN-001 có thay đổi: chuyển sang bác sĩ Minh. Vui lòng xem lại.");
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

  it("MSG-90 dùng tên tham số đã chọn, MSG-48 dùng {amount} như R3", () => {
    expect(
      formatAppMessage("MSG-90", {
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

  it("MSG-81 và MSG-93 đến MSG-97 theo R3", () => {
    expect(formatAppMessage("MSG-81")).toBe(
      "Chưa thể hoàn tất khám: cần có chẩn đoán chính và mọi chỉ định đã có kết quả (kể cả kết quả đọc phim) hoặc đã hủy.",
    );
    expect(formatAppMessage("MSG-93")).toBe(
      "Khoản đã thanh toán không được hoàn lại, kể cả khi hủy lịch hẹn. Vui lòng kiểm tra kỹ trước khi thanh toán hoặc hủy.",
    );
    expect(formatAppMessage("MSG-94")).toBe(
      "Bạn chưa thanh toán phí khám nên chưa thể tiếp nhận. Vui lòng thanh toán trực tuyến hoặc tại quầy lễ tân.",
    );
    expect(formatAppMessage("MSG-95")).toBe(
      "Chỉ định chưa được thanh toán nên chưa thể tiếp nhận. Vui lòng đề nghị bệnh nhân thanh toán tại quầy.",
    );
    expect(
      formatAppMessage("MSG-96", { amount: "150.000 ₫", patientName: "An" }),
    ).toBe("Đã ghi nhận hoàn tiền 150.000 ₫ cho An.");
    expect(formatAppMessage("MSG-97", { date: "17/11/2026" })).toBe(
      "Lịch hẹn ngày 17/11/2026 đã bị hủy bởi phòng khám. Khoản phí đã thanh toán sẽ được hoàn lại, phòng khám sẽ liên hệ với bạn.",
    );
  });

  it("mã không có trong bảng rơi về MSG-04", () => {
    expect(formatAppMessage("MSG-999")).toBe(APP_MESSAGES["MSG-04"]);
  });
});

describe("messageFromError", () => {
  it("uses the HTTP status when no business code is provided", () => {
    expect(messageFromError(new ApiError(404))).toBe(formatAppMessage("MSG-41"));
    expect(messageFromError(new ApiError(403))).toBe(formatAppMessage("MSG-40"));
  });
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
    "MSG-77",
    "MSG-82",
    "MSG-84",
  ])("%s có câu hiển thị không rỗng và không lộ mã", (code) => {
    const text = APP_MESSAGES[code];
    expect(text).toBeTruthy();
    expect(text).not.toMatch(/MSG-|UC\d|BR-/);
  });
});

it("formats MSG-78 with the patient name", () => {
  expect(formatAppMessage("MSG-78", { date: "17/11/2026", patientName: "An" })).toBe(
    "Kết quả khám ngày 17/11/2026 của An đã được công bố. Vui lòng đăng nhập để xem.",
  );
});
it("formats MSG-79 with the patient name", () => {
  expect(formatAppMessage("MSG-79", { date: "17/11/2026", patientName: "An" })).toBe(
    "Phiếu kết quả khám ngày 17/11/2026 của An đã có phiên bản mới. Vui lòng xem lại.",
  );
});
