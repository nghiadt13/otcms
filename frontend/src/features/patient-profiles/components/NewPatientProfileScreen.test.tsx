import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import { apiRequest } from "@/lib/api/client";
import { NewPatientProfileScreen } from "./NewPatientProfileScreen";

const mocks = vi.hoisted(() => ({ push: vi.fn(), notifySuccess: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: mocks.push }) }));
vi.mock("@/lib/messages/notify", () => ({
  notifySuccess: mocks.notifySuccess,
}));
vi.mock("@/lib/api/client", () => ({ apiRequest: vi.fn() }));
const request = vi.mocked(apiRequest);

beforeEach(() => vi.resetAllMocks());

function submitDependent() {
  render(<NewPatientProfileScreen />);
  for (const [label, value] of [
    ["Họ và tên", "Nguyễn Minh An"],
    ["Ngày sinh", "2017-05-01"],
    ["Giới tính", "FEMALE"],
    ["Quan hệ với bạn", "CHILD"],
    ["Loại giấy tờ", "CCCD"],
    ["Số giấy tờ", "001217000001"],
  ]) {
    fireEvent.change(screen.getByLabelText(label), { target: { value } });
  }
  fireEvent.click(screen.getByRole("button", { name: "Gửi yêu cầu" }));
}

it("gửi đúng hợp đồng và trở về danh sách khi liên kết ngay", async () => {
  request.mockResolvedValueOnce({ result: "LINKED", linkId: "link-1" });
  submitDependent();
  await waitFor(() => expect(mocks.push).toHaveBeenCalled());
  expect(mocks.notifySuccess).toHaveBeenCalledWith("MSG-90");
  expect(request).toHaveBeenCalledWith("/api/v1/me/dependents", {
    method: "POST",
    body: JSON.stringify({
      fullName: "Nguyễn Minh An",
      dateOfBirth: "2017-05-01",
      gender: "FEMALE",
      relationship: "CHILD",
      nationalId: "001217000001",
      identityDocumentType: "CCCD",
    }),
  });
});

it("hướng dẫn xác minh giấy tờ khi backend yêu cầu đến quầy", async () => {
  request.mockResolvedValueOnce({
    result: "COUNTER_REQUIRED",
    linkId: "link-1",
  });
  submitDependent();
  expect(await screen.findByText("Chờ xác nhận tại quầy")).toBeInTheDocument();
  expect(mocks.push).not.toHaveBeenCalled();
});

it("giữ bước OTP khi mã sai, cho gửi lại và xác nhận mã mới", async () => {
  const otp = {
    result: "OTP_SENT",
    linkId: "link-1",
    maskedEmail: "a***@example.com",
  };
  request
    .mockResolvedValueOnce(otp)
    .mockRejectedValueOnce(new ApiError(400, { errorCode: "OTP_INVALID" }))
    .mockResolvedValueOnce(otp)
    .mockResolvedValueOnce({ result: "LINKED", linkId: "link-1" });
  submitDependent();
  expect(await screen.findByText(/a\*\*\*@example\.com/)).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText("Mã xác minh"), {
    target: { value: "111111" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Xác nhận mã" }));
  expect(await screen.findByRole("alert")).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Gửi lại mã" }));
  expect(
    await screen.findByText("Đã gửi lại mã xác minh."),
  ).toBeInTheDocument();
  expect(request).toHaveBeenNthCalledWith(
    3,
    "/api/v1/me/dependents/links/link-1/otp/resend",
    { method: "POST" },
  );
  fireEvent.change(screen.getByLabelText("Mã xác minh"), {
    target: { value: "222222" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Xác nhận mã" }));
  await waitFor(() => expect(mocks.push).toHaveBeenCalled());
  expect(request).toHaveBeenNthCalledWith(
    4,
    "/api/v1/me/dependents/links/link-1/otp/verify",
    { method: "POST", body: JSON.stringify({ code: "222222" }) },
  );
});
