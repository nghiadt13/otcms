import { act, render, screen } from "@testing-library/react";
import { Toaster } from "sonner";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { resetReceptionMockStore } from "../api/receptionMockStore";
import {
  forceNextReceptionQueueFailure,
  resetReceptionQueueMockControls,
} from "../api/receptionQueueMockControls";
import { addTestEntry } from "../api/receptionQueueTestEntries";
import { ReceptionQueueBoard } from "./ReceptionQueueBoard";
import { formatAppMessage } from "@/lib/messages/appMessages";
// Tự tải lại mỗi 20 giây khi tab hiển thị. Test dùng đồng hồ giả để không chờ thật.

const REFRESH_MS = 20_000;

function setVisibility(state: "visible" | "hidden") {
  Object.defineProperty(document, "visibilityState", { configurable: true, get: () => state });
  document.dispatchEvent(new Event("visibilitychange"));
}

/** Để promise của lớp giả và cập nhật trạng thái chạy xong trong act. */
async function flush(ms = 0) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

beforeEach(() => {
  vi.useFakeTimers();
  resetReceptionMockStore();
  resetReceptionQueueMockControls();
  setVisibility("visible");
});

afterEach(() => {
  setVisibility("visible");
  vi.useRealTimers();
});

describe("ReceptionQueueBoard tự tải lại", () => {
  it("sau 20 giây người vừa tiếp nhận hiện trên màn", async () => {
    render(
      <>
        <ReceptionQueueBoard />
        <Toaster />
      </>,
    );
    await flush();
    expect(screen.getByRole("button", { name: "Tôn Thất Long" })).toBeInTheDocument();

    addTestEntry({ patientName: "Người vừa đến", roomName: "Phòng khám 2", time: "09:50" });
    await flush(REFRESH_MS);
    expect(screen.getByRole("button", { name: "Người vừa đến" })).toBeInTheDocument();
  });

  it("tab ẩn thì không tải lại; hiện lại thì tải ngay", async () => {
    render(
      <>
        <ReceptionQueueBoard />
        <Toaster />
      </>,
    );
    await flush();

    setVisibility("hidden");
    addTestEntry({ patientName: "Người lúc ẩn", roomName: "Phòng khám 2", time: "09:51" });
    await flush(REFRESH_MS * 2);
    expect(screen.queryByRole("button", { name: "Người lúc ẩn" })).toBeNull();

    setVisibility("visible");
    await flush();
    expect(screen.getByRole("button", { name: "Người lúc ẩn" })).toBeInTheDocument();
  });

  it("lần tự tải lỗi giữ dữ liệu cũ, hiện chữ chưa cập nhật và không toast; lần sau thành công thì chữ biến mất", async () => {
    render(
      <>
        <ReceptionQueueBoard />
        <Toaster />
      </>,
    );
    await flush();

    forceNextReceptionQueueFailure("queue", 500);
    await flush(REFRESH_MS);
    expect(screen.getByRole("button", { name: "Tôn Thất Long" })).toBeInTheDocument();
    expect(screen.getByText("Chưa cập nhật được, thử lại sau")).toBeInTheDocument();
    expect(screen.queryByText(formatAppMessage("MSG-04"))).toBeNull();

    await flush(REFRESH_MS);
    expect(screen.queryByText("Chưa cập nhật được, thử lại sau")).toBeNull();
  });
});
