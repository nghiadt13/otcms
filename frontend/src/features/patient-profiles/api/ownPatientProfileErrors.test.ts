import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import {
  isOwnProfileAccessLost,
  isOwnProfileStale,
  ownProfileDisplayError,
  ownProfileErrorMessage,
  ownProfileFieldErrors,
} from "./ownPatientProfileErrors";

describe("own patient profile errors", () => {
  it("maps backend VALIDATION_FAILED field codes to their MSG", () => {
    const error = new ApiError(400, {
      errorCode: "VALIDATION_FAILED",
      fieldErrors: [{ field: "identityDocumentType", code: "MSG-08" }],
    });
    expect(ownProfileFieldErrors(error)?.citizenId).toBe(
      "Không thể hoàn tất yêu cầu với thông tin hiện tại. Vui lòng kiểm tra và thử lại.",
    );
    expect(ownProfileDisplayError(error).code).toBe("MSG-08");
  });

  it("recognizes only HTTP 409 STALE_VERSION and uses MSG-16", () => {
    const stale = new ApiError(409, { errorCode: "STALE_VERSION" });
    expect(isOwnProfileStale(stale)).toBe(true);
    expect(ownProfileDisplayError(stale).code).toBe("MSG-16");
    expect(isOwnProfileStale(new ApiError(409, { detail: "STALE" }))).toBe(
      false,
    );
  });

  it("maps auth loss, forbidden, not found, and ordinary service failures", () => {
    expect(isOwnProfileAccessLost(new ApiError(401))).toBe(true);
    expect(
      isOwnProfileAccessLost(new Error("Bạn cần đăng nhập để tiếp tục.")),
    ).toBe(true);
    expect(ownProfileErrorMessage(new ApiError(401))).toBe(
      "Bạn không có quyền thực hiện thao tác này hoặc tài khoản không còn khả dụng.",
    );
    expect(ownProfileDisplayError(new ApiError(403)).code).toBe("MSG-19");
    expect(ownProfileDisplayError(new ApiError(404)).code).toBe("MSG-19");
    expect(ownProfileDisplayError(new Error("network down")).code).toBe(
      "MSG-04",
    );
  });
});
