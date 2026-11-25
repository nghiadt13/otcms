import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { toast } from "sonner";
import { ApiError } from "@/lib/api/apiError";
import { formatAppMessage } from "@/lib/messages/appMessages";
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
      id: "item-1",
      status: "PENDING",
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
    if (path === "/api/v1/reception/patients/pat-1/return-candidate")
      return undefined;
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
      return { id: "pay-1" };
    }
    if (path === "/api/v1/payments/pay-1/receipt")
      return {
        receiptNumber: "RC-1",
        patientName: "Nguyễn Minh Anh",
        invoiceNumber: "OTC-1",
        confirmedAt: "2026-11-11T08:00:00Z",
        channel: "COUNTER",
        amount: 250000,
        items: [],
      };
    if (path === "/api/v1/reception/walk-in-suggestions")
      return {
        items: [
          {
            doctorProfileId: "doc-1",
            doctorName: "BS Nguyễn An",
            roomId: "room-101",
            roomCode: "101",
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
      return { id: "pay-1" };
    if (path === "/api/v1/visits/v-walk/exam-queue-entry") return walkInEntry;
    throw new Error(`Unexpected request ${method} ${path}`);
  });
});

function renderDialog(
  props: {
    patientId?: string;
    appointmentId?: string;
    onUpdated?: () => void;
  } = {},
) {
  return render(
    <ReceptionProvider>
      <CheckInDialog
        patientId={props.patientId ?? "pat-1"}
        appointmentId={props.appointmentId}
        onClose={vi.fn()}
        onUpdated={props.onUpdated ?? vi.fn()}
      />
    </ReceptionProvider>,
  );
}

