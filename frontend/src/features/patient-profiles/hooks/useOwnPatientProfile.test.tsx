import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";

const { apiRequest, session } = vi.hoisted(() => ({
  apiRequest: vi.fn(),
  session: { accountId: "patient-a" as string | null },
}));
vi.mock("@/lib/api/client", () => ({ apiRequest }));
vi.mock("@/lib/auth/authSession", async (importOriginal) => ({
  ...(await importOriginal()),
  peekSession: () => session,
}));

import { useOwnPatientProfile } from "./useOwnPatientProfile";

const profile = {
  patientProfileId: "profile-a",
  fullName: "Patient A",
  phoneCanonical: "+84912345678",
  phoneOwnerType: "PATIENT",
  dateOfBirth: "1990-05-20",
  gender: "MALE",
  address: null,
  nationalId: null,
  identityDocumentType: null,
  hasAccount: true,
  version: 0,
};

beforeEach(() => {
  apiRequest.mockReset();
  session.accountId = "patient-a";
});

describe("useOwnPatientProfile", () => {
  it("loads only the authenticated self endpoint and returns server detail", async () => {
    apiRequest.mockResolvedValue(profile);
    const { result } = renderHook(() => useOwnPatientProfile());
    await waitFor(() => expect(result.current.state.status).toBe("ready"));
    expect(apiRequest).toHaveBeenCalledWith("/api/v1/me/patient-profile", {
      signal: expect.any(AbortSignal),
    });
    if (result.current.state.status === "ready")
      expect(result.current.state.detail.fullName).toBe("Patient A");
  });

  it.each([401, 403, 404, 503])(
    "maps HTTP %i without retaining profile data",
    async (status) => {
      apiRequest.mockRejectedValue(
        new ApiError(status, { code: status === 401 ? "MSG-14" : "MSG-04" }),
      );
      const { result } = renderHook(() => useOwnPatientProfile());
      await waitFor(() =>
        expect(result.current.state.status).not.toBe("loading"),
      );
      expect(result.current.state.status).toBe(
        status === 404 ? "missing" : "error",
      );
      if (result.current.state.status === "ready")
        throw new Error("Profile must not be exposed");
    },
  );

  it("treats apiRequest's missing-access-token error as lost authentication", async () => {
    apiRequest.mockRejectedValue(new Error("Bạn cần đăng nhập để tiếp tục."));
    const { result } = renderHook(() => useOwnPatientProfile());
    await waitFor(() => expect(result.current.state.status).toBe("error"));
    expect(result.current.state).toEqual({
      status: "error",
      message:
        "Bạn không có quyền thực hiện thao tác này hoặc tài khoản không còn khả dụng.",
    });
  });

  it("ignores a late response from a session that changed while GET was pending", async () => {
    let resolveRequest!: (value: unknown) => void;
    apiRequest.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveRequest = resolve;
        }),
    );
    const { result } = renderHook(() => useOwnPatientProfile());
    await waitFor(() => expect(resolveRequest).toBeDefined());
    session.accountId = "patient-b";
    await act(async () => resolveRequest(profile));
    await waitFor(() => expect(result.current.state.status).toBe("loading"));
  });

  it("refreshes after account-change event and clears the prior profile while loading", async () => {
    apiRequest.mockResolvedValueOnce(profile).mockResolvedValueOnce({
      ...profile,
      patientProfileId: "profile-b",
      fullName: "Patient B",
    });
    const { result } = renderHook(() => useOwnPatientProfile());
    await waitFor(() => expect(result.current.state.status).toBe("ready"));
    session.accountId = "patient-b";
    act(() =>
      window.dispatchEvent(
        new CustomEvent("my-account:changed", { detail: { id: "patient-b" } }),
      ),
    );
    await waitFor(() => expect(result.current.state.status).toBe("ready"));
    if (result.current.state.status === "ready")
      expect(result.current.state.detail.fullName).toBe("Patient B");
  });

  it("clears loaded profile data when the session access-lost event arrives", async () => {
    apiRequest.mockResolvedValue(profile);
    const { result } = renderHook(() => useOwnPatientProfile());
    await waitFor(() => expect(result.current.state.status).toBe("ready"));
    act(() => window.dispatchEvent(new Event("my-account:access-lost")));
    expect(result.current.state).toEqual({
      status: "error",
      message:
        "Bạn không có quyền thực hiện thao tác này hoặc tài khoản không còn khả dụng.",
    });
  });

  it("does not render a previously loaded profile after session identity changes", async () => {
    apiRequest.mockResolvedValue(profile);
    const { result, rerender } = renderHook(() => useOwnPatientProfile());
    await waitFor(() => expect(result.current.state.status).toBe("ready"));
    session.accountId = "patient-b";
    rerender();
    expect(result.current.state).toEqual({ status: "loading" });
  });
});
