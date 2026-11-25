"use client";

import { ConfirmActionDialog } from "@/components/admin/ConfirmActionDialog";
import { FieldError } from "@/components/admin/FieldError";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  NativeSelect,
  NativeSelectOption,
} from "@/components/ui/native-select";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Skeleton } from "@/components/ui/skeleton";
import {
  useWebsiteContentEditor,
  type EditorResult,
} from "../hooks/useWebsiteContentEditor";
import {
  WEBSITE_CONTENT_TYPES,
  WEBSITE_CONTENT_TYPE_LABELS,
  type WebsiteContentType,
} from "../types/websiteContentAdmin";
import { healthArticleSamples } from "../schema/healthArticleSamples";
import { WebsiteContentArticleFields } from "./WebsiteContentArticleFields";
import { WebsiteContentClinicInfoFields } from "./WebsiteContentClinicInfoFields";
import { WebsiteContentField } from "./WebsiteContentField";
import { WebsiteContentPreviewDialog } from "./WebsiteContentPreviewDialog";

type Props = {
  /** null = thêm mới; có id = sửa (tải chi tiết trong Sheet). */
  editId: string | null;
  initialType?: WebsiteContentType | "";
  onFinish: (result: EditorResult) => void;
};

/** Sheet rộng để thêm/sửa nội dung. Mọi ghi dữ liệu là "Đăng", luôn qua hộp xác nhận. */
export function WebsiteContentEditSheet({
  editId,
  initialType,
  onFinish,
}: Props) {
  const editor = useWebsiteContentEditor(editId, onFinish, initialType);
  const { form, errors, isEdit, loading, publishing } = editor;
  const publishLabel = editor.hiddenArticle
    ? "Lưu thay đổi"
    : isEdit
      ? "Đăng bản cập nhật"
      : "Đăng nội dung";

  return (
    <>
      <Dialog open onOpenChange={(open) => !open && editor.requestClose()}>
        <DialogContent className="flex max-h-[calc(100dvh-2rem)] flex-col gap-0 overflow-hidden p-0 sm:max-w-3xl">
          <DialogHeader className="shrink-0 border-b p-4 pr-12">
            <DialogTitle>
              {isEdit
                ? "Sửa nội dung"
                : initialType === "HEALTH_ARTICLE"
                  ? "Thêm bài viết"
                  : "Thêm nội dung"}
            </DialogTitle>
            <DialogDescription>
              {editor.hiddenArticle
                ? "Bài viết vẫn ẩn sau khi lưu."
                : "Nội dung sẽ hiển thị công khai ngay khi đăng."}
            </DialogDescription>
          </DialogHeader>

          <div className="min-h-0 flex-1 overflow-y-auto p-4">
            {loading ? (
              <div
                className="grid gap-4"
                aria-busy="true"
                aria-label="Đang tải nội dung"
              >
                <Skeleton className="h-8 w-full" />
                <Skeleton className="h-8 w-full" />
                <Skeleton className="h-48 w-full" />
              </div>
            ) : (
              <div className="grid gap-4">
                <WebsiteContentField
                  id="content-type"
                  label="Loại nội dung"
                  required
                  error={errors.type}
                >
                  {isEdit ? (
                    <Input
                      id="content-type"
                      readOnly
                      disabled
                      value={
                        form.type ? WEBSITE_CONTENT_TYPE_LABELS[form.type] : ""
                      }
                    />
                  ) : (
                    <NativeSelect
                      id="content-type"
                      className="w-full"
                      value={form.type}
                      aria-invalid={Boolean(errors.type)}
                      onChange={(event) =>
                        editor.requestTypeChange(
                          event.target.value as WebsiteContentType,
                        )
                      }
                    >
                      <NativeSelectOption value="" disabled>
                        Chọn loại nội dung
                      </NativeSelectOption>
                      {WEBSITE_CONTENT_TYPES.map((type) => (
                        <NativeSelectOption key={type} value={type}>
                          {WEBSITE_CONTENT_TYPE_LABELS[type]}
                        </NativeSelectOption>
                      ))}
                    </NativeSelect>
                  )}
                </WebsiteContentField>

                {!isEdit && form.type === "HEALTH_ARTICLE" ? (
                  <fieldset className="rounded-lg border p-3">
                    <legend className="px-1 text-sm font-medium">
                      Bài viết mẫu
                    </legend>
                    <div className="flex flex-wrap gap-2">
                      {healthArticleSamples.map(({ label, title, body }) => (
                        <Button
                          key={label}
                          type="button"
                          variant="outline"
                          size="sm"
                          disabled={Boolean(
                            form.title.trim() || form.body.trim(),
                          )}
                          onClick={() => editor.patchForm({ title, body })}
                        >
                          {label}
                        </Button>
                      ))}
                    </div>
                  </fieldset>
                ) : null}

                <WebsiteContentField
                  id="content-title"
                  label="Tiêu đề"
                  required
                  error={errors.title}
                >
                  <Input
                    id="content-title"
                    autoComplete="off"
                    value={form.title}
                    aria-invalid={Boolean(errors.title)}
                    onChange={(event) =>
                      editor.patchForm({ title: event.target.value })
                    }
                  />
                </WebsiteContentField>

                {!form.type ? (
                  <p className="text-sm text-muted-foreground">
                    Chọn loại nội dung để bắt đầu soạn.
                  </p>
                ) : form.type === "CLINIC_INFO" ? (
                  <WebsiteContentClinicInfoFields
                    value={form.clinic}
                    errors={errors}
                    onChange={editor.patchClinic}
                  />
                ) : (
                  <WebsiteContentArticleFields
                    type={form.type}
                    body={form.body}
                    imageUrl={form.imageUrl}
                    errors={errors}
                    onChange={editor.patchForm}
                  />
                )}
                <FieldError message={errors.form} />
              </div>
            )}
          </div>

          <DialogFooter className="m-0 shrink-0 sm:flex-row-reverse sm:justify-start">
            <Button
              disabled={loading || publishing}
              onClick={editor.requestPublish}
            >
              {publishing ? "Đang đăng..." : publishLabel}
            </Button>
            <Button
              variant="outline"
              disabled={loading || !form.type}
              onClick={() => editor.setPreviewOpen(true)}
            >
              Xem trước
            </Button>
            <Button
              variant="ghost"
              disabled={publishing}
              onClick={editor.requestClose}
            >
              Hủy
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <WebsiteContentPreviewDialog
        open={editor.previewOpen}
        onOpenChange={editor.setPreviewOpen}
        form={form}
      />
      <ConfirmActionDialog
        open={editor.confirmOpen}
        onOpenChange={editor.setConfirmOpen}
        title={
          editor.hiddenArticle
            ? "Lưu thay đổi?"
            : isEdit
              ? "Đăng bản cập nhật này?"
              : "Đăng nội dung này?"
        }
        description={
          editor.hiddenArticle
            ? "Bài viết vẫn ẩn trên website."
            : "Nội dung sẽ hiển thị công khai ngay."
        }
        confirmLabel={publishLabel}
        pendingLabel="Đang đăng..."
        cancelLabel="Quay lại"
        onConfirm={editor.publish}
      />
      <ConfirmActionDialog
        open={editor.discard !== null}
        onOpenChange={(open) => !open && editor.cancelDiscard()}
        title="Bỏ thay đổi?"
        description={
          editor.discard?.kind === "type"
            ? "Nội dung đã soạn của loại cũ sẽ mất."
            : "Nội dung chưa đăng sẽ mất."
        }
        confirmLabel="Bỏ thay đổi"
        pendingLabel="Đang bỏ..."
        cancelLabel="Tiếp tục soạn"
        destructive
        onConfirm={editor.confirmDiscard}
      />
    </>
  );
}