describe("CheckInDialog (tiếp nhận tại quầy)", () => {
  const candidate = {
    visitId: "v-return",
    firstCheckInAt: "2026-11-11T08:00:00+07:00",
    returnDeadline: "2026-11-18",
    doctorProfileId: "doc-1",
    doctorName: "BS Nguyễn An",
    roomId: "room-101",
    roomLabel: "Phòng 101",
  };

  it("tiếp nhận lại lượt chờ kết luận và làm mới hàng chờ", async () => {
    const user = userEvent.setup();
    const updated = vi.fn();
    const base = request.getMockImplementation()!;
    request.mockImplementation(async (path: string, init?: RequestInit) => {
      if (path.endsWith("/return-candidate")) return candidate;
      if (path === "/api/v1/visits/v-return/returns")
        return { messageCode: "MSG-43", data: {} };
      return base(path, init);
    });
    renderDialog({ onUpdated: updated });
    await user.click(
      screen.getByRole("button", { name: "Xác minh hồ sơ bệnh nhân" }),
    );
    expect(
      await screen.findByText("Lượt khám đang chờ kết luận"),
    ).toBeInTheDocument();
    expect(screen.getByText(/Hạn quay lại: 18\/11\/2026/)).toBeInTheDocument();
    await user.click(
      screen.getByRole("button", { name: "Quay lại lượt khám" }),
    );
    await waitFor(() =>
      expect(toast.success).toHaveBeenCalledWith(
        "Thông tin lượt khám đã được cập nhật.",
      ),
    );
    expect(request).toHaveBeenCalledWith("/api/v1/visits/v-return/returns", {
      method: "POST",
    });
    expect(updated).toHaveBeenCalledOnce();
    expect(
      request.mock.calls.some(([path]) => String(path).includes("/invoices")),
    ).toBe(false);
  });

  it("không hiện quay lại khi không có lượt phù hợp", async () => {
    const user = userEvent.setup();
    renderDialog();
    await user.click(
      screen.getByRole("button", { name: "Xác minh hồ sơ bệnh nhân" }),
    );
    await waitFor(() =>
      expect(request).toHaveBeenCalledWith(
        "/api/v1/reception/patients/pat-1/return-candidate",
        expect.anything(),
      ),
    );
    expect(
      screen.queryByRole("button", { name: "Quay lại lượt khám" }),
    ).not.toBeInTheDocument();
  });

  it("hiện MSG-42 khi lượt không còn được quay lại", async () => {
    const user = userEvent.setup();
    const base = request.getMockImplementation()!;
    request.mockImplementation(async (path: string, init?: RequestInit) => {
      if (path.endsWith("/return-candidate")) return candidate;
      if (path === "/api/v1/visits/v-return/returns")
        throw new ApiError(409, { code: "MSG-42" });
      return base(path, init);
    });
    renderDialog();
    await user.click(
      screen.getByRole("button", { name: "Xác minh hồ sơ bệnh nhân" }),
    );
    await user.click(
      await screen.findByRole("button", { name: "Quay lại lượt khám" }),
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Không thể thực hiện thao tác này với trạng thái hiện tại.",
    );
    expect(toast.success).not.toHaveBeenCalled();
  });
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
    expect(screen.getByText(formatAppMessage("MSG-94"))).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Thu phí khám" }));
    const dialog = await screen.findByRole("dialog", { name: "Thu phí khám" });
    await user.click(
      within(dialog).getByRole("checkbox", { name: /Đã nhận tiền/ }),
    );
    await user.click(
      within(dialog).getByRole("button", { name: "Xác nhận thu tiền" }),
    );

    const receipt = await screen.findByRole("dialog", {
      name: "Biên lai thu tiền",
    });
    expect(await within(receipt).findByText("RC-1")).toBeInTheDocument();
    await user.keyboard("{Escape}");

    await waitFor(() =>
      expect(request).toHaveBeenCalledWith(
        "/api/v1/invoices/inv-1/counter-payments",
        expect.objectContaining({
          method: "POST",
          body: JSON.stringify({ itemIds: ["item-1"], method: "CASH" }),
        }),
      ),
    );
    await waitFor(() =>
      expect(
        screen.getByRole("button", { name: "Check-in lịch hẹn" }),
      ).toBeEnabled(),
    );
  });

  it.each([
    [
      new ApiError(409, { code: "MSG-94" }),
      "Bạn chưa thanh toán phí khám nên chưa thể tiếp nhận.",
    ],
    [
      new ApiError(409, { errorCode: "PATIENT_HAS_OPEN_VISIT" }),
      "Bệnh nhân đã có lượt khám đang mở. Vui lòng vào mục Tiến trình lượt khám để tìm và tiếp tục xử lý lượt khám hiện tại.",
    ],
  ])("hiển thị hướng dẫn khi check-in lịch hẹn bị từ chối (%s)", async (error, message) => {
    const user = userEvent.setup();
    feePaid = true;
    appointments = [appointment({ examFeePaid: true })];
    const base = request.getMockImplementation()!;
    request.mockImplementation(async (path: string, init?: RequestInit) => {
      if (path === "/api/v1/appointments/apt-1/check-in")
        throw error;
      return base(path, init);
    });
    renderDialog({ appointmentId: "apt-1" });

    await user.click(
      await screen.findByRole("button", { name: "Xác minh hồ sơ bệnh nhân" }),
    );
    await user.click(
      await screen.findByRole("button", { name: "Check-in lịch hẹn" }),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(message);
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
    await user.click(
      within(dialog).getByRole("checkbox", { name: /Đã nhận tiền/ }),
    );
    await user.click(
      within(dialog).getByRole("button", { name: "Xác nhận thu tiền" }),
    );

    const receipt = await screen.findByRole("dialog", {
      name: "Biên lai thu tiền",
    });
    expect(await within(receipt).findByText("RC-1")).toBeInTheDocument();
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
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ itemIds: ["item-1"], method: "CASH" }),
      }),
    );
    expect(request).toHaveBeenCalledWith(
      "/api/v1/visits/v-walk/exam-queue-entry",
    );
  });

  it.each([
    [
      new ApiError(409, { errorCode: "PATIENT_HAS_OPEN_VISIT" }),
      "Bệnh nhân đã có lượt khám đang mở. Vui lòng vào mục Tiến trình lượt khám để tìm và tiếp tục xử lý lượt khám hiện tại.",
    ],
    [
      new ApiError(503, { errorCode: "DEPENDENCY_UNAVAILABLE" }),
      formatAppMessage("MSG-04"),
    ],
  ])("walk-in hiển thị đúng thông báo khi API trả lỗi %s", async (error, message) => {
    const user = userEvent.setup();
    const base = request.getMockImplementation()!;
    request.mockImplementation(async (path: string, init?: RequestInit) => {
      if (path === "/api/v1/visits/walk-ins" && init?.method === "POST") {
        throw error;
      }
      return base(path, init);
    });
    renderDialog();

    await user.click(
      screen.getByRole("button", { name: "Xác minh hồ sơ bệnh nhân" }),
    );
    const service = await screen.findByLabelText("Dịch vụ khám");
    await waitFor(() =>
      expect(within(service).getByRole("option", { name: /Khám/ })).toBeInTheDocument(),
    );
    await user.selectOptions(service, "svc-kham");
    await user.click(
      screen.getByRole("button", { name: "Tạo lượt khám trực tiếp" }),
    );

    expect(await screen.findByText(message)).toHaveAttribute("role", "alert");
    expect(screen.queryByRole("dialog", { name: "Thu phí khám" })).toBeNull();
    expect(toast.success).not.toHaveBeenCalled();
  });

  it("walk-in chọn sẵn gợi ý đầu, hiện số chờ và cho đổi bác sĩ", async () => {
    const user = userEvent.setup();
    const base = request.getMockImplementation()!;
    request.mockImplementation(async (path: string, init?: RequestInit) => {
      if (path === "/api/v1/reception/walk-in-suggestions")
        return {
          items: [
            {
              roomId: "r1",
              roomCode: "101",
              roomLabel: "Phòng 101",
              doctorProfileId: "doc-1",
              doctorName: "BS Nguyễn An",
              waitingCount: 2,
            },
            {
              roomId: "r2",
              roomCode: "102",
              roomLabel: "Phòng 102",
              doctorProfileId: "doc-2",
              doctorName: "BS Bình",
              waitingCount: 5,
            },
          ],
        };
      return base(path, init);
    });
    renderDialog();
    await user.click(
      screen.getByRole("button", { name: "Xác minh hồ sơ bệnh nhân" }),
    );
    const doctor = await screen.findByLabelText("Bác sĩ đang trong ca");
    await waitFor(() => expect(doctor).toHaveValue("doc-1"));
    expect(
      within(doctor).getByRole("option", { name: /Phòng 101 · 2 đang chờ/ }),
    ).toBeInTheDocument();
    await user.selectOptions(doctor, "doc-2");
    await user.selectOptions(screen.getByLabelText("Dịch vụ khám"), "svc-kham");
    await user.click(
      screen.getByRole("button", { name: "Tạo lượt khám trực tiếp" }),
    );
    expect(request).toHaveBeenCalledWith(
      "/api/v1/visits/walk-ins",
      expect.objectContaining({
        body: expect.stringContaining('"doctorProfileId":"doc-2"'),
      }),
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
      if (path === "/api/v1/reception/walk-in-suggestions")
        return { items: [] };
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
