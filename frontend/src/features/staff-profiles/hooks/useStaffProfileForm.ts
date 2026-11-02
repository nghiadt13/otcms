"use client";

import { useCallback, useState } from "react";
import type { MsgCode } from "@/lib/api/apiError";
import type { StaffRole } from "@/lib/domain/clinicRoles";
import {
  validatePhotoFile,
  validateStaffProfileInput,
  type StaffProfileFieldErrors,
} from "../schema/staffProfileFieldRules";
import {
  focusFirstError,
  toStaffProfileInput,
  type StaffProfileFormValues,
} from "../schema/staffProfileFormValues";
import type { DoctorInfo } from "../types/staffProfile";

/** State dùng chung cho form tạo (Dialog) và form sửa (Sheet). */
export function useStaffProfileForm(initial: StaffProfileFormValues) {
  const [values, setValues] = useState(initial);
  const [photo, setPhotoState] = useState<File | null>(null);
  const [errors, setErrors] = useState<StaffProfileFieldErrors>({});

  const clearError = (key: keyof StaffProfileFieldErrors) =>
    setErrors((current) => {
      if (!current[key]) return current;
      const next = { ...current };
      delete next[key];
      return next;
    });

  const setField = useCallback(
    (key: "displayName" | "workContact", value: string) => {
      setValues((current) => ({ ...current, [key]: value }));
      clearError(key);
    },
    [],
  );

  const setDoctor = useCallback((patch: Partial<DoctorInfo>) => {
    setValues((current) => ({
      ...current,
      doctor: { ...current.doctor, ...patch },
    }));
    for (const key of Object.keys(patch)) {
      if (key !== "specialties" && key !== "isPublic")
        clearError(`doctor.${key}` as keyof StaffProfileFieldErrors);
    }
  }, []);

  /** Chọn ảnh: kiểm sơ bộ định dạng và dung lượng, sai thì không nhận file. */
  const setPhoto = useCallback((file: File | null) => {
    const code = file ? validatePhotoFile(file) : null;
    if (code) {
      setErrors((current) => ({ ...current, photo: code }));
      return;
    }
    setPhotoState(file);
    clearError("photo");
  }, []);

  /** Kiểm tra trước khi gửi; có lỗi thì hiện dưới ô và focus ô đầu tiên. */
  const validate = (role: StaffRole): boolean => {
    const found = validateStaffProfileInput(values, role);
    setErrors(found);
    if (Object.keys(found).length > 0) {
      focusFirstError(found);
      return false;
    }
    return true;
  };

  /** Lỗi từng ô do BE trả về (400, MSG-22). */
  const applyServerErrors = (map: Record<string, MsgCode>) => {
    setErrors(map as StaffProfileFieldErrors);
    focusFirstError(map);
  };

  return {
    values,
    photo,
    errors,
    setField,
    setDoctor,
    setPhoto,
    validate,
    applyServerErrors,
    buildInput: (role: StaffRole) => toStaffProfileInput(values, role),
  };
}
