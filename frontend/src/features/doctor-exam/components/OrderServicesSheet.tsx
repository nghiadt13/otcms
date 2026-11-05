"use client";

import { useRef, useState } from "react";
import { FieldError } from "@/components/admin/FieldError";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Textarea } from "@/components/ui/textarea";
import { ApiError } from "@/lib/api/apiError";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { notifyError } from "@/lib/messages/notify";
import { cn } from "@/lib/utils";
import { getDoctorExamApi } from "../api/doctorExamApi";
import { useAsyncAction } from "../hooks/useAsyncAction";
import {
  draftItems,
  draftTotal,
  servicesOfKind,
  toggleService,
  validateDraft,
  type DraftErrors,
  type OrderDraft,
} from "../schema/orderDraft";
import type {
  CatalogService,
  ParaclinicalOrder,
  ServiceKind,
} from "../types/doctorExam";
import { BackendGapNotice } from "./BackendGapNotice";

// Phiếu chỉ định cận lâm sàng của một lượt khám (UC45): chọn dịch vụ trong danh mục, ghi lý do
// lâm sàng (và vùng chụp với chẩn đoán hình ảnh), gửi thành một đợt chỉ định (BR-42).

const KIND_LABEL: Record<ServiceKind, string> = {
  IMAGING: "Chẩn đoán hình ảnh",
  LABORATORY: "Xét nghiệm",
};

