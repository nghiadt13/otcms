import { afterEach, describe, expect, it, vi } from "vitest";
import type { RoomInput } from "../types/room";

const newRoom: RoomInput = {
  name: "Phòng thủ thuật 1",
  type: "procedure",
  description: "Bó bột và tiêm khớp.",
  supportedServices: ["Bó bột"],
};

// roomApi giữ dữ liệu trong biến cấp module, nên nạp lại module cho mỗi test để
// các test không ảnh hưởng nhau. Hàm giả có độ trễ (setTimeout) → dùng fake timer.
async function loadApi() {
  vi.resetModules();
  vi.useFakeTimers();
  return import("./roomApi");
}

// Chờ một lời gọi API giả: tua hết độ trễ rồi trả kết quả (hoặc ném lỗi).
// Dùng allSettled để lỗi bị bắt ngay, không thành "unhandled rejection".
async function settled<T>(promise: Promise<T>): Promise<T> {
  const outcome = Promise.allSettled([promise]);
  await vi.runAllTimersAsync();
  const [result] = await outcome;
  if (result.status === "rejected") throw result.reason;
  return result.value;
}

describe("roomApi (API giả)", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("liệt kê dữ liệu mẫu và trả bản sao, không lộ state nội bộ", async () => {
    const api = await loadApi();

    const first = await settled(api.listRooms());
    expect(first.length).toBeGreaterThan(0);

    first[0].name = "Bị sửa ở ngoài";
    const second = await settled(api.listRooms());
    expect(second[0].name).not.toBe("Bị sửa ở ngoài");
  });

  it("tạo phòng mới ở trạng thái sẵn sàng và cắt khoảng trắng tên", async () => {
    const api = await loadApi();

    const room = await settled(
      api.createRoom({ ...newRoom, name: "  Phòng thủ thuật 1  " }),
    );

    expect(room.name).toBe("Phòng thủ thuật 1");
    expect(room.status).toBe("available");
  });

  it("từ chối tên phòng trùng, không phân biệt hoa thường", async () => {
    const api = await loadApi();

    await expect(
      settled(api.createRoom({ ...newRoom, name: "PHÒNG X-QUANG" })),
    ).rejects.toThrow("Tên phòng đã tồn tại.");
  });

  it("cho phép sửa phòng giữ nguyên tên của chính nó", async () => {
    const api = await loadApi();

    const room = await settled(
      api.updateRoom("room-003", { ...newRoom, name: "Phòng X-quang" }),
    );

    expect(room.id).toBe("room-003");
  });

  it("đổi trạng thái phòng và báo lỗi khi không có phòng", async () => {
    const api = await loadApi();

    const room = await settled(api.setRoomStatus("room-001", "unavailable"));
    expect(room.status).toBe("unavailable");

    await expect(
      settled(api.setRoomStatus("khong-co", "available")),
    ).rejects.toThrow("Không tìm thấy phòng.");
  });
});
