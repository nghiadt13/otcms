"use client";

import { useRouter } from "next/navigation";
import { isApiError, toFieldErrorMap, type MsgCode } from "@/lib/api/apiError";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import { ADMIN_ROUTES } from "@/lib/routes/adminRoutes";
import {
  createStaffProfile,
  removeStaffProfile,
  updateStaffProfile,
} from "../api/staffProfileApi";
import type {
  StaffProfileDetail,
  StaffProfileInput,
} from "../types/staffProfile";

export type SubmitResult =
  | { ok: true; profile: StaffProfileDetail }
  | { ok: false; code?: MsgCode; fieldErrors: Record<string, MsgCode> };

export type RemoveResult = "removed" | "blocked" | "gone";

function toFailure(error: unknown): Extract<SubmitResult, { ok: false }> {
  notifyError(error);
  return {
    ok: false,
    code: isApiError(error) ? error.code : undefined,
    fieldErrors: isApiError(error) ? toFieldErrorMap(error.fieldErrors) : {},
  };
}

/** Thao tác ghi của màn Hồ sơ nhân viên. Mọi lỗi đều đã báo toast trước khi trả về. */
export function useStaffProfileActions() {
  const router = useRouter();

  async function create(
    input: StaffProfileInput,
    photo?: File,
  ): Promise<SubmitResult> {
    try {
      const profile = await createStaffProfile(input, photo);
      notifySuccess("MSG-92");
      return { ok: true, profile };
    } catch (error) {
      return toFailure(error);
    }
  }

  async function update(
    id: string,
    input: Omit<StaffProfileInput, "accountId">,
    photo?: File,
  ): Promise<SubmitResult> {
    try {
      const profile = await updateStaffProfile(id, input, photo);
      notifySuccess("MSG-92");
      return { ok: true, profile };
    } catch (error) {
      return toFailure(error);
    }
  }

  /** Bật/tắt công khai: PUT đủ dữ liệu hiện tại kèm cờ mới (spec AF1 UC18). */
  function setPublic(detail: StaffProfileDetail, isPublic: boolean) {
    if (!detail.doctor) throw new Error("Chỉ bác sĩ mới có cờ công khai.");
    return update(detail.id, {
      displayName: detail.displayName,
      workContact: detail.workContact,
      doctor: { ...detail.doctor, isPublic },
    });
  }

  /**
   * Gỡ hồ sơ. MSG-36 → toast kèm "Xem lịch hẹn" (chỉ đổi URL), hồ sơ giữ nguyên.
   * MSG-23 → hồ sơ không còn. Lỗi khác ném lại để hộp xác nhận giữ mở cho người dùng thử lại.
   */
  async function remove(id: string): Promise<RemoveResult> {
    try {
      await removeStaffProfile(id);
      notifySuccess("MSG-92");
      return "removed";
    } catch (error) {
      if (isApiError(error) && error.code === "MSG-36") {
        notifyError(error, {
          action: {
            label: "Xem lịch hẹn",
            onClick: () =>
              router.push(`${ADMIN_ROUTES.appointments}?doctorId=${id}`),
          },
        });
        return "blocked";
      }
      notifyError(error);
      if (isApiError(error) && error.code === "MSG-23") return "gone";
      throw error;
    }
  }

  return { create, update, setPublic, remove };
}
