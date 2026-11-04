import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { apiRequest } from "@/lib/api/client";
import { PatientProfileDetailScreen } from "./PatientProfileDetailScreen";

const mocks = vi.hoisted(() => ({ replace: vi.fn(), notifySuccess: vi.fn() }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mocks.replace }),
}));
vi.mock("@/lib/messages/notify", () => ({
  notifySuccess: mocks.notifySuccess,
}));
vi.mock("@/lib/api/client", () => ({ apiRequest: vi.fn() }));
const request = vi.mocked(apiRequest);
const profile = {
  patientProfileId: "child",
  fullName: "Nguyễn Minh An",
  dateOfBirth: "2017-05-01",
  gender: "FEMALE",
  phoneCanonical: null,
  phoneOwnerType: null,
  address: null,
  nationalId: null,
  identityDocumentType: null,
  version: 0,
};

beforeEach(() => vi.resetAllMocks());

function seed(linkStatus = "CONFIRMED") {
  request.mockResolvedValueOnce({
    items: [{ ...profile, relationship: "CHILD", linkStatus }],
  });
}

it("tải và lưu hành chính qua endpoint người thân", async () => {
  seed();
  request
    .mockResolvedValueOnce(profile)
    .mockResolvedValueOnce({ ...profile, address: "Hà Nội" });
  render(<PatientProfileDetailScreen profileId="child" />);
  fireEvent.change(await screen.findByLabelText("Địa chỉ"), {
    target: { value: "Hà Nội" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Lưu thông tin" }));
  await waitFor(() =>
    expect(mocks.notifySuccess).toHaveBeenCalledWith("MSG-90"),
  );
  expect(request).toHaveBeenNthCalledWith(2, "/api/v1/me/dependents/child", {
    signal: expect.any(AbortSignal),
  });
  expect(request).toHaveBeenNthCalledWith(3, "/api/v1/me/dependents/child", {
    method: "PUT",
    body: expect.stringContaining('"address":"Hà Nội"'),
  });
});

it("yêu cầu xác nhận trước khi gỡ và giữ nguyên lịch sử khám", async () => {
  seed();
  request.mockResolvedValueOnce(profile).mockResolvedValueOnce(undefined);
  render(<PatientProfileDetailScreen profileId="child" />);
  fireEvent.click(await screen.findByRole("button", { name: "Gỡ liên kết" }));
  expect(
    screen.getByText(/lịch sử khám tại phòng khám vẫn được giữ/),
  ).toBeInTheDocument();
  expect(request).toHaveBeenCalledTimes(2);
  fireEvent.click(screen.getByRole("button", { name: "Xác nhận gỡ" }));
  await waitFor(() =>
    expect(mocks.replace).toHaveBeenCalledWith("/portal/profiles"),
  );
  expect(mocks.notifySuccess).toHaveBeenCalledWith("MSG-91");
  expect(request).toHaveBeenLastCalledWith("/api/v1/me/dependents/child", {
    method: "DELETE",
  });
});

it("không tải chi tiết của liên kết đang chờ", async () => {
  seed("PENDING_COUNTER");
  render(<PatientProfileDetailScreen profileId="child" />);
  expect(
    await screen.findByText("Không thể quản lý hồ sơ này."),
  ).toBeInTheDocument();
  expect(request).toHaveBeenCalledTimes(1);
});
