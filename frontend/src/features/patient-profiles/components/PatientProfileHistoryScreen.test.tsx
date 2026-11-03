import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { toast } from "sonner";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "@/lib/api/client";
import { ApiError } from "@/lib/api/apiError";
import { AuthSessionError } from "@/lib/auth/authSession";
import { formatAppMessage } from "@/lib/messages/appMessages";
import {
  MedicalHistoryTestServer,
  HISTORY_PROFILE_ID,
  HISTORY_DEPENDENT_ID,
  HISTORY_PATH,
  emptyHistory,
} from "../api/medicalHistoryTestServer";
import { MEDICAL_HISTORY_SECTIONS } from "../schema/medicalHistoryRules";
import { PatientProfileHistoryScreen } from "./PatientProfileHistoryScreen";

vi.mock("@/lib/api/client", () => ({ apiRequest: vi.fn() }));
vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}));
let server: MedicalHistoryTestServer;

beforeEach(() => {
  vi.resetAllMocks();
  server = new MedicalHistoryTestServer();
  vi.mocked(apiRequest).mockImplementation(server.request);
});
const putCalls = () =>
  vi.mocked(apiRequest).mock.calls.filter(([, init]) => init?.method === "PUT");
const editAllergies = (value: string) =>
  fireEvent.change(screen.getByLabelText("Dị ứng"), { target: { value } });

