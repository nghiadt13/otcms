import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "@/lib/api/client";
import { ApiError, isApiError, toFieldErrorMap } from "@/lib/api/apiError";
import {
  changeMyPassword,
  getMyAccount,
  removeMyAvatar,
  resetMyAccountMockStore,
  updateMyAvatar,
} from "./myAccountApi";
import { MOCK_MY_ACCOUNT_FIRST_LOGIN } from "./myAccountMockData";
import { currentMockAccountState } from "./myAccountMockStore";
import { accountResponse, patientResponse } from "./myAccountTestFixtures";

vi.mock("@/lib/api/client", () => ({ apiRequest: vi.fn() }));

beforeEach(() => {
  vi.resetAllMocks();
  resetMyAccountMockStore();
});

describe("GET /me — KAN-29 thật", () => {
  it.each([
    ["PATIENT", "PATIENT", "PATIENT"],
    ["ADMIN", "ADMIN", "STAFF"],
    ["OWNER", "CLINIC_OWNER", "STAFF"],
    ["DOCTOR", "ORTHOPEDIC_DOCTOR", "STAFF"],
    ["RECEPTIONIST", "RECEPTIONIST", "STAFF"],
    ["LAB_TECHNICIAN", "LAB_TECHNICIAN", "STAFF"],
    ["IMAGING_TECHNICIAN", "IMAGING_TECHNICIAN", "STAFF"],
  ] as const)(
    "đọc %s, giữ kind/role server và quyền được cấp",
    async (accountType, role, kind) => {
      const response = accountResponse({
        accountType,
        role,
        kind,
        allowedActions: [],
      });
      vi.mocked(apiRequest).mockResolvedValue(response);
      const controller = new AbortController();
      const result = await getMyAccount(controller.signal);
      expect(apiRequest).toHaveBeenCalledWith("/api/v1/me", {
        signal: controller.signal,
      });
      expect(result).toMatchObject({
        kind,
        id: response.id,
        displayName: response.displayName,
        allowedActions: [],
      });
      if (result.kind === "STAFF") {
        expect(result.role).toBe(role);
        expect(result.username).toBe(response.username);
        expect(result).not.toHaveProperty("email");
      } else expect(result.email).toBe(response.email);
    },
  );

  it("projection tường minh, bỏ trường lạ và quyền chưa biết; không sửa mảng của response", async () => {
    const response = {
      ...patientResponse({
        allowedActions: ["changePassword", "futureAction"],
      }),
      passwordHash: "not-for-ui",
    };
    vi.mocked(apiRequest).mockResolvedValue(response);
    const result = await getMyAccount();
    expect(result.allowedActions).toEqual(["changePassword"]);
    expect(result).not.toHaveProperty("passwordHash");
    expect(result).not.toHaveProperty("username");
    expect(response.allowedActions).toEqual(["changePassword", "futureAction"]);
  });

  it("giữ nguyên giá trị rỗng và avatar URL server, không tự dựng tên/ảnh/quyền", async () => {
    vi.mocked(apiRequest).mockResolvedValue(
      accountResponse({
        displayName: "",
        username: "",
        avatarUrl: "/api/v1/accounts/id/avatar",
        allowedActions: ["removeAvatar"],
      }),
    );
    expect(await getMyAccount()).toMatchObject({
      displayName: "",
      username: "",
      avatarUrl: "/api/v1/accounts/id/avatar",
      allowedActions: ["removeAvatar"],
    });
  });

  it("forced-password vẫn đọc được, chỉ dùng quyền server", async () => {
    vi.mocked(apiRequest).mockResolvedValue(
      accountResponse({
        mustChangePassword: true,
        allowedActions: ["changePassword"],
      }),
    );
    expect(await getMyAccount()).toMatchObject({
      mustChangePassword: true,
      allowedActions: ["changePassword"],
    });
  });

  it.each([
    null,
    {},
    [],
    { ...accountResponse(), id: null },
    { ...accountResponse(), allowedActions: undefined },
    { ...accountResponse(), status: "UNKNOWN" },
    { ...accountResponse(), avatarUrl: undefined },
    { ...accountResponse(), accountType: "UNKNOWN" },
    { ...accountResponse(), role: "PATIENT" },
    { ...accountResponse(), kind: "PATIENT" },
    { ...accountResponse(), mustChangePassword: undefined },
  ])(
    "response sai contract bị từ chối, không fallback mock (%j)",
    async (response) => {
      vi.mocked(apiRequest).mockResolvedValue(response);
      await expect(getMyAccount()).rejects.toMatchObject({
        status: 502,
        errorCode: "INVALID_ACCOUNT_RESPONSE",
      });
    },
  );

  it.each([401, 403, 404, 500, 503])(
    "giữ lỗi HTTP %s để caller xử lý",
    async (status) => {
      const error = new ApiError(status);
      vi.mocked(apiRequest).mockRejectedValue(error);
      await expect(getMyAccount()).rejects.toBe(error);
    },
  );

  it("giữ lỗi mạng và thiếu token trước request, không đọc mock store", async () => {
    for (const error of [
      new TypeError("Failed to fetch"),
      new Error("Bạn cần đăng nhập để tiếp tục."),
    ]) {
      vi.mocked(apiRequest).mockRejectedValue(error);
      await expect(getMyAccount()).rejects.toBe(error);
    }
  });
});

