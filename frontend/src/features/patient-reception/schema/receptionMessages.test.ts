import { beforeEach, describe, expect, it } from "vitest";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { APP_MESSAGES, formatAppMessage } from "@/lib/messages/appMessages";
import {
  executeReception,
  loadReception,
  resetReceptionMock,
} from "../api/receptionApi";
import { createReceptionSeed } from "../api/receptionSeed";
import type { ReceptionCommand } from "../types";
import { receptionFeedback } from "./receptionMessages";

// Thông báo lễ tân đi qua adapter thật để kiểm câu chữ đúng bảng MSG của R3.
const DAY = "2026-10-24";
async function command(input: ReceptionCommand) {
  const next = await executeReception(input, (await loadReception()).version);
  return receptionFeedback(input, next);
}
const cash = (chargeIds: string[]) =>
  command({
    type: "pay-cash",
    chargeIds,
    cashReceived: 1000000,
    confirmed: true,
  });
beforeEach(() => resetReceptionMock(createReceptionSeed(DAY)));

describe("receptionFeedback", () => {
  it("thu tiền mặt dùng MSG-48 với số tiền vừa ghi nhận", async () => {
    const feedback = await cash(["phi-LH001"]);
    expect(feedback?.tone).toBe("success");
    expect(feedback?.text).toBe(
      formatAppMessage("MSG-48", { amount: formatCurrencyVnd(200000) }),
    );
  });

  it("tiếp nhận dùng MSG-94 kèm số thứ tự và tên phòng", async () => {
    await cash(["phi-LH001"]);
    const feedback = await command({
      type: "check-in",
      patientId: "BN0001",
      appointmentId: "LH001",
      present: true,
      priority: "normal",
    });
    expect(feedback?.text).toMatch(
      /^Đã tiếp nhận bệnh nhân\. Số thứ tự \S+-\d{3}, phòng .+\.$/,
    );
    expect(feedback?.text).not.toContain("{");
  });

  it("VNPay: tạo QR là MSG-52, kết quả chưa rõ là MSG-52, bị từ chối là MSG-53", async () => {
    const start = await command({ type: "start-qr", chargeIds: ["phi-LH001"] });
    expect(start).toEqual({ tone: "warning", text: APP_MESSAGES["MSG-52"] });
    const tx = (await loadReception()).transactions[0];
    expect(
      await command({
        type: "demo-gateway",
        id: tx.id,
        outcome: "unconfirmed",
      }),
    ).toEqual({ tone: "warning", text: APP_MESSAGES["MSG-52"] });
    expect(
      await command({ type: "demo-gateway", id: tx.id, outcome: "failed" }),
    ).toEqual({ tone: "error", text: APP_MESSAGES["MSG-53"] });
  });

  it("điều phối hàng đợi dùng MSG-43, đặt lịch dùng MSG-26", async () => {
    expect(
      (await command({ type: "call-next", roomId: "p101", kind: "exam" }))
        ?.text,
    ).toBe(APP_MESSAGES["MSG-43"]);
    expect(
      (
        await command({
          type: "book",
          patientId: "BN0001",
          date: DAY,
          time: "10:20",
          serviceId: "kham",
          doctorId: "bs-an",
        })
      )?.text,
    ).toBe(APP_MESSAGES["MSG-26"]);
  });
});
