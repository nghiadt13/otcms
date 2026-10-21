import { beforeEach, describe, expect, it } from "vitest";
import * as unpaidItemApi from "./unpaidItemApi";
import {
  getUnpaidItem,
  listUnpaidItemServiceOptions,
  listUnpaidItems,
} from "./unpaidItemApi";
import {
  MOCK_SERVICE_OPTIONS,
  UNPAID_ITEM_FORBIDDEN_ID,
  UNPAID_ITEM_NOT_FOUND_ID,
  UNPAID_ITEM_SEED,
  resetUnpaidItemMockStore,
} from "./unpaidItemMockData";

const sum = (items: { priceVnd: number }[]) =>
  items.reduce((total, item) => total + item.priceVnd, 0);

beforeEach(() => resetUnpaidItemMockStore());

describe("listUnpaidItems", () => {
  it("mặc định: 20 dòng, 2 trang, orderedAt tăng dần", async () => {
    const page = await listUnpaidItems({});
    expect(page.totalItems).toBe(26);
    expect(page.totalPages).toBe(2);
    expect(page.items).toHaveLength(20);
    const times = page.items.map((item) => item.orderedAt);
    expect(times).toEqual([...times].sort());
    expect(page.items[0]).not.toHaveProperty("orderedByDoctorName");
    expect((await listUnpaidItems({ page: 1 })).items).toHaveLength(6);
  });

  it("summary là tổng toàn kỳ lọc, không phải trang 1", async () => {
    const page = await listUnpaidItems({});
    expect(page.summary.totalPriceVnd).toBe(sum(UNPAID_ITEM_SEED));
    expect(page.summary.totalPriceVnd).toBeGreaterThan(sum(page.items));
  });

  it("lọc kind và serviceId đúng, summary theo tập đã lọc", async () => {
    const fees = await listUnpaidItems({ kind: "CONSULTATION_FEE", size: 100 });
    expect(fees.items.every((item) => item.kind === "CONSULTATION_FEE")).toBe(true);
    expect(fees.summary.totalPriceVnd).toBe(sum(fees.items));

    const serviceId = MOCK_SERVICE_OPTIONS[1].id;
    const byService = await listUnpaidItems({ serviceId, size: 100 });
    expect(byService.totalItems).toBeGreaterThan(0);
    expect(byService.items.every((item) => item.serviceId === serviceId)).toBe(true);

    const none = await listUnpaidItems({ kind: "CONSULTATION_FEE", serviceId });
    expect(none.items).toEqual([]);
    expect(none.summary.totalPriceVnd).toBe(0);
  });

  it("from/to gồm cả hai đầu", async () => {
    const day = await listUnpaidItems({ from: "2026-09-28", to: "2026-09-28" });
    expect(day.totalItems).toBe(1);
    expect(day.items[0].orderedAt.startsWith("2026-09-28")).toBe(true);
    const late = await listUnpaidItems({ from: "2026-10-01", size: 100 });
    expect(late.totalItems).toBe(25);
  });

  it("q khớp tên và SĐT; q toàn khoảng trắng bị bỏ", async () => {
    expect((await listUnpaidItems({ q: " trần văn " })).totalItems).toBeGreaterThan(0);
    const phone = UNPAID_ITEM_SEED[1].patient.phone!;
    const byPhone = await listUnpaidItems({ q: phone });
    expect(byPhone.items.every((item) => item.patient.phone === phone)).toBe(true);
    expect((await listUnpaidItems({ q: "   " })).totalItems).toBe(26);
  });

  it("kho rỗng: items rỗng, summary 0", async () => {
    resetUnpaidItemMockStore([]);
    const page = await listUnpaidItems({});
    expect(page.items).toEqual([]);
    expect(page.summary.totalPriceVnd).toBe(0);
  });

  it("lỗi: loi-may-chu → 500 MSG-04; từ > đến → 400 MSG-54", async () => {
    await expect(listUnpaidItems({ q: "loi-may-chu" })).rejects.toMatchObject({
      status: 500,
      code: "MSG-04",
    });
    await expect(
      listUnpaidItems({ from: "2026-10-20", to: "2026-10-01" }),
    ).rejects.toMatchObject({ status: 400, code: "MSG-54" });
  });

  it("seed có đủ ca bắt buộc", () => {
    expect(UNPAID_ITEM_SEED.some((item) => item.patient.phone === null)).toBe(true);
    expect(UNPAID_ITEM_SEED.some((item) => item.patient.fullName.length > 40)).toBe(true);
    expect(UNPAID_ITEM_SEED.some((item) => item.allowedActions.length === 0)).toBe(true);
    expect(new Set(UNPAID_ITEM_SEED.map((item) => item.reference.type))).toEqual(
      new Set(["APPOINTMENT", "VISIT"]),
    );
  });
});

describe("getUnpaidItem", () => {
  it("chi tiết có allowedActions rỗng; phí khám không có bác sĩ chỉ định", async () => {
    const fee = await getUnpaidItem(UNPAID_ITEM_SEED[0].id);
    expect(fee.kind).toBe("CONSULTATION_FEE");
    expect(fee.orderedByDoctorName).toBeNull();
    expect(fee.allowedActions).toEqual([]);
    const order = await getUnpaidItem(UNPAID_ITEM_SEED[1].id);
    expect(order.orderedByDoctorName).toMatch(/^BS\./);
    expect(order.allowedActions).toEqual([]);
  });

  it("404 (kết thúc 0404 hoặc không có trong kho) và 403 (kết thúc 0403) đều MSG-50", async () => {
    await expect(getUnpaidItem(UNPAID_ITEM_NOT_FOUND_ID)).rejects.toMatchObject({
      status: 404,
      code: "MSG-50",
    });
    await expect(getUnpaidItem(UNPAID_ITEM_FORBIDDEN_ID)).rejects.toMatchObject({
      status: 403,
      code: "MSG-50",
    });
  });
});

it("listUnpaidItemServiceOptions trả id + name", async () => {
  const options = await listUnpaidItemServiceOptions();
  expect(options).toHaveLength(MOCK_SERVICE_OPTIONS.length);
  expect(Object.keys(options[0]).sort()).toEqual(["id", "name"]);
});

it("unpaidItemApi chỉ export hàm đọc", () => {
  expect(Object.keys(unpaidItemApi).sort()).toEqual([
    "getUnpaidItem",
    "listUnpaidItemServiceOptions",
    "listUnpaidItems",
  ]);
});
