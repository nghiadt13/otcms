import { beforeEach, describe, expect, it } from "vitest";
import {
  createOnlinePayment,
  getPayableSource,
  getPayment,
  listMyPayments,
  listPayableSources,
  resetPatientBillingMock,
} from "./patientBillingApi";
import {
  FORBIDDEN_SOURCE_ID,
  PENDING_TWICE_ITEM_ID,
  SEED_PATIENTS,
} from "./patientBillingMockData";
import { isSafeRedirectUrl } from "./patientBillingRedirect";

// Hợp đồng đầy đủ của lớp API giả: đọc, tạo giao dịch, kết quả, lịch sử. Kiểm tra đường dẫn
// trả về nằm ở patientBillingRedirect.test.ts.

const AN_VISIT = { sourceKind: "visit", sourceId: "vis-an-today" } as const;
const LAN_VISIT = { sourceKind: "visit", sourceId: "vis-lan-await" } as const;

beforeEach(() => {
  resetPatientBillingMock();
});

describe("listPayableSources", () => {
  it("chỉ trả nguồn còn khoản chờ thanh toán, kèm số khoản và tổng tiền chờ", async () => {
    const { items } = await listPayableSources();
    expect(items.map((item) => item.id).sort()).toEqual(
      ["apt-lan-27", "vis-an-today", "vis-lan-await"].sort(),
    );
    const an = items.find((item) => item.id === "vis-an-today");
    expect(an).toMatchObject({ pendingCount: 2, pendingAmount: 1450000 });
  });

  it("lọc theo hồ sơ người khám", async () => {
    const { items } = await listPayableSources({
      patientId: SEED_PATIENTS.lan.id,
    });
    expect(items.map((item) => item.id).sort()).toEqual(
      ["apt-lan-27", "vis-lan-await"].sort(),
    );
  });

  it("lỗi máy chủ ném 500 MSG-04", async () => {
    resetPatientBillingMock({ failSources: true });
    await expect(listPayableSources()).rejects.toMatchObject({
      status: 500,
      code: "MSG-04",
    });
  });
});

describe("getPayableSource", () => {
  it("trả khoản theo trạng thái, không lộ phòng phục vụ", async () => {
    const detail = await getPayableSource("visit", "vis-an-today");
    expect(detail.items.map((item) => item.status)).toEqual([
      "PENDING_PAYMENT",
      "PENDING_PAYMENT",
      "PAID",
      "CANCELLED",
    ]);
    expect(
      detail.items.find((item) => item.status === "PAID")?.receipt,
    ).toEqual({
      paymentId: "pm-1029",
      receiptNo: "PT-1029",
    });
    expect(
      detail.items.find((item) => item.status === "CANCELLED")
        ?.cancellationReason,
    ).toBe("Bác sĩ thay bằng MRI");
    expect(JSON.stringify(detail)).not.toContain("Phòng MRI");
  });

  it("nguồn không tồn tại hoặc sai loại → 404 MSG-41", async () => {
    await expect(
      getPayableSource("visit", "vis-khong-co"),
    ).rejects.toMatchObject({
      status: 404,
      code: "MSG-41",
    });
    await expect(
      getPayableSource("appointment", "vis-an-today"),
    ).rejects.toMatchObject({
      status: 404,
      code: "MSG-41",
    });
  });

  it("nguồn ngoài phạm vi tài khoản → 403 MSG-40", async () => {
    await expect(
      getPayableSource("visit", FORBIDDEN_SOURCE_ID),
    ).rejects.toMatchObject({
      status: 403,
      code: "MSG-40",
    });
  });
});

describe("createOnlinePayment", () => {
  it("tạo giao dịch PENDING, tổng do BE tính và đường dẫn chuyển về nội bộ", async () => {
    const response = await createOnlinePayment({
      ...AN_VISIT,
      itemIds: ["svc-cbc"],
    });
    expect(response.total).toBe(250000);
    expect(response.paymentId).toMatch(/^pay-mock-\d+$/);
    expect(response.redirectUrl).toBe(
      `/portal/payments/result?paymentId=${response.paymentId}`,
    );
    expect(isSafeRedirectUrl(response.redirectUrl)).toBe(true);
  });

  it("khoản đã thanh toán, đã hủy, không thuộc nguồn hoặc trùng mã → không cho trả", async () => {
    await expect(
      createOnlinePayment({ ...AN_VISIT, itemIds: ["svc-xray-knee"] }),
    ).rejects.toMatchObject({ status: 409, code: "MSG-49" });
    await expect(
      createOnlinePayment({ ...AN_VISIT, itemIds: ["svc-us-shoulder"] }),
    ).rejects.toMatchObject({ status: 409, code: "MSG-49" });
    await expect(
      createOnlinePayment({ ...AN_VISIT, itemIds: ["khoan-la"] }),
    ).rejects.toMatchObject({ status: 409, code: "MSG-49" });
    await expect(
      createOnlinePayment({ ...AN_VISIT, itemIds: ["svc-cbc", "svc-cbc"] }),
    ).rejects.toMatchObject({ status: 400, code: "MSG-12" });
  });

  it("danh sách khoản rỗng → 400 MSG-12", async () => {
    await expect(
      createOnlinePayment({ ...AN_VISIT, itemIds: [] }),
    ).rejects.toMatchObject({
      status: 400,
      code: "MSG-12",
    });
  });

  it("nguồn không tồn tại → 404 MSG-41; nguồn ngoài phạm vi → 403 MSG-40", async () => {
    await expect(
      createOnlinePayment({
        sourceKind: "visit",
        sourceId: "vis-khong-co",
        itemIds: ["svc-cbc"],
      }),
    ).rejects.toMatchObject({ status: 404, code: "MSG-41" });
    await expect(
      createOnlinePayment({
        sourceKind: "visit",
        sourceId: FORBIDDEN_SOURCE_ID,
        itemIds: ["svc-cbc"],
      }),
    ).rejects.toMatchObject({ status: 403, code: "MSG-40" });
  });

  it("khoản đang nằm trong một giao dịch khác (tab khác) → 409 MSG-49", async () => {
    await createOnlinePayment({ ...AN_VISIT, itemIds: ["svc-cbc"] });
    await expect(
      createOnlinePayment({ ...AN_VISIT, itemIds: ["svc-cbc"] }),
    ).rejects.toMatchObject({ status: 409, code: "MSG-49" });
  });
});

