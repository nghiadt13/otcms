import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import { PrescriptionForm } from "./PrescriptionForm";
import { RecordsScreen } from "./RecordsScreen";
import { TreatmentScreen } from "./TreatmentScreen";
import { ClosingScreen } from "./ClosingScreen";
import { VisitWorkspaceProvider } from "./VisitWorkspaceProvider";
import { doctorDemoApi, resetDoctorDemo } from "../api/doctorExamDemoApi";
import type { Prescription } from "../types/doctorExam";
const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
vi.mock("@/lib/messages/notify", () => ({
  notifySuccess: vi.fn(),
  notifyError: vi.fn(),
  notifyWarning: vi.fn(),
}));
const line = {
  medicine: "Medicine",
  dose: "1 tablet",
  frequency: "Twice daily",
  usage: "After meals",
};
const rx: Prescription = {
  id: "rx",
  version: 4,
  status: "DRAFT",
  issuedAt: null,
  lines: [line],
};
const dto = {
  id: "rx",
  version: 5,
  status: "DRAFT",
  issuedAt: null,
  items: [{ lineNumber: 1, ...line }],
};
let historyError = false;
let historyItems: unknown[] = [];
let prescription: unknown = null;
beforeEach(() => {
  window.localStorage.removeItem("otcms.doctorDemo");
  resetDoctorDemo();
  request.mockReset();
  historyError = false;
  historyItems = [];
  prescription = null;
  request.mockImplementation(async (path: string) => {
    if (path === "/api/v1/doctor/visits/active")
      return {
        items: [
          {
            visitId: "current",
            patientProfileId: "patient",
            patient: { fullName: "Patient", dateOfBirth: null, gender: null },
            visitSource: "WALK_IN",
            linkedAppointmentId: null,
            assignmentId: "a",
            stepCode: "CONSULTATION",
            startedAt: "2026-11-06T00:00:00Z",
            expectedEnd: null,
          },
        ],
      };
    if (path.startsWith("/api/v1/appointments?")) return { items: [] };
    if (path.includes("/medical-history"))
      return {
        conditions: "",
        allergies: "",
        procedures: "",
        injuries: "",
        orthopedicHistory: "",
        version: 1,
      };
    if (path.endsWith("/past-medical-records")) {
      if (historyError) throw new ApiError(503);
      return {
        currentVisitId: "current",
        patientProfileId: "patient",
        items: historyItems,
        page: 0,
        size: 20,
        total: historyItems.length,
      };
    }
    if (path.endsWith("/examination"))
      return {
        reasonForVisit: "Pain",
        symptoms: "Pain",
        examinationFindings: "Finding",
        diagnoses: [{ code: "M54.5", type: "PRIMARY" }],
        status: "COMPLETED",
        version: 1,
      };
    if (path.endsWith("/after-visit-advice"))
      throw new ApiError(404, { code: "MSG-41" });
    if (path.endsWith("/prescription")) {
      if (prescription) return prescription;
      throw new ApiError(404, { code: "MSG-41" });
    }
    if (path.includes("imaging-requests")) return { items: [] };
    throw new Error(`Unexpected request: ${path}`);
  });
});
function workspace(child: React.ReactNode) {
  return render(
    <VisitWorkspaceProvider visitId="current">{child}</VisitWorkspaceProvider>,
  );
}
it("issued prescription has read-only fields and no reissue/save action", () => {
  render(
    <PrescriptionForm
      visitId="current"
      initial={{ ...rx, status: "ISSUED", issuedAt: "2026-11-06T00:00:00Z" }}
      available
      locked={false}
      onSaved={vi.fn()}
    />,
  );
  expect(screen.getByText("Đã phát hành")).toBeInTheDocument();
  screen
    .getAllByRole("textbox")
    .forEach((input) => expect(input).toBeDisabled());
  expect(screen.getByRole("button", { name: /Xóa dòng/ })).toBeDisabled();
  expect(
    screen.queryByRole("button", { name: /Phát hành|Lưu nháp/ }),
  ).not.toBeInTheDocument();
});
it("partial form fails validation before backend call", async () => {
  render(
    <PrescriptionForm
      visitId="current"
      initial={null}
      available
      locked={false}
      onSaved={vi.fn()}
    />,
  );
  await userEvent.type(screen.getByLabelText("Tên thuốc"), "Medicine");
  await userEvent.click(screen.getByRole("button", { name: "Lưu nháp" }));
  expect(await screen.findByText(/Nhập đủ tên thuốc/)).toBeInTheDocument();
  expect(request).not.toHaveBeenCalled();
});
it("draft save retains returned version for issuance and locks immediately", async () => {
  request
    .mockResolvedValueOnce(dto)
    .mockResolvedValueOnce({ ...dto, version: 6 })
    .mockResolvedValueOnce({
      ...dto,
      version: 7,
      status: "ISSUED",
      issuedAt: "2026-11-06T00:00:00Z",
    });
  render(
    <PrescriptionForm
      visitId="current"
      initial={rx}
      available
      locked={false}
      onSaved={vi.fn()}
    />,
  );
  await userEvent.click(screen.getByRole("button", { name: "Lưu nháp" }));
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "Phát hành đơn" })).toBeEnabled(),
  );
  await userEvent.click(screen.getByRole("button", { name: "Phát hành đơn" }));
  expect(await screen.findByText("Đã phát hành")).toBeInTheDocument();
  expect(JSON.parse(request.mock.calls[1][1].body).version).toBe(5);
  expect(JSON.parse(request.mock.calls[2][1].body).version).toBe(6);
  expect(
    screen.queryByRole("button", { name: /Phát hành/ }),
  ).not.toBeInTheDocument();
});
it("RecordsScreen uses Visit for past records and Patient for history/appointments", async () => {
  historyItems = [
    {
      visitId: "old",
      arrivedAt: "2026-08-01T00:00:00Z",
      examination: { diagnoses: [{ code: "M54.5", type: "PRIMARY" }] },
      advice: { homeCareAdvice: "Rest" },
      prescription: { id: "issued", issuedAt: "2026-08-01T00:00:00Z" },
      imagingResults: [
        { imagingType: "MRI", findingsText: "Released finding" },
      ],
      documents: [],
    },
  ];
  workspace(<RecordsScreen />);
  expect(await screen.findByText("MRI: Released finding")).toBeInTheDocument();
  expect(screen.getByText(/M54.5.*1 đơn thuốc/)).toBeInTheDocument();
  expect(request).toHaveBeenCalledWith(
    "/api/v1/visits/current/past-medical-records",
    expect.anything(),
  );
  expect(request).toHaveBeenCalledWith(
    "/api/v1/patient-profiles/patient/medical-history",
    expect.anything(),
  );
  expect(
    request.mock.calls.some(([path]) =>
      path.includes("appointments?patientProfileId=patient"),
    ),
  ).toBe(true);
  expect(
    request.mock.calls.some(([path]) =>
      path.includes("visits/patient/past-medical"),
    ),
  ).toBe(false);
});
it("Past Records errors render alert/retry, never empty history", async () => {
  historyError = true;
  workspace(<RecordsScreen />);
  const section = await screen.findByRole("region", {
    name: "Hồ sơ bệnh án cũ",
  });
  expect(await within(section).findByRole("alert")).toBeInTheDocument();
  expect(
    within(section).queryByText(/chưa có lượt khám nào/),
  ).not.toBeInTheDocument();
  historyError = false;
  await userEvent.click(
    within(section).getByRole("button", { name: "Thử lại" }),
  );
  expect(
    await within(section).findByText(/chưa có lượt khám nào/),
  ).toBeInTheDocument();
});
it("TreatmentScreen accepts absent prescription and shows canonical fields", async () => {
  workspace(<TreatmentScreen />);
  expect(await screen.findByLabelText("Liều dùng")).toBeEnabled();
  expect(screen.getByLabelText("Tần suất")).toBeInTheDocument();
  expect(screen.queryByLabelText("Hàm lượng")).not.toBeInTheDocument();
});
it("TreatmentScreen renders issued prescription read-only", async () => {
  prescription = { ...dto, status: "ISSUED", issuedAt: "2026-11-06T00:00:00Z" };
  workspace(<TreatmentScreen />);
  expect(await screen.findByText("Đã phát hành")).toBeInTheDocument();
  expect(screen.getByLabelText("Tên thuốc")).toBeDisabled();
  expect(
    screen.queryByRole("button", { name: /Phát hành/ }),
  ).not.toBeInTheDocument();
});
it("live readiness uses persisted Clinical orders and cannot mutate Visit", async () => {
  workspace(<ClosingScreen />);
  expect(
    await screen.findByText(
      /Phần khám còn điều kiện chưa đạt hoặc chưa kiểm tra được/,
    ),
  ).toBeInTheDocument();
  expect(
    screen.queryByText(/^Phần khám đã đủ điều kiện/),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: /Hoàn tất khám/ }),
  ).not.toBeInTheDocument();
  expect(
    request.mock.calls.some(
      ([, init]) => init?.method && init.method !== "GET",
    ),
  ).toBe(false);
});
it("ready demo checklist hands closure to Receptionist and leaves Visit open", async () => {
  window.localStorage.setItem("otcms.doctorDemo", "1");
  await doctorDemoApi.saveExamination(
    "demo-visit-1",
    {
      reasonForVisit: "Pain",
      symptoms: "Pain",
      examinationFindings: "Finding",
      injuryBodySite: "",
      injurySide: "",
      injuryMechanism: "",
      diagnoses: [{ code: "M54.5", type: "PRIMARY" }],
    },
    "complete",
  );
  render(
    <VisitWorkspaceProvider visitId="demo-visit-1">
      <ClosingScreen />
    </VisitWorkspaceProvider>,
  );
  expect(
    await screen.findByText(/^Phần khám đã đủ điều kiện\. Lễ tân/),
  ).toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: /Hoàn tất khám/ }),
  ).not.toBeInTheDocument();
  expect((await doctorDemoApi.getVisitContext("demo-visit-1")).status).not.toBe(
    "COMPLETED",
  );
});
