import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "@/lib/api/client";
import { ApiError } from "@/lib/api/apiError";
import {
  requestMyEmailChange,
  updateMyDisplayName,
  verifyMyEmailChange,
} from "./myAccountProfileApi";
import { accountResponse, patientResponse } from "./myAccountTestFixtures";
import { MY_ACCOUNT_CHANGED, MY_ACCOUNT_ACCESS_LOST } from "./myAccountEvents";

vi.mock("@/lib/api/client", () => ({ apiRequest: vi.fn() }));
beforeEach(() => {
  vi.resetAllMocks();
});

describe("PATCH /me — KAN-29 thật", () => {
  it("gửi {displayName} sau trim, dùng response chuẩn hóa và phát event đồng bộ", async () => {
    const response = patientResponse({
      displayName: "Tên chuẩn từ server",
      allowedActions: [],
    });
    vi.mocked(apiRequest).mockResolvedValue(response);
    const changed = vi.fn();
    window.addEventListener(MY_ACCOUNT_CHANGED, changed);
    try {
      const result = await updateMyDisplayName(" \tTên đã nhập\n ");
      expect(apiRequest).toHaveBeenCalledExactlyOnceWith("/api/v1/me", {
        method: "PATCH",
        body: JSON.stringify({ displayName: "Tên đã nhập" }),
      });
      expect(result).toMatchObject({
        displayName: "Tên chuẩn từ server",
        allowedActions: [],
      });
      expect(changed).toHaveBeenCalledTimes(1);
      expect((changed.mock.calls[0][0] as CustomEvent).detail).toEqual(result);
    } finally {
      window.removeEventListener(MY_ACCOUNT_CHANGED, changed);
    }
  });

  it.each(["AB", "😀😀", "😀".repeat(100), "a".repeat(95) + "😀".repeat(5)])(
    "chấp nhận biên Unicode code points hợp lệ",
    async (name) => {
      vi.mocked(apiRequest).mockResolvedValue(
        patientResponse({ displayName: name }),
      );
      await expect(
        updateMyDisplayName(" " + name + " "),
      ).resolves.toMatchObject({ displayName: name });
      expect(
        JSON.parse(String(vi.mocked(apiRequest).mock.calls[0][1]?.body)),
      ).toEqual({ displayName: name });
    },
  );

  it.each(["", " \t\n ", "A", "😀", "a".repeat(101), "😀".repeat(101)])(
    "tên không hợp lệ không gọi HTTP",
    async (name) => {
      await expect(updateMyDisplayName(name)).rejects.toMatchObject({
        status: 400,
        code: "MSG-08",
        fieldErrors: [{ field: "displayName", code: "MSG-08" }],
      });
      expect(apiRequest).not.toHaveBeenCalled();
    },
  );

  it("không áp luật mock cấm staff PATCH tên: backend quyết định quyền", async () => {
    vi.mocked(apiRequest).mockResolvedValue(
      accountResponse({ displayName: "Tên staff từ server" }),
    );
    await expect(updateMyDisplayName("Tên staff")).resolves.toMatchObject({
      kind: "STAFF",
      displayName: "Tên staff từ server",
    });
  });

  it.each([400, 401, 403, 404, 500, 503])(
    "PATCH HTTP %s không phát event thành công",
    async (status) => {
      const error = new ApiError(status, {
        errorCode: status === 400 ? "MSG-08" : "MSG-14",
      });
      vi.mocked(apiRequest).mockRejectedValue(error);
      const changed = vi.fn(),
        lost = vi.fn();
      window.addEventListener(MY_ACCOUNT_CHANGED, changed);
      window.addEventListener(MY_ACCOUNT_ACCESS_LOST, lost);
      try {
        await expect(updateMyDisplayName("Tên hợp lệ")).rejects.toBe(error);
        expect(changed).not.toHaveBeenCalled();
        expect(lost).toHaveBeenCalledTimes(
          [401, 403, 404].includes(status) ? 1 : 0,
        );
      } finally {
        window.removeEventListener(MY_ACCOUNT_CHANGED, changed);
        window.removeEventListener(MY_ACCOUNT_ACCESS_LOST, lost);
      }
    },
  );

  it("forced-password được server từ chối mutation", async () => {
    vi.mocked(apiRequest).mockRejectedValue(
      new ApiError(403, { errorCode: "MSG-14" }),
    );
    await expect(updateMyDisplayName("Tên mới")).rejects.toMatchObject({
      status: 403,
      errorCode: "MSG-14",
    });
  });

  it("response PATCH sai contract không được publish", async () => {
    vi.mocked(apiRequest).mockResolvedValue({
      displayName: "Không đủ contract",
    });
    await expect(updateMyDisplayName("Tên mới")).rejects.toMatchObject({
      errorCode: "INVALID_ACCOUNT_RESPONSE",
    });
  });
});

