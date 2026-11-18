"use client";

import { useEffect, useState } from "react";
import { apiRequest } from "@/lib/api/client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

type Value = {
  indicator: string;
  value: string;
  unit: string;
  referenceRange: string;
  comment: string;
  abnormal: boolean | null;
};
const blank = (): Value => ({
  indicator: "",
  value: "",
  unit: "",
  referenceRange: "",
  comment: "",
  abnormal: null,
});

export function LaboratoryResultDraft({ orderId }: { orderId: string }) {
  const [values, setValues] = useState<Value[]>([blank()]);
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState("");
  const [loadError, setLoadError] = useState(false);
  const [reload, setReload] = useState(0);
  const [dirty, setDirty] = useState(false);
  const [checked, setChecked] = useState(false);
  const [critical, setCritical] = useState("");
  const [contactedDoctor, setContactedDoctor] = useState("");
  const [contactedAt, setContactedAt] = useState("");
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
            result.values.length
              ? result.values.map((v) => ({
                  indicator: v.indicator ?? "",
                  value: v.value ?? "",
                  unit: v.unit ?? "",
                  referenceRange: v.referenceRange ?? "",
                  comment: v.comment ?? "",
                  abnormal: v.abnormal ?? null,
                }))
              : [blank()],
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
    setMessage("");
    try {
      await apiRequest(
        `/api/v1/laboratory/orders/${encodeURIComponent(orderId)}/result-draft`,
        { method: "PUT", body: JSON.stringify({ values }) },
      );
      setDirty(false);
      setChecked(false);
      setMessage("Đã lưu bản nháp. Kết quả chưa gửi cho bác sĩ.");
    } catch {
      setMessage("Không thể lưu bản nháp. Vui lòng kiểm tra và thử lại.");
    } finally {
      setPending(false);
    }
  }
  async function approve() {
    setPending(true);
    setMessage("");
    try {
      await apiRequest(
        `/api/v1/laboratory/orders/${encodeURIComponent(orderId)}/result-approval`,
        {
          method: "POST",
          body: JSON.stringify({
            checked,
            critical: critical === "yes",
            contactedDoctor: critical === "yes" ? contactedDoctor.trim() : null,
            contactedAt:
              critical === "yes" ? new Date(contactedAt).toISOString() : null,
          }),
        },
      );
      setIssued(true);
      setMessage("Đã duyệt và gửi kết quả cho bác sĩ.");
    } catch {
      setMessage(
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
  return (
    <section className="space-y-4 border-t pt-4">
      <h3 className="font-semibold">Nhập kết quả xét nghiệm</h3>
      <p className="text-sm text-muted-foreground">
        Nhập giá trị từng chỉ số đọc từ máy xét nghiệm, kèm đơn vị và khoảng
        tham chiếu.
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
                onChange={(event) => {
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
          <Button
            variant="outline"
            disabled={values.length === 1 || pending || issued}
            onClick={() => {
              setDirty(true);
              setChecked(false);
              setValues((current) => current.filter((_, i) => i !== index));
            }}
          >
            Xóa chỉ số
          </Button>
        </fieldset>
      ))}
      <div className="flex flex-wrap gap-2">
        <Button
          variant="outline"
          disabled={pending || issued || values.length >= 200}
          onClick={() => {
            setDirty(true);
            setChecked(false);
            setValues((current) => [...current, blank()]);
          }}
        >
          Thêm chỉ số
        </Button>
        <Button
          disabled={
            pending ||
            issued ||
            values.some((item) => !item.indicator.trim() || !item.value.trim())
          }
          onClick={() => void save()}
        >
          Lưu bản nháp
        </Button>
      </div>
      {message && <p role="status">{message}</p>}
      {!issued && (
        <div className="space-y-3 rounded-md border p-4">
          <h3 className="font-semibold">Đối chiếu và gửi bác sĩ</h3>
          <label className="flex gap-2">
            <input
              type="checkbox"
              checked={checked}
              onChange={(event) => setChecked(event.target.checked)}
            />
            <span>
              Tôi đã đối chiếu bệnh nhân, mã mẫu, xét nghiệm, chỉ số, đơn vị và
              khoảng tham chiếu với kết quả trên máy.
            </span>
          </label>
          <label className="block space-y-1">
            <span>Kết quả có giá trị nguy cấp?</span>
            <select
              className="w-full rounded-md border p-2"
              value={critical}
              onChange={(event) => setCritical(event.target.value)}
            >
              <option value="">Chọn tình trạng</option>
              <option value="no">Không</option>
              <option value="yes">Có</option>
            </select>
          </label>
          {critical === "yes" && (
            <>
              <p className="text-sm">
                Liên hệ trực tiếp bác sĩ trước khi phát hành.
              </p>
              <label className="block">
                Người nhận thông báo
                <Input
                  value={contactedDoctor}
                  onChange={(event) => setContactedDoctor(event.target.value)}
                />
              </label>
              <label className="block">
                Thời gian liên hệ
                <Input
                  type="datetime-local"
                  value={contactedAt}
                  onChange={(event) => setContactedAt(event.target.value)}
                />
              </label>
            </>
          )}
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
              ) ||
              (critical === "yes" && (!contactedDoctor.trim() || !contactedAt))
            }
            onClick={() => void approve()}
          >
            Duyệt và gửi bác sĩ
          </Button>
          {dirty && <p className="text-sm">Lưu bản nháp trước khi duyệt.</p>}
        </div>
      )}
    </section>
  );
}
