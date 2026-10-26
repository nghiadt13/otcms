"use client";

import { useEffect } from "react";
import { resetNotificationMockStore } from "../api/notificationApi";
import { MOCK_PATIENT_NOTIFICATIONS } from "../api/patientNotificationMockData";

// Nạp kho thông báo giả của khu bệnh nhân khi khu mở (kho mặc định là dữ liệu admin). Chạy trên
// trình duyệt sau khi mount, không đụng kho lúc dựng trên máy chủ. Xóa khi nối BE: BE tự trả theo phiên.
export function PatientNotificationMockBootstrap(): null {
  useEffect(() => {
    resetNotificationMockStore(MOCK_PATIENT_NOTIFICATIONS);
  }, []);
  return null;
}
