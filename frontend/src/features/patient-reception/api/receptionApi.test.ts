import { beforeEach, describe, expect, it } from "vitest";
import { createReceptionSeed } from "./receptionSeed";
import {
  executeReception,
  loadReception,
  resetReceptionMock,
} from "./receptionApi";
import {
  availableDoctors,
  validatePatient,
  withinReturnWindow,
} from "../schema/receptionRules";
import type { ReceptionCommand, ReceptionState } from "../types";

const DAY = "2026-10-24";
const seed = () => createReceptionSeed(DAY);
async function command(input: ReceptionCommand) {
  return executeReception(input, (await loadReception()).version);
}
async function cash(chargeIds: string[], cashReceived = 1000000) {
  return command({
    type: "pay-cash",
    chargeIds,
    cashReceived,
    confirmed: true,
  });
}
beforeEach(() => resetReceptionMock(seed()));

describe("patient identification and care links", () => {
  it("rejects duplicate identifiers without partial writes", async () => {
    const s = await loadReception();
    const count = s.patients.length;
    await expect(
      command({
        type: "save-patient",
        input: { ...s.patients[0], phone: "+84900000001" },
        identityConfirmed: true,
      }),
    ).rejects.toThrow("MSG-18");
    expect((await loadReception()).patients).toHaveLength(count);
  });
  it("requires identity confirmation and validates birthdate", async () => {
    const input = {
      ...seed().patients[0],
      phone: "0901111111",
      name: "Bệnh nhân mới",
      dob: "2027-01-01",
    };
    expect(validatePatient(input, DAY).dob).toBeTruthy();
    expect(
      validatePatient({ ...input, dob: "2026-02-30" }, DAY).dob,
    ).toBeTruthy();
    await expect(
      command({
        type: "save-patient",
        input: { ...input, dob: "1990-01-01" },
        identityConfirmed: false,
      }),
    ).rejects.toThrow("MSG-17");
  });
  it("matches duplicate names despite accents, case and repeated whitespace", async () => {
    await expect(
      command({
        type: "save-patient",
        input: {
          ...seed().patients[0],
          name: "  NGUYEN   MINH  ANH  ",
          phone: "+84 900 000 001",
        },
        identityConfirmed: true,
      }),
    ).rejects.toThrow("MSG-18");
    expect((await loadReception()).patients).toHaveLength(
      seed().patients.length,
    );
  });
  it("matches passport identifiers regardless of letter case", async () => {
    const s = seed();
    s.patients[0].nationalId = "A1234567";
    resetReceptionMock(s);
    await expect(
      command({
        type: "save-patient",
        input: {
          ...s.patients[0],
          name: "Bệnh nhân khác",
          phone: "0901111111",
          nationalId: "a1234567",
        },
        identityConfirmed: true,
      }),
    ).rejects.toThrow("MSG-18");
  });
  it("creates only administrative information without a login or medical fields", async () => {
    const before = seed();
    const result = await command({
      type: "save-patient",
      input: {
        ...before.patients[0],
        name: "Bệnh nhân mới",
        phone: "0901111111",
      },
      identityConfirmed: true,
    });
    expect(result.patients).toHaveLength(before.patients.length + 1);
    expect(result.patients[0]).not.toHaveProperty("allergies");
    expect(result.patients[0]).not.toHaveProperty("medicalHistory");
    expect(result.account).toEqual(before.account);
  });
  it("requires papers and existing eligible relationship request", async () => {
    await expect(
      command({
        type: "confirm-link",
        id: "link1",
        evidence: "Đã đối chiếu khai sinh",
        identityConfirmed: false,
      }),
    ).rejects.toThrow("MSG-17");
    const result = await command({
      type: "confirm-link",
      id: "link1",
      evidence: "Đã đối chiếu khai sinh",
      identityConfirmed: true,
    });
    expect(result.careLinks[0].status).toBe("confirmed");
    expect(result.audit[0].reason).toContain("khai sinh");
    await expect(
      command({
        type: "confirm-link",
        id: "link1",
        evidence: "Đã đối chiếu khai sinh",
        identityConfirmed: true,
      }),
    ).rejects.toThrow("MSG-21");
  });
});

