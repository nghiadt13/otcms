import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import { doctorDemoApi, resetDoctorDemo } from "../api/doctorExamDemoApi";
import { ClosingScreen } from "./ClosingScreen";
import { ExaminationForm } from "./ExaminationForm";
import { TodayPatientsScreen } from "./TodayPatientsScreen";
import { VisitPatientHeader } from "./VisitPatientHeader";
import { VisitWorkspaceProvider } from "./VisitWorkspaceProvider";

// Màn hình chạy thật trên bản demo của DoctorExamApi; chỉ bọc ranh giới ngoài (toast, điều hướng).
const notify = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
const router = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("@/lib/messages/notify", () => ({
  notifySuccess: notify.success,
  notifyError: notify.error,
  notifyWarning: vi.fn(),
}));
vi.mock("next/navigation", () => ({ useRouter: () => router }));

beforeEach(() => {
  window.localStorage.setItem("otcms.doctorDemo", "1");
  resetDoctorDemo();
  notify.success.mockReset();
  notify.error.mockReset();
  router.push.mockReset();
});

describe("Bệnh nhân hôm nay", () => {
  it("xếp theo nhóm trạng thái rồi số thứ tự, đếm theo trạng thái, và khóa nút mở khi chưa check-in", async () => {
    render(<TodayPatientsScreen />);
    const table = await screen.findByRole("table");
    const rows = within(table).getAllByRole("row").slice(1);
    // Cột: STT, Giờ, Bệnh nhân, ...
    const names = rows.map(
      (row) => within(row).getAllByRole("cell")[2].textContent,
    );
    expect(names[0]).toContain("Trần Thị Bích"); // đang khám
    expect(names[1]).toContain("Lê Quang Huy"); // chờ kết luận
    expect(names[2]).toContain("Nguyễn Văn An"); // chờ khám, số 3
    expect(names[3]).toContain("Đỗ Thanh Bình"); // chờ khám, số 4 (vãng lai)
    expect(names[4]).toContain("Hoàng Minh Tuấn"); // chưa đến
    expect(names[5]).toContain("Phạm Thu Hà"); // đã xong
    expect(within(rows[3]).getAllByRole("cell")[0]).toHaveTextContent("4");

    expect(
      screen.getByRole("button", { name: /Chờ khám\s*2/ }),
    ).toBeInTheDocument();
    const notArrived = within(table).getByText("Chờ lễ tân tiếp nhận");
    expect(notArrived.closest("tr")).toHaveTextContent("Hoàng Minh Tuấn");
    expect(
      within(table).getAllByRole("link", { name: /lượt khám/i }),
    ).toHaveLength(5);

    await userEvent.type(screen.getByLabelText("Tìm bệnh nhân"), "tran thi");
    expect(within(screen.getByRole("table")).getAllByRole("row")).toHaveLength(
      2,
    );
  });

  it("walk-in đang chờ có lối mở lượt khám", async () => {
    render(<TodayPatientsScreen />);
    const table = await screen.findByRole("table");
    const walkIn = within(table).getByText("Đỗ Thanh Bình").closest("tr");
    expect(
      within(walkIn!).getByRole("link", { name: /Mở lượt khám/ }),
    ).toHaveAttribute("href", "/doctor/visits/demo-visit-6");
  });
});

describe("Bắt đầu khám", () => {
  it("walk-in đang chờ bắt đầu được khám: báo MSG-43 và chuyển sang đang khám", async () => {
    render(
      <VisitWorkspaceProvider visitId="demo-visit-6">
        <VisitPatientHeader />
      </VisitWorkspaceProvider>,
    );
    await userEvent.click(
      await screen.findByRole("button", { name: "Bắt đầu khám" }),
    );
    await waitFor(() => expect(notify.success).toHaveBeenCalledWith("MSG-43"));
    expect(await screen.findByText("Đang khám")).toBeInTheDocument();
  });

  it("bác sĩ đang khám người khác: bắt đầu bị từ chối và báo MSG-42", async () => {
    const start = vi
      .spyOn(doctorDemoApi, "startExamination")
      .mockRejectedValueOnce(
        new ApiError(409, { errorCode: "VISIT_EXAM_STATE_CONFLICT" }),
      );
    render(
      <VisitWorkspaceProvider visitId="demo-visit-6">
        <VisitPatientHeader />
      </VisitWorkspaceProvider>,
    );
    await userEvent.click(
      await screen.findByRole("button", { name: "Bắt đầu khám" }),
    );
    await waitFor(() => expect(notify.error).toHaveBeenCalledOnce());
    expect(notify.error.mock.calls[0][0]).toMatchObject({ code: "MSG-42" });
    expect(notify.success).not.toHaveBeenCalled();
    start.mockRestore();
  });
});

