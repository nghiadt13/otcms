import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { ImagingFindingsDialog } from "@/features/doctor-exam/components/ImagingFindingsDialog";
import type { ParaclinicalOrder } from "@/features/doctor-exam/types/doctorExam";
import { DoctorImagingViewerBridge } from "./DoctorImagingViewerBridge";
const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
vi.mock("@/lib/api/apiBaseUrl", () => ({ apiBaseUrl: "" }));
const originalLocation = window.location;
const assign = vi.fn();
beforeEach(() => {
  request.mockReset();
  assign.mockReset();
  Object.defineProperty(window, "location", {
    configurable: true,
    value: {
      ...originalLocation,
      origin: "http://localhost:3000",
      pathname: "/doctor/visits/visit-id",
      search: "?tab=clinical",
      assign,
    },
  });
  window.localStorage.removeItem("otcms.doctorDemo");
});
afterEach(() => {
  Object.defineProperty(window, "location", {
    configurable: true,
    value: originalLocation,
  });
});
const order: ParaclinicalOrder = {
  id: "request",
  code: null,
  round: null,
  kind: "IMAGING",
  serviceName: "MRI",
  bodyArea: "Knee",
  clinicalReason: "Pain",
  price: null,
  status: "RESULTS_AVAILABLE",
  statusNote: null,
  cancellable: false,
  imaging: {
    requestId: "request",
    version: 1,
    studyRef: "study-ref",
    reviewed: true,
    findingsText: "",
    findingsVersion: null,
    findingsCompleted: false,
    reportId: null,
  },
};
function show(value = order) {
  render(
    <DoctorImagingViewerBridge>
      <ImagingFindingsDialog
        order={value}
        onClose={vi.fn()}
        onChanged={vi.fn()}
      />
    </DoctorImagingViewerBridge>,
  );
}
it("Doctor opens the same-origin viewer after the backend authorizes a scoped grant", async () => {
  request.mockResolvedValue({
    studyInstanceUid: "1.2.840.123",
    expiresAt: "2026-11-06T01:00:00Z",
  });
  show();
  await userEvent.click(
    screen.getByRole("button", { name: "Mở ảnh DICOM" }),
  );
  await waitFor(() => {
    expect(request).toHaveBeenCalledWith(
      "/api/v1/imaging/studies/study-ref/viewer-grants",
      { method: "POST", body: JSON.stringify({ purpose: "DOCTOR_REVIEW" }) },
    );
    expect(assign).toHaveBeenCalledWith(
      "/imaging-viewer?studyInstanceUid=1.2.840.123&returnTo=%2Fdoctor%2Fvisits%2Fvisit-id%3Ftab%3Dclinical",
    );
  });
});
it("missing study shows empty state without launcher", () => {
  show({ ...order, imaging: { ...order.imaging!, studyRef: null } });
  expect(screen.getByText("Chưa có ca chụp để xem.")).toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "Mở ảnh DICOM" }),
  ).not.toBeInTheDocument();
  expect(request).not.toHaveBeenCalled();
});
it("denied grant never reveals a viewer/archive link", async () => {
  request.mockRejectedValue(new Error("Denied"));
  show();
  await userEvent.click(
    screen.getByRole("button", { name: "Mở ảnh DICOM" }),
  );
  expect(await screen.findByRole("alert")).toBeInTheDocument();
  expect(screen.queryByRole("link")).not.toBeInTheDocument();
});
