import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { toast } from "sonner";
import { ApiError } from "@/lib/api/apiError";
import { APP_MESSAGES } from "@/lib/messages/appMessages";
import { ReceptionProvider } from "./ReceptionProvider";
import { ServiceOrderRoomTransferDialog } from "./ServiceOrderRoomTransferDialog";

const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock("sonner", () => ({
  toast: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
}));

const ROOMS_PATH =
  "/api/v1/reception/clinical-service-orders/eligible-imaging-rooms?serviceOrderIds=order-1";
const TRANSFER_PATH = "/api/v1/service-orders/order-1/room-transfers";

const handlers = { onSaved: vi.fn(), onFailed: vi.fn(), onClose: vi.fn() };

function mount() {
  return render(
    <ReceptionProvider>
      <ServiceOrderRoomTransferDialog
        orderId="order-1"
        serviceName="Chụp X-quang ngực"
        currentRoomId="room-ct"
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
    if (path === ROOMS_PATH)
      return {
        items: [
          {
            serviceOrderId: "order-1",
            items: [
              { roomId: "room-ct", roomCode: "CT1", displayLabel: "CT 1" },
              { roomId: "room-xq", roomCode: "XQ2", displayLabel: "X-quang 2" },
            ],
          },
        ],
      };
    if (path === TRANSFER_PATH)
      return {
        messageCode: "MSG-43",
        data: {
          orderId: "order-1",
          roomId: "room-xq",
          previousRoomId: "room-ct",
        },
      };
    throw new Error(`Unexpected request ${path}`);
  });
});

async function chooseRoomAndReason(reason: string) {
  const user = userEvent.setup();
  const select = await screen.findByLabelText("Phòng mới");
  await user.selectOptions(select, "room-xq");
  if (reason) await user.type(screen.getByLabelText("Lý do đổi *"), reason);
  return user;
}

describe("ServiceOrderRoomTransferDialog (UC-31)", () => {
  it("loại phòng hiện tại khỏi danh sách và đổi phòng thành công gửi đúng body đã cắt khoảng trắng", async () => {
    mount();
    const user = await chooseRoomAndReason("  Phòng CT đang bảo trì  ");

    const options = within(screen.getByLabelText("Phòng mới"))
      .getAllByRole("option")
      .map((o) => o.textContent);
    expect(options).toEqual(["Chọn phòng", "XQ2 · X-quang 2"]);

    await user.click(screen.getByRole("button", { name: "Xác nhận đổi" }));

    await waitFor(() => expect(handlers.onSaved).toHaveBeenCalledTimes(1));
    expect(request).toHaveBeenCalledWith(TRANSFER_PATH, {
      method: "POST",
      body: JSON.stringify({
        newRoomId: "room-xq",
        reason: "Phòng CT đang bảo trì",
      }),
    });
    expect(toast.success).toHaveBeenCalledWith(APP_MESSAGES["MSG-43"]);
  });

  it("lý do trống báo MSG-39 và không gửi yêu cầu", async () => {
    mount();
    const user = await chooseRoomAndReason("");
    await user.click(screen.getByRole("button", { name: "Xác nhận đổi" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      APP_MESSAGES["MSG-39"],
    );
    expect(request).not.toHaveBeenCalledWith(TRANSFER_PATH, expect.anything());
    expect(handlers.onSaved).not.toHaveBeenCalled();
  });

  it("BE trả MSG-41 (phòng hoặc chỉ định không còn) thì hiện đúng câu MSG-41", async () => {
    request.mockImplementation(async (path: string) => {
      if (path === ROOMS_PATH)
        return {
          items: [
            {
              serviceOrderId: "order-1",
              items: [
                {
                  roomId: "room-xq",
                  roomCode: "XQ2",
                  displayLabel: "X-quang 2",
                },
              ],
            },
          ],
        };
      throw new ApiError(404, { code: "MSG-41" });
    });
    mount();
    const user = await chooseRoomAndReason("Đổi phòng");
    await user.click(screen.getByRole("button", { name: "Xác nhận đổi" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      APP_MESSAGES["MSG-41"],
    );
    expect(handlers.onFailed).toHaveBeenCalledTimes(1);
    expect(handlers.onSaved).not.toHaveBeenCalled();
  });

  it("BE trả MSG-42 (chỉ định đã thực hiện hoặc đã đổi) thì hiện đúng câu MSG-42", async () => {
    request.mockImplementation(async (path: string) => {
      if (path === ROOMS_PATH)
        return {
          items: [
            {
              serviceOrderId: "order-1",
              items: [
                {
                  roomId: "room-xq",
                  roomCode: "XQ2",
                  displayLabel: "X-quang 2",
                },
              ],
            },
          ],
        };
      throw new ApiError(409, { code: "MSG-42" });
    });
    mount();
    const user = await chooseRoomAndReason("Đổi phòng");
    await user.click(screen.getByRole("button", { name: "Xác nhận đổi" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      APP_MESSAGES["MSG-42"],
    );
    expect(handlers.onFailed).toHaveBeenCalledTimes(1);
    expect(handlers.onSaved).not.toHaveBeenCalled();
  });

  it("không có phòng khác đủ điều kiện thì không cho xác nhận", async () => {
    request.mockImplementation(async () => ({
      items: [{ serviceOrderId: "order-1", items: [] }],
    }));
    mount();

    expect(await screen.findByRole("status")).toHaveTextContent(
      APP_MESSAGES["MSG-41"],
    );
    expect(screen.getByRole("button", { name: "Xác nhận đổi" })).toBeDisabled();
  });
});
