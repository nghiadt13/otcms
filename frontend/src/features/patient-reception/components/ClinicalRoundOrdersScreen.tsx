"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import {
  createClinicalRoundInvoice,
  getBillingInvoice,
  type BillingInvoice,
} from "@/lib/api/billingApi";
import {
  dispatchClinicalImagingRound,
  listClinicalOrderingRounds,
  listClinicalImagingRoomOptions,
  type ImagingQueueRoom,
  type ClinicalOrderingRound,
} from "../api/receptionLiveApi";
import { ClinicalRoundPaymentForm } from "./PaymentScreens";
import { money } from "./ReceptionUi";

async function loadRoundQueue(page: number, signal?: AbortSignal) {
  const result = await listClinicalOrderingRounds(page, signal);
  const ids = result.items.flatMap((round) =>
    round.orders
      .filter(
        (order) =>
          order.serviceType === "IMAGING" &&
          order.orderStatus === "ORDERED" &&
          order.paymentStatus === "PAID" &&
          !order.diagnostic,
      )
      .map((order) => order.orderId),
  );
  const options = await listClinicalImagingRoomOptions(ids, signal);
  return { result, options };
}

export function ClinicalRoundOrdersScreen() {
  const [rounds, setRounds] = useState<ClinicalOrderingRound[]>([]);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [invoice, setInvoice] = useState<BillingInvoice | null>(null);
  const busy = useRef(false);
  const [roomOptions, setRoomOptions] = useState<
    Record<string, ImagingQueueRoom[]>
  >({});
  const [selectedRooms, setSelectedRooms] = useState<Record<string, string>>(
    {},
  );
  const applyQueue = useCallback(
    ({ result, options }: Awaited<ReturnType<typeof loadRoundQueue>>) => {
      setRoomOptions(
        Object.fromEntries(
          options.map((row) => [row.serviceOrderId, row.items]),
        ),
      );
      setSelectedRooms(
        Object.fromEntries(
          options.map((row) => [
            row.serviceOrderId,
            row.items.length === 1 ? row.items[0].roomId : "",
          ]),
        ),
      );
      setRounds(result.items);
      setHasMore(result.hasMore);
    },
    [],
  );
  const reload = useCallback(
    async (signal?: AbortSignal) => {
      const result = await loadRoundQueue(page, signal);
      if (!signal?.aborted) applyQueue(result);
    },
    [page, applyQueue],
  );
  useEffect(() => {
    const controller = new AbortController();
    void loadRoundQueue(page, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) applyQueue(result);
      })
      .catch(() => {
        if (!controller.signal.aborted)
          setError("Chưa tải được chỉ định cận lâm sàng.");
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [page, applyQueue]);
  async function action(work: () => Promise<void>) {
    if (busy.current) return;
    busy.current = true;
    setPending(true);
    setError("");
    try {
      await work();
    } catch {
      setError(
        "Chưa thực hiện được thao tác. Vui lòng tải lại và kiểm tra trạng thái.",
      );
    } finally {
      busy.current = false;
      setPending(false);
    }
  }
  return (
    <>
      <PageHeader
        title="Chỉ định cận lâm sàng"
        description="Thanh toán theo đợt bác sĩ chỉ định, sau đó gửi yêu cầu hình ảnh cho KTV."
        actions={
          <Button
            variant="outline"
            disabled={pending}
            onClick={() => void action(() => reload())}
          >
            Tải lại
          </Button>
        }
      />
      {error && (
        <p role="alert" className="mb-4 text-destructive">
          {error}
        </p>
      )}
      {loading ? (
        <p role="status">Đang tải chỉ định...</p>
      ) : error && rounds.length === 0 ? null : rounds.length === 0 ? (
        <p role="status">Chưa có đợt chỉ định.</p>
      ) : (
        <div className="space-y-4">
          {rounds.map((round) => {
            const effective = round.orders.filter(
              (order) => order.orderStatus !== "CANCELLED",
            );
            const imaging = effective.filter(
              (order) => order.serviceType === "IMAGING",
            );
            const paid =
              effective.length > 0 &&
              effective.every((order) => order.paymentStatus === "PAID");
            const awaitingDispatch = imaging.filter(
              (order) => !order.diagnostic,
            );
            const roomsSelected = awaitingDispatch.every(
              (order) =>
                selectedRooms[order.orderId] &&
                roomOptions[order.orderId]?.some(
                  (room) => room.roomId === selectedRooms[order.orderId],
                ),
            );
            const dispatched =
              imaging.length > 0 && imaging.every((order) => order.diagnostic);
            return (
              <section
                key={round.roundId}
                aria-label={`${round.patientName ?? round.patientProfileId} · Đợt ${round.roundNumber}`}
                className="space-y-3 rounded-lg border p-4"
              >
                <h2 className="text-lg font-semibold">
                  {round.patientName ?? "Bệnh nhân"} · Đợt #{round.roundNumber}
                </h2>
                <p className="text-sm text-muted-foreground">
                  Lượt khám {round.visitId} ·{" "}
                  {new Date(round.orderedAt).toLocaleString("vi-VN")}
                </p>
                <ul className="divide-y">
                  {round.orders.map((order) => (
                    <li
                      key={order.orderId}
                      className="flex justify-between gap-3 py-2"
                    >
                      <div className="space-y-2">
                        <span>
                          {order.serviceName}
                          {order.orderStatus === "CANCELLED" ? " · Đã hủy" : ""}
                        </span>
                        {order.serviceType === "IMAGING" &&
                          order.orderStatus !== "CANCELLED" &&
                          (order.diagnostic ? (
                            <p className="text-sm">
                              Đã gửi KTV · Phòng:{" "}
                              {order.diagnostic.displayLabel ??
                                order.diagnostic.roomCode ??
                                order.diagnostic.roomId ??
                                "Chưa có phòng"}
                            </p>
                          ) : order.paymentStatus === "PAID" ? (
                            roomOptions[order.orderId]?.length ? (
                              <label className="block text-sm">
                                Phòng thực hiện
                                <select
                                  aria-label={`Phòng thực hiện · ${order.serviceName}`}
                                  className="mt-1 block w-full rounded-md border bg-background px-3 py-2"
                                  value={selectedRooms[order.orderId] ?? ""}
                                  disabled={pending}
                                  onChange={(event) =>
                                    setSelectedRooms((current) => ({
                                      ...current,
                                      [order.orderId]: event.target.value,
                                    }))
                                  }
                                >
                                  <option value="">Chọn phòng</option>
                                  {roomOptions[order.orderId].map((room) => (
                                    <option
                                      key={room.roomId}
                                      value={room.roomId}
                                    >
                                      {room.roomCode} · {room.displayLabel}
                                    </option>
                                  ))}
                                </select>
                              </label>
                            ) : (
                              <p className="text-sm text-muted-foreground">
                                Không có phòng hoặc kỹ thuật viên phù hợp
                              </p>
                            )
                          ) : (
                            <p className="text-sm text-muted-foreground">
                              Chờ thanh toán
                            </p>
                          ))}
                      </div>
                      <span>{money(order.priceSnapshot)}</span>
                    </li>
                  ))}
                </ul>
                <p role="status">
                  {dispatched
                    ? "Đã gửi KTV"
                    : paid
                      ? "Đã thanh toán"
                      : effective.length
                        ? "Chờ thanh toán"
                        : "Đã hủy"}
                </p>
                <div className="flex gap-2">
                  <Button
                    variant="outline"
                    disabled={pending || effective.length === 0}
                    onClick={() =>
                      void action(async () => {
                        const created = await createClinicalRoundInvoice(
                          round.roundId,
                        );
                        setInvoice(await getBillingInvoice(created.id));
                      })
                    }
                  >
                    {paid ? "Mở hóa đơn" : "Thanh toán"}
                  </Button>
                  <Button
                    disabled={
                      pending ||
                      loading ||
                      Boolean(error) ||
                      !paid ||
                      !roomsSelected ||
                      dispatched ||
                      imaging.length === 0
                    }
                    onClick={() =>
                      void action(async () => {
                        await dispatchClinicalImagingRound(
                          round.roundId,
                          awaitingDispatch.map((order) => ({
                            serviceOrderId: order.orderId,
                            roomId: selectedRooms[order.orderId],
                          })),
                        );
                        await reload();
                      })
                    }
                  >
                    Gửi KTV
                  </Button>
                </div>
              </section>
            );
          })}
        </div>
      )}
      <div className="mt-4 flex gap-2">
        <Button
          variant="outline"
          disabled={page === 0 || pending}
          onClick={() => {
            setLoading(true);
            setPage(page - 1);
          }}
        >
          Trang trước
        </Button>
        <Button
          variant="outline"
          disabled={!hasMore || pending}
          onClick={() => {
            setLoading(true);
            setPage(page + 1);
          }}
        >
          Trang sau
        </Button>
      </div>
      {invoice && (
        <ClinicalRoundPaymentForm
          key={invoice.id}
          invoice={invoice}
          onClose={() => setInvoice(null)}
          onPaid={async () => {
            await getBillingInvoice(invoice.id);
            await reload();
            setInvoice(null);
          }}
        />
      )}
    </>
  );
}
