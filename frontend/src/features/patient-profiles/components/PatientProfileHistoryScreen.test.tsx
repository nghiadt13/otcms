import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "@/lib/api/client";
import { ApiError } from "@/lib/api/apiError";
import { formatAppMessage } from "@/lib/messages/appMessages";
import {
  MedicalHistoryTestServer,
  HISTORY_PROFILE_ID,
  HISTORY_DEPENDENT_ID,
  HISTORY_PATH,
  emptyHistory,
} from "../api/medicalHistoryTestServer";
import { PatientProfileHistoryScreen } from "./PatientProfileHistoryScreen";

vi.mock("@/lib/api/client", () => ({ apiRequest: vi.fn() }));

let server: MedicalHistoryTestServer;

beforeEach(() => {
  vi.resetAllMocks();
  server = new MedicalHistoryTestServer();
  vi.mocked(apiRequest).mockImplementation(server.request);
});

const putCalls = () =>
  vi.mocked(apiRequest).mock.calls.filter(([, init]) => init?.method === "PUT");

describe("PatientProfileHistoryScreen — chế độ chỉ xem cho bệnh nhân", () => {
  it("tải tiền sử đã công bố ở chế độ chỉ xem, đúng tên/quan hệ và AbortSignal", async () => {
    render(<PatientProfileHistoryScreen profileId={HISTORY_PROFILE_ID} />);
    expect(await screen.findByText("Penicillin.")).toBeInTheDocument();
    expect(screen.getByText("Đang theo dõi bệnh nền.")).toBeInTheDocument();
    expect(screen.getByText("Hồ sơ thật · Bản thân")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Tiền sử bệnh do bác sĩ ghi nhận và được hiển thị sau khi kết quả khám được phát hành.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Cập nhật lần cuối 20/10/2026 bởi Bác sĩ điều trị"),
    ).toBeInTheDocument();
    expect(apiRequest).toHaveBeenCalledWith(HISTORY_PATH, {
      signal: expect.any(AbortSignal),
    });
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /^Lưu/ }),
    ).not.toBeInTheDocument();
    expect(putCalls()).toHaveLength(0);
  });

  it("khi chưa có kết quả khám công bố tiền sử thì hiện trạng thái trống và không có nút lưu", async () => {
    server.history = emptyHistory();
    render(<PatientProfileHistoryScreen profileId={HISTORY_PROFILE_ID} />);
    expect(
      await screen.findByText("Chưa có tiền sử nào được ghi."),
    ).toBeInTheDocument();
    expect(screen.queryByText(/Cập nhật lần cuối/)).not.toBeInTheDocument();
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    expect(putCalls()).toHaveLength(0);
  });

  it.each([
    [400, "MSG-20", "MSG-20"],
    [403, "MSG-19", "MSG-19"],
    [404, "MSG-19", "MSG-19"],
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

  it("thử lại sau lỗi tải GET hiển thị lại tiền sử đã công bố", async () => {
    const user = userEvent.setup();
    server.getError = new ApiError(503);
    render(<PatientProfileHistoryScreen profileId={HISTORY_PROFILE_ID} />);
    expect(
      await screen.findByText("Không tải được tiền sử bệnh."),
    ).toBeInTheDocument();
    server.getError = null;
    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByText("Penicillin.")).toBeInTheDocument();
    expect(putCalls()).toHaveLength(0);
  });

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
      expect(screen.queryByText("Penicillin.")).not.toBeInTheDocument();
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

  it("người thân CONFIRMED chỉ được xem tiền sử đã công bố", async () => {
    server.profiles = [
      {
        patientProfileId: HISTORY_DEPENDENT_ID,
        fullName: "Người thân thật",
        relationship: "PARENT",
        linkStatus: "CONFIRMED",
      },
    ];
    render(<PatientProfileHistoryScreen profileId={HISTORY_DEPENDENT_ID} />);
    expect(await screen.findByText("Penicillin.")).toBeInTheDocument();
    expect(screen.getByText("Người thân thật · Cha/Mẹ")).toBeInTheDocument();
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    expect(putCalls()).toHaveLength(0);
  });

  it("đổi profileId không hiện tiền sử cũ trong lúc đang tải hồ sơ mới", async () => {
    const view = render(
      <PatientProfileHistoryScreen profileId={HISTORY_PROFILE_ID} />,
    );
    await screen.findByText("Penicillin.");
    server.listError = new ApiError(503);
    view.rerender(
      <PatientProfileHistoryScreen profileId={HISTORY_DEPENDENT_ID} />,
    );
    expect(screen.queryByText("Penicillin.")).not.toBeInTheDocument();
    expect(
      await screen.findByText(formatAppMessage("MSG-04")),
    ).toBeInTheDocument();
  });
});
