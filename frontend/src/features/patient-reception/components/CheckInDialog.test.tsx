import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { toast } from "sonner";
import { ApiError } from "@/lib/api/apiError";
import { ReceptionProvider } from "./ReceptionProvider";
import { CheckInDialog } from "./CheckInDialog";

const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock("sonner", () => ({
  toast: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
}));

// Trạng thái giả của backend cho một bệnh nhân: lịch hẹn hôm nay và việc thu phí tại quầy.
let appointments: Record<string, unknown>[] = [];
let feePaid = false;

const invoice = {
  id: "inv-1",
  invoiceNumber: "OTC-1",
  appointmentId: "apt-1",
  visitId: null,
  status: "OPEN",
  totalAmount: 250000,
  paidAmount: 0,
  outstandingBalance: 250000,
  issuedAt: "2026-11-11T08:00:00+07:00",
  items: [
    {
      lineNumber: 1,
      description: "Khám nội",
      quantity: 1,
      unitPrice: 250000,
      lineTotal: 250000,
    },
  ],
};

const appointment = (overrides: Record<string, unknown> = {}) => ({
  id: "apt-1",
  patientProfileId: "pat-1",
  patientName: "Nguyễn Minh Anh",
  serviceId: "svc-kham",
  serviceName: "Khám nội",
  servicePrice: 250000,
  doctorId: "doc-1",
  doctorDisplayName: "BS Nguyễn An",
  status: "CONFIRMED",
  purpose: null,
  startTime: "2026-11-11T08:30:00+07:00",
  visitId: null,
  examFeePaid: feePaid,
  ...overrides,
});

const walkInVisit = {
  visitId: "v-walk",
  patientProfileId: "pat-1",
  priorAppointmentId: null,
  arrivedAt: "2026-11-11T09:00:00+07:00",
  examStatus: "PENDING_PAYMENT",
  queueNumber: null,
  doctorName: "BS Nguyễn An",
  roomLabel: "Phòng 101",
  feeAmount: 250000,
};

const walkInEntry = {
  visitId: "v-walk",
  queueNumber: 4,
  examStatus: "WAITING",
  source: "WALK_IN",
  appointmentId: null,
  patientProfileId: "pat-1",
  patientName: "Nguyễn Minh Anh",
  doctorProfileId: "doc-1",
  doctorName: "BS Nguyễn An",
  roomId: "room-102",
  roomLabel: "Phòng 102",
  serviceId: "svc-kham",
  queuedAt: "2026-11-11T09:10:00+07:00",
  startedAt: null,
  finishedAt: null,
};

beforeEach(() => {
  vi.clearAllMocks();
  appointments = [];
  feePaid = false;
  request.mockReset();
  request.mockImplementation(async (path: string, init?: RequestInit) => {
    const method = init?.method ?? "GET";
    if (path.startsWith("/api/v1/appointments?"))
      return {
        // Trạng thái thu phí lấy theo lúc gọi: sau khi thu tiền, lần tải lại thấy đã trả.
        items: appointments.map((item) => ({ ...item, examFeePaid: feePaid })),
        page: 0,
        size: 100,
        totalElements: appointments.length,
        totalPages: 1,
      };
    if (path.startsWith("/api/v1/appointment-arrivals?"))
      return {
        date: "2026-11-11",
        page: 0,
        size: 100,
        hasMore: false,
        items: [],
      };
    if (path === "/api/v1/patient-profiles/pat-1/reception-verification")
      return {
        patientProfileId: "pat-1",
        fullName: "Nguyễn Minh Anh",
        dateOfBirth: "1990-01-01",
      };
    if (path === "/api/v1/appointments/apt-1/check-in" && method === "POST")
      return {
        appointmentId: "apt-1",
        visitId: "v-1",
        checkedInAt: "2026-11-11T08:20:00+07:00",
        queueNumber: 3,
        doctorName: "BS Nguyễn An",
        roomLabel: "Phòng 101",
      };
    if (path === "/api/v1/appointments/apt-1/invoices" && method === "POST")
      return invoice;
    if (
      path === "/api/v1/invoices/inv-1/counter-payments" &&
      method === "POST"
    ) {
      feePaid = true;
      return {};
    }
    if (path === "/api/v1/reception/on-shift-doctors")
      return {
        items: [
          {
            doctorProfileId: "doc-1",
            doctorName: "BS Nguyễn An",
            roomId: "room-101",
            roomLabel: "Phòng 101",
            waitingCount: 2,
          },
        ],
      };
    if (path === "/api/v1/staff/clinic-services")
      return {
        items: [
          {
            id: "svc-kham",
            name: "Khám nội",
            type: "EXAMINATION",
            price: 250000,
            active: true,
          },
          {
            id: "svc-xq",
            name: "X-quang",
            type: "IMAGING",
            price: 150000,
            active: true,
          },
        ],
      };
    if (path === "/api/v1/visits/walk-ins" && method === "POST")
      return walkInVisit;
    if (path === "/api/v1/visits/v-walk/invoices" && method === "POST")
      return {
        ...invoice,
        id: "inv-walk",
        visitId: "v-walk",
        appointmentId: null,
      };
    if (
      path === "/api/v1/invoices/inv-walk/counter-payments" &&
      method === "POST"
    )
      return {};
    if (path === "/api/v1/visits/v-walk/exam-queue-entry") return walkInEntry;
    throw new Error(`Unexpected request ${method} ${path}`);
  });
});

