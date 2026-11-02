import { apiRequest } from "@/lib/api/client";
import { clone, mockDelay, mockFail, paginate } from "@/lib/api/mockServer";
import {
  toQueryString,
  type PageQuery,
  type PageResponse,
} from "@/lib/api/pagination";
import type {
  PrescriptionDocument,
  PrescriptionListItem,
  PrescriptionListQuery,
} from "../types/prescription";
import {
  MOCK_PRESCRIPTIONS,
  PRESCRIPTION_FORBIDDEN_ID,
  PRESCRIPTION_LIST_ERROR_PATIENT_ID,
  PRESCRIPTION_NOT_ISSUED_ID,
} from "./prescriptionMockData";

// Lớp API giả cho đơn thuốc. Chữ ký khớp REST của BE; khi nối BE đặt USE_MOCK = false.
const USE_MOCK = true;

const latency = (signal?: AbortSignal) =>
  mockDelay(process.env.NODE_ENV === "test" ? 0 : 300, signal);

function mockList(patientId: string | undefined): PrescriptionListItem[] {
  return MOCK_PRESCRIPTIONS.filter(
    (entry) => !patientId || entry.patientId === patientId,
  )
    .map(({ patientId: owner, document }) => ({
      id: document.id,
      issuedAt: document.issuedAt,
      itemCount: document.items.length,
      doctorName: document.doctorName,
      patient: {
        id: owner,
        fullName: document.patient.fullName,
        relationship: document.patient.relationship,
      },
    }))
    .sort((a, b) => b.issuedAt.localeCompare(a.issuedAt));
}

function mockDocumentOrFail(id: string): PrescriptionDocument {
  if (id === PRESCRIPTION_FORBIDDEN_ID) mockFail(403, "MSG-40");
  if (id === PRESCRIPTION_NOT_ISSUED_ID) mockFail(409, "MSG-42");
  const found = MOCK_PRESCRIPTIONS.find((entry) => entry.document.id === id);
  if (!found) mockFail(404, "MSG-41");
  return clone(found.document);
}

/** GET /api/v1/prescriptions: đơn đã phát hành, mới nhất trước. */
export async function listPrescriptions(
  query: PrescriptionListQuery & PageQuery,
  signal?: AbortSignal,
): Promise<PageResponse<PrescriptionListItem>> {
  if (!USE_MOCK) {
    const qs = toQueryString({
      patientId: query.patientId,
      page: query.page,
      size: query.size,
    });
    return apiRequest<PageResponse<PrescriptionListItem>>(
      `/api/v1/prescriptions${qs}` as const,
      { signal },
    );
  }
  await latency(signal);
  if (query.patientId === PRESCRIPTION_LIST_ERROR_PATIENT_ID)
    mockFail(500, "MSG-04");
  return clone(paginate(mockList(query.patientId), query));
}

/** GET /api/v1/prescriptions/{id}: 403 MSG-40, 404 MSG-41, 409 MSG-42 khi chưa phát hành. */
export async function getPrescription(
  id: string,
  signal?: AbortSignal,
): Promise<PrescriptionDocument> {
  if (!USE_MOCK)
    return apiRequest<PrescriptionDocument>(
      `/api/v1/prescriptions/${encodeURIComponent(id)}` as const,
      { signal },
    );
  await latency(signal);
  return mockDocumentOrFail(id);
}