async function failure(promise: Promise<unknown>) {
  try {
    await promise;
  } catch (error) {
    if (isApiError(error)) return error;
  }
  throw new Error("Mong đợi ApiError");
}

describe("avatar — HTTP KAN-29", () => {
  it("multipart photo, không tự đặt Content-Type; trả projection server", async () => {
    const response = accountResponse({
      avatarUrl: "/api/v1/accounts/id/avatar",
      allowedActions: ["removeAvatar"],
    });
    vi.mocked(apiRequest).mockResolvedValue(response);
    const photo = new File(["png-data"], "photo.png", { type: "image/png" });
    const updated = await updateMyAvatar(photo);
    expect(updated.avatarUrl).toBe(response.avatarUrl);
    expect(updated.allowedActions).toContain("removeAvatar");
    const [path, init] = vi.mocked(apiRequest).mock.calls[0];
    expect(path).toBe("/api/v1/me/avatar");
    expect(init?.method).toBe("PUT");
    expect(init?.body).toBeInstanceOf(FormData);
    const body = init?.body as FormData;
    expect(body.get("photo")).toBe(photo);
    expect(body.has("file")).toBe(false);
    expect(init?.headers).toBeUndefined();
    vi.mocked(apiRequest).mockResolvedValue(undefined);
    await expect(removeMyAvatar()).resolves.toBeUndefined();
    expect(apiRequest).toHaveBeenLastCalledWith("/api/v1/me/avatar", {
      method: "DELETE",
    });
  });
  it.each([
    new File(["x"], "a.gif", { type: "image/gif" }),
    new File([], "empty.png", { type: "image/png" }),
    new File([new Uint8Array(2097153)], "big.png", { type: "image/png" }),
  ])("ảnh sai MIME/rỗng/quá 2 MB: không gửi HTTP", async (photo) => {
    const error = await failure(updateMyAvatar(photo));
    expect(error.status).toBe(400);
    expect(toFieldErrorMap(error.fieldErrors)).toEqual({ photo: "MSG-08" });
    expect(apiRequest).not.toHaveBeenCalled();
  });
  it.each(["image/jpeg", "image/png", "image/webp"])(
    "chấp nhận %s tại biên 2 MB",
    async (type) => {
      vi.mocked(apiRequest).mockResolvedValue(accountResponse());
      await updateMyAvatar(
        new File([new Uint8Array(2097152)], "photo", { type }),
      );
      expect(apiRequest).toHaveBeenCalledTimes(1);
    },
  );
  it.each([400, 401, 403, 404, 500, 503])(
    "upload/delete giữ lỗi HTTP %s",
    async (status) => {
      const error = new ApiError(status, { errorCode: "MSG-08" });
      vi.mocked(apiRequest).mockRejectedValue(error);
      await expect(
        updateMyAvatar(
          new File(["invalid magic bytes"], "a.png", { type: "image/png" }),
        ),
      ).rejects.toBe(error);
      await expect(removeMyAvatar()).rejects.toBe(error);
    },
  );
});

describe("adapter mật khẩu chưa thuộc giai đoạn này", () => {
  it("mật khẩu hiện tại sai vẫn báo đúng trường", async () => {
    const error = await failure(
      changeMyPassword({
        currentPassword: "SaiMatKhau1",
        newPassword: "MatKhauMoi2",
      }),
    );
    expect(toFieldErrorMap(error.fieldErrors)).toEqual({
      currentPassword: "MSG-08",
    });
  });

  it("mật khẩu mới chưa đạt chính sách vẫn báo đúng trường", async () => {
    const error = await failure(
      changeMyPassword({
        currentPassword: "MatKhauCu1",
        newPassword: "abcdefgh",
      }),
    );
    expect(toFieldErrorMap(error.fieldErrors)).toEqual({
      newPassword: "MSG-08",
    });
  });

  it("lỗi dịch vụ vẫn là MSG-04", async () => {
    const error = await failure(
      changeMyPassword({
        currentPassword: "LoiDichVu1",
        newPassword: "MatKhauMoi2",
      }),
    );
    expect([error.status, error.code]).toEqual([500, "MSG-04"]);
  });

  it("adapter đổi mật khẩu vẫn tắt forced-password; không giả lập cập nhật GET thật", async () => {
    resetMyAccountMockStore(MOCK_MY_ACCOUNT_FIRST_LOGIN);
    await changeMyPassword({
      currentPassword: "MatKhauCu1",
      newPassword: "MatKhauMoi2",
    });
    expect(currentMockAccountState().account).toMatchObject({
      mustChangePassword: false,
    });
    expect(apiRequest).not.toHaveBeenCalled();
  });
});