describe("appointment scheduling", () => {
  it("rejects two patients booking the same doctor and time without creating an extra charge", async () => {
    const input = {
      type: "book" as const,
      patientId: "BN0008",
      date: DAY,
      time: "10:20",
      serviceId: "kham",
      doctorId: "bs-an",
    };
    const first = await command(input);
    await expect(command({ ...input, patientId: "BN0009" })).rejects.toThrow(
      "MSG-30",
    );
    const next = await loadReception();
    expect(next.appointments).toHaveLength(first.appointments.length);
    expect(next.charges).toHaveLength(first.charges.length);
  });
  it("rejects one patient booking the same time with different doctors, including checked-in appointments", async () => {
    const input = {
      type: "book" as const,
      patientId: "BN0001",
      date: DAY,
      time: "10:20",
      serviceId: "kham",
      doctorId: "bs-an",
    };
    const first = await command(input);
    await expect(command({ ...input, doctorId: "bs-minh" })).rejects.toThrow(
      "MSG-29",
    );
    const fixture = seed();
    fixture.appointments.find((a) => a.id === "LH001")!.status = "checked-in";
    fixture.appointments.find((a) => a.id === "LH002")!.status = "cancelled";
    resetReceptionMock(fixture);
    await expect(
      command({ ...input, time: "09:20", doctorId: "bs-minh" }),
    ).rejects.toThrow();
    expect(
      first.charges.filter((c) => c.contextId === first.appointments[0].id),
    ).toHaveLength(1);
  });
  it("allows different patients at the same time with different doctors and multiple non-conflicting dates", async () => {
    const input = {
      type: "book" as const,
      patientId: "BN0008",
      date: DAY,
      time: "10:20",
      serviceId: "kham",
      doctorId: "bs-an",
    };
    await command(input);
    await command({ ...input, patientId: "BN0009", doctorId: "bs-minh" });
    await command({ ...input, time: "10:40" });
    const next = await command({ ...input, date: "2026-10-26" });
    expect(
      next.appointments.filter(
        (a) => a.patientId === "BN0008" && a.status === "booked",
      ),
    ).toHaveLength(4);
  });
  it("releases cancelled reservations for both doctor and patient", async () => {
    const fixture = seed();
    fixture.appointments.find((a) => a.id === "LH001")!.status = "cancelled";
    resetReceptionMock(fixture);
    const result = await command({
      type: "book",
      patientId: "BN0001",
      date: DAY,
      time: "09:20",
      serviceId: "kham",
      doctorId: "bs-an",
    });
    expect(result.appointments[0]).toMatchObject({
      patientId: "BN0001",
      time: "09:20",
      status: "booked",
    });
  });
  it("accepts only one concurrent reservation for two different patients at the same doctor and time", async () => {
    const initial = await loadReception();
    const input = {
      type: "book" as const,
      patientId: "BN0008",
      date: DAY,
      time: "10:20",
      serviceId: "kham",
      doctorId: "bs-an",
    };
    const outcomes = await Promise.allSettled([
      executeReception(input, initial.version),
      executeReception({ ...input, patientId: "BN0009" }, initial.version),
    ]);
    expect(outcomes.filter((r) => r.status === "fulfilled")).toHaveLength(1);
    const next = await loadReception();
    expect(next.appointments).toHaveLength(initial.appointments.length + 1);
    expect(next.charges).toHaveLength(initial.charges.length + 1);
    await expect(command({ ...input, patientId: "BN0009" })).rejects.toThrow(
      "MSG-30",
    );
  });

  it("assigns an optional doctor and fixes the price at booking", async () => {
    const result = await command({
      type: "book",
      patientId: "BN0001",
      date: DAY,
      time: "10:20",
      serviceId: "kham",
      doctorId: "",
    });
    expect(result.appointments[0].doctorId).toBe("bs-an");
    expect(result.appointments[0].status).toBe("booked");
    expect(
      result.charges.find((c) => c.contextId === result.appointments[0].id)
        ?.amount,
    ).toBe(200000);
  });
  it("rejects elapsed times, off-shift, already booked and wrong service slots", async () => {
    const input = {
      type: "book" as const,
      patientId: "BN0008",
      date: DAY,
      time: "08:00",
      serviceId: "kham",
      doctorId: "bs-an",
    };
    await expect(command(input)).rejects.toThrow("MSG-31");
    await expect(command({ ...input, time: "12:20" })).rejects.toThrow(
      "MSG-30",
    );
    await expect(command({ ...input, time: "09:20" })).rejects.toThrow(
      "MSG-30",
    );
    await expect(
      command({ ...input, time: "10:20", serviceId: "xquang" }),
    ).rejects.toThrow("MSG-29");
  });
  it("moves only absent doctor's appointment and never changes its time", async () => {
    await expect(
      command({ type: "reassign", id: "LH001", doctorId: "bs-minh" }),
    ).rejects.toThrow("MSG-32");
    const result = await command({
      type: "reassign",
      id: "LH003",
      doctorId: "bs-minh",
    });
    expect(result.appointments.find((a) => a.id === "LH003")).toMatchObject({
      status: "booked",
      doctorId: "bs-minh",
      time: "10:00",
      date: DAY,
    });
  });
  it("keeps appointment booked when no replacement exists", async () => {
    const state = seed();
    state.doctors.forEach((d) => {
      d.absent = true;
    });
    resetReceptionMock(state);
    const result = await command({ type: "notify-unavailable", id: "LH003" });
    expect(result.appointments.find((a) => a.id === "LH003")).toMatchObject({
      status: "booked",
      time: "10:00",
    });
  });
  it("rejects stale submissions atomically", async () => {
    const version = (await loadReception()).version;
    const input: ReceptionCommand = {
      type: "book",
      patientId: "BN0001",
      date: DAY,
      time: "10:20",
      serviceId: "kham",
      doctorId: "bs-an",
    };
    const results = await Promise.allSettled([
      executeReception(input, version),
      executeReception(input, version),
    ]);
    expect(results.filter((r) => r.status === "fulfilled")).toHaveLength(1);
    expect(
      (await loadReception()).appointments.filter((a) => a.time === "10:20"),
    ).toHaveLength(1);
  });
  it("marks missed bookings no-show after shift end without cancelling or refunding", async () => {
    const state = seed();
    state.clock = `${DAY}T17:01:00+07:00`;
    resetReceptionMock(state);
    const result = await loadReception();
    expect(result.appointments.find((a) => a.id === "LH002")?.status).toBe(
      "no-show",
    );
    expect(result.charges.find((c) => c.id === "phi-LH002")?.status).toBe(
      "paid",
    );
  });
  it("availability is derived from eligible shifts and reserved slots", () => {
    expect(availableDoctors(seed(), DAY, "09:20", "kham")).toHaveLength(0);
    expect(availableDoctors(seed(), DAY, "10:20", "kham")).toHaveLength(2);
    expect(availableDoctors(seed(), DAY, "10:25", "kham")).toHaveLength(0);
  });
});

