import { render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { apiRequest } from "@/lib/api/client";
import { PatientAnnouncements } from "./PatientAnnouncements";
vi.mock("@/lib/api/client", () => ({ apiRequest: vi.fn() }));
it("shows patient announcements and their full text", async () => {
  vi.mocked(apiRequest).mockResolvedValue({ items: [{ id: "a", title: "Lịch nghỉ", body: "Phòng khám đóng cửa", publishedAt: "2026-11-17T00:00:00Z" }], page: 0, size: 10, totalItems: 1, totalPages: 1 });
  render(<PatientAnnouncements />);
  expect(await screen.findByText(/Lịch nghỉ/)).toBeInTheDocument();
  expect(screen.getByText("Phòng khám đóng cửa")).toBeInTheDocument();
});
