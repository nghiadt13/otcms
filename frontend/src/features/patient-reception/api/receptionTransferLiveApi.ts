import { apiRequest } from "@/lib/api/client";

// Đổi bác sĩ phụ trách của lượt chờ (UC-30) và đổi phòng của chỉ định cận lâm sàng (UC-31).
// Lỗi nghiệp vụ đi qua ApiError; màn hình đổi sang câu MSG bằng messageFromError, không viết chuỗi lỗi ở đây.

/** Kết quả đổi bác sĩ (BE: VisitLifecycleDtos.VisitQueueTicket trong `data`). */
export type AttendingDoctorTransferResult = {
  messageCode: string;
  data: {
    visitId: string;
    status: string;
    queueNumber: number | null;
    doctorProfileId: string;
    doctorName: string;
    roomId: string | null;
    roomLabel: string | null;
  };
};

/** Kết quả đổi phòng chỉ định (BE: VisitLifecycleDtos.ServiceOrderRoomTransfer trong `data`). */
export type ServiceOrderRoomTransferResult = {
  messageCode: string;
  data: { orderId: string; roomId: string; previousRoomId: string | null };
};

/** Chuyển lượt đang chờ sang bác sĩ khác đang trong ca. BE: MSG-41 bác sĩ không trong ca, MSG-42 trạng thái không cho phép. */
export function transferVisitAttendingDoctor(input: {
  visitId: string;
  doctorProfileId: string;
  reason: string;
}) {
  return apiRequest<AttendingDoctorTransferResult>(
    `/api/v1/visits/${encodeURIComponent(input.visitId)}/attending-doctor-transfers`,
    {
      method: "POST",
      body: JSON.stringify({
        doctorProfileId: input.doctorProfileId,
        reason: input.reason.trim(),
      }),
    },
  );
}

/** Đổi phòng thực hiện của chỉ định cận lâm sàng chưa thực hiện. BE: MSG-41 phòng/chỉ định không có, MSG-42 không đổi được. */
export function transferServiceOrderRoom(input: {
  orderId: string;
  newRoomId: string;
  reason: string;
}) {
  return apiRequest<ServiceOrderRoomTransferResult>(
    `/api/v1/service-orders/${encodeURIComponent(input.orderId)}/room-transfers`,
    {
      method: "POST",
      body: JSON.stringify({
        newRoomId: input.newRoomId,
        reason: input.reason.trim(),
      }),
    },
  );
}
