// GIẢ LẬP PHIÊN cho lớp API giả `/api/v1/me`. Xóa file khi BE trả tài khoản theo phiên thật.
// Đăng nhập thật vẫn đi qua Supabase; file này chỉ chọn loại tài khoản giả đi kèm.
// Lưu trong localStorage như phiên Supabase: còn sau khi tải lại và giữa các tab; đăng xuất
// đi qua /login thì xóa. Đăng nhập bệnh nhân ghi "PATIENT"; không có giá trị thì coi là tài
// khoản nhân viên/quản trị (kho giả của khu quản trị).

export type MockSignedInKind = "STAFF" | "PATIENT";

const STORAGE_KEY = "otcms.mock.signedInKind";

export function getMockSignedInKind(): MockSignedInKind {
  return readStoredKind() === "PATIENT" ? "PATIENT" : "STAFF";
}

export function setMockSignedInKind(kind: MockSignedInKind): void {
  writeStoredKind(kind);
}

/** Mở màn đăng nhập nghĩa là chưa có phiên giả (đăng xuất đi qua màn này). */
export function clearMockSignedInKind(): void {
  writeStoredKind(null);
}

function readStoredKind(): string | null {
  if (typeof window === "undefined") return null;
  try {
    return window.localStorage.getItem(STORAGE_KEY);
  } catch {
    // Trình duyệt chặn bộ nhớ cục bộ: coi như chưa có phiên giả, dùng tài khoản mặc định.
    return null;
  }
}

function writeStoredKind(kind: MockSignedInKind | null): void {
  if (typeof window === "undefined") return;
  try {
    if (kind === null) window.localStorage.removeItem(STORAGE_KEY);
    else window.localStorage.setItem(STORAGE_KEY, kind);
  } catch {
    // Không ghi được thì phiên giả không lưu qua lần tải lại; tài khoản mặc định vẫn chạy.
  }
}
