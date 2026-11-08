import type { PatientAppointment } from "../types/patientAppointment";
import * as live from "./patientAppointmentLiveApi";
import * as mock from "./patientAppointmentApi";

// Nguồn dữ liệu lịch hẹn khu bệnh nhân: backend thật khi chạy app; kho giả chỉ trong test (các test
// hiện có dựa vào kho giả dùng chung với khu Lễ tân).
const isTest = process.env.NODE_ENV === "test";
const source = isTest ? mock : live;

export const listMyAppointments = source.listMyAppointments;
export const getMyAppointment = source.getMyAppointment;
export const cancelMyAppointment = source.cancelMyAppointment;

/** Kho giả (chỉ test) đặt xong là coi như xác nhận, nên không có lịch chờ thanh toán. */
export const listAwaitingPaymentAppointments: (
  signal?: AbortSignal,
) => Promise<PatientAppointment[]> = isTest
  ? async () => []
  : live.listAwaitingPaymentAppointments;
