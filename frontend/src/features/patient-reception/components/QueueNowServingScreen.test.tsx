import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { ApiError } from "@/lib/api/apiError";
import { APP_MESSAGES } from "@/lib/messages/appMessages";
import {
  NOW_SERVING_REFRESH_MS,
  QueueNowServingScreen,
} from "./QueueNowServingScreen";

const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ publicApiRequest: request }));

beforeEach(() => {
  request.mockReset();
});

describe("QueueNowServingScreen (màn gọi số công khai)", () => {
  it("chỉ hiện các số đang gọi của phòng, không có tên bệnh nhân", async () => {
    request.mockResolvedValue([3, 7]);
    render(<QueueNowServingScreen roomId="room-101" />);

    const list = await screen.findByRole("list", { name: "Số đang gọi" });
    const items = within(list).getAllByRole("listitem");
    expect(items.map((item) => item.textContent)).toEqual(["3", "7"]);
    expect(request).toHaveBeenCalledWith(
      "/api/v1/public/queue/rooms/room-101/now-serving",
      { signal: expect.any(AbortSignal) },
    );
  });

  it("chưa có số nào được gọi thì hiện câu hướng dẫn", async () => {
    request.mockResolvedValue([]);
    render(<QueueNowServingScreen roomId="room-101" />);

    expect(
      await screen.findByText("Chưa có số nào được gọi."),
    ).toBeInTheDocument();
  });

  it("lỗi tải hiện đúng mã MSG, không hiện số cũ", async () => {
    request.mockRejectedValue(new ApiError(404, { code: "MSG-41" }));
    render(<QueueNowServingScreen roomId="room-404" />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      APP_MESSAGES["MSG-41"],
    );
    expect(screen.queryByRole("list")).not.toBeInTheDocument();
  });

  it("tự làm mới đúng chu kỳ 5 giây", async () => {
    request.mockResolvedValue([]);
    const interval = vi.spyOn(window, "setInterval");
    render(<QueueNowServingScreen roomId="room-101" />);

    await screen.findByText("Chưa có số nào được gọi.");
    expect(interval).toHaveBeenCalledWith(
      expect.any(Function),
      NOW_SERVING_REFRESH_MS,
    );
    expect(NOW_SERVING_REFRESH_MS).toBe(5000);
    interval.mockRestore();
  });
});
