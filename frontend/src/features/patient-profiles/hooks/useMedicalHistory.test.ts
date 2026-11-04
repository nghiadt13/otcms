import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "@/lib/api/client";
import { ApiError } from "@/lib/api/apiError";
import { AuthSessionError } from "@/lib/auth/authSession";
import {
  MedicalHistoryTestServer,
  HISTORY_PROFILE_ID,
} from "../api/medicalHistoryTestServer";
import { useMedicalHistory } from "./useMedicalHistory";

vi.mock("@/lib/api/client", () => ({ apiRequest: vi.fn() }));
let server: MedicalHistoryTestServer;

beforeEach(() => {
  vi.resetAllMocks();
  server = new MedicalHistoryTestServer();
  vi.mocked(apiRequest).mockImplementation(server.request);
});

describe.each(["refresh", "save"] as const)(
  "useMedicalHistory.%s xóa dữ liệu khi mất quyền truy cập",
  (operation) => {
    it.each([
      { name: "HTTP 401", error: new ApiError(401) },
      {
        name: "không có access token",
        error: new Error("Bạn cần đăng nhập để tiếp tục."),
      },
      { name: "AuthSessionError 401", error: new AuthSessionError(401) },
      { name: "AuthSessionError 403", error: new AuthSessionError(403) },
      { name: "HTTP 403", error: new ApiError(403) },
      { name: "HTTP 404", error: new ApiError(404) },
    ])(
      "$name: history bị xóa khỏi state và lỗi vẫn được ném lại",
      async ({ error }) => {
        const { result } = renderHook(() =>
          useMedicalHistory(HISTORY_PROFILE_ID),
        );
        await waitFor(() =>
          expect(result.current.history).toEqual(server.history),
        );
        server.getError = error;
        server.putError = error;
        await act(async () => {
          const pending =
            operation === "refresh"
              ? result.current.refresh()
              : result.current.save({
                  ...server.history,
                  allergies: "Bản sửa chưa lưu",
                });
          await expect(pending).rejects.toBe(error);
        });
        expect(result.current.history).toBeNull();
        expect(result.current.error).toBe(error);
        expect(result.current.loading).toBe(false);
      },
    );
  },
);
