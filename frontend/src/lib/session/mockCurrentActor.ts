import type { AccountType } from "@/lib/domain/clinicRoles";

// GIAI ĐOẠN GIẢ: chưa có `/api/v1/me`. Chỉ dùng để hiển thị tên và vai trò trên khung
// (header, menu tài khoản). Không dùng để ẩn/hiện nút: nút theo `allowedActions` trong
// response, quyền thật do backend kiểm. Xóa file này khi có `/api/v1/me`.

export type MockActorArea = "staff" | "patient";

export interface MockCurrentActor {
  accountId: string;
  role: AccountType;
  displayName: string;
  /** Chỉ tài khoản bệnh nhân có email; nhân viên đăng nhập bằng tên đăng nhập. */
  email?: string;
}

const MOCK_ACTORS: Record<MockActorArea, MockCurrentActor> = {
  patient: {
    accountId: "acc-an",
    role: "PATIENT",
    displayName: "Nguyễn Văn An",
    email: "an@example.com",
  },
  staff: {
    accountId: "acc-thu-ngan",
    role: "RECEPTIONIST",
    displayName: "Trần Thu Ngân",
  },
};

/** Người đang đăng nhập giả cho một khu. Trả bản sao để nơi gọi không sửa kho giả. */
export function getMockCurrentActor(area: MockActorArea): MockCurrentActor {
  return { ...MOCK_ACTORS[area] };
}
