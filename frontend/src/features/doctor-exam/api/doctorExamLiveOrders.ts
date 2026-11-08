import { ApiError } from "@/lib/api/apiError";
import { apiRequest } from "@/lib/api/client";
import type { ServiceOrderStatus } from "@/lib/domain/clinicVisit";
import type {
  CancelCause,
  CatalogKind,
  CatalogService,
  ImagingResult,
  OrderDraftItem,
  ParaclinicalOrder,
} from "../types/doctorExam";

// Clinical ServiceOrders persist before payment/dispatch; Diagnostic supplies subsequent work state.

type CatalogDto = {
  id: string;
  name: string;
  description: string | null;
  type: CatalogKind | "EXAMINATION";
  price: number;
  active: boolean;
  modality: string | null;
};

export async function listCatalog(
  signal?: AbortSignal,
): Promise<CatalogService[]> {
  const result = await apiRequest<{ items: CatalogDto[] }>(
    "/api/v1/staff/clinic-services",
    { signal },
  );
  return result.items
    .filter((item) => item.active)
    .map((item) => ({
      id: item.id,
      name: item.name,
      kind: item.type === "LABORATORY" ? "LABORATORY" : item.type,
      price: item.price,
      modality: item.modality,
      description: item.description ?? "",
    }));
}

type RequestStatusDto = {
  requestId: string;
  imagingType: string;
  status:
    "PENDING" | "IN_PROGRESS" | "RESULT_AVAILABLE" | "REVIEWED" | "CANCELLED";
  version: number;
};
type HistoryItemDto = {
  requestId: string;
  studyRef: string | null;
  findingsText: string | null;
  findingsVersion: number | null;
  reportId: string | null;
};

const DIAGNOSTIC_STATUS: Record<
  RequestStatusDto["status"],
  { status: ServiceOrderStatus; note: string | null }
> = {
  PENDING: { status: "PAID", note: "Đã gửi KTV, chờ chụp" },
  IN_PROGRESS: { status: "PAID", note: "Đang chụp" },
  RESULT_AVAILABLE: {
    status: "RESULTS_AVAILABLE",
    note: "Đã có ảnh, chờ bác sĩ xem",
  },
  REVIEWED: { status: "RESULTS_AVAILABLE", note: "Đã xem ảnh" },
  CANCELLED: { status: "CANCELLED", note: null },
};

type ClinicalOrderDto = {
  orderId: string;
  orderCode: string;
  roundNumber?: number;
  orderingRoundId?: string;
  serviceId: string;
  serviceName: string;
  clinicalReason: string;
  bodyArea: string | null;
  priceSnapshot: number;
  orderStatus: "ORDERED" | "RESULTS_AVAILABLE" | "CANCELLED";
  paymentStatus: "PENDING_PAYMENT" | "PAID";
  diagnostic?: RequestStatusDto | null;
};

function clinicalOrder(
  order: ClinicalOrderDto,
  round?: number,
): ParaclinicalOrder {
  const request = order.diagnostic;
  const mapped = request ? DIAGNOSTIC_STATUS[request.status] : null;
  return {
    id: order.orderId,
    code: order.orderCode,
    round: order.roundNumber ?? round ?? null,
    kind: "IMAGING",
    serviceName: order.serviceName,
    bodyArea: order.bodyArea ?? "",
    clinicalReason: order.clinicalReason,
    price: order.priceSnapshot,
    status:
      order.orderStatus === "CANCELLED"
        ? "CANCELLED"
        : order.paymentStatus !== "PAID"
          ? "PENDING_PAYMENT"
          : (mapped?.status ??
            (order.orderStatus === "ORDERED" ? "PAID" : order.orderStatus)),
    statusNote:
      order.orderStatus === "CANCELLED"
        ? null
        : order.paymentStatus !== "PAID"
          ? "Chờ thanh toán tại lễ tân"
          : (mapped?.note ?? "Đã thanh toán, chờ lễ tân gửi KTV"),
    cancellable: request?.status === "PENDING",
    imaging: request
      ? {
          requestId: request.requestId,
          version: request.version,
          studyRef: null,
          reviewed: request.status === "REVIEWED",
          findingsText: "",
          findingsVersion: null,
          findingsCompleted: false,
          reportId: null,
        }
      : null,
  };
}

export async function listOrders(
  visitId: string,
  signal?: AbortSignal,
): Promise<ParaclinicalOrder[]> {
  const id = encodeURIComponent(visitId);
  const result = await apiRequest<{ items: ClinicalOrderDto[] }>(
    `/api/v1/visits/${id}/imaging-orders`,
    { signal },
  );
  const orders = result.items.map((order) => clinicalOrder(order));
  if (!orders.some((order) => order.imaging)) return orders;
  const history = await apiRequest<{ items: HistoryItemDto[] }>(
    `/api/v1/imaging-history?visitId=${id}&size=100`,
    { signal },
  ).catch((error: unknown) => {
    if (signal?.aborted) throw error;
    return { items: [] as HistoryItemDto[] };
  });
  const byRequest = new Map(
    history.items.map((item) => [item.requestId, item]),
  );
  return orders.map((order) => {
    const past = order.imaging ? byRequest.get(order.imaging.requestId) : null;
    return order.imaging && past
      ? {
          ...order,
          imaging: {
            ...order.imaging,
            studyRef: past.studyRef,
            findingsText: past.findingsText ?? "",
            findingsVersion: past.findingsVersion,
            reportId: past.reportId,
            findingsCompleted: !!past.reportId && past.findingsVersion !== null,
          },
        }
      : order;
  });
}

