"use client";

import { ArrowRight, CircleCheck, CircleDashed, CircleDot } from "lucide-react";
import Link from "next/link";
import { PageHeader } from "@/components/PageHeader";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { buttonVariants } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { doctorVisitHref } from "@/lib/routes/doctorRoutes";
import { cn } from "@/lib/utils";
import {
  useAdvice,
  useExamination,
  useOrders,
  usePrescription,
} from "../hooks/useDoctorData";
import {
  examinationStep,
  ordersStep,
  treatmentStep,
  type StepState,
  type StepSummary,
} from "../schema/visitProgress";
import { doctorErrorMessage } from "../schema/doctorErrors";
import { OrderStatusBadge } from "./doctorStatus";
import {
  mergeWorkspaceOrders,
  useVisitWorkspace,
} from "./VisitWorkspaceProvider";

const STATE_ICON = {
  TODO: CircleDashed,
  IN_PROGRESS: CircleDot,
  DONE: CircleCheck,
} satisfies Record<StepState, typeof CircleDot>;

const STATE_LABEL: Record<StepState, string> = {
  TODO: "Chưa làm",
  IN_PROGRESS: "Đang làm",
  DONE: "Đã xong",
};

function Step({
  title,
  step,
  href,
  action,
}: {
  title: string;
  step: StepSummary | undefined;
  href: string;
  action: string;
}) {
  if (!step) return <Skeleton className="h-16 w-full" />;
  const Icon = STATE_ICON[step.state];
  return (
    <li className="flex flex-wrap items-center gap-x-4 gap-y-2 py-4">
      <Icon
        aria-hidden
        className={cn(
          "size-5 shrink-0",
          step.state === "DONE" ? "text-success" : "text-muted-foreground",
        )}
      />
      <div className="min-w-0 flex-1 basis-60">
        <p className="font-medium">{title}</p>
        <p className="text-sm text-muted-foreground">{step.summary}</p>
      </div>
      <StatusBadge
        tone={
          step.state === "DONE"
            ? "success"
            : step.state === "IN_PROGRESS"
              ? "info"
              : "neutral"
        }
      >
        {STATE_LABEL[step.state]}
      </StatusBadge>
      <Link
        href={href}
        className={buttonVariants({ variant: "outline", size: "sm" })}
      >
        {action}
        <ArrowRight aria-hidden />
      </Link>
    </li>
  );
}

/** Bước có dữ liệu tải lỗi: hiện lý do thay vì khung chờ vô tận (ví dụ 403 khi chưa bắt đầu khám). */
function failed(remote: { error?: unknown }): StepSummary | undefined {
  return remote.error
    ? {
        state: "TODO",
        summary: `Chưa đọc được: ${doctorErrorMessage(remote.error)}`,
      }
    : undefined;
}

function stepOf<T>(
  remote: { data?: T; error?: unknown },
  build: (data: T) => StepSummary,
): StepSummary | undefined {
  return (
    failed(remote) ??
    (remote.data === undefined ? undefined : build(remote.data))
  );
}

export function VisitOverview() {
  const workspace = useVisitWorkspace();
  const { visitId } = workspace;
  const examination = useExamination(visitId);
  const orders = useOrders(visitId);
  const advice = useAdvice(visitId);
  const prescription = usePrescription(visitId);
  const href = (section: string) => doctorVisitHref(visitId, section);
  const paraclinicalStep =
    failed(orders) ??
    (orders.data === undefined
      ? undefined
      : ordersStep(
          mergeWorkspaceOrders(
            orders.data,
            workspace.submittedOrders,
            workspace.findingsAwaitingRelease,
          ),
        ));

  return (
    <>
      <PageHeader
        title="Tổng quan lượt khám"
        description="Tiến trình khám của bệnh nhân này và lối vào từng bước."
      />
      <div className="grid gap-6 xl:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <section
          aria-labelledby="progress-title"
          className="rounded-lg border bg-card px-5"
        >
          <h2 id="progress-title" className="sr-only">
            Các bước của lượt khám
          </h2>
          <ul className="divide-y">
            <Step
              title="Ghi nhận khám và chẩn đoán"
              step={stepOf(examination, examinationStep)}
              href={href("examination")}
              action="Khám bệnh"
            />
            <Step
              title="Cận lâm sàng"
              step={paraclinicalStep}
              href={href("paraclinical")}
              action="Chỉ định"
            />
            <Step
              title="Hướng điều trị và đơn thuốc"
              step={
                failed(advice) ??
                failed(prescription) ??
                (advice.data !== undefined && prescription.data !== undefined
                  ? treatmentStep(advice.data, prescription.data)
                  : undefined)
              }
              href={href("treatment")}
              action="Điều trị"
            />
            <Step
              title="Hoàn tất lượt khám"
              step={{
                state: "TODO",
                summary: "Kiểm tra điều kiện rồi hoàn tất khám.",
              }}
              href={href("closing")}
              action="Hoàn tất"
            />
          </ul>
        </section>

        <section
          aria-labelledby="orders-title"
          className="rounded-lg border bg-card p-5"
        >
          <h2 id="orders-title" className="text-lg font-semibold">
            Trạng thái chỉ định
          </h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Chỉ hiện trạng thái; kết quả xem ở tab Cận lâm sàng.
          </p>
          {orders.error ? (
            <p role="alert" className="mt-4 text-sm text-destructive">
              Chưa tải được trạng thái chỉ định.
            </p>
          ) : orders.data === undefined ? (
            <Skeleton className="mt-4 h-20 w-full" />
          ) : orders.data.length === 0 ? (
            <p className="mt-4 text-sm text-muted-foreground">
              Chưa có chỉ định nào.
            </p>
          ) : (
            <ul className="mt-3 divide-y">
              {orders.data.map((order) => (
                <li
                  key={order.id}
                  className="flex items-center justify-between gap-3 py-2.5"
                >
                  <span className="min-w-0 truncate text-sm">
                    {order.serviceName}
                  </span>
                  <OrderStatusBadge order={order} />
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </>
  );
}
