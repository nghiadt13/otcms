import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import {
  listServiceCatalog,
  getServiceCatalog,
  createServiceCatalog,
  updateServiceCatalog,
  discontinueServiceCatalog,
  activateServiceCatalog,
} from "./serviceCatalogApi";
const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
beforeEach(() => {
  request.mockReset();
});
const service = {
  id: "s",
  name: "Dịch vụ loi",
  description: null,
  price: 200000,
  durationMinutes: 20,
  bufferMinutes: 5,
  onlineBookable: true,
  active: true,
};
it("reads all admin services, filters and paginates; 'loi' is ordinary search", async () => {
  request.mockResolvedValue({
    items: [service, { ...service, id: "old", active: false }],
  });
  expect(
    (await listServiceCatalog({ q: "loi", status: "DISCONTINUED" })).items[0]
      .id,
  ).toBe("old");
  expect(await getServiceCatalog("s")).toEqual(service);
  await expect(getServiceCatalog("missing")).rejects.toMatchObject({
    code: "MSG-50",
  });
});
it("uses staff fallback only when the admin endpoint is missing", async () => {
  request
    .mockRejectedValueOnce(new ApiError(404))
    .mockResolvedValueOnce({ items: [service] });
  expect((await listServiceCatalog()).items).toEqual([service]);
  expect(request.mock.calls[1][0]).toBe("/api/v1/staff/clinic-services");
  request.mockReset();
  request.mockRejectedValue(new ApiError(403));
  expect(
    await listServiceCatalog().catch((e) => ({ status: e.status })),
  ).toEqual({ status: 403 });
  expect(request).toHaveBeenCalledTimes(1);
});
it("sends exactly the BE service fields and uses soft deactivation/activation", async () => {
  const { id, active, ...body } = service;
  void active;
  request.mockImplementation(async (_path, init) =>
    init?.method ? service : { items: [service] },
  );
  await createServiceCatalog(body);
  expect(request).toHaveBeenLastCalledWith("/api/v1/admin/clinic-services", {
    method: "POST",
    body: JSON.stringify(body),
  });
  await updateServiceCatalog(id, body);
  expect(request.mock.calls.at(-1)?.[1].method).toBe("PUT");
  await discontinueServiceCatalog(id);
  expect(
    request.mock.calls.some(
      ([p, i]) => p.endsWith("/s") && i?.method === "DELETE",
    ),
  ).toBe(true);
  await activateServiceCatalog(id);
  expect(
    request.mock.calls.some(
      ([p, i]) => p.endsWith("/s/activation") && i?.method === "POST",
    ),
  ).toBe(true);
});