function renderDialog(
  props: { patientId?: string; appointmentId?: string } = {},
) {
  return render(
    <ReceptionProvider>
      <CheckInDialog
        patientId={props.patientId ?? "pat-1"}
        appointmentId={props.appointmentId}
        onClose={vi.fn()}
        onUpdated={vi.fn()}
      />
    </ReceptionProvider>,
  );
}

describe("CheckInDialog (tiếp nhận tại quầy)", () => {
  it("check-in lịch đã thu phí hiện MSG-88 với số thứ tự và phòng", async () => {
    const user = userEvent.setup();
    feePaid = true;
    appointments = [appointment({ examFeePaid: true })];
    renderDialog({ appointmentId: "apt-1" });

    await user.click(
      await screen.findByRole("button", { name: "Xác minh hồ sơ bệnh nhân" }),
    );
    await user.click(
      await screen.findByRole("button", { name: "Check-in lịch hẹn" }),
    );

    await waitFor(() =>
      expect(toast.success).toHaveBeenCalledWith(
        "Đã tiếp nhận bệnh nhân. Số thứ tự 3, phòng Phòng 101.",
      ),
    );
    expect(request).toHaveBeenCalledWith(
      "/api/v1/appointments/apt-1/check-in",
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("phí khám chưa trả: nút check-in khoá tới khi thu tiền tại quầy xong", async () => {
    const user = userEvent.setup();
    appointments = [appointment({ examFeePaid: false })];
    renderDialog({ appointmentId: "apt-1" });

    await user.click(
      await screen.findByRole("button", { name: "Xác minh hồ sơ bệnh nhân" }),
    );
    expect(
      await screen.findByRole("button", { name: "Check-in lịch hẹn" }),
    ).toBeDisabled();

    await user.click(screen.getByRole("button", { name: "Thu phí khám" }));
    const dialog = await screen.findByRole("dialog", { name: "Thu phí khám" });
    await user.click(within(dialog).getByRole("checkbox"));
    await user.click(
      within(dialog).getByRole("button", { name: "Xác nhận thu tiền" }),
    );

    await waitFor(() =>
      expect(request).toHaveBeenCalledWith(
        "/api/v1/invoices/inv-1/counter-payments",
        expect.objectContaining({ method: "POST" }),
      ),
    );
    await waitFor(() =>
      expect(
        screen.getByRole("button", { name: "Check-in lịch hẹn" }),
      ).toBeEnabled(),
    );
  });

  it("check-in bị backend từ chối vì chưa thu phí hiện MSG-37", async () => {
    const user = userEvent.setup();
    feePaid = true;
    appointments = [appointment({ examFeePaid: true })];
    const base = request.getMockImplementation()!;
    request.mockImplementation(async (path: string, init?: RequestInit) => {
      if (path === "/api/v1/appointments/apt-1/check-in")
        throw new ApiError(409, { errorCode: "EXAM_FEE_UNPAID" });
      return base(path, init);
    });
    renderDialog({ appointmentId: "apt-1" });

    await user.click(
      await screen.findByRole("button", { name: "Xác minh hồ sơ bệnh nhân" }),
    );
    await user.click(
      await screen.findByRole("button", { name: "Check-in lịch hẹn" }),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Không thể tiếp nhận lượt khám này.",
    );
    expect(toast.success).not.toHaveBeenCalled();
  });

  it("walk-in: chọn bác sĩ và dịch vụ, thu phí tại quầy, rồi hiện số thứ tự", async () => {
    const user = userEvent.setup();
    renderDialog();

    await user.click(
      await screen.findByRole("button", { name: "Xác minh hồ sơ bệnh nhân" }),
    );
    const doctor = await screen.findByLabelText("Bác sĩ đang trong ca");
    await waitFor(() =>
      expect(
        within(doctor).getByRole("option", { name: /BS Nguyễn An/ }),
      ).toBeInTheDocument(),
    );
    await user.selectOptions(doctor, "doc-1");
    await user.selectOptions(screen.getByLabelText("Dịch vụ khám"), "svc-kham");
    await user.click(
      screen.getByRole("button", { name: "Tạo lượt khám trực tiếp" }),
    );

    const dialog = await screen.findByRole("dialog", { name: "Thu phí khám" });
    await user.click(within(dialog).getByRole("checkbox"));
    await user.click(
      within(dialog).getByRole("button", { name: "Xác nhận thu tiền" }),
    );

    await waitFor(() =>
      expect(toast.success).toHaveBeenCalledWith(
        "Đã tiếp nhận bệnh nhân. Số thứ tự 4, phòng Phòng 102.",
      ),
    );
    expect(request).toHaveBeenCalledWith(
      "/api/v1/visits/walk-ins",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({
          patientProfileId: "pat-1",
          doctorProfileId: "doc-1",
          serviceId: "svc-kham",
        }),
      }),
    );
    expect(request).toHaveBeenCalledWith(
      "/api/v1/visits/v-walk/invoices",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({
          items: [{ serviceId: "svc-kham", quantity: 1 }],
        }),
      }),
    );
    expect(request).toHaveBeenCalledWith(
      "/api/v1/invoices/inv-walk/counter-payments",
      expect.objectContaining({ method: "POST" }),
    );
    expect(request).toHaveBeenCalledWith(
      "/api/v1/visits/v-walk/exam-queue-entry",
    );
  });

  it("lập hóa đơn lỗi: hiện MSG-04, thử lại dùng cùng Idempotency-Key", async () => {
    const user = userEvent.setup();
    let invoiceCalls = 0;
    const base = request.getMockImplementation()!;
    request.mockImplementation(async (path: string, init?: RequestInit) => {
      if (
        path === "/api/v1/visits/v-walk/invoices" &&
        init?.method === "POST"
      ) {
        invoiceCalls += 1;
        if (invoiceCalls === 1) throw new ApiError(500, {});
      }
      return base(path, init);
    });
    renderDialog();

    await user.click(
      await screen.findByRole("button", { name: "Xác minh hồ sơ bệnh nhân" }),
    );
    const doctor = await screen.findByLabelText("Bác sĩ đang trong ca");
    await waitFor(() =>
      expect(
        within(doctor).getByRole("option", { name: /BS Nguyễn An/ }),
      ).toBeInTheDocument(),
    );
    await user.selectOptions(doctor, "doc-1");
    await user.selectOptions(screen.getByLabelText("Dịch vụ khám"), "svc-kham");
    await user.click(
      screen.getByRole("button", { name: "Tạo lượt khám trực tiếp" }),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Không thể hoàn tất yêu cầu.",
    );
    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(
      await screen.findByRole("dialog", { name: "Thu phí khám" }),
    ).toBeInTheDocument();

    const keys = request.mock.calls
      .filter(([path]) => path === "/api/v1/visits/v-walk/invoices")
      .map(
        ([, init]) => (init as RequestInit).headers as Record<string, string>,
      )
      .map((headers) => headers["Idempotency-Key"]);
    expect(keys).toHaveLength(2);
    expect(keys[0]).toBeTruthy();
    expect(keys[1]).toBe(keys[0]);
  });

  it("không có bác sĩ trong ca: hiện MSG-04 và khoá nút tạo walk-in", async () => {
    const user = userEvent.setup();
    const base = request.getMockImplementation()!;
    request.mockImplementation(async (path: string, init?: RequestInit) => {
      if (path === "/api/v1/reception/on-shift-doctors") return { items: [] };
      return base(path, init);
    });
    renderDialog();

    await user.click(
      await screen.findByRole("button", { name: "Xác minh hồ sơ bệnh nhân" }),
    );
    expect(
      await screen.findByText(
        "Không thể hoàn tất yêu cầu. Vui lòng thử lại sau hoặc bắt đầu lại.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Tạo lượt khám trực tiếp" }),
    ).toBeDisabled();
  });
});