export function OrderServicesSheet({
  open,
  onOpenChange,
  visitId,
  catalog,
  laboratoryEnabled,
  onSubmitted,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  visitId: string;
  catalog: readonly CatalogService[];
  laboratoryEnabled: boolean;
  onSubmitted: (orders: ParaclinicalOrder[]) => void;
}) {
  const [kind, setKind] = useState<ServiceKind>("IMAGING");
  const [query, setQuery] = useState("");
  const [draft, setDraft] = useState<OrderDraft>({});
  const [errors, setErrors] = useState<DraftErrors>({});
  // Một khóa cho mỗi lần mở phiếu: gửi lại do mạng chập chờn không tạo thêm đợt (Idempotency-Key).
  const key = useRef(crypto.randomUUID());
  const { pending, run } = useAsyncAction();
  const services = servicesOfKind(catalog, kind, query);
  const selected = catalog.filter((service) => service.id in draft);
  const locked = kind === "LABORATORY" && !laboratoryEnabled;

  const patch = (
    id: string,
    field: "clinicalReason" | "bodyArea" | "instructions",
    value: string,
  ) => setDraft((cur) => ({ ...cur, [id]: { ...cur[id], [field]: value } }));

  async function submit() {
    if (selected.length === 0) return;
    const found = validateDraft(draft, catalog);
    setErrors(found);
    if (Object.keys(found).length) {
      notifyError(new ApiError(400, { code: "MSG-39" }));
      return;
    }
    const created = await run(
      () =>
        getDoctorExamApi().submitOrders(
          visitId,
          draftItems(draft, catalog),
          [...catalog],
          key.current,
        ),
      "MSG-45",
    );
    if (!created) return;
    onSubmitted(created);
    setDraft({});
    setErrors({});
    key.current = crypto.randomUUID();
    onOpenChange(false);
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="w-full gap-0 sm:max-w-3xl">
        <SheetHeader className="border-b">
          <SheetTitle>Chỉ định cận lâm sàng</SheetTitle>
          <SheetDescription>
            Chọn dịch vụ rồi ghi lý do lâm sàng. Mỗi chỉ định chờ thanh toán
            trước khi thực hiện.
          </SheetDescription>
        </SheetHeader>

        <div className="grid min-h-0 flex-1 gap-0 overflow-y-auto md:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
          <div className="space-y-3 border-b p-4 md:border-r md:border-b-0">
            <div
              role="tablist"
              aria-label="Loại dịch vụ"
              className="flex gap-1.5"
            >
              {(Object.keys(KIND_LABEL) as ServiceKind[]).map((item) => (
                <Button
                  key={item}
                  type="button"
                  size="sm"
                  role="tab"
                  aria-selected={kind === item}
                  variant={kind === item ? "default" : "outline"}
                  onClick={() => setKind(item)}
                >
                  {KIND_LABEL[item]}
                </Button>
              ))}
            </div>
            <Input
              type="search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Tìm dịch vụ"
              aria-label="Tìm dịch vụ"
            />
            {locked ? (
              <BackendGapNotice title="Chưa chỉ định xét nghiệm được">
                Backend hiện chỉ nhận chỉ định chẩn đoán hình ảnh.
              </BackendGapNotice>
            ) : services.length === 0 ? (
              <p className="py-6 text-center text-sm text-muted-foreground">
                Không có dịch vụ phù hợp.
              </p>
            ) : (
              <ul className="divide-y rounded-md border">
                {services.map((service) => {
                  const checked = service.id in draft;
                  return (
                    <li key={service.id}>
                      <label
                        className={cn(
                          "flex cursor-pointer items-start gap-3 px-3 py-2.5 hover:bg-accent",
                          checked && "bg-secondary/60",
                        )}
                      >
                        <Checkbox
                          checked={checked}
                          onCheckedChange={() =>
                            setDraft((cur) => toggleService(cur, service.id))
                          }
                          aria-label={service.name}
                          className="mt-0.5"
                        />
                        <span className="min-w-0 flex-1">
                          <span className="block text-sm font-medium">
                            {service.name}
                          </span>
                          <span className="block text-xs text-muted-foreground">
                            {[
                              service.modality,
                              formatCurrencyVnd(service.price),
                            ]
                              .filter(Boolean)
                              .join(" · ")}
                          </span>
                        </span>
                      </label>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>

          <div className="space-y-4 p-4">
            {selected.length === 0 ? (
              <p className="py-10 text-center text-sm text-muted-foreground">
                Chưa chọn dịch vụ nào. Chọn ở cột bên trái để ghi lý do chỉ
                định.
              </p>
            ) : (
              selected.map((service) => {
                const item = draft[service.id];
                const problem = errors[service.id];
                return (
                  <fieldset
                    key={service.id}
                    disabled={pending}
                    className="space-y-3 rounded-md border p-3"
                  >
                    <legend className="px-1 text-sm font-semibold">
                      {service.name}
                    </legend>
                    <div className="space-y-1.5">
                      <Label htmlFor={`reason-${service.id}`}>
                        Lý do lâm sàng
                      </Label>
                      <Textarea
                        id={`reason-${service.id}`}
                        rows={2}
                        value={item.clinicalReason}
                        aria-invalid={!!problem?.clinicalReason}
                        onChange={(e) =>
                          patch(service.id, "clinicalReason", e.target.value)
                        }
                      />
                      <FieldError message={problem?.clinicalReason} />
                    </div>
                    {service.kind === "IMAGING" ? (
                      <>
                        <div className="space-y-1.5">
                          <Label htmlFor={`area-${service.id}`}>
                            Vùng cần chụp
                          </Label>
                          <Input
                            id={`area-${service.id}`}
                            value={item.bodyArea}
                            aria-invalid={!!problem?.bodyArea}
                            onChange={(e) =>
                              patch(service.id, "bodyArea", e.target.value)
                            }
                          />
                          <FieldError message={problem?.bodyArea} />
                        </div>
                        <div className="space-y-1.5">
                          <Label htmlFor={`note-${service.id}`}>
                            Hướng dẫn cho kỹ thuật viên (không bắt buộc)
                          </Label>
                          <Input
                            id={`note-${service.id}`}
                            value={item.instructions}
                            onChange={(e) =>
                              patch(service.id, "instructions", e.target.value)
                            }
                          />
                        </div>
                      </>
                    ) : null}
                  </fieldset>
                );
              })
            )}
          </div>
        </div>

        <SheetFooter className="border-t sm:flex-row sm:items-center sm:justify-between">
          <p className="text-sm text-muted-foreground">
            {selected.length} dịch vụ ·{" "}
            <span className="font-medium text-foreground">
              {formatCurrencyVnd(draftTotal(draft, catalog))}
            </span>
          </p>
          <div className="flex gap-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
            >
              Đóng
            </Button>
            <Button
              type="button"
              disabled={pending || selected.length === 0}
              onClick={() => void submit()}
            >
              {pending ? "Đang gửi..." : "Gửi chỉ định"}
            </Button>
          </div>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  );
}
