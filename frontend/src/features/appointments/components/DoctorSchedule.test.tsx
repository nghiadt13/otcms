import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, it, vi } from "vitest";
import { DoctorSchedule } from "./DoctorSchedule";

vi.mock("@/lib/api/client", () => ({ apiRequest: vi.fn((url: string) => Promise.resolve(
  url.startsWith("/api/v1/doctors/me/shifts")
    ? { items: [{ id: "shift", startsAt: "2026-11-20T01:00:00Z", endsAt: "2026-11-20T04:00:00Z", roomId: null }] }
    : { items: [{ id: "appointment", patientName: "Nguyễn An", serviceName: "Khám", startTime: "2026-11-20T02:00:00Z", status: "CONFIRMED" }], totalPages: 1 },
)) }));

it("shows appointments and the doctor's shifts in separate tabs", async () => {
  render(<DoctorSchedule />);
  expect(await screen.findByText(/Nguyễn An/)).toBeInTheDocument();
  await userEvent.click(screen.getByRole("tab", { name: "Ca làm việc" }));
  expect(await screen.findByText(/20\/11\/2026/)).toBeInTheDocument();
});
