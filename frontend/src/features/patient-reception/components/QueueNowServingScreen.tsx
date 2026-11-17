"use client";

import { useEffect, useState } from "react";
import { messageFromError } from "@/lib/messages/appMessages";
import { getRoomNowServing } from "../api/receptionLiveApi";

/** Chu kỳ tự làm mới của màn gọi số (giây đổi số cần thấy nhanh). */
export const NOW_SERVING_REFRESH_MS = 5_000;

/**
 * Màn gọi số công khai của một phòng (không đăng nhập): chỉ hiện các số đang gọi, không hiện tên bệnh
 * nhân (BR-35 và quyền riêng tư). Tự tải lại mỗi 5 giây.
 */
export function QueueNowServingScreen({ roomId }: { roomId: string }) {
  const [numbers, setNumbers] = useState<number[] | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    const controller = new AbortController();
    const load = () => {
      getRoomNowServing(roomId, controller.signal)
        .then((next) => {
          setNumbers(next);
          setError("");
        })
        .catch((cause: unknown) => {
          if (!controller.signal.aborted) setError(messageFromError(cause));
        });
    };
    load();
    const timer = window.setInterval(load, NOW_SERVING_REFRESH_MS);
    return () => {
      controller.abort();
      window.clearInterval(timer);
    };
  }, [roomId]);

  return (
    <main className="mx-auto flex min-h-dvh max-w-3xl flex-col items-center justify-center gap-8 px-4 py-10 text-center">
      <h1 className="text-2xl font-semibold">Số đang gọi</h1>
      {error ? (
        <p role="alert" className="text-base text-destructive">
          {error}
        </p>
      ) : numbers === null ? (
        <p role="status" className="text-base text-muted-foreground">
          Đang tải...
        </p>
      ) : numbers.length === 0 ? (
        <p className="text-xl text-muted-foreground">
          Chưa có số nào được gọi.
        </p>
      ) : (
        <ul aria-label="Số đang gọi" className="flex flex-wrap justify-center gap-4">
          {numbers.map((number) => (
            <li
              key={number}
              className="rounded-2xl border bg-card px-10 py-6 text-6xl font-semibold tabular-nums"
            >
              {number}
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
