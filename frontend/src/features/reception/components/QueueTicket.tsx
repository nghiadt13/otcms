import Link from "next/link";
import { useEffect, useRef } from "react";
import { Button, buttonVariants } from "@/components/ui/button";
import { STAFF_ROUTES } from "@/lib/routes/staffRoutes";
import { queueSummary } from "../schema/checkInPresentation";
import type { CheckInResult } from "../types/checkIn";

interface QueueTicketProps {
  patientName: string;
  result: CheckInResult;
  onNext: () => void;
}

// Phiếu số thứ tự sau tiếp nhận: lễ tân đọc số to cho bệnh nhân. Đây là chỗ duy nhất dùng chữ
// lớn trên màn, không trang trí và không animation.
export function QueueTicket({ patientName, result, onNext }: QueueTicketProps) {
  const titleRef = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    titleRef.current?.focus();
  }, []);

  return (
    <section aria-labelledby="queue-ticket-title" className="space-y-6">
      <h2
        id="queue-ticket-title"
        ref={titleRef}
        tabIndex={-1}
        className="text-lg font-semibold outline-none"
      >
        Đã tiếp nhận {patientName}
      </h2>
      <div className="text-center">
        <p className="text-sm text-muted-foreground">Số thứ tự</p>
        <p role="status" className="text-5xl font-semibold tabular-nums">
          {result.queueNumber}
        </p>
      </div>
      <div className="space-y-1 text-sm">
        <p>
          {result.roomName} · {result.doctorName}
        </p>
        <p className="text-muted-foreground">{queueSummary(result)}</p>
      </div>
      <div className="flex flex-wrap gap-2">
        <Button onClick={onNext}>Tiếp nhận người tiếp theo</Button>
        <Link href={STAFF_ROUTES.receptionQueue} className={buttonVariants({ variant: "outline" })}>
          Xem hàng đợi tiếp nhận
        </Link>
      </div>
    </section>
  );
}
