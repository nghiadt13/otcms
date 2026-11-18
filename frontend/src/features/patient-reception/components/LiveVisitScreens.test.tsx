import { beforeEach, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { VisitScreen } from "./LiveVisitScreens";
const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({
  apiRequest: request,
  fetchBlob: vi.fn(),
}));
vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock("./ReceptionProvider", () => ({
  useReception: () => ({ pending: false }),
}));
beforeEach(() => {
  request.mockReset();
});
it("opens a real closed visit and loads its result sheets", async () => {
  request.mockImplementation((path: string) => {
    if (path.includes("/progress"))
      return Promise.resolve({
        visitId: "visit-1",
        status: "CLOSED",
        currentStage: "CLOSED",
        arrivedAt: "2026-11-18T01:00:00Z",
        currentRoom: null,
        queueNumber: null,
        peopleAhead: null,
        beingCalled: false,
        serviceOrders: [],
      });
    if (path.endsWith("/result-sheets"))
      return Promise.resolve({
        items: [
          {
            documentId: "doc-1",
            version: 1,
            status: "ACTIVE",
            releasedAt: null,
          },
        ],
      });
    return Promise.resolve({
      items: [
        {
          visitId: "visit-1",
          patientId: "patient-1",
          patientName: "An",
          status: "CLOSED",
          arrivedAt: "2026-11-18T01:00:00Z",
        },
      ],
      hasMore: false,
    });
  });
  render(<VisitScreen />);
  expect(await screen.findByText("An")).toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "Tiến trình" }));
  expect(
    await screen.findByRole("button", { name: "Xem / in PDF" }),
  ).toBeInTheDocument();
  expect(request).toHaveBeenCalledWith("/api/v1/visits/visit-1/result-sheets", {
    signal: expect.any(AbortSignal),
  });
});
