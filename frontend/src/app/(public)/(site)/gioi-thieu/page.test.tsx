import { render, screen } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { clinicContact } from "@/features/clinic-website/siteNavigation";
import { publicApiRequest } from "@/lib/api/client";
import AboutRoutePage from "./page";

vi.mock("@/lib/api/client", () => ({ publicApiRequest: vi.fn() }));

const request = vi.mocked(publicApiRequest);

beforeEach(() => {
  request.mockReset();
});

it("hiển thị giới thiệu và liên hệ mặc định khi backend không phản hồi", async () => {
  request.mockRejectedValue(new TypeError("fetch failed"));

  render(await AboutRoutePage());

  expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(
    "Giới thiệu phòng khám",
  );
  expect(screen.getByText(clinicContact.address)).toBeVisible();
  expect(
    screen.getByRole("link", { name: `Hotline ${clinicContact.hotline}` }),
  ).toBeVisible();
});

it("giữ nội dung Admin khi chỉ API thông tin liên hệ lỗi", async () => {
  request.mockImplementation((path) => {
    if (path === "/api/v1/public/about") {
      return Promise.resolve({
        title: "Giới thiệu đã công khai",
        body: "Nội dung từ Admin.",
      });
    }
    return Promise.reject(new TypeError("fetch failed"));
  });

  render(await AboutRoutePage());

  expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(
    "Giới thiệu đã công khai",
  );
  expect(screen.getByText("Nội dung từ Admin.")).toBeVisible();
  expect(screen.getByText(clinicContact.address)).toBeVisible();
});
