import { render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { apiRequest } from "@/lib/api/client";
import { PatientProfilesScreen } from "./PatientProfilesScreen";

vi.mock("@/lib/api/client", () => ({ apiRequest: vi.fn() }));

it("hiển thị các trạng thái và chỉ mở chi tiết hồ sơ đã xác nhận", async () => {
  vi.mocked(apiRequest).mockResolvedValue({
    items: [
      {
        patientProfileId: "own",
        fullName: "Nguyễn Văn An",
        relationship: "SELF",
        linkStatus: "CONFIRMED",
      },
      {
        patientProfileId: "child",
        fullName: "Nguyễn Gia Huy",
        relationship: "CHILD",
        linkStatus: "PENDING_OTP",
        linkId: "one",
      },
      {
        patientProfileId: "parent",
        fullName: "Nguyễn Thị Lan",
        relationship: "PARENT",
        linkStatus: "PENDING_COUNTER",
        linkId: "two",
      },
    ],
  });
  render(<PatientProfilesScreen />);
  expect(await screen.findByText("Chờ OTP")).toBeInTheDocument();
  expect(screen.getByText("Chờ xác nhận tại quầy")).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "Xem hồ sơ" })).toHaveAttribute(
    "href",
    "/portal/profiles/own",
  );
  expect(
    await screen.findByRole("link", { name: /Xem tiền sử bệnh/ }),
  ).toHaveAttribute("href", "/portal/profiles/own/history");
  expect(screen.getByRole("link", { name: "Thêm người thân" })).toHaveAttribute(
    "href",
    "/portal/profiles/new",
  );
});

it("hiển thị trạng thái trống", async () => {
  vi.mocked(apiRequest).mockResolvedValue({ items: [] });
  render(<PatientProfilesScreen />);
  expect(
    await screen.findByText("Chưa có hồ sơ bệnh nhân."),
  ).toBeInTheDocument();
});

it("hiển thị lỗi tải và cho thử lại", async () => {
  vi.mocked(apiRequest).mockRejectedValue(new Error("Offline"));
  render(<PatientProfilesScreen />);
  expect(await screen.findByText("Không tải được hồ sơ.")).toBeInTheDocument();
});
