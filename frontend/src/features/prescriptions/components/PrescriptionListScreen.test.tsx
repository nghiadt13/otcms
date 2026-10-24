import { render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { resetLinkedProfilesMock } from "@/lib/patient-scope/linkedProfilesMockStore";
import { PrescriptionListScreen } from "./PrescriptionListScreen";

// Ranh giới ngoài: router của Next; `search` giả lập `?profileId=` trên URL.
const nav = vi.hoisted(() => ({ search: "" }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
  usePathname: () => "/portal/prescriptions",
  useSearchParams: () => new URLSearchParams(nav.search),
}));

const HUY_ID = "5c1e7a20-1f3b-4c8d-9e2a-0b7d6c5a4f03";

beforeEach(() => {
  nav.search = "";
  resetLinkedProfilesMock();
});

afterEach(() => {
  resetLinkedProfilesMock();
});

describe("PrescriptionListScreen", () => {
  it("liệt kê đơn của mọi hồ sơ kèm số thuốc và người bệnh", async () => {
    render(<PrescriptionListScreen />);
    expect(
      await screen.findByRole("link", { name: /23\/10\/2026 · 3 thuốc/ }),
    ).toHaveAttribute("href", "/portal/prescriptions/pres-an-1");
    expect(
      screen.getByRole("link", { name: /Nguyễn Thị Lan \(Cha\/Mẹ\) · BS\. Đỗ Minh Tuấn/ }),
    ).toHaveAttribute("href", "/portal/prescriptions/pres-lan-1");
  });

  it("hồ sơ chưa có đơn hiện câu trống", async () => {
    nav.search = `profileId=${HUY_ID}`;
    render(<PrescriptionListScreen />);
    expect(await screen.findByText(/Chưa có đơn thuốc nào\./)).toBeInTheDocument();
  });
});
