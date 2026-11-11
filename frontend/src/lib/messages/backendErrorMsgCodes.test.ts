import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import {
  APP_MESSAGES,
  formatAppMessage,
  messageFromError,
} from "./appMessages";
import {
  BACKEND_ERROR_MSG_CODES,
  msgCodeFromBackendError,
} from "./backendErrorMsgCodes";

describe("msgCodeFromBackendError", () => {
  it("mã nghiệp vụ của BE tra theo bảng", () => {
    expect(msgCodeFromBackendError("EXAM_FEE_UNPAID")).toBe("MSG-37");
    expect(msgCodeFromBackendError("APPOINTMENT_CHANGE_TOO_LATE")).toBe(
      "MSG-32",
    );
  });

  it("mã dạng MSG-n giữ nguyên", () => {
    expect(msgCodeFromBackendError("MSG-36")).toBe("MSG-36");
  });

  it.each([undefined, "", "SOME_UNKNOWN_CODE", "constructor", "__proto__"])(
    "mã %j không có trong bảng thì không có mã MSG",
    (code) => {
      expect(msgCodeFromBackendError(code)).toBeUndefined();
    },
  );

  it("mọi mã đích trong bảng đều có câu hiển thị trong APP_MESSAGES", () => {
    for (const code of Object.values(BACKEND_ERROR_MSG_CODES)) {
      expect(APP_MESSAGES[code]).toBeTruthy();
    }
  });
});

describe("ApiError dựng từ errorCode", () => {
  it("code được suy từ errorCode khi không có code tường minh", () => {
    const error = new ApiError(409, { errorCode: "EXAM_FEE_UNPAID" });
    expect(error.code).toBe("MSG-37");
    expect(error.errorCode).toBe("EXAM_FEE_UNPAID");
    expect(messageFromError(error)).toBe(formatAppMessage("MSG-37"));
  });

  it("code tường minh thắng errorCode", () => {
    const error = new ApiError(409, {
      code: "MSG-30",
      errorCode: "EXAM_FEE_UNPAID",
    });
    expect(error.code).toBe("MSG-30");
  });

  it("message của Error giữ nguyên, không lộ mã MSG thô ra màn hình", () => {
    const error = new ApiError(409, { errorCode: "EXAM_FEE_UNPAID" });
    expect(error.message).toBe("API trả về mã 409.");
  });

  it("errorCode không có trong bảng thì code vẫn là undefined", () => {
    expect(
      new ApiError(409, { errorCode: "SOME_UNKNOWN_BACKEND_CODE" }).code,
    ).toBeUndefined();
  });
});
