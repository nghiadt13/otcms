import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { toast } from "sonner";
import { ApiError } from "@/lib/api/apiError";
import { APP_MESSAGES } from "@/lib/messages/appMessages";
import { ReceptionProvider } from "./ReceptionProvider";
import { AttendingDoctorTransferDialog } from "./AttendingDoctorTransferDialog";

const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock("sonner", () => ({
  toast: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
}));

const suggestion = (
  doctorProfileId: string,
  doctorName: string,
  roomLabel: string,
) => ({
  roomId: `room-${doctorProfileId}`,
  roomCode: `R-${doctorProfileId}`,
  roomLabel,
  doctorProfileId,
  doctorName,
  waitingCount: 0,
});

const handlers = {
  onSaved: vi.fn(),
  onFailed: vi.fn(),
  onClose: vi.fn(),
};

function mount() {
  return render(
    <ReceptionProvider>
      <AttendingDoctorTransferDialog
        visitId="visit-3"
        patientName="Lê Hoa"
        currentDoctorProfileId="doc-a"
        {...handlers}
      />
    </ReceptionProvider>,
  );
}

beforeEach(() => {
  request.mockReset();
  handlers.onSaved.mockReset();
  handlers.onFailed.mockReset();
  handlers.onClose.mockReset();
  vi.mocked(toast.success).mockClear();
  vi.mocked(toast.error).mockClear();
  request.mockImplementation(async (path: string) => {
    if (path === "/api/v1/reception/walk-in-suggestions")
      return {
        items: [
          suggestion("doc-a", "BS Nguyễn An", "Phòng 101"),
          suggestion("doc-b", "BS Trần Bình", "Phòng 202"),
          suggestion("doc-b", "BS Trần Bình", "Phòng 202"),
        ],
      };
    if (path.startsWith("/api/v1/visits/visit-3/attending-doctor-transfers"))
      return {
        messageCode: "MSG-43",
        data: {
          visitId: "visit-3",
          status: "WAITING",
          queueNumber: 7,
          doctorProfileId: "doc-b",
          doctorName: "BS Trần Bình",
          roomId: "room-doc-b",
          roomLabel: "Phòng 202",
        },
      };
    throw new Error(`Unexpected request ${path}`);
  });
});

async function fillForm(reason: string) {
  const user = userEvent.setup();
  const select = await screen.findByLabelText("Bác sĩ đang trong ca");
  await user.selectOptions(select, "doc-b");
  if (reason) await user.type(screen.getByLabelText("Lý do đổi *"), reason);
  return user;
}

describe("AttendingDoctorTransferDialog (UC-30)", () => {
  it("chỉ liệt kê bác sĩ khác bác sĩ hiện tại, không trùng, và đổi thành công gửi đúng body", async () => {
    mount();
    const user = await fillForm("  Bác sĩ phụ trách vắng đột xuất  ");

    const select = screen.getByLabelText("Bác sĩ đang trong ca");
    const options = within(select)
      .getAllByRole("option")
      .map((o) => o.textContent);
    expect(options).toEqual(["Chọn bác sĩ", "BS Trần Bình · Phòng 202"]);

    await user.click(screen.getByRole("button", { name: "Xác nhận đổi" }));

    await waitFor(() => expect(handlers.onSaved).toHaveBeenCalledTimes(1));
    expect(request).toHaveBeenCalledWith(
      "/api/v1/visits/visit-3/attending-doctor-transfers",
      {
        method: "POST",
        body: JSON.stringify({
          doctorProfileId: "doc-b",
          reason: "Bác sĩ phụ trách vắng đột xuất",
        }),
      },
    );
    expect(toast.success).toHaveBeenCalledWith(APP_MESSAGES["MSG-43"]);
    expect(handlers.onFailed).not.toHaveBeenCalled();
  });

  it("lý do trống báo MSG-39 và không gửi yêu cầu", async () => {
    mount();
    const user = await fillForm("");
    await user.click(screen.getByRole("button", { name: "Xác nhận đổi" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      APP_MESSAGES["MSG-39"],
    );
    expect(request).not.toHaveBeenCalledWith(
      expect.stringContaining("attending-doctor-transfers"),
      expect.anything(),
    );
    expect(handlers.onSaved).not.toHaveBeenCalled();
  });

  it("BE trả MSG-41 (bác sĩ không trong ca) thì hiện đúng câu MSG-41 và giữ hộp thoại", async () => {
    request.mockImplementation(async (path: string) => {
      if (path === "/api/v1/reception/walk-in-suggestions")
        return { items: [suggestion("doc-b", "BS Trần Bình", "Phòng 202")] };
      throw new ApiError(404, { code: "MSG-41" });
    });
    mount();
    const user = await fillForm("Bác sĩ vắng ca");
    await user.click(screen.getByRole("button", { name: "Xác nhận đổi" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      APP_MESSAGES["MSG-41"],
    );
    expect(handlers.onFailed).toHaveBeenCalledTimes(1);
    expect(handlers.onSaved).not.toHaveBeenCalled();
    expect(toast.success).not.toHaveBeenCalled();
  });

  it("BE trả MSG-42 (lượt không ở trạng thái cho phép) thì hiện đúng câu MSG-42", async () => {
    request.mockImplementation(async (path: string) => {
      if (path === "/api/v1/reception/walk-in-suggestions")
        return { items: [suggestion("doc-b", "BS Trần Bình", "Phòng 202")] };
      throw new ApiError(409, { code: "MSG-42" });
    });
    mount();
    const user = await fillForm("Đổi bác sĩ");
    await user.click(screen.getByRole("button", { name: "Xác nhận đổi" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      APP_MESSAGES["MSG-42"],
    );
    expect(handlers.onFailed).toHaveBeenCalledTimes(1);
    expect(handlers.onSaved).not.toHaveBeenCalled();
  });

  it("không có bác sĩ nào trong ca thì không cho xác nhận và hiện MSG-41", async () => {
    request.mockImplementation(async () => ({
      items: [suggestion("doc-a", "BS Nguyễn An", "Phòng 101")],
    }));
    mount();

    expect(await screen.findByRole("status")).toHaveTextContent(
      APP_MESSAGES["MSG-41"],
    );
    expect(screen.getByRole("button", { name: "Xác nhận đổi" })).toBeDisabled();
  });
});