describe("check-in and return visit", () => {
  const checkin: ReceptionCommand = {
    type: "check-in",
    patientId: "BN0001",
    appointmentId: "LH001",
    present: true,
    priority: "normal",
  };
  it("blocks unpaid or absent check-in; paid fee issues one visit and queue only", async () => {
    await expect(command(checkin)).rejects.toThrow("MSG-37");
    await cash(["phi-LH001"]);
    await expect(command({ ...checkin, present: false })).rejects.toThrow(
      "MSG-39",
    );
    const result = await command(checkin);
    const visit = result.visits.find((v) => v.appointmentId === "LH001")!;
    expect(result.queues.filter((q) => q.visitId === visit.id)).toHaveLength(1);
    await expect(command(checkin)).rejects.toThrow("MSG-37");
  });
  it("walk-in charges then check-in does not create an appointment", async () => {
    const first = await command({
      type: "prepare-walk-in",
      patientId: "BN0003",
      serviceId: "kham",
    });
    const fee = first.charges.at(-1)!;
    await cash([fee.id]);
    const result = await command({
      type: "check-in",
      patientId: "BN0003",
      present: true,
      priority: "normal",
    });
    expect(result.appointments).toHaveLength(seed().appointments.length);
    expect(result.visits[0]).toMatchObject({
      patientId: "BN0003",
      appointmentId: undefined,
      doctorId: "bs-minh",
    });
  });
  it("does not check in an absent doctor's patient", async () => {
    await cash(["phi-LH003"]);
    await expect(
      command({
        type: "check-in",
        patientId: "BN0003",
        appointmentId: "LH003",
        present: true,
        priority: "normal",
      }),
    ).rejects.toThrow("MSG-41");
  });
  it("day seven is included and day eight expires without resetting first date", async () => {
    expect(withinReturnWindow("2026-10-18", DAY)).toBe(true);
    expect(withinReturnWindow("2026-10-17", DAY)).toBe(false);
    const state = seed();
    state.visits.find((v) => v.id === "LK003")!.firstCheckIn = "2026-10-17";
    resetReceptionMock(state);
    const result = await loadReception();
    expect(result.visits.find((v) => v.id === "LK003")?.status).toBe(
      "incomplete",
    );
    expect(
      result.orders
        .filter((o) => o.visitId === "LK003")
        .every((o) => o.status === "cancelled"),
    ).toBe(true);
    await expect(
      command({
        type: "check-in",
        patientId: "BN0006",
        returnVisitId: "LK003",
        present: true,
        priority: "normal",
      }),
    ).rejects.toThrow("MSG-37");
  });
  it("returns a paid service visit to the same visit with no new fee or booking", async () => {
    const state = seed();
    state.orders.find((o) => o.id === "CD001")!.status = "paid";
    state.charges.find((c) => c.orderId === "CD001")!.status = "paid";
    resetReceptionMock(state);
    const result = await command({
      type: "check-in",
      patientId: "BN0006",
      returnVisitId: "LK003",
      present: true,
      priority: "normal",
    });
    expect(result.visits).toHaveLength(state.visits.length);
    expect(result.charges).toHaveLength(state.charges.length);
    expect(result.visits.find((v) => v.id === "LK003")!.firstCheckIn).toBe(
      "2026-10-22",
    );
    expect(
      result.queues.some((q) => q.visitId === "LK003" && q.kind === "service"),
    ).toBe(true);
  });
  it("does not enqueue result review until every latest-round result exists", async () => {
    const state = seed();
    state.orders.find((o) => o.id === "CD001")!.status = "results";
    resetReceptionMock(state);
    await expect(
      command({
        type: "check-in",
        patientId: "BN0006",
        returnVisitId: "LK003",
        present: true,
        priority: "normal",
      }),
    ).rejects.toThrow("MSG-37");
    expect(
      (await loadReception()).queues.some(
        (q) => q.visitId === "LK003" && q.kind === "review",
      ),
    ).toBe(false);
  });
});

