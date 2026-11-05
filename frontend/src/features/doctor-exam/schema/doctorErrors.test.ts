import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import { doctorErrorMessage, normalizeDoctorError } from "./doctorErrors";

describe("lỗi khu bác sĩ theo mã MSG của R3", () => {
  it("controller clinical trả MSG trong error.code thì dùng đúng câu đó", () => {
    expect(doctorErrorMessage(new ApiError(403, { errorCode: "MSG-40" }))).toBe(
      "Bạn không có quyền truy cập hoặc thực hiện thao tác này.",
    );
  });

  it("mã kỹ thuật của diagnostic đổi sang MSG, không rơi vào câu chung MSG-04", () => {
    expect(
      doctorErrorMessage(new ApiError(409, { errorCode: "STATE_CONFLICT" })),
    ).toContain("Không thể thực hiện thao tác này");
    expect(
      doctorErrorMessage(new ApiError(400, { errorCode: "EVIDENCE_MISMATCH" })),
    ).toContain("Tệp không hợp lệ");
  });

  it("không có mã thì suy từ trạng thái HTTP; 500 giữ MSG-04", () => {
    expect(doctorErrorMessage(new ApiError(404))).toContain("không");
    expect((normalizeDoctorError(new ApiError(404)) as ApiError).code).toBe(
      "MSG-41",
    );
    expect(doctorErrorMessage(new ApiError(500))).toContain(
      "Không thể hoàn tất yêu cầu",
    );
  });

  it("câu cần đăng nhập giữ nguyên, lỗi đã có mã không bị đổi", () => {
    expect(
      doctorErrorMessage(new Error("Bạn cần đăng nhập để tiếp tục.")),
    ).toBe("Bạn cần đăng nhập để tiếp tục.");
    const coded = new ApiError(409, { code: "MSG-36" });
    expect(normalizeDoctorError(coded)).toBe(coded);
  });
});
