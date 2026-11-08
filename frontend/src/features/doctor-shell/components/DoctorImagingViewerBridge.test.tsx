import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { ImagingFindingsDialog } from "@/features/doctor-exam/components/ImagingFindingsDialog";
import type { ParaclinicalOrder } from "@/features/doctor-exam/types/doctorExam";
import { DoctorImagingViewerBridge } from "./DoctorImagingViewerBridge";
const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
vi.mock("@/lib/api/apiBaseUrl", () => ({ apiBaseUrl: "" }));
beforeEach(() => {
  request.mockReset();
  window.localStorage.removeItem("otcms.doctorDemo");
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
it("Doctor findings requests secure grant before exposing same-origin viewer link", async () => {
  request.mockResolvedValue({
    studyInstanceUid: "1.2.840.123",
    expiresAt: "2026-11-06T01:00:00Z",
  });
  show();
  expect(screen.queryByRole("link")).not.toBeInTheDocument();
  await userEvent.click(
    screen.getByRole("button", { name: "Cấp quyền xem ảnh" }),
  );
  const link = await screen.findByRole("link", { name: "Mở trình xem ảnh" });
  expect(request).toHaveBeenCalledWith(
    "/api/v1/imaging/studies/study-ref/viewer-grants",
    { method: "POST", body: JSON.stringify({ purpose: "DOCTOR_REVIEW" }) },
  );
  expect(link.getAttribute("href")).toBe(
    "/viewer?StudyInstanceUIDs=1.2.840.123",
  );
  expect(link.getAttribute("href")).not.toMatch(
    /token|orthanc|localhost|study-ref/i,
  );
});
it("missing study shows empty state without launcher", () => {
  show({ ...order, imaging: { ...order.imaging!, studyRef: null } });
  expect(screen.getByText("Chưa có ca chụp để xem.")).toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "Cấp quyền xem ảnh" }),
  ).not.toBeInTheDocument();
  expect(request).not.toHaveBeenCalled();
});
it("denied grant never reveals a viewer/archive link", async () => {
  request.mockRejectedValue(new Error("Denied"));
  show();
  await userEvent.click(
    screen.getByRole("button", { name: "Cấp quyền xem ảnh" }),
  );
  expect(await screen.findByRole("alert")).toBeInTheDocument();
  expect(screen.queryByRole("link")).not.toBeInTheDocument();
});
