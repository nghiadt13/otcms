export type Patient = {
  id: string;
  name: string;
  dob: string;
  sex: "Nam" | "Nữ" | "Khác";
  phone: string;
  address: string;
  nationalId: string;
  email: string;
};
export type PatientSearchQuery = {
  q: string;
  dob?: string;
};
export type Priority =
  | "normal"
  | "emergency"
  | "child"
  | "pregnant"
  | "disability"
  | "elderly"
  | "merit";
export const PRIORITIES: Record<Priority, string> = {
  normal: "Thông thường",
  emergency: "Cấp cứu",
  child: "Trẻ dưới 6 tuổi",
  pregnant: "Phụ nữ mang thai",
  disability: "Khuyết tật nặng",
  elderly: "Từ 75 tuổi",
  merit: "Người có công",
};
export type Room = {
  id: string;
  name: string;
  category: "exam" | "imaging" | "lab";
  available: boolean;
  serviceIds: string[];
};
export type Doctor = {
  id: string;
  name: string;
  roomId: string;
  absent: boolean;
};
export type Shift = {
  id: string;
  staffId: string;
  date: string;
  start: string;
  end: string;
  roomId: string;
};
export type Service = {
  id: string;
  name: string;
  category: Room["category"];
  price: number;
  active: boolean;
};
export type Appointment = {
  id: string;
  patientId: string;
  doctorId: string;
  serviceId: string;
  date: string;
  time: string;
  status: "booked" | "checked-in" | "cancelled" | "no-show";
  notice?: string;
  unavailabilityDiscussedAt?: string;
};
export type VisitStatus =
  "waiting" | "examining" | "awaiting" | "completed" | "incomplete" | "left";
export type Visit = {
  id: string;
  patientId: string;
  doctorId: string;
  appointmentId?: string;
  firstCheckIn: string;
  status: VisitStatus;
  priority: Priority;
  followUp: boolean;
  previousVisitId?: string;
};
export type QueueEntry = {
  id: string;
  visitId: string;
  roomId: string;
  kind: "exam" | "service" | "review";
  number: string;
  joinedAt: string;
  date: string;
  status: "waiting" | "serving" | "absent" | "done" | "closed";
  priority: Priority;
  position: number;
  calledAt?: string;
  callerId?: string;
};
export type Order = {
  id: string;
  visitId: string;
  serviceId: string;
  roomId: string;
  round: number;
  status: "pending" | "paid" | "results" | "cancelled";
  resultAt?: string;
  cancellationReason?: string;
};
export type Charge = {
  id: string;
  patientId: string;
  contextId: string;
  serviceId: string;
  orderId?: string;
  amount: number;
  status: "pending" | "paid" | "cancelled";
  createdAt: string;
  paymentId?: string;
};
export type Payment = {
  id: string;
  patientId: string;
  patientName: string;
  contextId: string;
  items: { chargeId: string; name: string; amount: number }[];
  amount: number;
  cashReceived?: number;
  method: "cash" | "vnpay";
  createdAt: string;
  receivedBy: string;
  reference?: string;
};
export type QrTransaction = {
  id: string;
  chargeIds: string[];
  amount: number;
  status: "pending" | "succeeded" | "failed";
  expiresAt: number;
  needsReconciliation: boolean;
  paymentId?: string;
};
export type CareLink = {
  id: string;
  patientId: string;
  accountName: string;
  accountEmail: string;
  relationship: string;
  status: "pending" | "confirmed";
  evidence?: string;
};
export type ReceptionNotice = {
  id: string;
  title: string;
  body: string;
  read: boolean;
  href?: string;
  createdAt: string;
};
export type ResultSheet = {
  id: string;
  visitId: string;
  version: number;
  replaced: boolean;
  issuedAt: string;
  diagnosis: string;
  conclusion: string;
  advice: string;
  doctorName: string;
};
export type ReceptionState = {
  version: number;
  clock: string;
  sequence: number;
  patients: Patient[];
  doctors: Doctor[];
  rooms: Room[];
  services: Service[];
  shifts: Shift[];
  appointments: Appointment[];
  visits: Visit[];
  queues: QueueEntry[];
  orders: Order[];
  charges: Charge[];
  payments: Payment[];
  transactions: QrTransaction[];
  careLinks: CareLink[];
  notices: ReceptionNotice[];
  resultSheets: ResultSheet[];
  audit: {
    id: string;
    at: string;
    actor: string;
    action: string;
    reason: string;
    before: string;
    after: string;
  }[];
  account: {
    id: string;
    name: string;
    username: string;
    phone: string;
    email: string;
    avatar: string | null;
  };
};
export type PatientInput = Omit<Patient, "id">;
export type ReceptionCommand =
  | {
      type: "save-patient";
      input: PatientInput;
      id?: string;
      identityConfirmed: boolean;
    }
  | {
      type: "confirm-link";
      id: string;
      evidence: string;
      identityConfirmed: boolean;
    }
  | {
      type: "book";
      patientId: string;
      serviceId: string;
      date: string;
      time: string;
      doctorId: string;
    }
  | { type: "reassign"; id: string; doctorId: string; patientAgreed?: boolean }
  | { type: "notify-unavailable"; id: string }
  | { type: "prepare-walk-in"; patientId: string; serviceId: string }
  | {
      type: "check-in";
      patientId: string;
      appointmentId?: string;
      returnVisitId?: string;
      priority: Priority;
      present: boolean;
    }
  | { type: "call-next"; roomId: string; kind: "exam" | "review" }
  | {
      type: "queue-action";
      id: string;
      action: "arrived" | "absent" | "return";
    }
  | {
      type: "reorder";
      id: string;
      position: number;
      priority: Priority;
      reason: string;
    }
  | { type: "move-room"; id: string; roomId: string; reason: string }
  | {
      type: "transfer-doctor";
      visitId: string;
      doctorId: string;
      reason: string;
    }
  | {
      type: "pay-cash";
      chargeIds: string[];
      cashReceived: number;
      confirmed: boolean;
    }
  | { type: "start-qr"; chargeIds: string[] }
  | {
      type: "demo-gateway";
      id: string;
      outcome: "succeeded" | "failed" | "unconfirmed";
    }
  | { type: "read-notice"; id: string }
  | { type: "avatar"; dataUrl: string | null };