describe("queues", () => {
  it("calls by queue order, blocks occupied room and advances only after arrival", async () => {
    const result = await command({
      type: "call-next",
      roomId: "p101",
      kind: "exam",
    });
    expect(result.queues.find((q) => q.id === "q1")).toMatchObject({
      status: "waiting",
      callerId: result.account.id,
    });
    await expect(
      command({ type: "call-next", roomId: "p101", kind: "exam" }),
    ).rejects.toThrow("MSG-42");
    const arrived = await command({
      type: "queue-action",
      id: "q1",
      action: "arrived",
    });
    expect(arrived.visits.find((v) => v.id === "LK001")?.status).toBe(
      "examining",
    );
  });
  it("absence frees room and same-day return keeps priority with new arrival time", async () => {
    await command({ type: "call-next", roomId: "p101", kind: "exam" });
    await command({ type: "queue-action", id: "q1", action: "absent" });
    const next = await command({
      type: "call-next",
      roomId: "p101",
      kind: "exam",
    });
    expect(next.queues.find((q) => q.id === "q2")?.calledAt).toBeTruthy();
    const returned = await command({
      type: "queue-action",
      id: "q1",
      action: "return",
    });
    expect(returned.queues.find((q) => q.id === "q1")).toMatchObject({
      status: "waiting",
      priority: "child",
      joinedAt: returned.clock,
      calledAt: undefined,
    });
  });
  it("blocks service-room calls and another caller's absent action", async () => {
    await expect(
      command({ type: "call-next", roomId: "xq1", kind: "exam" }),
    ).rejects.toThrow("MSG-42");
    const state = seed();
    state.queues[0].callerId = "another-receptionist";
    state.queues[0].calledAt = state.clock;
    resetReceptionMock(state);
    await expect(
      command({ type: "queue-action", id: "q1", action: "absent" }),
    ).rejects.toThrow("MSG-40");
  });
  it("requires a reason and retains before/after queue history", async () => {
    await expect(
      command({
        type: "reorder",
        id: "q2",
        position: 1,
        priority: "emergency",
        reason: "",
      }),
    ).rejects.toThrow("MSG-39");
    const result = await command({
      type: "reorder",
      id: "q2",
      position: 1,
      priority: "emergency",
      reason: "Điều phối theo tình huống cấp cứu",
    });
    expect(result.audit[0]).toMatchObject({
      actor: "Thiên Quang",
      reason: "Điều phối theo tình huống cấp cứu",
    });
    expect(result.queues.find((q) => q.id === "q2")?.position).toBe(1);
  });
  it("moves only into service-compatible rooms and preserves visit", async () => {
    const paid = await cash(["phi-CD001"]);
    const entry = paid.queues.find((q) => q.visitId === "LK003")!;
    await expect(
      command({
        type: "move-room",
        id: entry.id,
        roomId: "xn",
        reason: "Chuyển phòng đang chờ",
      }),
    ).rejects.toThrow("MSG-42");
    const result = await command({
      type: "move-room",
      id: entry.id,
      roomId: "xq2",
      reason: "Chuyển phòng giảm chờ",
    });
    expect(result.orders.find((o) => o.id === "CD001")?.roomId).toBe("xq2");
    expect(result.visits.find((v) => v.id === "LK003")?.doctorId).toBe(
      "bs-minh",
    );
  });
  it("transfers clinical responsibility only from an absent doctor to one on duty", async () => {
    await expect(
      command({
        type: "transfer-doctor",
        visitId: "LK003",
        doctorId: "bs-an",
        reason: "Bàn giao cho bác sĩ trực",
      }),
    ).rejects.toThrow("MSG-42");
    const state = seed();
    state.doctors.find((d) => d.id === "bs-minh")!.absent = true;
    resetReceptionMock(state);
    const result = await command({
      type: "transfer-doctor",
      visitId: "LK003",
      doctorId: "bs-an",
      reason: "Bác sĩ phụ trách vắng ca",
    });
    expect(result.visits.find((v) => v.id === "LK003")?.doctorId).toBe("bs-an");
    expect(result.audit).toHaveLength(1);
  });
});

