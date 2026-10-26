"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Skeleton } from "@/components/ui/skeleton";
import { isApiError, toFieldErrorMap } from "@/lib/api/apiError";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import {
  createServiceCatalog,
  updateServiceCatalog,
} from "../api/serviceCatalogApi";
import {
  isStaleServiceError,
  useServiceDetail,
} from "../hooks/useServiceDetail";
import {
  validateServiceForm,
  type FieldErrorMap,
} from "../schema/serviceFieldRules";
import {
  emptyServiceDraft,
  serviceDetailToDraft,
  serviceDraftToCreateRequest,
  serviceDraftToUpdateRequest,
  type ServiceFormDraft,
} from "../schema/serviceFormDraft";
import type { ServiceCatalogDetail } from "../types/serviceCatalog";
import { ServiceFormFields } from "./ServiceFormFields";

type ServiceFormSheetProps = {
  /** Có `serviceId` → sửa; không có → tạo. Màn gắn lại bằng `key` mỗi lần mở. */
  serviceId?: string;
  onClose: () => void;
  onSaved: (saved: ServiceCatalogDetail, mode: "create" | "edit") => void;
  /** Bản ghi không còn hoặc ngoài quyền: màn đóng Sheet và tải lại danh sách. */
  onStale: () => void;
};

const SHEET_WIDE_CLASS =
  "data-[side=right]:w-full data-[side=right]:sm:max-w-3xl";

export function ServiceFormSheet({
  serviceId,
  onClose,
  onSaved,
  onStale,
}: ServiceFormSheetProps) {
  const mode = serviceId ? "edit" : "create";
  return (
    <Sheet open onOpenChange={(open) => !open && onClose()}>
      <SheetContent className={SHEET_WIDE_CLASS}>
        {serviceId ? (
          <EditLoader
            serviceId={serviceId}
            onClose={onClose}
            onSaved={onSaved}
            onStale={onStale}
          />
        ) : (
          <FormBody
            mode={mode}
            initial={emptyServiceDraft()}
            onClose={onClose}
            onSaved={onSaved}
            onStale={onStale}
          />
        )}
      </SheetContent>
    </Sheet>
  );
}

function EditLoader({
  serviceId,
  onClose,
  onSaved,
  onStale,
}: Required<Pick<ServiceFormSheetProps, "serviceId">> &
  Omit<ServiceFormSheetProps, "serviceId">) {
  const { detail } = useServiceDetail(serviceId, (stale) => {
    if (stale) onStale();
    else onClose();
  });
  if (!detail) {
    return (
      <>
        <SheetHeader>
          <SheetTitle>Sửa dịch vụ</SheetTitle>
          <SheetDescription>Đang tải thông tin dịch vụ.</SheetDescription>
        </SheetHeader>
        <div className="space-y-4 px-4" aria-hidden>
          <Skeleton className="h-9 w-full" />
          <Skeleton className="h-20 w-full" />
          <Skeleton className="h-9 w-full" />
        </div>
      </>
    );
  }
  return (
    <FormBody
      mode="edit"
      serviceId={serviceId}
      initial={serviceDetailToDraft(detail)}
      onClose={onClose}
      onSaved={onSaved}
      onStale={onStale}
    />
  );
}

type FormBodyProps = Omit<ServiceFormSheetProps, "serviceId"> & {
  mode: "create" | "edit";
  serviceId?: string;
  initial: ServiceFormDraft;
};

function FormBody({
  mode,
  serviceId,
  initial,
  onClose,
  onSaved,
  onStale,
}: FormBodyProps) {
  const [draft, setDraft] = useState(initial);
  const [errors, setErrors] = useState<FieldErrorMap>({});
  const [submitting, setSubmitting] = useState(false);
  const title = mode === "create" ? "Thêm dịch vụ" : "Sửa dịch vụ";

  async function submit() {
    const found = validateServiceForm(draft, mode);
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    setSubmitting(true);
    try {
      const saved =
        mode === "create"
          ? await createServiceCatalog(serviceDraftToCreateRequest(draft))
          : await updateServiceCatalog(
              serviceId!,
              serviceDraftToUpdateRequest(draft),
            );
      notifySuccess("MSG-65");
      onSaved(saved, mode);
    } catch (error) {
      if (isApiError(error) && error.fieldErrors?.length) {
        setErrors(toFieldErrorMap(error.fieldErrors)); // lỗi theo ô: hiện tại chỗ, không toast
      } else {
        notifyError(error);
        if (isStaleServiceError(error)) onStale();
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form
      noValidate
      className="flex min-h-0 flex-1 flex-col"
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <SheetHeader>
        <SheetTitle>{title}</SheetTitle>
        <SheetDescription>
          {mode === "create"
            ? "Chọn loại dịch vụ để hiện các ô phù hợp."
            : "Chỉnh giá, mô tả và thông tin theo loại dịch vụ."}
        </SheetDescription>
      </SheetHeader>
      <div className="min-h-0 flex-1 overflow-y-auto px-4 pb-4">
        <ServiceFormFields
          mode={mode}
          draft={draft}
          onChange={setDraft}
          errors={errors}
          disabled={submitting}
        />
      </div>
      <SheetFooter className="flex-col-reverse border-t sm:flex-row sm:justify-end">
        <Button
          type="button"
          variant="outline"
          onClick={onClose}
          disabled={submitting}
        >
          Hủy
        </Button>
        <Button type="submit" disabled={submitting}>
          {submitting
            ? "Đang lưu..."
            : mode === "create"
              ? "Thêm dịch vụ"
              : "Lưu thay đổi"}
        </Button>
      </SheetFooter>
    </form>
  );
}