export async function submitOrders(
  visitId: string,
  items: OrderDraftItem[],
  catalog: CatalogService[],
  idempotencyKey: string,
  patientConsentConfirmed: boolean,
): Promise<ParaclinicalOrder[]> {
  if (patientConsentConfirmed !== true)
    throw new ApiError(400, { code: "MSG-39" });
  const kinds = new Map(catalog.map((service) => [service.id, service.kind]));
  if (items.some((item) => kinds.get(item.serviceId) !== "IMAGING"))
    throw new ApiError(400, { code: "MSG-39" });
  const round = await apiRequest<{
    roundNumber: number;
    orders: ClinicalOrderDto[];
  }>(`/api/v1/visits/${encodeURIComponent(visitId)}/imaging-requests`, {
    method: "POST",
    headers: { "Idempotency-Key": idempotencyKey },
    body: JSON.stringify({ patientConsentConfirmed, orders: items }),
  });
  return round.orders.map((order) => clinicalOrder(order, round.roundNumber));
}

const idem = () => ({ "Idempotency-Key": crypto.randomUUID() });

async function currentRequest(
  requestId: string,
): Promise<{ version: number; status: RequestStatusDto["status"] }> {
  return apiRequest<RequestStatusDto>(
    `/api/v1/imaging-requests/${encodeURIComponent(requestId)}`,
  );
}

async function currentVersion(requestId: string): Promise<number> {
  return (await currentRequest(requestId)).version;
}

export async function cancelOrder(
  order: ParaclinicalOrder,
  cause: CancelCause,
  reason: string,
): Promise<void> {
  if (!order.imaging) throw new ApiError(409, { code: "MSG-42" });
  const { requestId } = order.imaging;
  await apiRequest<unknown>(
    `/api/v1/imaging-requests/${encodeURIComponent(requestId)}/cancellations`,
    {
      method: "POST",
      headers: {
        ...idem(),
        "X-Expected-Version": String(await currentVersion(requestId)),
      },
      body: JSON.stringify({ cause, reason }),
    },
  );
}

type FindingsDto = { version: number; reportId: string | null };

/** Xem ảnh (RESULT_AVAILABLE → REVIEWED) rồi lưu nháp kết luận; trả trạng thái mới. */
export async function saveFindings(
  order: ParaclinicalOrder,
  content: string,
): Promise<ImagingResult> {
  const imaging = order.imaging;
  if (!imaging?.studyRef) throw new ApiError(404, { code: "MSG-41" });
  const base =
    `/api/v1/imaging-requests/${encodeURIComponent(imaging.requestId)}` as const;
  // Lấy trạng thái thật trên BE: prop có thể cũ sau một lần lưu lỗi giữa chừng, và gọi /reviews lần
  // hai khi yêu cầu đã REVIEWED sẽ bị 409.
  const live = await currentRequest(imaging.requestId);
  if (live.status === "RESULT_AVAILABLE")
    await apiRequest<unknown>(`${base}/reviews`, {
      method: "POST",
      headers: {
        ...idem(),
        "X-Expected-Version": String(live.version),
      },
      body: JSON.stringify({ studyRef: imaging.studyRef }),
    });
  const draft = await apiRequest<FindingsDto>(`${base}/findings`, {
    method: "POST",
    headers: {
      ...idem(),
      "X-Expected-Version": String(await currentVersion(imaging.requestId)),
    },
    body: JSON.stringify({ content }),
  });
  return {
    ...imaging,
    reviewed: true,
    version: await currentVersion(imaging.requestId),
    findingsText: content,
    findingsVersion: draft.version,
  };
}

/** Lưu nháp, đính tệp PDF báo cáo (BE đòi) rồi hoàn tất kết luận. */
export async function completeFindings(
  order: ParaclinicalOrder,
  content: string,
  report: File | null,
): Promise<ImagingResult> {
  if (!report) throw new ApiError(400, { code: "MSG-47" });
  const saved = await saveFindings(order, content);
  const base =
    `/api/v1/imaging-requests/${encodeURIComponent(saved.requestId)}` as const;
  const form = new FormData();
  form.append("file", report);
  const uploaded = await apiRequest<{ reportId: string }>(
    `${base}/reports?expectedVersion=${await currentVersion(saved.requestId)}`,
    { method: "POST", headers: idem(), body: form },
  );
  await apiRequest<unknown>(`${base}/findings/completions`, {
    method: "POST",
    headers: {
      ...idem(),
      "X-Expected-Version": String(await currentVersion(saved.requestId)),
    },
    body: JSON.stringify({
      findingsVersion: saved.findingsVersion,
      reportId: uploaded.reportId,
      studyRef: saved.studyRef,
    }),
  });
  return {
    ...saved,
    version: await currentVersion(saved.requestId),
    reportId: uploaded.reportId,
    findingsCompleted: true,
  };
}
