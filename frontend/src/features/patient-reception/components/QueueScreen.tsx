"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import {
  listReceptionExamQueue,
  type ExamQueueItem,
} from "@/lib/api/examQueueApi";
import { messageFromError } from "@/lib/messages/appMessages";
import { QueueDoctorGroup } from "./QueueDoctorGroup";
import { WalkInFeeStep } from "./WalkInFeeStep";
import { DataLoading, DataTable } from "./ReceptionUi";

const REFRESH_MS = 15_000;

/** Hàng chờ khám hôm nay theo bác sĩ (A1). Lượt chờ thu phí nằm riêng để lễ tân thu tiếp. */
export function QueueScreen() {
  const [items, setItems] = useState<ExamQueueItem[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [paying, setPaying] = useState<ExamQueueItem | null>(null);
  const inFlight = useRef(false);
  const mounted = useRef(false);

  const load = useCallback(async () => {
    if (inFlight.current) return;
    inFlight.current = true;
    setLoading(true);
    try {
      const page = await listReceptionExamQueue();
      if (mounted.current) {
        setItems(page.items);
        setError("");
      }
    } catch (cause) {
      if (mounted.current) setError(messageFromError(cause));
    } finally {
      inFlight.current = false;
      if (mounted.current) {
        setLoaded(true);
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    mounted.current = true;
    const initialLoad = window.setTimeout(() => void load(), 0);
    const interval = window.setInterval(() => void load(), REFRESH_MS);
    return () => {
      mounted.current = false;
      window.clearTimeout(initialLoad);
      window.clearInterval(interval);
    };
  }, [load]);

  const awaitingPayment = items.filter(
    (item) => item.examStatus === "PENDING_PAYMENT",
  );
  const groups = groupByDoctor(
    items.filter((item) => item.examStatus !== "PENDING_PAYMENT"),
  );

  return (
    <>
      <PageHeader
        title="Hàng chờ khám"
        description="Bệnh nhân xếp theo bác sĩ và số thứ tự trong ngày. Danh sách tự làm mới mỗi 15 giây."
        actions={
          <Button
            variant="outline"
            disabled={loading}
            onClick={() => void load()}
          >
            {loading ? "Đang tải..." : "Làm mới"}
          </Button>
        }
      />
      {error && (
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
          <Button variant="outline" onClick={() => void load()}>
            Thử lại
          </Button>
        </div>
      )}
      {!loaded ? (
        <DataLoading />
      ) : items.length === 0 ? (
        <p className="rounded-lg border p-6 text-center text-sm text-muted-foreground">
          Chưa có bệnh nhân trong hàng chờ hôm nay. Bệnh nhân có lịch hẹn được
          đưa vào hàng chờ khi tiếp nhận; khách đến trực tiếp thì tạo lượt khám
          ở mục Tiếp nhận bệnh nhân.
        </p>
      ) : (
        <div className="space-y-8">
          {awaitingPayment.length > 0 && (
            <section className="space-y-3">
              <h2 className="text-lg font-semibold">Chờ thu phí</h2>
              <DataTable
                label="Lượt chờ thu phí"
                headers={["Bệnh nhân", "Bác sĩ", "Thao tác"]}
                empty="Không có lượt chờ thu phí."
                rows={awaitingPayment.map((item) => ({
                  id: item.visitId,
                  cells: [
                    item.patientName,
                    item.doctorName,
                    item.serviceId ? (
                      <Button
                        key="pay"
                        variant="outline"
                        onClick={() => setPaying(item)}
                      >
                        Thu phí
                      </Button>
                    ) : (
                      "Chưa có dịch vụ"
                    ),
                  ],
                }))}
              />
            </section>
          )}
          {groups.map((group) => (
            <QueueDoctorGroup
              key={group.doctorProfileId}
              doctorName={group.doctorName}
              roomLabel={group.roomLabel}
              items={group.items}
            />
          ))}
        </div>
      )}
      {paying?.serviceId && (
        <WalkInFeeStep
          visitId={paying.visitId}
          serviceId={paying.serviceId}
          onClose={() => setPaying(null)}
          onQueued={() => {
            setPaying(null);
            void load();
          }}
        />
      )}
    </>
  );
}

/** Gom lượt theo bác sĩ, giữ thứ tự BE trả về (theo tên bác sĩ rồi số thứ tự). */
function groupByDoctor(items: ExamQueueItem[]) {
  const groups = new Map<
    string,
    {
      doctorProfileId: string;
      doctorName: string;
      roomLabel: string | null;
      items: ExamQueueItem[];
    }
  >();
  for (const item of items) {
    const group = groups.get(item.doctorProfileId) ?? {
      doctorProfileId: item.doctorProfileId,
      doctorName: item.doctorName,
      roomLabel: item.roomLabel,
      items: [],
    };
    group.items.push(item);
    groups.set(item.doctorProfileId, group);
  }
  return [...groups.values()];
}
