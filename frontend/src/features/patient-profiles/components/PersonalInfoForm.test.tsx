import { fireEvent, render, screen, waitFor } from "@testing-library/react";
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

import { PersonalInfoForm } from "./PersonalInfoForm";
import type { OwnPatientProfile } from "../types/ownPatientProfile";

const detail: OwnPatientProfile = {
  patientProfileId: "profile-1",
  fullName: "Nguyễn An",
  phoneCanonical: "+84912345678",
  phoneOwnerType: "PATIENT",
  dateOfBirth: "1990-05-20",
  gender: "MALE",
  sex: "MALE",
  address: "Hà Nội",
  nationalId: null,
  identityDocumentType: null,
  hasAccount: true,
  version: 4,
};

function renderForm() {
  const onSaved = vi.fn();
  render(
    <PersonalInfoForm
      detail={detail}
      emailSlot={<div>Email</div>}
      onSaved={onSaved}
    />,
  );
  return onSaved;
}

beforeEach(() => {
  apiRequest.mockReset();
  session.accountId = "patient-a";
});

describe("PersonalInfoForm with KAN-29 own profile API", () => {
  it("sends the complete PUT DTO with the server version and applies the returned version/data", async () => {
    const onSaved = renderForm();
    apiRequest.mockResolvedValue({
      ...detail,
      fullName: "Nguyễn An Chuẩn",
      version: 5,
    });
    fireEvent.change(screen.getByLabelText(/Họ và tên/), {
      target: { value: " Nguyễn An " },
    });
    fireEvent.click(screen.getByRole("button", { name: "Lưu thay đổi" }));
    await waitFor(() =>
      expect(onSaved).toHaveBeenCalledWith(
        expect.objectContaining({ fullName: "Nguyễn An Chuẩn", version: 5 }),
      ),
    );
    expect(apiRequest).toHaveBeenCalledWith(
      "/api/v1/me/patient-profile",
      expect.objectContaining({
        method: "PUT",
        body: JSON.stringify({
          fullName: "Nguyễn An",
          phone: "0912345678",
          phoneOwnerType: "PATIENT",
          dateOfBirth: "1990-05-20",
          gender: "MALE",
          address: "Hà Nội",
          nationalId: null,
          identityDocumentType: null,
          version: 4,
        }),
      }),
    );
  });

  it("locks writes on STALE_VERSION and keeps the draft through failed reload until a successful reload", async () => {
    renderForm();
    fireEvent.change(screen.getByLabelText(/Họ và tên/), {
      target: { value: "Tên bản nháp" },
    });
    apiRequest.mockRejectedValueOnce(
      new ApiError(409, { errorCode: "STALE_VERSION" }),
    );
    fireEvent.click(screen.getByRole("button", { name: "Lưu thay đổi" }));
    await screen.findByText(
      "Không thể hoàn tất yêu cầu. Hãy tải lại trạng thái trước khi thử lại.",
    );
    expect(screen.getByLabelText(/Họ và tên/)).toHaveValue("Tên bản nháp");
    expect(screen.getByRole("button", { name: "Lưu thay đổi" })).toBeDisabled();

    apiRequest.mockRejectedValueOnce(new ApiError(503, { code: "MSG-04" }));
    fireEvent.click(
      screen.getByRole("button", { name: "Tải lại hồ sơ mới nhất" }),
    );
    await waitFor(() => expect(apiRequest).toHaveBeenCalledTimes(2));
    expect(screen.getByLabelText(/Họ và tên/)).toHaveValue("Tên bản nháp");
    expect(screen.getByRole("button", { name: "Lưu thay đổi" })).toBeDisabled();

    apiRequest.mockResolvedValueOnce({
      ...detail,
      version: 9,
      fullName: "Nguyễn An mới",
    });
    fireEvent.click(
      screen.getByRole("button", { name: "Tải lại hồ sơ mới nhất" }),
    );
    await waitFor(() =>
      expect(
        screen.getByRole("button", { name: "Lưu thay đổi" }),
      ).toBeEnabled(),
    );
    expect(screen.getByLabelText(/Họ và tên/)).toHaveValue("Tên bản nháp");
    apiRequest.mockResolvedValueOnce({
      ...detail,
      fullName: "Tên bản nháp",
      version: 10,
    });
    fireEvent.click(screen.getByRole("button", { name: "Lưu thay đổi" }));
    await waitFor(() => expect(apiRequest).toHaveBeenCalledTimes(4));
    expect(JSON.parse(apiRequest.mock.calls[3][1].body).version).toBe(9);
  });

  it("shows local validation and does not send PUT for invalid required values", async () => {
    renderForm();
    fireEvent.change(screen.getByLabelText(/Họ và tên/), {
      target: { value: "A" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Lưu thay đổi" }));
    expect(
      await screen.findByText("Họ và tên gồm ít nhất hai chữ, không có số."),
    ).toBeInTheDocument();
    expect(apiRequest).not.toHaveBeenCalled();
  });

  it("preserves the draft after an ordinary network failure", async () => {
    renderForm();
    fireEvent.change(screen.getByLabelText(/Họ và tên/), {
      target: { value: "Tên còn giữ" },
    });
    apiRequest.mockRejectedValueOnce(new Error("network down"));
    fireEvent.click(screen.getByRole("button", { name: "Lưu thay đổi" }));
    await waitFor(() => expect(apiRequest).toHaveBeenCalledTimes(1));
    expect(screen.getByLabelText(/Họ và tên/)).toHaveValue("Tên còn giữ");
    expect(screen.getByRole("button", { name: "Lưu thay đổi" })).toBeEnabled();
  });

  it.each([401, 403, 404])(
    "hides prior profile data after PUT HTTP %i",
    async (status) => {
      renderForm();
      fireEvent.change(screen.getByLabelText(/Họ và tên/), {
        target: { value: "Tên mới" },
      });
      apiRequest.mockRejectedValueOnce(new ApiError(status));
      fireEvent.click(screen.getByRole("button", { name: "Lưu thay đổi" }));
      await screen.findByRole("alert");
      expect(screen.queryByLabelText(/Họ và tên/)).toBeNull();
      expect(screen.queryByText("Tên mới")).toBeNull();
    },
  );
});