describe("PatientProfileHistoryScreen — API thật tại ranh giới apiRequest", () => {
  it("hồ sơ thật không có trong mock vẫn tải được, đúng tên/quan hệ và AbortSignal", async () => {
    render(<PatientProfileHistoryScreen profileId={HISTORY_PROFILE_ID} />);
    expect(await screen.findByLabelText("Dị ứng")).toHaveValue("Penicillin.");
    expect(screen.getByText("Hồ sơ thật · Bản thân")).toBeInTheDocument();
    expect(
      screen.getByText("Cập nhật lần cuối 20/10/2026 bởi Bác sĩ điều trị"),
    ).toBeInTheDocument();
    expect(apiRequest).toHaveBeenCalledWith(HISTORY_PATH, {
      signal: expect.any(AbortSignal),
    });
    expect(screen.getByRole("button", { name: "Lưu Dị ứng" })).toBeDisabled();
  });

  it("lưu MSG-90, dùng nội dung/version trả về; đọc lại vẫn có, lần lưu sau gửi version mới", async () => {
    const user = userEvent.setup();
    const view = render(
      <PatientProfileHistoryScreen profileId={HISTORY_PROFILE_ID} />,
    );
    await screen.findByLabelText("Dị ứng");
    editAllergies("  Dị ứng mới  ");
    await user.click(screen.getByRole("button", { name: "Lưu Dị ứng" }));
    await waitFor(() =>
      expect(toast.success).toHaveBeenCalledWith(formatAppMessage("MSG-90")),
    );
    expect(screen.getByLabelText("Dị ứng")).toHaveValue("Dị ứng mới");
    expect(screen.getByText(/bởi Người cập nhật/)).toBeInTheDocument();
    expect(JSON.parse(String(putCalls()[0][1]?.body))).toEqual({
      conditions: "Đang theo dõi bệnh nền.",
      allergies: "Dị ứng mới",
      procedures: "",
      injuries: "",
      orthopedicHistory: "",
      version: 3,
    });
    view.unmount();
    render(<PatientProfileHistoryScreen profileId={HISTORY_PROFILE_ID} />);
    expect(await screen.findByLabelText("Dị ứng")).toHaveValue("Dị ứng mới");
    editAllergies("Dị ứng lần hai");
    await user.click(screen.getByRole("button", { name: "Lưu Dị ứng" }));
    await waitFor(() => expect(toast.success).toHaveBeenCalledTimes(2));
    expect(JSON.parse(String(putCalls()[1][1]?.body)).version).toBe(4);
  });

  it("version 0 -> 1, server sở hữu metadata", async () => {
    const user = userEvent.setup();
    server.history = emptyHistory();
    render(<PatientProfileHistoryScreen profileId={HISTORY_PROFILE_ID} />);
    await screen.findByLabelText("Dị ứng");
    expect(
      screen.getByText("Chưa có tiền sử nào được ghi."),
    ).toBeInTheDocument();
    editAllergies("Nội dung đầu tiên");
    await user.click(screen.getByRole("button", { name: "Lưu Dị ứng" }));
    await waitFor(() => expect(server.history.version).toBe(1));
    expect(JSON.parse(String(putCalls()[0][1]?.body)).version).toBe(0);
    editAllergies("Nội dung tiếp");
    await user.click(screen.getByRole("button", { name: "Lưu Dị ứng" }));
    await waitFor(() => expect(server.history.version).toBe(2));
    expect(JSON.parse(String(putCalls()[1][1]?.body)).version).toBe(1);
  });

  it.each([
    { name: "503", failure: new ApiError(503) },
    { name: "lỗi mạng", failure: new TypeError("Failed to fetch") },
    { name: "lỗi mạng khi lấy phiên", failure: new AuthSessionError(0) },
    { name: "503 khi lấy phiên", failure: new AuthSessionError(503) },
  ])(
    "hai tab cùng sửa: tab sau thấy MSG-16; reload $name vẫn stale; thành công mới mở khóa",
    async ({ failure }) => {
      const user = userEvent.setup();
      const first = render(
        <PatientProfileHistoryScreen profileId={HISTORY_PROFILE_ID} />,
      );
      const second = render(
        <PatientProfileHistoryScreen profileId={HISTORY_PROFILE_ID} />,
      );
      const a = within(first.container);
      const b = within(second.container);
      await a.findByLabelText("Dị ứng");
      await b.findByLabelText("Dị ứng");
      fireEvent.change(a.getByLabelText("Dị ứng"), {
        target: { value: "Tab A" },
      });
      await user.click(a.getByRole("button", { name: "Lưu Dị ứng" }));
      await waitFor(() => expect(toast.success).toHaveBeenCalledTimes(1));
      fireEvent.change(b.getByLabelText("Dị ứng"), {
        target: { value: "Tab B" },
      });
      await user.click(b.getByRole("button", { name: "Lưu Dị ứng" }));
      expect(
        await b.findByText(formatAppMessage("MSG-16")),
      ).toBeInTheDocument();
      expect(b.getByRole("button", { name: "Lưu Dị ứng" })).toBeDisabled();
      expect(server.history.allergies).toBe("Tab A");
      server.getError = failure;
      await user.click(b.getByRole("button", { name: "Tải lại" }));
      expect(
        await b.findByText(formatAppMessage("MSG-04")),
      ).toBeInTheDocument();
      expect(b.getByText(formatAppMessage("MSG-16"))).toBeInTheDocument();
      expect(b.getByLabelText("Dị ứng")).toHaveValue("Tab B");
      expect(b.getByRole("button", { name: "Lưu Dị ứng" })).toBeDisabled();
      expect(putCalls()).toHaveLength(2);
      server.getError = null;
      await user.click(b.getByRole("button", { name: "Tải lại" }));
      await waitFor(() =>
        expect(
          b.queryByText(formatAppMessage("MSG-16")),
        ).not.toBeInTheDocument(),
      );
      expect(b.getByLabelText("Dị ứng")).toHaveValue("Tab A");
      fireEvent.change(b.getByLabelText("Dị ứng"), {
        target: { value: "Tab B sau tải lại" },
      });
      await user.click(b.getByRole("button", { name: "Lưu Dị ứng" }));
      await waitFor(() => expect(server.history.version).toBe(5));
      expect(JSON.parse(String(putCalls()[2][1]?.body)).version).toBe(4);
    },
  );

  it.each(MEDICAL_HISTORY_SECTIONS)(
    "validation $field: >2000 UTF-16 units báo MSG-20, không PUT",
    async ({ label }) => {
      const user = userEvent.setup();
      render(<PatientProfileHistoryScreen profileId={HISTORY_PROFILE_ID} />);
      const input = await screen.findByLabelText(label);
      fireEvent.change(input, { target: { value: "😀".repeat(1000) + "x" } });
      await user.click(screen.getByRole("button", { name: `Lưu ${label}` }));
      expect(
        await screen.findByText(formatAppMessage("MSG-20")),
      ).toBeInTheDocument();
      expect(input).toHaveAttribute("aria-invalid", "true");
      expect(putCalls()).toHaveLength(0);
    },
  );

  it("đúng 2000 units sau trim vẫn lưu được, giữ newline nội bộ", async () => {
    const user = userEvent.setup();
    render(<PatientProfileHistoryScreen profileId={HISTORY_PROFILE_ID} />);
    await screen.findByLabelText("Dị ứng");
    const value = "😀".repeat(999) + "\na";
    editAllergies(" \t" + value + "\n ");
    await user.click(screen.getByRole("button", { name: "Lưu Dị ứng" }));
    await waitFor(() => expect(toast.success).toHaveBeenCalled());
    expect(JSON.parse(String(putCalls()[0][1]?.body)).allergies).toBe(value);
  });

  it.each([
    [400, "VALIDATION_FAILED", "MSG-20"],
    [403, "ACCESS_DENIED", "MSG-19"],
    [404, "PATIENT_PROFILE_NOT_FOUND", "MSG-19"],
  ] as const)(
    "PUT HTTP %s hiển thị %s, không báo thành công",
    async (status, errorCode, messageCode) => {
      const user = userEvent.setup();
      render(<PatientProfileHistoryScreen profileId={HISTORY_PROFILE_ID} />);
      await screen.findByLabelText("Dị ứng");
      server.putError = new ApiError(status, {
        errorCode,
        detail: "Backend message is not a MSG code",
      });
      editAllergies("Nội dung mới");
      await user.click(screen.getByRole("button", { name: "Lưu Dị ứng" }));
      expect(
        await screen.findByText(formatAppMessage(messageCode)),
      ).toBeInTheDocument();
      expect(toast.success).not.toHaveBeenCalled();
      if (status === 403 || status === 404)
        expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    },
  );

  it.each([
    [400, "VALIDATION_FAILED", "MSG-20"],
    [403, "ACCESS_DENIED", "MSG-19"],
    [404, "PATIENT_PROFILE_NOT_FOUND", "MSG-19"],
  ] as const)(
    "GET HTTP %s hiển thị đúng MSG và không có nội dung y tế",
    async (status, errorCode, messageCode) => {
      server.getError = new ApiError(status, { errorCode });
      render(<PatientProfileHistoryScreen profileId={HISTORY_PROFILE_ID} />);
      expect(
        await screen.findByText(formatAppMessage(messageCode)),
      ).toBeInTheDocument();
      expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
      expect(screen.queryByText("Penicillin.")).not.toBeInTheDocument();
    },
  );

  it.each(["PENDING_OTP", "PENDING_COUNTER", "REVOKED"])(
    "%s không tải hoặc hiển thị dữ liệu y tế",
    async (linkStatus) => {
      server.profiles[0].linkStatus = linkStatus;
      render(<PatientProfileHistoryScreen profileId={HISTORY_PROFILE_ID} />);
      expect(
        await screen.findByText(formatAppMessage("MSG-19")),
      ).toBeInTheDocument();
      expect(apiRequest).not.toHaveBeenCalledWith(
        HISTORY_PATH,
        expect.anything(),
      );
      expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    },
  );

  it("không liên quan bị từ chối dù biết UUID", async () => {
    server.profiles = [];
    render(<PatientProfileHistoryScreen profileId={HISTORY_PROFILE_ID} />);
    expect(
      await screen.findByText(formatAppMessage("MSG-19")),
    ).toBeInTheDocument();
    expect(vi.mocked(apiRequest).mock.calls).toHaveLength(1);
  });

  it("người thân CONFIRMED được xem và sửa; backend quyết định quyền cuối cùng", async () => {
    const user = userEvent.setup();
    server.profiles = [
      {
        patientProfileId: HISTORY_DEPENDENT_ID,
        fullName: "Người thân thật",
        relationship: "PARENT",
        linkStatus: "CONFIRMED",
      },
    ];
    render(<PatientProfileHistoryScreen profileId={HISTORY_DEPENDENT_ID} />);
    expect(await screen.findByLabelText("Dị ứng")).toHaveValue("Penicillin.");
    expect(screen.getByText("Người thân thật · Cha/Mẹ")).toBeInTheDocument();
    editAllergies("Dữ liệu người thân");
    await user.click(screen.getByRole("button", { name: "Lưu Dị ứng" }));
    await waitFor(() => expect(toast.success).toHaveBeenCalled());
    expect(putCalls()[0][0]).toBe(
      `/api/v1/patient-profiles/${HISTORY_DEPENDENT_ID}/medical-history`,
    );
  });

  it("giữ draft mục khác khi lưu một mục, dùng version mới cho lần lưu tiếp", async () => {
    const user = userEvent.setup();
    render(<PatientProfileHistoryScreen profileId={HISTORY_PROFILE_ID} />);
    await screen.findByLabelText("Dị ứng");
    editAllergies("Dị ứng mới");
    fireEvent.change(screen.getByLabelText("Chấn thương"), {
      target: { value: "Chấn thương chưa lưu" },
    });
    await user.click(screen.getByRole("button", { name: "Lưu Dị ứng" }));
    await waitFor(() => expect(toast.success).toHaveBeenCalledTimes(1));
    expect(screen.getByRole("button", { name: "Lưu Dị ứng" })).toBeDisabled();
    expect(screen.getByLabelText("Chấn thương")).toHaveValue(
      "Chấn thương chưa lưu",
    );
    expect(
      screen.getByRole("button", { name: "Lưu Chấn thương" }),
    ).toBeEnabled();
    expect(server.history.injuries).toBe("");
    await user.click(screen.getByRole("button", { name: "Lưu Chấn thương" }));
    await waitFor(() => expect(toast.success).toHaveBeenCalledTimes(2));
    expect(server.history.injuries).toBe("Chấn thương chưa lưu");
    expect(JSON.parse(String(putCalls()[1][1]?.body)).version).toBe(4);
  });

  it("đổi profileId không hiện tiền sử cũ trong lúc đang tải hồ sơ mới", async () => {
    const view = render(
      <PatientProfileHistoryScreen profileId={HISTORY_PROFILE_ID} />,
    );
    await screen.findByLabelText("Dị ứng");
    server.listError = new ApiError(503);
    view.rerender(
      <PatientProfileHistoryScreen profileId={HISTORY_DEPENDENT_ID} />,
    );
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    expect(
      await screen.findByText(formatAppMessage("MSG-04")),
    ).toBeInTheDocument();
  });
});

