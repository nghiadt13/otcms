import type { MsgCode } from "@/lib/api/apiError";
import { mockDelay, mockFail } from "@/lib/api/mockServer";
import { isPasswordValid } from "@/lib/domain/passwordPolicy";
import {
  addDays,
  clinicTimestamp,
  isFutureSlot,
  availableDoctors,
  latestRoundReady,
  normalizePhone,
  normalizeText,
  onDuty,
  priorityRank,
  queueSort,
  validatePatient,
  withinReturnWindow,
} from "../schema/receptionRules";
import {
  PRIORITIES,
  type Charge,
  type QueueEntry,
  type ReceptionCommand,
  type ReceptionState,
  type Visit,
} from "../types";
import { createReceptionSeed } from "./receptionSeed";

// UI prototype transport, matching the existing admin mock boundary. No Supabase writes.
// Replace this adapter with authorized Spring endpoints; never ship the mock as clinical storage.
let store = createReceptionSeed();
let currentTime = () => new Date();
let passwordHash: string | null = null;
const delay = () => mockDelay(process.env.NODE_ENV === "test" ? 0 : 180);
export function resetReceptionMock(state?: ReceptionState, now?: () => Date) {
  store = structuredClone(state ?? createReceptionSeed());
  const fixtureTime = store.clock;
  currentTime = now ?? (state ? () => new Date(fixtureTime) : () => new Date());
  passwordHash = null;
}
// Lỗi mang mã MSG của R3 theo hình ProblemDetail mà BE sẽ trả (xem fe-be-api-contract);
// màn hình hiển thị bằng `messageFromError`, không tự đặt câu chữ.
function requireThat(
  condition: unknown,
  code: MsgCode,
  status = 409,
): asserts condition {
  if (!condition) mockFail(status, code);
}
function find<T extends { id: string }>(
  items: T[],
  id: string,
  code: MsgCode = "MSG-41",
): T {
  const result = items.find((x) => x.id === id);
  requireThat(result, code, 404);
  return result;
}
const id = (s: ReceptionState, prefix: string) => `${prefix}${++s.sequence}`;
const today = (s: ReceptionState) => s.clock.slice(0, 10);
const activeQueue = (q: QueueEntry) =>
  ["waiting", "serving", "absent"].includes(q.status);
const roomBusy = (s: ReceptionState, roomId: string) =>
  s.queues.some(
    (q) =>
      q.roomId === roomId &&
      q.date === today(s) &&
      (q.status === "serving" || (q.status === "waiting" && q.calledAt)),
  );
