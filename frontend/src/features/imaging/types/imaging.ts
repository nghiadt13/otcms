export type OrderStatus =
  "Pending Payment" | "Paid" | "Results Available" | "Cancelled";

export type QueueStatus =
  "Waiting" | "Being Served" | "Done" | "Absent" | "Closed";

export type VisitStatus = "Awaiting Conclusion" | "Completed" | "Incomplete";

export type ImagingOrder = {
  id: string;
  patientId: string;
  patient: string;
  age: number;
  sex: string;
  visitId: string;
  service: string;
  modality: string;
  room: string;
  doctor: string;
  reason: string;
  bodyArea: string;
  instructions: string;
  status: OrderStatus;
  round: number;
  createdAt: string;
  paidAt?: string;
  resultAt?: string;
  kind: "imaging" | "lab";
  cancellationReason?: string;
  issue?: { kind: string; reason: string; at: string };
};

export type QueueEntry = {
  id: string;
  visitId: string;
  room: string;
  number: string;
  priority: number;
  priorityLabel: string;
  joinedAt: string;
  status: QueueStatus;
  calledBy?: string;
  calledOn?: string;
};

export type Visit = {
  id: string;
  patientId: string;
  patient: string;
  doctor: string;
  doctorRoom: string;
  latestRound: number;
  status: VisitStatus;
  firstCheckIn: string;
  reviewQueued: boolean;
  sheetVersion: number;
};

export type StudyVersion = {
  uid: string;
  version: number;
  series: number;
  files: number;
  size: number;
  uploadedAt: string;
  reason?: string;
  replaced?: boolean;
};

export type UploadDraft = {
  patientId: string;
  orderId: string;
  uid: string;
  series: number;
  files: { name: string; size: number; valid: boolean }[];
  confirmed: boolean;
  reason: string;
  errors: string[];
  checkedAt: string;
  sent: boolean;
};

export type PersonalNotification = {
  id: string;
  title: string;
  text: string;
  time: string;
  read: boolean;
  href?: string;
  category: string;
};

export type ImagingState = {
  orders: ImagingOrder[];
  queue: QueueEntry[];
  visits: Visit[];
  studies: Record<string, StudyVersion[]>;
  drafts: Record<string, UploadDraft>;
  notifications: PersonalNotification[];
  profile: { username: string; name: string; avatar: string };
  activity: { id: string; orderId: string; text: string; at: string }[];
};
