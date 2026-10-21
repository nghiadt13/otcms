import { DetailList } from "@/components/admin/DetailList";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import type { WebsiteContentForm } from "../schema/websiteContentFieldRules";
import { WebsiteContentBody } from "./WebsiteContentBody";

type Props = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  form: WebsiteContentForm;
};

/** Xem trước từ form hiện tại. Không gọi API, không lưu. */
export function WebsiteContentPreviewDialog({ open, onOpenChange, form }: Props) {
  const { clinic } = form;
  const hours = clinic.openingHours.split("\n").filter((line) => line.trim());
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="flex h-dvh max-h-dvh w-full max-w-none flex-col rounded-none sm:h-auto sm:max-h-[85dvh] sm:max-w-3xl sm:rounded-xl">
        <DialogHeader>
          <DialogTitle>Xem trước · chưa đăng</DialogTitle>
          <DialogDescription>
            Nội dung dựng từ bản đang soạn và chưa được lưu.
          </DialogDescription>
        </DialogHeader>
        <div className="min-h-0 flex-1 overflow-y-auto pr-1">
          <h2 className="mb-4 text-2xl font-semibold tracking-tight">
            {form.title.trim() || "Chưa có tiêu đề"}
          </h2>
          {form.type === "CLINIC_INFO" ? (
            <DetailList
              items={[
                { label: "Tên phòng khám", value: clinic.clinicName || "—" },
                { label: "Địa chỉ", value: clinic.address || "—" },
                { label: "Hotline", value: clinic.hotline || "—" },
                { label: "Email", value: clinic.email || "—" },
                {
                  label: "Giờ mở cửa",
                  value: hours.length ? (
                    <ul>
                      {hours.map((line) => (
                        <li key={line}>{line}</li>
                      ))}
                    </ul>
                  ) : (
                    "—"
                  ),
                },
              ]}
            />
          ) : (
            <>
              {form.type === "HEALTH_ARTICLE" && form.imageUrl ? (
                // eslint-disable-next-line @next/next/no-img-element -- ảnh do Admin tải lên
                <img
                  src={form.imageUrl}
                  alt=""
                  className="mb-4 aspect-video w-full rounded-lg object-cover"
                />
              ) : null}
              {form.body.trim() ? (
                <WebsiteContentBody markdown={form.body} />
              ) : (
                <p className="text-sm text-muted-foreground">
                  Chưa có nội dung. Nhập nội dung để xem bản dựng.
                </p>
              )}
            </>
          )}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Đóng
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
