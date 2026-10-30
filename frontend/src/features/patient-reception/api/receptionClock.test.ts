import { beforeEach, describe, expect, it } from "vitest";
import { createReceptionSeed } from "./receptionSeed";
import {
  executeReception,
  loadReception,
  resetReceptionMock,
} from "./receptionApi";
import { clinicTimestamp, isFutureSlot } from "../schema/receptionRules";

const DAY = "2026-10-27";
let now = `${DAY}T16:05:30+07:00`;
beforeEach(() => {
  now = `${DAY}T16:05:30+07:00`;
  resetReceptionMock(createReceptionSeed(DAY), () => new Date(now));
});
const booking = (date = DAY, time = "16:20") => ({
  type: "book" as const,
  patientId: "BN0008",
  serviceId: "kham",
  doctorId: "bs-an",
  date,
  time,
});

describe("real clinic time and booking deadlines", () => {
  it("formats Vietnam time and rolls over its date independently of the host timezone", () => {
    expect(clinicTimestamp(new Date("2026-10-27T02:10:15Z"))).toBe(
      "2026-10-27T09:10:15+07:00",
    );
    expect(clinicTimestamp(new Date("2026-10-27T17:00:00Z"))).toBe(
      "2026-10-28T00:00:00+07:00",
    );
  });
  it("rejects a slot once its start instant is reached, including the rest of that minute", () => {
    expect(isFutureSlot(DAY, "09:40", `${DAY}T09:39:59+07:00`)).toBe(true);
    expect(isFutureSlot(DAY, "09:40", `${DAY}T09:40:00+07:00`)).toBe(false);
    expect(isFutureSlot(DAY, "09:40", `${DAY}T09:40:59+07:00`)).toBe(false);
    expect(isFutureSlot(DAY, "24:00", `${DAY}T09:10:00+07:00`)).toBe(false);
    expect(isFutureSlot("2026-02-30", "10:20", `${DAY}T09:10:00+07:00`)).toBe(
      false,
    );
  });
  it("loads the current time instead of the seed's fixed fixture clock", async () => {
    expect((await loadReception()).clock).toBe(now);
  });
  it("rejects past days and past slots today without creating a charge or appointment", async () => {
    const before = await loadReception();
    await expect(
      executeReception(booking("2026-10-26", "16:40"), before.version),
    ).rejects.toThrow("MSG-31");
    await expect(
      executeReception(booking(DAY, "14:20"), before.version),
    ).rejects.toThrow("MSG-31");
    const after = await loadReception();
    expect(after.appointments).toHaveLength(before.appointments.length);
    expect(after.charges).toHaveLength(before.charges.length);
  });
  it("rechecks actual time when submitting an earlier snapshot", async () => {
    now = `${DAY}T09:39:59+07:00`;
    const snapshot = await loadReception();
    now = `${DAY}T09:40:01+07:00`;
    await expect(
      executeReception(booking(DAY, "09:40"), snapshot.version),
    ).rejects.toThrow("MSG-31");
    expect((await loadReception()).appointments).toHaveLength(
      snapshot.appointments.length,
    );
  });
  it("does not invalidate the form version for a clock tick and still accepts a future slot", async () => {
    const snapshot = await loadReception();
    now = `${DAY}T16:06:30+07:00`;
    expect((await loadReception()).version).toBe(snapshot.version);
    const result = await executeReception(booking(), snapshot.version);
    expect(result.appointments[0]).toMatchObject({ date: DAY, time: "16:20" });
    expect(
      result.charges.find((c) => c.contextId === result.appointments[0].id)
        ?.createdAt,
    ).toBe(now);
  });
  it("reads the new Vietnam calendar day after midnight", async () => {
    now = "2026-10-27T23:59:59+07:00";
    await loadReception();
    now = "2026-10-28T00:00:01+07:00";
    const snapshot = await loadReception();
    expect(snapshot.clock.slice(0, 10)).toBe("2026-10-28");
    await expect(
      executeReception(booking(DAY, "16:40"), snapshot.version),
    ).rejects.toThrow("MSG-31");
    expect(
      (await executeReception(booking("2026-10-28", "10:20"), snapshot.version))
        .appointments[0].date,
    ).toBe("2026-10-28");
  });
});
