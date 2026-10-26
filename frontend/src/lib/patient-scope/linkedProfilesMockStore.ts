import type { LinkedProfile } from "./types";

// KHO GIẢ hồ sơ đã gắn, dùng chung cho mọi màn cần phạm vi "người khám" (đặt lịch, tiến
// độ, kết quả, đơn thuốc, ảnh, thanh toán). Thư mục này chỉ đọc và báo đổi: KHÔNG đặt
// thao tác nghiệp vụ (gắn, tạo, bỏ gắn, luật quyền) ở đây. Feature `patient-profiles`
// ghi qua `replaceLinkedProfilesMock` ở phase 02; các màn đang mở tự tải lại.

const SELF: LinkedProfile = {
  id: "5c1e7a20-1f3b-4c8d-9e2a-0b7d6c5a4f01",
  patientCode: "BN-000123",
  fullName: "Nguyễn Văn An",
  dateOfBirth: "1992-07-21",
  sex: "MALE",
  relationship: "SELF",
};

const PARENT: LinkedProfile = {
  id: "5c1e7a20-1f3b-4c8d-9e2a-0b7d6c5a4f02",
  patientCode: "BN-000456",
  fullName: "Nguyễn Thị Lan",
  dateOfBirth: "1936-03-14",
  sex: "FEMALE",
  relationship: "PARENT",
};

const CHILD: LinkedProfile = {
  id: "5c1e7a20-1f3b-4c8d-9e2a-0b7d6c5a4f03",
  patientCode: "BN-000789",
  fullName: "Nguyễn Gia Huy",
  dateOfBirth: "2020-05-09",
  sex: "MALE",
  relationship: "CHILD",
};

/** Biến thể dữ liệu cho test và demo. `default`: An, Lan (Cha/Mẹ), Huy (Con). */
export const LINKED_PROFILE_VARIANTS: Record<
  "default" | "selfOnly" | "empty",
  LinkedProfile[]
> = {
  default: [SELF, PARENT, CHILD],
  selfOnly: [SELF],
  empty: [],
};

export type LinkedProfilesVariant = keyof typeof LINKED_PROFILE_VARIANTS;

type Listener = () => void;

let records: LinkedProfile[] = structuredClone(LINKED_PROFILE_VARIANTS.default);
let failList = false;
const listeners = new Set<Listener>();

function notify() {
  for (const listener of listeners) listener();
}

/** Chỉ dùng trong test và demo: đặt lại kho; `failList` làm lần tải kế tiếp lỗi 500 MSG-04. */
export function resetLinkedProfilesMock(
  options: { variant?: LinkedProfilesVariant; failList?: boolean } = {},
): void {
  records = structuredClone(
    LINKED_PROFILE_VARIANTS[options.variant ?? "default"],
  );
  failList = options.failList ?? false;
  notify();
}

/** Bản sao kho hiện tại; nơi gọi sửa không ảnh hưởng kho. */
export function getLinkedProfileRecords(): LinkedProfile[] {
  return structuredClone(records);
}

export function isLinkedProfilesMockFailing(): boolean {
  return failList;
}

/** Thay toàn bộ kho và báo các màn đang đọc tải lại. Chỉ feature ghi hồ sơ được gọi. */
export function replaceLinkedProfilesMock(next: LinkedProfile[]): void {
  records = structuredClone(next);
  notify();
}

/** Đăng ký nhận báo khi kho đổi. Trả về hàm hủy đăng ký (gọi khi unmount). */
export function subscribeLinkedProfiles(listener: Listener): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}
