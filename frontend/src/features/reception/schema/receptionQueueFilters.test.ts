import { describe, expect, it } from "vitest";
import {
  matchesReceptionQueueSearch,
  STATUS_FILTER_STATUSES,
} from "./receptionQueueFilters";

const row = { patientName: "Tôn Thất Long", queueNumber: 5 };

describe("matchesReceptionQueueSearch", () => {
  it("ô trống hoặc chỉ khoảng trắng khớp mọi dòng", () => {
    expect(matchesReceptionQueueSearch(row, "")).toBe(true);
    expect(matchesReceptionQueueSearch(row, "   ")).toBe(true);
  });

  it("tên khớp không phân biệt hoa thường và dấu tiếng Việt", () => {
    expect(matchesReceptionQueueSearch(row, "long")).toBe(true);
    expect(matchesReceptionQueueSearch(row, "TON THAT")).toBe(true);
    expect(matchesReceptionQueueSearch(row, "Bình")).toBe(false);
  });

  it("số thứ tự khớp đúng số; khớp một phần số thì không tính", () => {
    expect(matchesReceptionQueueSearch(row, "5")).toBe(true);
    expect(matchesReceptionQueueSearch(row, " 5 ")).toBe(true);
    expect(matchesReceptionQueueSearch(row, "15")).toBe(false);
  });
});

describe("STATUS_FILTER_STATUSES", () => {
  it("mặc định là Đang chờ + Vắng mặt; Tất cả thêm Đang phục vụ", () => {
    expect(STATUS_FILTER_STATUSES.WAITING_ABSENT).toEqual(["WAITING", "ABSENT"]);
    expect(STATUS_FILTER_STATUSES.ALL).toEqual(["WAITING", "BEING_SERVED", "ABSENT"]);
  });
});
