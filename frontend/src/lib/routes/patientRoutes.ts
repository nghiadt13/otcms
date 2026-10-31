// Đường dẫn khu bệnh nhân (/portal). Nguồn duy nhất cho liên kết giữa các màn: feature
// không import navigation của nhau. Đường dẫn có id được mã hóa để không vỡ URL.
const enc = encodeURIComponent;

export const PATIENT_ROUTES = {
  appointments: "/portal/appointments",
  newAppointment: "/portal/appointments/new",
  appointmentDetail: (id: string) => `/portal/appointments/${enc(id)}`,

  visits: "/portal/visits",
  visitDetail: (visitId: string) => `/portal/visits/${enc(visitId)}`,

  results: "/portal/results",
  resultDetail: (visitId: string) => `/portal/results/${enc(visitId)}`,

  prescriptions: "/portal/prescriptions",
  prescriptionDetail: (id: string) => `/portal/prescriptions/${enc(id)}`,

  images: "/portal/images",
  imageStudy: (studyId: string) => `/portal/images/${enc(studyId)}`,

  payments: "/portal/payments",
  payAppointment: (appointmentId: string) =>
    `/portal/payments/appointment/${enc(appointmentId)}`,
  payInvoice: (invoiceId: string) =>
    `/portal/payments/invoice/${enc(invoiceId)}`,
  payVisit: (visitId: string) => `/portal/payments/visit/${enc(visitId)}`,
  /** Luồng mock cũ; lịch khám online dùng route appointment/invoice PayOS riêng. */
  paymentConfirm: "/portal/payments/pay",
  /** Thêm `?paymentId=` khi gọi. */
  paymentResult: "/portal/payments/result",
  paymentHistory: "/portal/payments/history",
  paymentDetail: (paymentId: string) =>
    `/portal/payments/history/${enc(paymentId)}`,

  profiles: "/portal/profiles",
  newProfile: "/portal/profiles/new",
  profileDetail: (profileId: string) => `/portal/profiles/${enc(profileId)}`,
  profileHistory: (profileId: string) =>
    `/portal/profiles/${enc(profileId)}/history`,

  account: "/portal/account",
  accountPassword: "/portal/account/password",

  notifications: "/portal/notifications",

  reviews: "/portal/reviews",
  newReview: (visitId: string) => `/portal/reviews/new?visitId=${enc(visitId)}`,
} as const;