describe("cash, VNPay and immutable receipts", () => {
  it("rejects insufficient cash, unconfirmed cash, empty selection and mixed contexts", async () => {
    await expect(cash(["phi-LH001"], 199999)).rejects.toThrow("MSG-39");
    await expect(
      command({
        type: "pay-cash",
        chargeIds: ["phi-LH001"],
        cashReceived: 200000,
        confirmed: false,
      }),
    ).rejects.toThrow("MSG-39");
    await expect(cash([])).rejects.toThrow("MSG-49");
    await expect(cash(["phi-LH001", "phi-LH003"])).rejects.toThrow("MSG-49");
    expect((await loadReception()).payments).toHaveLength(1);
  });
  it("records one receipt for whole selected items and enqueues service rooms", async () => {
    const result = await cash(["phi-CD001", "phi-CD002"], 300000);
    expect(result.payments[0]).toMatchObject({
      amount: 280000,
      cashReceived: 300000,
      patientName: "Đỗ Thu Hà",
    });
    expect(result.payments[0].items).toHaveLength(2);
    expect(
      result.queues.filter(
        (q) => q.visitId === "LK003" && q.kind === "service",
      ),
    ).toHaveLength(2);
    expect(
      result.queues.some((q) => q.visitId === "LK003" && q.kind === "review"),
    ).toBe(false);
    await expect(cash(["phi-CD001"])).rejects.toThrow("MSG-49");
  });
  it("rolls back payment when no service room is available", async () => {
    const state = seed();
    state.rooms
      .filter((r) => r.serviceIds.includes("xquang"))
      .forEach((r) => {
        r.available = false;
      });
    resetReceptionMock(state);
    await expect(cash(["phi-CD001"])).rejects.toThrow("MSG-41");
    const result = await loadReception();
    expect(result.payments).toHaveLength(state.payments.length);
    expect(result.charges.find((c) => c.id === "phi-CD001")?.status).toBe(
      "pending",
    );
  });
  it("keeps pending QR unpaid and prevents a second cash charge", async () => {
    const result = await command({
      type: "start-qr",
      chargeIds: ["phi-LH001"],
    });
    expect(result.charges.find((c) => c.id === "phi-LH001")?.status).toBe(
      "pending",
    );
    expect(result.payments).toHaveLength(1);
    await expect(cash(["phi-LH001"])).rejects.toThrow("MSG-52");
  });
  it("handles success once even when callback repeats", async () => {
    const start = await command({ type: "start-qr", chargeIds: ["phi-LH001"] });
    const tx = start.transactions[0];
    await command({ type: "demo-gateway", id: tx.id, outcome: "succeeded" });
    const result = await command({
      type: "demo-gateway",
      id: tx.id,
      outcome: "succeeded",
    });
    expect(result.payments.filter((p) => p.contextId === "LH001")).toHaveLength(
      1,
    );
    expect(result.transactions[0].paymentId).toBeTruthy();
  });
  it("blocks expired unknown transactions until explicit reconciliation", async () => {
    const state = seed();
    state.transactions.push({
      id: "QR-expired",
      chargeIds: ["phi-LH001"],
      amount: 200000,
      expiresAt: Date.now() - 1,
      status: "pending",
      needsReconciliation: false,
    });
    resetReceptionMock(state);
    const result = await loadReception();
    expect(result.transactions[0]).toMatchObject({
      status: "failed",
      needsReconciliation: true,
    });
    await expect(cash(["phi-LH001"])).rejects.toThrow("MSG-52");
    await command({
      type: "demo-gateway",
      id: "QR-expired",
      outcome: "failed",
    });
    expect(
      (await cash(["phi-LH001"])).charges.find((c) => c.id === "phi-LH001")
        ?.status,
    ).toBe("paid");
  });
  it("retains patient name on issued receipt after administrative correction", async () => {
    const paid = await cash(["phi-LH001"]);
    const patient = paid.patients.find((p) => p.id === "BN0001")!;
    const result = await command({
      type: "save-patient",
      id: patient.id,
      input: { ...patient, name: "Nguyễn Minh Anh Đã Sửa" },
      identityConfirmed: true,
    });
    expect(result.payments[0].patientName).toBe("Nguyễn Minh Anh");
  });
  it("creates one queue entry for multiple paid orders in the same room", async () => {
    const state: ReceptionState = seed();
    state.orders[0].serviceId = "crp";
    state.orders[0].roomId = "xn";
    state.charges.find((c) => c.orderId === "CD001")!.serviceId = "crp";
    resetReceptionMock(state);
    const result = await cash(["phi-CD001", "phi-CD002"]);
    expect(
      result.queues.filter(
        (q) =>
          q.visitId === "LK003" && q.roomId === "xn" && q.status === "waiting",
      ),
    ).toHaveLength(1);
  });
});