describe("Ghi nhận khám (UC37)", () => {
  async function renderForm(visitId: string) {
    const initial = await doctorDemoApi.getExamination(visitId);
    const onSaved = vi.fn();
    render(
      <ExaminationForm
        visitId={visitId}
        initial={initial}
        locked={false}
        onSaved={onSaved}
      />,
    );
    return { onSaved };
  }

  it("hoàn thành khi thiếu thông tin báo lỗi từng trường và không gọi API", async () => {
    const save = vi.spyOn(doctorDemoApi, "saveExamination");
    await renderForm("demo-visit-1");
    await userEvent.click(
      screen.getByRole("button", { name: "Hoàn thành ghi nhận khám" }),
    );
    expect(
      await screen.findByText("Nhập lý do bệnh nhân đến khám."),
    ).toBeInTheDocument();
    expect(screen.getByText("Nhập triệu chứng.")).toBeInTheDocument();
    expect(screen.getByText("Nhập phát hiện khi khám.")).toBeInTheDocument();
    expect(
      screen.getByText("Chọn đúng một chẩn đoán chính."),
    ).toBeInTheDocument();
    expect(save).not.toHaveBeenCalled();
    expect(notify.error).toHaveBeenCalledOnce();
  });

  it("lưu nháp không đòi gì, báo MSG-44 và gọi onSaved", async () => {
    const save = vi.spyOn(doctorDemoApi, "saveExamination");
    const { onSaved } = await renderForm("demo-visit-1");
    await userEvent.type(screen.getByLabelText("Triệu chứng"), "Đau gối");
    await userEvent.click(screen.getByRole("button", { name: "Lưu nháp" }));
    await waitFor(() => expect(onSaved).toHaveBeenCalled());
    expect(save).toHaveBeenCalledWith(
      "demo-visit-1",
      expect.objectContaining({ symptoms: "Đau gối", diagnoses: [] }),
      "draft",
    );
    expect(notify.success).toHaveBeenCalledWith("MSG-44");
  });

  it("lượt đã hoàn tất chỉ xem: mọi nút lưu bị khóa", async () => {
    const initial = await doctorDemoApi.getExamination("demo-visit-4");
    render(
      <ExaminationForm
        visitId="demo-visit-4"
        initial={initial}
        locked
        onSaved={vi.fn()}
      />,
    );
    expect(screen.getByRole("button", { name: "Lưu nháp" })).toBeDisabled();
    expect(
      screen.getByRole("button", { name: "Hoàn thành ghi nhận khám" }),
    ).toBeDisabled();
    expect(screen.getByText(/Lượt khám đã hoàn tất/)).toBeInTheDocument();
  });
});

describe("Điều kiện hoàn tất và kết thúc lượt (BR-33)", () => {
  function renderClosing(visitId: string) {
    render(
      <VisitWorkspaceProvider visitId={visitId}>
        <ClosingScreen />
      </VisitWorkspaceProvider>,
    );
  }

  it("còn chỉ định chưa xong thì nêu rõ điều kiện thiếu; nút kết thúc có nhưng bị khóa", async () => {
    renderClosing("demo-visit-3");
    expect(
      await screen.findByText(/1 chỉ định chưa xong: MRI khớp gối/),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Kết thúc lượt khám" }),
    ).toBeDisabled();
  });

  it("lượt chưa bắt đầu khám không có nút kết thúc", async () => {
    renderClosing("demo-visit-1");
    await screen.findByRole("status");
    expect(
      screen.queryByRole("button", { name: "Kết thúc lượt khám" }),
    ).not.toBeInTheDocument();
  });

  it("khi điều kiện đạt: xác nhận, báo MSG-43, lượt chuyển sang đã xong và về danh sách hôm nay", async () => {
    const [mri] = await doctorDemoApi.listOrders("demo-visit-3");
    await doctorDemoApi.cancelOrder(mri, "CLINIC_UNABLE", "Máy đang bảo trì");
    renderClosing("demo-visit-3");
    await userEvent.click(
      await screen.findByRole("button", { name: "Kết thúc lượt khám" }),
    );
    const dialog = await screen.findByRole("alertdialog");
    await userEvent.click(
      within(dialog).getByRole("button", { name: "Kết thúc lượt khám" }),
    );
    await waitFor(() =>
      expect(router.push).toHaveBeenCalledWith("/doctor/today"),
    );
    expect(notify.success).toHaveBeenCalledWith("MSG-43");
    expect((await doctorDemoApi.getVisitContext("demo-visit-3")).status).toBe(
      "COMPLETED",
    );
  });

  it("kết thúc bị từ chối (VISIT_COMPLETION_NOT_ALLOWED) báo MSG-42, không chuyển trang", async () => {
    const [mri] = await doctorDemoApi.listOrders("demo-visit-3");
    await doctorDemoApi.cancelOrder(mri, "CLINIC_UNABLE", "Máy đang bảo trì");
    const finish = vi
      .spyOn(doctorDemoApi, "finishVisit")
      .mockRejectedValueOnce(
        new ApiError(409, { errorCode: "VISIT_COMPLETION_NOT_ALLOWED" }),
      );
    renderClosing("demo-visit-3");
    await userEvent.click(
      await screen.findByRole("button", { name: "Kết thúc lượt khám" }),
    );
    const dialog = await screen.findByRole("alertdialog");
    await userEvent.click(
      within(dialog).getByRole("button", { name: "Kết thúc lượt khám" }),
    );
    await waitFor(() => expect(notify.error).toHaveBeenCalledOnce());
    expect(notify.error.mock.calls[0][0]).toMatchObject({ code: "MSG-42" });
    expect(router.push).not.toHaveBeenCalled();
    finish.mockRestore();
  });

  it("lượt đã hoàn tất chỉ hiển thị trạng thái, không có nút kết thúc hay phiếu kết quả giả", async () => {
    renderClosing("demo-visit-4");
    expect(
      await screen.findByText("Lượt khám đã hoàn tất."),
    ).toBeInTheDocument();
    expect(screen.queryByText("Phiên bản 1")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Kết thúc lượt khám" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Lưu sửa đổi" }),
    ).not.toBeInTheDocument();
  });
});