describe("mất xác thực sau khi màn đã tải tiền sử", () => {
  const failures = [
    { name: "HTTP 401", error: new ApiError(401) },
    {
      name: "không còn access token trước request",
      error: new Error("Bạn cần đăng nhập để tiếp tục."),
    },
    { name: "HTTP 401 khi lấy CSRF/phiên", error: new AuthSessionError(401) },
    { name: "HTTP 403 khi lấy CSRF/phiên", error: new AuthSessionError(403) },
  ];

  it.each(failures)(
    "PUT lỗi $name: ẩn dữ liệu đã tải, draft và metadata",
    async ({ error }) => {
      const user = userEvent.setup();
      render(<PatientProfileHistoryScreen profileId={HISTORY_PROFILE_ID} />);
      await screen.findByLabelText("Dị ứng");
      editAllergies("Draft y tế chưa lưu");
      server.putError = error;
      await user.click(screen.getByRole("button", { name: "Lưu Dị ứng" }));
      expect(
        await screen.findByText("Không tải được tiền sử bệnh."),
      ).toBeInTheDocument();
      expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
      expect(screen.queryByText(/Cập nhật lần cuối/)).not.toBeInTheDocument();
      expect(
        screen.queryByDisplayValue("Draft y tế chưa lưu"),
      ).not.toBeInTheDocument();
      expect(toast.success).not.toHaveBeenCalled();
    },
  );

  it.each(failures)(
    "reload lỗi $name sau STALE_VERSION: ẩn cả editor đang stale",
    async ({ error }) => {
      const user = userEvent.setup();
      render(<PatientProfileHistoryScreen profileId={HISTORY_PROFILE_ID} />);
      await screen.findByLabelText("Dị ứng");
      editAllergies("Draft y tế chưa lưu");
      server.putError = new ApiError(409, { errorCode: "STALE_VERSION" });
      await user.click(screen.getByRole("button", { name: "Lưu Dị ứng" }));
      expect(
        await screen.findByText(formatAppMessage("MSG-16")),
      ).toBeInTheDocument();
      server.getError = error;
      await user.click(screen.getByRole("button", { name: "Tải lại" }));
      expect(
        await screen.findByText("Không tải được tiền sử bệnh."),
      ).toBeInTheDocument();
      expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
      expect(screen.queryByText(/Cập nhật lần cuối/)).not.toBeInTheDocument();
      expect(
        screen.queryByRole("button", { name: "Lưu Dị ứng" }),
      ).not.toBeInTheDocument();
      expect(
        screen.queryByDisplayValue("Draft y tế chưa lưu"),
      ).not.toBeInTheDocument();
      expect(toast.success).not.toHaveBeenCalled();
    },
  );

  it("sau mất xác thực chỉ hiện lại dữ liệu/version từ GET thành công, không khôi phục draft cũ", async () => {
    const user = userEvent.setup();
    render(<PatientProfileHistoryScreen profileId={HISTORY_PROFILE_ID} />);
    await screen.findByLabelText("Dị ứng");
    editAllergies("Draft y tế chưa lưu");
    server.putError = new ApiError(401);
    await user.click(screen.getByRole("button", { name: "Lưu Dị ứng" }));
    await screen.findByText("Không tải được tiền sử bệnh.");
    server.putError = null;
    server.history = {
      ...server.history,
      allergies: "Bản mới sau đăng nhập",
      version: 8,
    };
    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByLabelText("Dị ứng")).toHaveValue(
      "Bản mới sau đăng nhập",
    );
    editAllergies("Bản sửa sau đăng nhập");
    await user.click(screen.getByRole("button", { name: "Lưu Dị ứng" }));
    await waitFor(() => expect(toast.success).toHaveBeenCalledTimes(1));
    expect(JSON.parse(String(putCalls()[1][1]?.body)).version).toBe(8);
  });
});
