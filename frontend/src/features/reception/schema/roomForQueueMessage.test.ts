import { describe, expect, it } from "vitest";
import { roomForQueueMessage } from "./roomForQueueMessage";

describe("roomForQueueMessage", () => {
  it("bỏ tiền tố 'Phòng ' để câu MSG-94 không lặp chữ phòng", () => {
    expect(roomForQueueMessage("Phòng khám 1")).toBe("khám 1");
    expect(roomForQueueMessage("Phòng khám 2")).toBe("khám 2");
    expect(roomForQueueMessage("phòng khám 3")).toBe("khám 3");
    expect(roomForQueueMessage("Phòng  Thủ thuật")).toBe("Thủ thuật");
  });

  it("tên không có tiền tố giữ nguyên", () => {
    expect(roomForQueueMessage("Khoa Nội tổng hợp")).toBe("Khoa Nội tổng hợp");
    expect(roomForQueueMessage("Phòngkhám 4")).toBe("Phòngkhám 4");
  });

  it("chỉ có đúng chữ 'Phòng' thì giữ nguyên, không ra câu trống", () => {
    expect(roomForQueueMessage("Phòng")).toBe("Phòng");
  });
});
