import { render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { DoctorSchedule } from "./DoctorSchedule";

vi.mock("@/lib/api/client", () => ({
  apiRequest: vi.fn((url: string) =>
    Promise.resolve(
      url.startsWith("/api/v1/doctors/me/shifts")
        ? {
            items: [
              {
                id: "shift",
                startsAt: "2026-11-20T01:00:00Z",
                endsAt: "2026-11-20T04:00:00Z",
                roomId: null,
              },
            ],
          }
        : { items: [] },
    ),
  ),
}));

it("shows the doctor's shifts", async () => {
  render(<DoctorSchedule />);
  expect(await screen.findByText(/20\/11\/2026/)).toBeInTheDocument();
  expect(screen.queryByText(/Nguyễn An/)).not.toBeInTheDocument();
});
