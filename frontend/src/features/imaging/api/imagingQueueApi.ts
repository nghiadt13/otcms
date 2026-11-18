import { apiRequest } from "@/lib/api/client";

/** Trạng thái một dòng hàng chờ phòng dịch vụ (stage SERVICE). */
export type ServiceQueueStatus =
  | "WAITING"
  | "CALLED"
  | "IN_EXAMINATION"
  | "ABSENT";

export type ServiceWorklistOrder = {
  /** Với chẩn đoán hình ảnh cũng là mã yêu cầu dùng cho bắt đầu chụp / tải study. */
  orderId: string;
  orderCode: string;
  serviceName: string;
  serviceType: string;
  status: string;
  /** false: KTV không được tiếp nhận (D7, MSG-95). */
  paid: boolean;
  clinicalReason: string | null;
  bodyArea: string | null;
  instructions: string | null;
  executionStatus: string | null;
};

export type ServiceWorklistEntry = {
  entryId: string;
  visitId: string;
  queueNumber: number | null;
  status: ServiceQueueStatus;
  sortOrder: number;
  queuedAt: string | null;
  calledAt: string | null;
  patient: {
    patientProfileId: string | null;
    fullName: string | null;
    dateOfBirth: string | null;
    age: number | null;
    gender: string | null;
  };
  orders: ServiceWorklistOrder[];
};

export type ServiceWorklist = { roomId: string; entries: ServiceWorklistEntry[] };

/** Hàng chờ thật của phòng (UC-46): thứ tự do BE xếp, KTV không tự đổi vị trí. */
export function getServiceWorklist(roomId: string, signal?: AbortSignal) {
  return apiRequest<ServiceWorklist>(
    `/api/v1/queue/rooms/${encodeURIComponent(roomId)}/service-worklist`,
    { signal },
  );
}

/** Gọi bệnh nhân kế tiếp. BE trả MSG-42 khi đang có người được gọi hoặc đang chụp, MSG-41 khi hết hàng. */
export function callNextService(roomId: string) {
  return apiRequest<{ messageCode: string }>(
    `/api/v1/queue/rooms/${encodeURIComponent(roomId)}/call-next?stage=SERVICE`,
    { method: "POST" },
  );
}

export function markServiceEntryAbsent(entryId: string) {
  return apiRequest<{ messageCode: string }>(
    `/api/v1/queue/entries/${encodeURIComponent(entryId)}/absence`,
    { method: "POST" },
  );
}

/** Đưa người vắng quay lại cuối hàng; lý do bắt buộc (MSG-39 khi rỗng). */
export function returnServiceEntry(entryId: string, reason: string) {
  return apiRequest<{ messageCode: string }>(
    `/api/v1/queue/entries/${encodeURIComponent(entryId)}/return`,
    {
      method: "POST",
      body: JSON.stringify({ beforeEntryId: null, reason: reason.trim() }),
    },
  );
}

/**
 * UC-46 AF3: KTV không thực hiện được chỉ định (chống chỉ định, bệnh nhân từ chối, máy hỏng...). Bệnh nhân rời hàng,
 * chỉ định giữ nguyên để bác sĩ quyết định; lý do bắt buộc.
 */
export function reportCannotPerform(requestId: string, reason: string) {
  return apiRequest<void>(
    `/api/v1/imaging-requests/${encodeURIComponent(requestId)}/cannot-perform`,
    { method: "POST", body: JSON.stringify({ reason: reason.trim() }) },
  );
}
