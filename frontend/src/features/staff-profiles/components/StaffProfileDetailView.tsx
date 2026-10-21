import { ExternalLink } from "lucide-react";
import { DetailList } from "@/components/admin/DetailList";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { ROLE_LABELS } from "@/lib/domain/clinicRoles";
import type { StaffProfileDetail } from "../types/staffProfile";

type StaffProfileDetailViewProps = {
  detail: StaffProfileDetail;
  onEdit: () => void;
  onRemove: () => void;
  onTogglePublic: (next: boolean) => void;
  togglePending: boolean;
};

const multiline = (text: string) =>
  text ? <span className="whitespace-pre-line">{text}</span> : null;

/** Chế độ xem của Sheet. Nút hiện theo `allowedActions` của bản ghi. */
export function StaffProfileDetailView({
  detail,
  onEdit,
  onRemove,
  onTogglePublic,
  togglePending,
}: StaffProfileDetailViewProps) {
  const { doctor, allowedActions } = detail;
  const items = [
    { label: "Vai trò", value: ROLE_LABELS[detail.role] },
    { label: "Tên đăng nhập", value: detail.username },
    { label: "Liên hệ công việc", value: detail.workContact },
    ...(doctor
      ? [
          {
            label: "Chuyên khoa",
            value: doctor.specialties.join(", ") || null,
          },
          { label: "Trình độ", value: doctor.qualifications || null },
          { label: "Tiểu sử", value: multiline(doctor.biography) },
          { label: "Thông tin khám", value: multiline(doctor.consultationInfo) },
        ]
      : []),
  ];

  return (
    <>
      <div className="flex-1 space-y-6 overflow-y-auto px-4">
        <DetailList items={items} />
        {doctor && allowedActions.includes("togglePublic") ? (
          <div className="space-y-3 border-t pt-4">
            <div className="flex items-center justify-between gap-4">
              <div>
                <Label htmlFor="staff-detail-public">Hiển thị công khai</Label>
                <p className="mt-0.5 text-xs text-muted-foreground">
                  {doctor.isPublic
                    ? "Bác sĩ đang hiện trên website."
                    : "Bác sĩ chưa hiện trên website."}
                </p>
              </div>
              <Switch
                id="staff-detail-public"
                checked={doctor.isPublic}
                disabled={togglePending}
                onCheckedChange={onTogglePublic}
              />
            </div>
            {detail.publicUrl ? (
              <Button
                variant="outline"
                size="sm"
                nativeButton={false}
                render={
                  <a
                    href={detail.publicUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                  />
                }
              >
                Xem trang công khai
                <ExternalLink data-icon="inline-end" aria-hidden />
              </Button>
            ) : null}
          </div>
        ) : null}
      </div>
      {allowedActions.length > 0 ? (
        <div className="flex flex-wrap items-center justify-between gap-2 border-t p-4">
          {allowedActions.includes("edit") ? (
            <Button variant="outline" onClick={onEdit}>
              Sửa
            </Button>
          ) : (
            <span />
          )}
          {allowedActions.includes("remove") ? (
            <Button variant="destructive" onClick={onRemove}>
              Gỡ hồ sơ
            </Button>
          ) : null}
        </div>
      ) : null}
    </>
  );
}