function history(
  s: ReceptionState,
  action: string,
  reason: string,
  before: string,
  after: string,
) {
  s.audit.unshift({
    id: id(s, "log"),
    at: s.clock,
    actor: s.account.name,
    action,
    reason,
    before,
    after,
  });
}
function requireActive(visit: Visit) {
  requireThat(
    ["waiting", "examining", "awaiting"].includes(visit.status),
    "MSG-42",
  );
}
function expireState(s: ReceptionState) {
  for (const visit of s.visits)
    if (
      visit.status === "awaiting" &&
      !withinReturnWindow(visit.firstCheckIn, today(s))
    ) {
      visit.status = "incomplete";
      for (const order of s.orders.filter(
        (o) => o.visitId === visit.id && ["pending", "paid"].includes(o.status),
      )) {
        order.status = "cancelled";
        order.cancellationReason = "Hết thời hạn 7 ngày của lượt khám.";
        for (const c of s.charges.filter(
          (c) => c.orderId === order.id && c.status === "pending",
        ))
          c.status = "cancelled";
      }
      for (const queue of s.queues.filter(
        (q) => q.visitId === visit.id && activeQueue(q),
      ))
        queue.status = "closed";
    }
  for (const tx of s.transactions)
    if (tx.status === "pending" && tx.expiresAt <= Date.now()) {
      tx.status = "failed";
      tx.needsReconciliation = true;
    }
  for (const a of s.appointments.filter((a) => a.status === "booked")) {
    const shifts = s.shifts.filter(
      (sh) => sh.staffId === a.doctorId && sh.date === a.date,
    );
    const end = shifts
      .map((sh) => sh.end)
      .sort()
      .at(-1);
    if (
      a.date < today(s) ||
      (a.date === today(s) && end && s.clock.slice(11, 16) >= end)
    )
      a.status = "no-show";
  }
}
export async function loadReception(
  signal?: AbortSignal,
): Promise<ReceptionState> {
  await mockDelay(process.env.NODE_ENV === "test" ? 0 : 180, signal);
  const next = structuredClone(store);
  next.clock = clinicTimestamp(currentTime());
  expireState(next);
  // A clock tick is not a business mutation and must not invalidate an open form.
  if (
    JSON.stringify({ ...next, clock: store.clock }) !== JSON.stringify(store)
  ) {
    next.version++;
  }
  store = next;
  return structuredClone(store);
}
function addQueue(
  s: ReceptionState,
  visit: Visit,
  roomId: string,
  kind: QueueEntry["kind"],
  joinedAt = s.clock,
) {
  const existing = s.queues.find(
    (q) =>
      q.visitId === visit.id &&
      q.roomId === roomId &&
      q.date === today(s) &&
      activeQueue(q),
  );
  if (existing) return existing;
  const room = find(s.rooms, roomId);
  requireThat(room.available, "MSG-42");
  const number =
    s.queues.filter((q) => q.roomId === roomId && q.date === today(s)).length +
    1;
  const queue: QueueEntry = {
    id: id(s, "q"),
    visitId: visit.id,
    roomId,
    kind,
    date: today(s),
    number: `${roomId.replace(/^p/, "").toUpperCase()}-${String(number).padStart(3, "0")}`,
    status: "waiting",
    priority: visit.priority,
    joinedAt,
    position: 0,
  };
  s.queues.push(queue);
  insertByPriority(s, queue);
  return queue;
}
function insertByPriority(s: ReceptionState, entry: QueueEntry) {
  const list = s.queues
    .filter(
      (q) =>
        q.id !== entry.id &&
        q.roomId === entry.roomId &&
        q.kind === entry.kind &&
        q.date === entry.date &&
        q.status === "waiting",
    )
    .sort(queueSort);
  const index = list.findIndex(
    (q) =>
      !q.calledAt &&
      (priorityRank(q.priority) > priorityRank(entry.priority) ||
        (priorityRank(q.priority) === priorityRank(entry.priority) &&
          q.joinedAt > entry.joinedAt)),
  );
  list.splice(index < 0 ? list.length : index, 0, entry);
  list.forEach((q, i) => {
    q.position = i + 1;
  });
}
function leastBusyRoom(s: ReceptionState, serviceId: string) {
  return s.rooms
    .filter((r) => r.available && r.serviceIds.includes(serviceId))
    .sort(
      (a, b) =>
        s.queues.filter(
          (q) => q.roomId === a.id && q.date === today(s) && activeQueue(q),
        ).length -
        s.queues.filter(
          (q) => q.roomId === b.id && q.date === today(s) && activeQueue(q),
        ).length,
    )[0];
}
function selectedCharges(
  s: ReceptionState,
  ids: string[],
  ignoreTxId?: string,
) {
  requireThat(ids.length && new Set(ids).size === ids.length, "MSG-49", 400);
  const charges = ids.map((key) => find(s.charges, key, "MSG-50"));
  requireThat(
    charges.every((c) => c.status === "pending"),
    "MSG-49",
  );
  requireThat(
    charges.every(
      (c) =>
        c.patientId === charges[0].patientId &&
        c.contextId === charges[0].contextId,
    ),
    "MSG-49",
    400,
  );
  requireThat(
    !s.transactions.some(
      (t) =>
        t.id !== ignoreTxId &&
        (t.status === "pending" || t.needsReconciliation) &&
        t.chargeIds.some((key) => ids.includes(key)),
    ),
    "MSG-52",
  );
  for (const c of charges) {
    const a = s.appointments.find((a) => a.id === c.contextId);
    if (a) requireThat(a.status === "booked", "MSG-49");
    if (c.orderId) {
      const order = find(s.orders, c.orderId);
      requireThat(order.status === "pending", "MSG-49");
      requireActive(find(s.visits, order.visitId));
    }
  }
  return charges;
}
function recordPayment(
  s: ReceptionState,
  charges: Charge[],
  method: "cash" | "vnpay",
  cashReceived?: number,
  reference?: string,
) {
  const payment = {
    id: id(s, "PT"),
    patientId: charges[0].patientId,
    patientName: find(s.patients, charges[0].patientId).name,
    contextId: charges[0].contextId,
    items: charges.map((c) => ({
      chargeId: c.id,
      name: find(s.services, c.serviceId).name,
      amount: c.amount,
    })),
    amount: charges.reduce((sum, c) => sum + c.amount, 0),
    method,
    cashReceived,
    createdAt: s.clock,
    receivedBy: method === "cash" ? s.account.name : "VNPay",
    reference,
  };
  s.payments.unshift(payment);
  for (const charge of charges) {
    charge.status = "paid";
    charge.paymentId = payment.id;
    if (charge.orderId) {
      const order = find(s.orders, charge.orderId);
      order.status = "paid";
      const visit = find(s.visits, order.visitId);
      const room = leastBusyRoom(s, order.serviceId);
      requireThat(room, "MSG-41");
      // Reuse a compatible active queue for the same patient instead of creating duplicates.
      const existing = s.queues.find(
        (q) =>
          q.visitId === visit.id &&
          q.date === today(s) &&
          activeQueue(q) &&
          find(s.rooms, q.roomId).serviceIds.includes(order.serviceId),
      );
      order.roomId = existing?.roomId ?? room.id;
      addQueue(s, visit, order.roomId, "service");
    }
  }
  return payment;
}

