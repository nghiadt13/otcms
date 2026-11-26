"use client";

import { useEffect, useState } from "react";
import { apiRequest } from "@/lib/api/client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { toast } from "sonner";

type Value = {
  indicator: string;
  value: string;
  unit: string;
  referenceRange: string;
  comment: string;
  abnormal: boolean | null;
};

function isValidResult(value: string) {
  return /^(?:\d+(?:\.\d+)?|\.\d+)$/.test(value.trim());
}

export function LaboratoryResultDraft({
  orderId,
  onIssued,
}: {
  orderId: string;
  onIssued?: () => void;
}) {
  const [values, setValues] = useState<Value[]>([]);
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(false);
  const [loadError, setLoadError] = useState(false);
  const [reload, setReload] = useState(0);
  const [dirty, setDirty] = useState(false);
  const [checked, setChecked] = useState(false);
  const [critical, setCritical] = useState("");
  const [issued, setIssued] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    apiRequest<{ values: Value[] }>(
      `/api/v1/laboratory/orders/${encodeURIComponent(orderId)}/result-draft`,
      { signal: controller.signal },
    )
      .then((result) => {
        if (!controller.signal.aborted) {
          setValues(
            result.values.map((v) => ({
              indicator: v.indicator ?? "",
              value: v.value ?? "",
              unit: v.unit ?? "",
              referenceRange: v.referenceRange ?? "",
              comment: v.comment ?? "",
              abnormal: v.abnormal ?? null,
            })),
          );
          setLoadError(false);
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) setLoadError(true);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [orderId, reload]);
  async function save() {
    setPending(true);
    try {
      await apiRequest(
        `/api/v1/laboratory/orders/${encodeURIComponent(orderId)}/result-draft`,
        { method: "PUT", body: JSON.stringify({ values }) },
      );
      setDirty(false);
      setChecked(false);
      toast.success("Đã lưu bản nháp. Kết quả chưa gửi cho bác sĩ.");
    } catch {
      toast.error("Không thể lưu bản nháp. Vui lòng kiểm tra và thử lại.");
    } finally {
      setPending(false);
    }
  }
  async function approve() {
    setPending(true);
    try {
      await apiRequest(
        `/api/v1/laboratory/orders/${encodeURIComponent(orderId)}/result-approval`,
        {
          method: "POST",
          body: JSON.stringify({
            checked,
            critical: critical === "yes",
          }),
        },
      );
      setIssued(true);
      onIssued?.();
      toast.success("Đã gửi kết quả cho bác sĩ.");
    } catch {
      toast.error(
        "Không thể duyệt kết quả. Kiểm tra chỉ số và trạng thái chỉ định.",
      );
    } finally {
      setPending(false);
    }
  }
  if (loading) return <p role="status">Đang tải bản nháp...</p>;
  if (loadError)
    return (
      <div role="alert">
        Không thể tải bản nháp.{" "}
        <Button
          variant="outline"
          onClick={() => {
            setLoading(true);
            setReload((n) => n + 1);
          }}
        >
          Thử lại
        </Button>
      </div>
    );
  if (values.length === 0)
    return (
      <p role="alert" className="text-sm text-destructive">
        Dịch vụ xét nghiệm chưa có thông số được cấu hình. Vui lòng báo quản trị
        viên kiểm tra danh mục dịch vụ.
      </p>
    );
  return (
    <section className="space-y-4 border-t pt-4">
      <h3 className="font-semibold">Nhập kết quả xét nghiệm</h3>
      <p className="text-sm text-muted-foreground">
        Nhập kết quả dạng số không âm cho từng chỉ số. Tên chỉ số, đơn vị và
        khoảng tham chiếu được cấu hình sẵn.
      </p>
      {values.map((item, index) => (
        <fieldset
          key={index}
          disabled={issued}
          className="grid gap-3 rounded-md border p-4 sm:grid-cols-2"
        >
          <legend>Chỉ số {index + 1}</legend>
          {(
            [
              ["indicator", "Tên chỉ số"],
              ["value", "Kết quả"],
              ["unit", "Đơn vị"],
              ["referenceRange", "Khoảng tham chiếu"],
              ["comment", "Ghi chú"],
            ] as const
          ).map(([field, label]) => (
            <label key={field} className="space-y-1 text-sm">
              <span>{label}</span>
              <Input
                value={item[field]}
                type={field === "value" ? "number" : "text"}
                min={field === "value" ? 0 : undefined}
                step={field === "value" ? "any" : undefined}
                inputMode={field === "value" ? "decimal" : undefined}
                aria-invalid={
                  field === "value" &&
                  item.value.trim() !== "" &&
                  !isValidResult(item.value)
                }
                readOnly={
                  field === "indicator" ||
                  field === "unit" ||
                  field === "referenceRange"
                }
                className={
                  field === "indicator" ||
                  field === "unit" ||
                  field === "referenceRange"
                    ? "bg-muted"
                    : undefined
                }
                onChange={(event) => {
                  if (
                    field === "indicator" ||
                    field === "unit" ||
                    field === "referenceRange"
                  )
                    return;
                  setDirty(true);
                  setChecked(false);
                  setValues((current) =>
                    current.map((row, i) =>
                      i === index
                        ? { ...row, [field]: event.target.value }
                        : row,
                    ),
                  );
                }}
              />
              {field === "value" &&
                item.value.trim() !== "" &&
                !isValidResult(item.value) && (
                  <span className="text-destructive" role="alert">
                    Kết quả phải là số không âm (ví dụ: 3.5).
                  </span>
                )}
            </label>
          ))}
          <label className="space-y-1 text-sm">
            <span>Trạng thái chỉ số</span>
            <select
              className="w-full rounded-md border p-2"
              required
              value={
                item.abnormal === null
                  ? ""
                  : item.abnormal
                    ? "abnormal"
                    : "normal"
              }
              onChange={(event) => {
                setDirty(true);
                setChecked(false);
                const abnormal =
                  event.target.value === ""
                    ? null
                    : event.target.value === "abnormal";
                setValues((current) =>
                  current.map((row, i) =>
                    i === index ? { ...row, abnormal } : row,
                  ),
                );
              }}
            >
              <option value="">Chọn trạng thái</option>
              <option value="normal">Bình thường</option>
              <option value="abnormal">Bất thường</option>
            </select>
          </label>
        </fieldset>
      ))}
      <div className="flex flex-wrap gap-2">
        <Button
          disabled={
            pending ||
            issued ||
            values.some(
              (item) => !item.indicator.trim() || !isValidResult(item.value),
            )
          }
          onClick={() => void save()}
        >
          Lưu bản nháp
        </Button>
      </div>
      {!issued && (
        <>
          <div className="space-y-3">
            <div className="flex flex-col items-start gap-2">
              <span id="critical-result-label" className="text-sm font-medium">
                Kết quả có giá trị nguy cấp?
              </span>
              <select
                aria-labelledby="critical-result-label"
                className="w-48 rounded-md border bg-background p-2"
                value={critical}
                onChange={(event) => setCritical(event.target.value)}
              >
                <option value="">Chọn tình trạng</option>
                <option value="no">Không</option>
                <option value="yes">Có</option>
              </select>
            </div>
          </div>
          <div className="space-y-3 rounded-md border p-4">
            <h3 className="font-semibold">Đối chiếu và gửi bác sĩ</h3>
            <label className="flex gap-2">
              <input
                type="checkbox"
                checked={checked}
                onChange={(event) => setChecked(event.target.checked)}
              />
              <span>
                Tôi đã đối chiếu bệnh nhân, mã mẫu, xét nghiệm, chỉ số, đơn vị
                và khoảng tham chiếu với kết quả trên máy.
              </span>
            </label>
            <Button
              disabled={
                pending ||
                dirty ||
                !checked ||
                !critical ||
                values.some(
                  (v) =>
                    !v.unit.trim() ||
                    !v.referenceRange.trim() ||
                    v.abnormal === null,
                )
              }
              onClick={() => void approve()}
            >
              Duyệt và gửi bác sĩ
            </Button>
            {dirty && <p className="text-sm">Lưu bản nháp trước khi duyệt.</p>}
          </div>
        </>
      )}
    </section>
  );
}
