import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { doctorDemoApi, resetDoctorDemo } from "../api/doctorExamDemoApi";
import { ClosingScreen } from "./ClosingScreen";
import { ExaminationForm } from "./ExaminationForm";
import { TodayPatientsScreen } from "./TodayPatientsScreen";
import { VisitWorkspaceProvider } from "./VisitWorkspaceProvider";

// Màn hình chạy thật trên bản demo của DoctorExamApi; chỉ bọc ranh giới ngoài (toast).
const notify = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock("@/lib/messages/notify", () => ({
  notifySuccess: notify.success,
  notifyError: notify.error,
  notifyWarning: vi.fn(),
}));

beforeEach(() => {
  window.localStorage.setItem("otcms.doctorDemo", "1");
  resetDoctorDemo();
  notify.success.mockReset();
  notify.error.mockReset();
});

describe("Bệnh nhân hôm nay", () => {
  it("liệt kê bệnh nhân theo giờ, đếm theo trạng thái, và khóa nút mở khi chưa check-in", async () => {
    render(<TodayPatientsScreen />);
    const table = await screen.findByRole("table");
    const names = within(table)
      .getAllByRole("row")
      .slice(1)
      .map((row) => within(row).getAllByRole("cell")[1].textContent);
    expect(names[0]).toContain("Lê Quang Huy"); // 07:45 sớm nhất
    expect(names.at(-1)).toContain("Hoàng Minh Tuấn"); // 10:30 muộn nhất

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

describe("Hoàn tất lượt khám (UC36)", () => {
  function renderClosing(visitId: string) {
    render(
      <VisitWorkspaceProvider visitId={visitId}>
        <ClosingScreen />
      </VisitWorkspaceProvider>,
    );
  }

  it("còn chỉ định chưa xong thì nêu rõ chỉ định nào và khóa nút hoàn tất", async () => {
    renderClosing("demo-visit-3");
    expect(
      await screen.findByText(/1 chỉ định chưa xong: MRI khớp gối/),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Hoàn tất khám/ }),
    ).toBeDisabled();
  });

  it("lượt đã hoàn tất hiện phiếu kết quả và khóa nút hoàn tất", async () => {
    renderClosing("demo-visit-4");
    expect(
      await screen.findByText("Lượt khám đã hoàn tất."),
    ).toBeInTheDocument();
    expect(await screen.findByText("Phiên bản 1")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Hoàn tất khám/ }),
    ).toBeDisabled();
    expect(screen.getByRole("button", { name: "Lưu sửa đổi" })).toBeDisabled(); // chưa có lý do
  });
});
