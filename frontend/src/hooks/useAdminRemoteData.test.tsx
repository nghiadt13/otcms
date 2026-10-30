import { act, renderHook, waitFor } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { useAdminRemoteData } from "./useAdminRemoteData";

it("discards late responses from the previously selected doctor", async () => {
  let resolveFirst!: (value: string[]) => void;
  const first = vi.fn(
    () =>
      new Promise<string[]>((resolve) => {
        resolveFirst = resolve;
      }),
  );
  const second = vi.fn(async () => ["new doctor"]);
  const { result, rerender } = renderHook(
    ({ load }) => useAdminRemoteData(load),
    {
      initialProps: {
        load: first as (signal: AbortSignal) => Promise<string[]>,
      },
    },
  );
  rerender({ load: second });
  await waitFor(() => expect(result.current.data).toEqual(["new doctor"]));
  await act(async () => resolveFirst(["stale doctor"]));
  expect(result.current.data).toEqual(["new doctor"]);
});
