import * as live from "./patientAppointmentLiveApi";
import * as mock from "./patientAppointmentApi";

// Nguồn dữ liệu lịch hẹn khu bệnh nhân: backend thật khi chạy app; kho giả chỉ trong test (các test
// hiện có dựa vào kho giả dùng chung với khu Lễ tân).
const source = process.env.NODE_ENV === "test" ? mock : live;

export const listMyAppointments = source.listMyAppointments;
export const getMyAppointment = source.getMyAppointment;
export const cancelMyAppointment = source.cancelMyAppointment;
