"use client";

import { CalendarPlus } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  NativeSelect,
  NativeSelectOption,
} from "@/components/ui/native-select";
import { Textarea } from "@/components/ui/textarea";
import { getDoctorExamApi } from "../api/doctorExamApi";
import { useCatalog } from "../hooks/useDoctorData";
import { useAsyncAction } from "../hooks/useAsyncAction";
import { useVisitWorkspace } from "./VisitWorkspaceProvider";

// Hẹn tái khám liên kết với lượt khám (UC42 bước 8, UC21 AF2, BR-63). Bác sĩ chỉ đặt tái khám
// cho chính bệnh nhân của lượt khám này.

/** "2026-11-20T09:30" (giờ Việt Nam) → ISO có múi giờ. */
function toIso(local: string): string {
  return new Date(`${local}:00+07:00`).toISOString();
}

export function FollowUpDialog({ disabled }: { disabled: boolean }) {
  const { context } = useVisitWorkspace();
  const catalog = useCatalog();
  const [open, setOpen] = useState(false);
  const [serviceId, setServiceId] = useState("");
  const [when, setWhen] = useState("");
  const [reason, setReason] = useState("");
  const { pending, run } = useAsyncAction();
  const services = (catalog.data ?? []).filter((s) => s.kind === "EXAMINATION");
  const chosen = serviceId || services[0]?.id || "";
  const valid = !!chosen && !!when && !!reason.trim();

  async function submit() {
    if (!context || !valid) return;
    const done = await run(async () => {
      await getDoctorExamApi().bookFollowUp(context, {
        serviceId: chosen,
        startTime: toIso(when),
        purpose: "Tái khám",
        reason: reason.trim(),
      });
      return true;
    }, "Đã đặt lịch tái khám. Bệnh nhân sẽ nhận được nhắc hẹn.");
    if (!done) return;
    setOpen(false);
    setWhen("");
    setReason("");
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger
        render={<Button type="button" variant="outline" disabled={disabled} />}
      >
        <CalendarPlus aria-hidden />
        Hẹn tái khám
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Hẹn tái khám</DialogTitle>
          <DialogDescription>
            Lịch tái khám được liên kết với lượt khám này và gửi nhắc hẹn cho
            bệnh nhân.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="fu-service">Dịch vụ tái khám</Label>
            <NativeSelect
              id="fu-service"
              className="w-full"
              value={chosen}
              onChange={(e) => setServiceId(e.target.value)}
            >
              {services.map((s) => (
                <NativeSelectOption key={s.id} value={s.id}>
                  {s.name}
                </NativeSelectOption>
              ))}
            </NativeSelect>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="fu-when">Ngày và giờ hẹn</Label>
            <Input
              id="fu-when"
              type="datetime-local"
              value={when}
              onChange={(e) => setWhen(e.target.value)}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="fu-reason">Lý do tái khám</Label>
            <Textarea
              id="fu-reason"
              rows={2}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </div>
        </div>
        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            onClick={() => setOpen(false)}
          >
            Đóng
          </Button>
          <Button
            type="button"
            disabled={pending || !valid}
            onClick={() => void submit()}
          >
            {pending ? "Đang đặt..." : "Đặt lịch tái khám"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