describe("getPayment: kết quả chỉ do BE quyết định", () => {
  it("PENDING hai lần rồi SUCCESS: cấp số thứ tự phòng và khoản chuyển PAID", async () => {
    const { paymentId } = await createOnlinePayment({
      ...AN_VISIT,
      itemIds: [PENDING_TWICE_ITEM_ID],
    });
    expect((await getPayment(paymentId)).status).toBe("PENDING");
    expect((await getPayment(paymentId)).status).toBe("PENDING");

    const success = await getPayment(paymentId);
    expect(success).toMatchObject({
      status: "SUCCESS",
      method: "VNPAY",
      total: 1200000,
      patientName: "Nguyễn Văn An",
      receiptNo: "PT-1030",
      queueAssignments: [{ roomName: "Phòng MRI 1", queueNumber: 4 }],
    });
    expect(success.paidAt).toMatch(/\+07:00$/);

    const detail = await getPayableSource("visit", "vis-an-today");
    const mri = detail.items.find((item) => item.id === PENDING_TWICE_ITEM_ID);
    expect(mri).toMatchObject({
      status: "PAID",
      receipt: { paymentId, receiptNo: "PT-1030" },
    });
    // Khoản đã trả không trả lại được.
    await expect(
      createOnlinePayment({ ...AN_VISIT, itemIds: [PENDING_TWICE_ITEM_ID] }),
    ).rejects.toMatchObject({ status: 409, code: "MSG-49" });
  });

  it("tổng chẵn triệu → FAILED; khoản vẫn chờ thanh toán và trả lại được", async () => {
    const { paymentId } = await createOnlinePayment({
      ...LAN_VISIT,
      itemIds: ["svc-endoscopy"],
    });
    const failed = await getPayment(paymentId);
    expect(failed).toMatchObject({ status: "FAILED", queueAssignments: [] });

    const retry = await createOnlinePayment({
      ...LAN_VISIT,
      itemIds: ["svc-endoscopy"],
    });
    expect(retry.total).toBe(1000000);
  });

  it("mã giao dịch không tồn tại → 404 MSG-50", async () => {
    await expect(getPayment("pay-mock-khong-co")).rejects.toMatchObject({
      status: 404,
      code: "MSG-50",
    });
  });
});

describe("listMyPayments: lịch sử đã hoàn tất", () => {
  it("chỉ giao dịch SUCCESS, mới nhất trước, phân trang theo tổng thật", async () => {
    const page = await listMyPayments({ page: 0, size: 5 });
    expect(page.totalItems).toBe(14);
    expect(page.totalPages).toBe(3);
    expect(page.items).toHaveLength(5);
    expect(page.items[0].id).toBe("pm-1029");
    expect(page.items.every((item) => item.status === "SUCCESS")).toBe(true);
  });

  it("lọc khoảng ngày gồm cả hai đầu và theo hồ sơ", async () => {
    const window = await listMyPayments({
      from: "2026-10-18",
      to: "2026-10-23",
    });
    expect(window.items.map((item) => item.id)).toEqual(["pm-1029", "pm-1013"]);

    const lan = await listMyPayments({ patientId: SEED_PATIENTS.lan.id });
    expect(
      lan.items.every((item) => item.patientName === "Nguyễn Thị Lan"),
    ).toBe(true);
  });

  it("từ ngày sau đến ngày → 400 MSG-54", async () => {
    await expect(
      listMyPayments({ from: "2026-10-24", to: "2026-10-01" }),
    ).rejects.toMatchObject({
      status: 400,
      code: "MSG-54",
    });
  });

  it("lỗi máy chủ ném 500 MSG-04", async () => {
    resetPatientBillingMock({ failPayments: true });
    await expect(listMyPayments({})).rejects.toMatchObject({
      status: 500,
      code: "MSG-04",
    });
  });
});
