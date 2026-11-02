"use client";

import { useCallback, useState } from "react";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import { NOTIFY_SUCCESS_TEXT } from "../schema/appointmentMessages";
import { isStaleAppointmentError } from "../schema/staleAppointmentError";
import type {
  ReassignDoctorApi,
  ReassignTarget,
} from "../types/reassignDoctorApi";

type ReassignDoctorFlowOptions<T extends ReassignTarget> = {
  /** Bộ endpoint của khu đang đứng. */
  api: ReassignDoctorApi<T>;
  /** Thay một dòng đang hiện (sau khi chuyển hoặc báo bệnh nhân). */
  replaceItem: (match: (item: T) => boolean, next: T) => void;
  /** Tải lại danh sách khi lịch không còn dùng được. */
  reload: () => void;
  /** Bác sĩ đang lọc danh sách; chuyển sang bác sĩ khác thì dòng không còn thuộc bộ lọc. */
  doctorFilter: string | undefined;
};

/**
 * Trạng thái và thao tác của Dialog "Chuyển bác sĩ" và xác nhận "Báo cho bệnh nhân" (AF1) trên
 * một danh sách lịch. Admin và Lễ tân dùng chung; khác nhau ở `api` truyền vào.
 */
export function useReassignDoctorFlow<T extends ReassignTarget>({
  api,
  replaceItem,
  reload,
  doctorFilter,
}: ReassignDoctorFlowOptions<T>) {
  const [reassigning, setReassigning] = useState<T | null>(null);
  const [notifying, setNotifying] = useState<T | null>(null);

  // Lịch không còn dùng được: đóng Dialog và tải lại danh sách.
  const handleStale = useCallback(() => {
    setReassigning(null);
    reload();
  }, [reload]);

  const handleChanged = useCallback(
    (updated: T) => {
      setReassigning(null);
      replaceItem((item) => item.id === updated.id, updated);
      // Đang lọc theo bác sĩ cũ thì dòng không còn thuộc bộ lọc: tải lại.
      if (doctorFilter && updated.doctor?.id !== doctorFilter) reload();
    },
    [replaceItem, reload, doctorFilter],
  );

  // AF1: đóng Dialog chuyển rồi mở xác nhận báo bệnh nhân (không chồng hai hộp thoại).
  const requestNotify = useCallback((appointment: T) => {
    setReassigning(null);
    setNotifying(appointment);
  }, []);

  const confirmNotify = useCallback(async () => {
    if (!notifying) return;
    try {
      const updated = await api.notifyNoReplacement(notifying.id);
      notifySuccess(NOTIFY_SUCCESS_TEXT);
      replaceItem((item) => item.id === updated.id, updated);
    } catch (error) {
      notifyError(error);
      if (!isStaleAppointmentError(error)) throw error; // giữ Confirm để thử lại
      reload(); // lịch không còn dùng được: đóng Confirm và tải lại
    }
  }, [api, notifying, replaceItem, reload]);

  return {
    reassigning,
    setReassigning,
    notifying,
    setNotifying,
    requestNotify,
    handleStale,
    handleChanged,
    confirmNotify,
  };
}
