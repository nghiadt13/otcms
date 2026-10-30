"use client";

import { useCallback, useState } from "react";
import { toast } from "sonner";
import { AdminFormField as Field } from "@/components/admin/AdminFormField";
import { RemoteDataState } from "@/components/admin/RemoteDataState";
import { ConfirmActionDialog } from "@/components/admin/ConfirmActionDialog";
import { Button } from "@/components/ui/button";
import { NativeSelect } from "@/components/ui/native-select";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useAdminRemoteData } from "@/hooks/useAdminRemoteData";
import { adminApiMessage } from "@/lib/api/adminApiMessage";
import {
  assessAssignmentRemoval,
  assignServiceDoctor,
  getServiceAssignments,
  removeServiceDoctor,
  type AdminService,
  type DoctorAssignment,
} from "../api/adminServiceApi";

export function ServiceDoctorAssignments({
  service,
  close,
}: {
  service: AdminService;
  close: () => void;
}) {
  const load = useCallback(
    (signal: AbortSignal) => getServiceAssignments(service.id, signal),
    [service.id],
  );
  const remote = useAdminRemoteData(load);
  const [doctorId, setDoctorId] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [removing, setRemoving] = useState<DoctorAssignment | null>(null);
  const available =
    remote.data?.doctors.filter(
      (d) =>
        d.inActiveDirectory &&
        !remote.data?.assignments.some(
          (a) => a.doctorProfileId === d.doctorProfileId,
        ),
    ) ?? [];
  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && !pending && !removing) close();
      }}
    >
      <DialogContent
        className="max-h-[90dvh] overflow-y-auto sm:max-w-xl"
        showCloseButton={!pending && !removing}
      >
        <DialogHeader>
          <DialogTitle>Bác sĩ thực hiện dịch vụ</DialogTitle>
          <DialogDescription>
            {service.name}. Bác sĩ được phân công cần có ca trực và bật nhận
            lịch để xuất hiện trong giờ trống.
          </DialogDescription>
        </DialogHeader>
        <RemoteDataState
          loading={remote.loading}
          error={remote.error}
          retry={remote.refresh}
        />
        {remote.data && (
          <>
            <ul className="divide-y rounded-lg border">
              {remote.data.assignments.map((a) => (
                <li
                  key={a.id}
                  className="flex items-center justify-between gap-3 p-3 text-sm"
                >
                  <span>
                    {remote.data?.doctors.find(
                      (d) => d.doctorProfileId === a.doctorProfileId,
                    )?.fullName ?? "Bác sĩ không còn trong danh mục"}
                  </span>
                  <Button
                    variant="ghost"
                    size="sm"
                    disabled={pending}
                    onClick={() => {
                      setError("");
                      setRemoving(a);
                    }}
                  >
                    Gỡ phân công
                  </Button>
                </li>
              ))}
              {!remote.data.assignments.length && (
                <li className="p-4 text-sm text-muted-foreground">
                  Chưa phân công bác sĩ cho dịch vụ này.
                </li>
              )}
            </ul>
            {service.active && (
              <form
                className="space-y-3"
                onSubmit={async (e) => {
                  e.preventDefault();
                  if (!doctorId || pending) return;
                  setPending(true);
                  setError("");
                  try {
                    await assignServiceDoctor(service.id, doctorId);
                    toast.success("Đã phân công bác sĩ.");
                    setDoctorId("");
                    remote.refresh();
                  } catch (failure) {
                    setError(adminApiMessage(failure));
                  } finally {
                    setPending(false);
                  }
                }}
              >
                <Field id="assignment-doctor" label="Thêm bác sĩ">
                  <NativeSelect
                    id="assignment-doctor"
                    className="w-full"
                    disabled={pending}
                    required
                    value={doctorId}
                    onChange={(e) => setDoctorId(e.target.value)}
                  >
                    <option value="">
                      {available.length
                        ? "Chọn bác sĩ"
                        : "Không còn bác sĩ chưa phân công"}
                    </option>
                    {available.map((d) => (
                      <option key={d.doctorProfileId} value={d.doctorProfileId}>
                        {d.fullName}
                      </option>
                    ))}
                  </NativeSelect>
                </Field>
                <Button type="submit" disabled={!doctorId || pending}>
                  {pending ? "Đang lưu..." : "Phân công bác sĩ"}
                </Button>
              </form>
            )}
          </>
        )}
        {error && !removing && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}
        <ConfirmActionDialog
          open={!!removing}
          onOpenChange={(open) => {
            if (!open) setRemoving(null);
          }}
          title="Gỡ phân công bác sĩ?"
          description={
            <>
              Bác sĩ sẽ không nhận lịch mới cho dịch vụ này. Nếu còn lịch hẹn
              hoặc lượt khám bị ảnh hưởng, hệ thống sẽ chặn thao tác.
              {error && (
                <span role="alert" className="mt-2 block text-destructive">
                  {error}
                </span>
              )}
            </>
          }
          confirmLabel="Gỡ phân công"
          pendingLabel="Đang kiểm tra..."
          destructive
          onConfirm={async () => {
            if (!removing) return;
            try {
              const impact = await assessAssignmentRemoval(
                service.id,
                removing.id,
              );
              if (impact.blocked) {
                setError(
                  `Còn ${impact.affectedBookingCount} lịch hẹn${impact.activeCareBlocks ? " và lượt khám đang chăm sóc" : ""} bị ảnh hưởng. Hãy điều phối trước khi gỡ.`,
                );
                throw new Error("blocked");
              }
              await removeServiceDoctor(service.id, removing.id);
              toast.success("Đã gỡ phân công.");
              remote.refresh();
            } catch (failure) {
              if (!(failure instanceof Error && failure.message === "blocked"))
                setError(adminApiMessage(failure));
              throw failure;
            }
          }}
        />
      </DialogContent>
    </Dialog>
  );
}