describe("email-change/verify — HTTP KAN-29", () => {
  it.each(["moi@example.com", "taken@example.com", "an@example.com"])(
    "202 kể cả request bị suppress: không thay email hoặc publish account (%s)",
    async (newEmail) => {
      vi.mocked(apiRequest).mockResolvedValue({ message: "MSG-06" });
      const changed = vi.fn();
      window.addEventListener(MY_ACCOUNT_CHANGED, changed);
      try {
        await expect(requestMyEmailChange(newEmail)).resolves.toBeUndefined();
        expect(apiRequest).toHaveBeenCalledWith("/api/v1/me/email-change", {
          method: "POST",
          body: JSON.stringify({ newEmail }),
        });
        expect(changed).not.toHaveBeenCalled();
      } finally {
        window.removeEventListener(MY_ACCOUNT_CHANGED, changed);
      }
    },
  );
  it("verify gửi code, dùng account thật và phát event sau thành công", async () => {
    vi.mocked(apiRequest).mockResolvedValue(
      patientResponse({ email: "moi@example.com" }),
    );
    const changed = vi.fn();
    window.addEventListener(MY_ACCOUNT_CHANGED, changed);
    try {
      const result = await verifyMyEmailChange("123456");
      expect(apiRequest).toHaveBeenCalledWith(
        "/api/v1/me/email-change/verify",
        {
          method: "POST",
          body: JSON.stringify({ code: "123456" }),
        },
      );
      expect(result).toMatchObject({ email: "moi@example.com" });
      expect(changed).toHaveBeenCalledExactlyOnceWith(
        expect.objectContaining({ detail: result }),
      );
    } finally {
      window.removeEventListener(MY_ACCOUNT_CHANGED, changed);
    }
  });
  it.each(["sai", "hết hạn", "không có challenge", "challenge bị supersede"])(
    "verify %s: giữ lỗi MSG-82, không publish",
    async () => {
      const error = new ApiError(400, { errorCode: "MSG-82" });
      vi.mocked(apiRequest).mockRejectedValue(error);
      const changed = vi.fn();
      window.addEventListener(MY_ACCOUNT_CHANGED, changed);
      try {
        await expect(verifyMyEmailChange("000000")).rejects.toBe(error);
        expect(changed).not.toHaveBeenCalled();
      } finally {
        window.removeEventListener(MY_ACCOUNT_CHANGED, changed);
      }
    },
  );
  it.each([400, 401, 403, 404, 500, 503])(
    "request/verify lỗi %s không tạo dữ liệu giả",
    async (status) => {
      const error = new ApiError(status, {
        errorCode: status === 403 ? "MSG-14" : "MSG-08",
      });
      vi.mocked(apiRequest).mockRejectedValue(error);
      await expect(requestMyEmailChange("moi@example.com")).rejects.toBe(error);
      await expect(verifyMyEmailChange("123456")).rejects.toBe(error);
    },
  );
  it("lỗi mạng không bị thay bằng response mock", async () => {
    const error = new TypeError("Failed to fetch");
    vi.mocked(apiRequest).mockRejectedValue(error);
    await expect(requestMyEmailChange("moi@example.com")).rejects.toBe(error);
    await expect(verifyMyEmailChange("123456")).rejects.toBe(error);
  });
});