/** Atomic, versioned command adapter. Rechecks state after latency; never partially commits a failed command. */
export async function executeReception(
  command: ReceptionCommand,
  expectedVersion: number,
): Promise<ReceptionState> {
  await delay();
  requireThat(store.version === expectedVersion, "MSG-42");
  const s = structuredClone(store);
  s.clock = clinicTimestamp(currentTime());
  expireState(s);
  switch (command.type) {
    case "save-patient": {
      const input = {
        ...command.input,
        name: command.input.name.trim().replace(/\s+/g, " "),
        phone: normalizePhone(command.input.phone),
        address: command.input.address.trim(),
        nationalId: command.input.nationalId.trim().toUpperCase(),
        email: command.input.email.trim(),
      };
      requireThat(
        Object.keys(validatePatient(input, today(s))).length === 0,
        "MSG-17",
        400,
      );
      requireThat(command.identityConfirmed, "MSG-17", 400);
      const matches = s.patients.filter(
        (p) =>
          p.id !== command.id &&
          ((p.phone === input.phone &&
            p.dob === input.dob &&
            normalizeText(p.name) === normalizeText(input.name)) ||
            (input.nationalId &&
              p.nationalId.toUpperCase() === input.nationalId)),
      );
      requireThat(!matches.length, "MSG-18");
      if (command.id)
        Object.assign(find(s.patients, command.id, "MSG-19"), input);
      else s.patients.unshift({ ...input, id: id(s, "BN") });
      break;
    }
    case "confirm-link": {
      const link = find(s.careLinks, command.id, "MSG-21"),
        patient = find(s.patients, link.patientId);
      requireThat(
        link.status === "pending" && (!patient.email || !patient.nationalId),
        "MSG-21",
      );
      requireThat(
        command.identityConfirmed && command.evidence.trim().length >= 5,
        "MSG-17",
        400,
      );
      link.status = "confirmed";
      link.evidence = command.evidence.trim();
      history(
        s,
        "Xác minh quan hệ chăm sóc",
        link.evidence,
        "Chờ xác minh",
        link.id,
      );
      break;
    }
    case "book": {
      find(s.patients, command.patientId, "MSG-29");
      const service = find(s.services, command.serviceId);
      requireThat(service.active && service.category === "exam", "MSG-29");
      requireThat(
        isFutureSlot(command.date, command.time, s.clock),
        "MSG-31",
        400,
      );
      const doctors = availableDoctors(
        s,
        command.date,
        command.time,
        service.id,
      );
      const doctor = command.doctorId
        ? doctors.find((d) => d.id === command.doctorId)
        : doctors[0];
      requireThat(doctor, "MSG-30");
      requireThat(
        !s.appointments.some(
          (a) =>
            a.patientId === command.patientId &&
            a.date === command.date &&
            a.time === command.time &&
            ["booked", "checked-in"].includes(a.status),
        ),
        "MSG-29",
      );
      const appointment = {
        id: id(s, "LH"),
        patientId: command.patientId,
        doctorId: doctor.id,
        serviceId: service.id,
        date: command.date,
        time: command.time,
        status: "booked" as const,
      };
      s.appointments.unshift(appointment);
      s.charges.push({
        id: id(s, "phi"),
        patientId: command.patientId,
        contextId: appointment.id,
        serviceId: service.id,
        amount: service.price,
        status: "pending",
        createdAt: s.clock,
      });
      break;
    }
    case "reassign": {
      const a = find(s.appointments, command.id, "MSG-29");
      requireThat(a.status === "booked", "MSG-32");
      requireThat(find(s.doctors, a.doctorId).absent, "MSG-32");
      const doctor = availableDoctors(
        s,
        a.date,
        a.time,
        a.serviceId,
        a.id,
      ).find((d) => d.id === command.doctorId && d.id !== a.doctorId);
      requireThat(doctor, "MSG-24");
      history(
        s,
        "Đổi bác sĩ lịch hẹn",
        "Bác sĩ vắng ca",
        a.doctorId,
        doctor.id,
      );
      a.doctorId = doctor.id;
      a.notice = "Đã tạo thông báo đổi bác sĩ cho bệnh nhân (mô phỏng).";
      break;
    }
    case "notify-unavailable": {
      const a = find(s.appointments, command.id, "MSG-29");
      requireThat(
        a.status === "booked" && find(s.doctors, a.doctorId).absent,
        "MSG-32",
      );
      requireThat(
        !availableDoctors(s, a.date, a.time, a.serviceId, a.id).length,
        "MSG-42",
      );
      a.notice =
        "Đã ghi nhận thông báo bác sĩ vắng; bệnh nhân có thể tự hủy lịch. Giờ hẹn giữ nguyên (mô phỏng).";
      break;
    }
    case "prepare-walk-in": {
      find(s.patients, command.patientId, "MSG-18");
      const service = find(s.services, command.serviceId);
      requireThat(service.active && service.category === "exam", "MSG-39", 400);
      requireThat(
        !s.visits.some(
          (v) =>
            v.patientId === command.patientId &&
            ["waiting", "examining", "awaiting"].includes(v.status),
        ),
        "MSG-37",
      );
      const contextId = `walkin-${command.patientId}-${today(s)}`;
      requireThat(
        !s.charges.some(
          (c) => c.contextId === contextId && c.status !== "cancelled",
        ),
        "MSG-42",
      );
      requireThat(onDuty(s).length, "MSG-41");
      s.charges.push({
        id: id(s, "phi"),
        patientId: command.patientId,
        contextId,
        serviceId: service.id,
        amount: service.price,
        status: "pending",
        createdAt: s.clock,
      });
      break;
    }
    case "check-in": {
      requireThat(command.present, "MSG-39", 400);
      find(s.patients, command.patientId, "MSG-18");
      requireThat(command.priority in PRIORITIES, "MSG-39", 400);
      if (command.returnVisitId) {
        const visit = find(s.visits, command.returnVisitId);
        requireThat(
          visit.patientId === command.patientId &&
            visit.status === "awaiting" &&
            withinReturnWindow(visit.firstCheckIn, today(s)),
          "MSG-37",
        );
        requireThat(
          !s.queues.some(
            (q) =>
              q.visitId === visit.id && q.date === today(s) && activeQueue(q),
          ),
          "MSG-37",
        );
        visit.priority = command.priority;
        const paid = s.orders.filter(
          (o) => o.visitId === visit.id && o.status === "paid",
        );
        if (paid.length)
          for (const o of paid) {
            const room = leastBusyRoom(s, o.serviceId);
            requireThat(room, "MSG-41");
            o.roomId = room.id;
            addQueue(s, visit, room.id, "service");
          }
        else if (latestRoundReady(s, visit.id)) {
          const doctor = find(s.doctors, visit.doctorId);
          requireThat(
            onDuty(s).some((d) => d.id === doctor.id),
            "MSG-41",
          );
          const last = s.orders
            .filter((o) => o.visitId === visit.id && o.resultAt)
            .map((o) => o.resultAt!)
            .sort()
            .at(-1);
          addQueue(s, visit, doctor.roomId, "review", last ?? s.clock);
        } else mockFail(409, "MSG-37"); // còn chỉ định chưa thu phí: thu phí dịch vụ trước
        break;
      }
      requireThat(
        !s.visits.some(
          (v) =>
            v.patientId === command.patientId &&
            ["waiting", "examining", "awaiting"].includes(v.status),
        ),
        "MSG-37",
      );
      const a = command.appointmentId
        ? find(s.appointments, command.appointmentId, "MSG-37")
        : null;
      if (a)
        requireThat(
          a.patientId === command.patientId &&
            a.status === "booked" &&
            a.date === today(s),
          "MSG-37",
        );
      const fee = s.charges.find(
        (c) =>
          c.patientId === command.patientId &&
          c.contextId ===
            (a?.id ?? `walkin-${command.patientId}-${today(s)}`) &&
          c.status === "paid",
      );
      requireThat(fee, "MSG-37");
      const doctors = onDuty(s).filter((d) =>
        find(s.rooms, d.roomId).serviceIds.includes(fee.serviceId),
      );
      const doctor = a
        ? doctors.find((d) => d.id === a.doctorId)
        : doctors.sort(
            (x, y) =>
              s.queues.filter(
                (q) =>
                  q.roomId === x.roomId &&
                  q.date === today(s) &&
                  activeQueue(q),
              ).length -
              s.queues.filter(
                (q) =>
                  q.roomId === y.roomId &&
                  q.date === today(s) &&
                  activeQueue(q),
              ).length,
          )[0];
      requireThat(doctor, "MSG-41");
      const visit: Visit = {
        id: id(s, "LK"),
        patientId: command.patientId,
        doctorId: doctor.id,
        firstCheckIn: today(s),
        status: "waiting",
        priority: command.priority,
        followUp: false,
        appointmentId: a?.id,
      };
      s.visits.unshift(visit);
      if (a) a.status = "checked-in";
      else fee.contextId = visit.id;
      addQueue(s, visit, doctor.roomId, "exam");
      break;
    }
    case "call-next": {
      const room = find(s.rooms, command.roomId);
      requireThat(
        room.category === "exam" &&
          room.available &&
          onDuty(s).some((d) => d.roomId === room.id),
        "MSG-42",
      );
      requireThat(!roomBusy(s, room.id), "MSG-42");
      const entry = s.queues
        .filter(
          (q) =>
            q.roomId === room.id &&
            q.kind === command.kind &&
            q.date === today(s) &&
            q.status === "waiting",
        )
        .sort(queueSort)[0];
      requireThat(entry, "MSG-41");
      requireThat(
        !s.queues.some(
          (q) =>
            q.visitId === entry.visitId &&
            q.date === today(s) &&
            (q.status === "serving" || (q.status === "waiting" && q.calledAt)),
        ),
        "MSG-42",
      );
      entry.calledAt = s.clock;
      entry.callerId = s.account.id;
      break;
    }
    case "queue-action": {
      const q = find(s.queues, command.id),
        visit = find(s.visits, q.visitId);
      requireActive(visit);
      requireThat(
        q.date === today(s) &&
          q.kind !== "service" &&
          q.callerId === s.account.id,
        "MSG-40",
        403,
      );
      if (command.action === "return") {
        requireThat(q.status === "absent", "MSG-42");
        q.status = "waiting";
        q.calledAt = undefined;
        q.joinedAt = s.clock;
        insertByPriority(s, q);
      } else {
        requireThat(q.status === "waiting" && q.calledAt, "MSG-42");
        if (command.action === "absent") {
          q.status = "absent";
          q.calledAt = undefined;
        } else {
          q.status = "serving";
          visit.status = "examining";
        }
      }
      break;
    }
    case "reorder": {
      const q = find(s.queues, command.id);
      requireThat(
        q.date === today(s) && q.status === "waiting" && !q.calledAt,
        "MSG-42",
      );
      const list = s.queues
        .filter(
          (x) =>
            x.roomId === q.roomId &&
            x.kind === q.kind &&
            x.date === today(s) &&
            x.status === "waiting",
        )
        .sort(queueSort);
      requireThat(
        Number.isInteger(command.position) &&
          command.position >= 1 &&
          command.position <= list.length &&
          command.reason.trim().length >= 5 &&
          command.priority in PRIORITIES,
        "MSG-39",
        400,
      );
      requireThat(
        !list.slice(command.position - 1).some((x) => x.calledAt),
        "MSG-42",
      );
      const before = `${q.number}, vị trí ${q.position}, ${PRIORITIES[q.priority]}`;
      list.splice(list.indexOf(q), 1);
      list.splice(command.position - 1, 0, q);
      list.forEach((x, i) => {
        x.position = i + 1;
      });
      q.priority = command.priority;
      find(s.visits, q.visitId).priority = command.priority;
      history(
        s,
        "Điều chỉnh hàng đợi",
        command.reason.trim(),
        before,
        `${q.number}, vị trí ${q.position}, ${PRIORITIES[q.priority]}`,
      );
      break;
    }
    case "move-room": {
      const q = find(s.queues, command.id),
        visit = find(s.visits, q.visitId),
        room = find(s.rooms, command.roomId);
      requireActive(visit);
      requireThat(
        q.date === today(s) &&
          q.status === "waiting" &&
          !q.calledAt &&
          room.id !== q.roomId &&
          room.available,
        "MSG-42",
      );
      requireThat(command.reason.trim().length >= 5, "MSG-39", 400);
      // Examination-room changes also transfer clinical responsibility; use explicit absent-doctor workflow.
      requireThat(q.kind === "service", "MSG-42");
      const orders = s.orders.filter(
        (o) =>
          o.visitId === visit.id &&
          o.roomId === q.roomId &&
          o.status === "paid",
      );
      requireThat(
        orders.length &&
          orders.every((o) => room.serviceIds.includes(o.serviceId)),
        "MSG-42",
      );
      const before = q.roomId;
      q.status = "closed";
      for (const order of orders) order.roomId = room.id;
      addQueue(s, visit, room.id, "service");
      history(
        s,
        "Chuyển phòng dịch vụ",
        command.reason.trim(),
        before,
        room.id,
      );
      break;
    }
    case "transfer-doctor": {
      const visit = find(s.visits, command.visitId);
      requireActive(visit);
      requireThat(command.reason.trim().length >= 5, "MSG-39", 400);
      requireThat(find(s.doctors, visit.doctorId).absent, "MSG-42");
      const doctor = onDuty(s).find((d) => d.id === command.doctorId);
      requireThat(doctor, "MSG-41");
      requireThat(
        !s.queues.some((q) => q.visitId === visit.id && q.status === "serving"),
        "MSG-42",
      );
      const before = visit.doctorId;
      visit.doctorId = doctor.id;
      for (const q of s.queues.filter(
        (q) =>
          q.visitId === visit.id &&
          q.date === today(s) &&
          activeQueue(q) &&
          q.kind !== "service",
      )) {
        q.status = "closed";
        addQueue(s, visit, doctor.roomId, q.kind, q.joinedAt);
      }
      history(
        s,
        "Chuyển bác sĩ phụ trách và gửi thông báo",
        command.reason.trim(),
        before,
        doctor.id,
      );
      break;
    }
    case "pay-cash": {
      const charges = selectedCharges(s, command.chargeIds),
        total = charges.reduce((n, c) => n + c.amount, 0);
      requireThat(
        command.confirmed &&
          Number.isSafeInteger(command.cashReceived) &&
          command.cashReceived >= total,
        "MSG-39",
        400,
      );
      recordPayment(s, charges, "cash", command.cashReceived);
      break;
    }
    case "start-qr": {
      const charges = selectedCharges(s, command.chargeIds);
      s.transactions.unshift({
        id: id(s, "QR"),
        chargeIds: charges.map((c) => c.id),
        amount: charges.reduce((n, c) => n + c.amount, 0),
        status: "pending",
        needsReconciliation: false,
        expiresAt: Date.now() + 15 * 60000,
      });
      break;
    }
    case "demo-gateway": {
      const tx = find(s.transactions, command.id, "MSG-50");
      if (tx.status === "succeeded") break; // idempotent gateway callback
      requireThat(tx.status === "pending" || tx.needsReconciliation, "MSG-53");
      if (command.outcome === "unconfirmed") {
        tx.status = "failed";
        tx.needsReconciliation = true;
        break;
      }
      if (command.outcome === "failed") {
        tx.status = "failed";
        tx.needsReconciliation = false;
        break;
      }
      const payment = recordPayment(
        s,
        selectedCharges(s, tx.chargeIds, tx.id),
        "vnpay",
        undefined,
        `DEMO-VNP-${tx.id}`,
      );
      tx.status = "succeeded";
      tx.needsReconciliation = false;
      tx.paymentId = payment.id;
      break;
    }
    case "read-notice":
      find(s.notices, command.id, "MSG-50").read = true;
      break;
    case "avatar":
      requireThat(
        command.dataUrl === null ||
          (/^data:image\/(png|jpeg|webp);base64,/.test(command.dataUrl) &&
            command.dataUrl.length <= 2800000),
        "MSG-08",
        400,
      );
      s.account.avatar = command.dataUrl;
      break;
  }
  s.version++;
  store = s;
  return structuredClone(s);
}
export async function changeReceptionPassword(current: string, next: string) {
  await delay();
  const hash = async (input: string) =>
    Array.from(
      new Uint8Array(
        await crypto.subtle.digest("SHA-256", new TextEncoder().encode(input)),
      ),
    )
      .map((b) => b.toString(16).padStart(2, "0"))
      .join("");
  requireThat(
    (await hash(current)) === (passwordHash ?? (await hash("Demo12345"))),
    "MSG-08",
    400,
  );
  requireThat(isPasswordValid(next) && current !== next, "MSG-08", 400);
  passwordHash = await hash(next);
}
export const receptionDemoExpiry = (visit: Visit) =>
  addDays(visit.firstCheckIn, 6);
