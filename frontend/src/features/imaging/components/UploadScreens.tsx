"use client";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Checkbox } from "@/components/ui/checkbox";
import { Textarea } from "@/components/ui/textarea";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useRef, useState, type FormEvent } from "react";
import { useImaging } from "../ImagingProvider";
import { CURRENT_ROOM } from "../demoData";
import {
  ActionLink,
  Badge,
  Empty,
  Icon,
  Info,
  PageHeading,
  Panel,
} from "./ImagingUi";
import { PatientHeader } from "./OrderScreens";
import type { UploadDraft } from "../types";

const LIMIT = 500 * 1024 * 1024;
export function UploadScreen({ id }: { id: string }) {
  const { state, saveDraft, notify } = useImaging();
  const router = useRouter();
  const input = useRef<HTMLInputElement>(null);
  const order = state.orders.find(
    (o) => o.id === id && o.room === CURRENT_ROOM && o.kind === "imaging",
  );
  const previous = state.drafts[id];
  const replacement = !!state.studies[id]?.length;
  const [files, setFiles] = useState<UploadDraft["files"]>(
    previous && !previous.sent ? previous.files : [],
  );
  const [patient, setPatient] = useState(
    previous && !previous.sent ? previous.patientId : "",
  );
  const [orderCode, setOrderCode] = useState(
    previous && !previous.sent ? previous.orderId : "",
  );
  const [uid, setUid] = useState(
    previous && !previous.sent ? previous.uid : "",
  );
  const [series, setSeries] = useState(
    previous && !previous.sent ? previous.series : 1,
  );
  const [reason, setReason] = useState(
    previous && !previous.sent ? previous.reason : "",
  );
  const [confirmed, setConfirmed] = useState(
    previous && !previous.sent ? previous.confirmed : false,
  );
  const [reading, setReading] = useState(false);
  const [batch, setBatch] = useState(1);
  const [formError, setFormError] = useState("");
  if (!order || !["Paid", "Results Available"].includes(order.status))
    return (
      <Empty
        title="Không thể tải study cho chỉ định này"
        description="Chỉ định phải thuộc phòng của bạn, đã thanh toán và chưa bị hủy."
      >
        <ActionLink href={`/imaging/orders/${id}`}>
          Quay lại chỉ định
        </ActionLink>
      </Empty>
    );
  async function addFiles(selected: FileList | File[] | null) {
    if (!selected?.length) return;
    const list = Array.from(selected);
    if (list.reduce((n, f) => n + f.size, 0) > LIMIT) {
      setFormError(
        "Mỗi đợt chọn file không vượt quá 500 MB. Chia study lớn thành nhiều đợt tải.",
      );
      return;
    }
    setReading(true);
    setFormError("");
    try {
      const checked = await Promise.all(
        list.map(async (f) => {
          const bytes = new Uint8Array(await f.slice(0, 132).arrayBuffer());
          const valid =
            f.name.toLowerCase().endsWith(".dcm") &&
            bytes.length >= 132 &&
            String.fromCharCode(...bytes.slice(128, 132)) === "DICM";
          return { name: `Đợt ${batch} · ${f.name}`, size: f.size, valid };
        }),
      );
      setFiles((current) => [...current, ...checked]);
    } catch {
      setFormError("Không đọc được file đã chọn. Vui lòng chọn lại file.");
    } finally {
      setReading(false);
      if (input.current) input.current.value = "";
    }
  }
  function useSample() {
    setFiles([
      { name: "Đợt 1 · series-01-AP.dcm", size: 2_100_000, valid: true },
      { name: "Đợt 1 · series-02-LAT.dcm", size: 2_150_000, valid: true },
    ]);
    setPatient(order!.patientId);
    setOrderCode(id);
    setUid(
      `1.2.826.0.1.3680043.10.543.${id.split("-")[1]}.${(state.studies[id]?.length || 0) + 1}`,
    );
    setSeries(2);
    setFormError("");
    notify("Đã nạp bộ thông tin DICOM mẫu để thao tác thử.");
  }
  function validate(e: FormEvent) {
    e.preventDefault();
    const errors: string[] = [];
    if (!files.length)
      errors.push("Chưa có file DICOM. Chọn file hoặc sử dụng bộ dữ liệu mẫu.");
    if (files.some((f) => !f.valid))
      errors.push(
        "Có file không đúng định dạng DICOM được hỗ trợ. Chọn file .dcm có tiền tố DICM.",
      );
    if (patient.trim() !== order!.patientId)
      errors.push(
        "Mã bệnh nhân của study không khớp với bệnh nhân trên chỉ định.",
      );
    if (orderCode.trim() !== id)
      errors.push("Mã chỉ định của study không khớp với chỉ định đang mở.");
    if (!/^\d+(\.\d+)+$/.test(uid) || uid.length > 64)
      errors.push(
        "Study Instance UID phải là chuỗi số phân tách bởi dấu chấm, tối đa 64 ký tự.",
      );
    if (!Number.isInteger(series) || series < 1)
      errors.push("Số series phải là số nguyên lớn hơn 0.");
    if (!confirmed)
      errors.push("Cần xác nhận đã đối chiếu đúng bệnh nhân và chỉ định.");
    if (replacement && !reason.trim())
      errors.push("Cần nhập lý do khi thay thế study đã gửi.");
    saveDraft(id, {
      patientId: patient.trim(),
      orderId: orderCode.trim(),
      uid,
      series,
      files,
      confirmed,
      reason: reason.trim(),
      errors,
      checkedAt: "09:30",
      sent: false,
    });
    router.push(`/imaging/orders/${id}/validation`);
  }
  return (
    <>
      <Link href={`/imaging/orders/${id}`} className="im-back-link">
        <Icon name="back" size={16} />
        Chi tiết {id}
      </Link>
      <PageHeading
        eyebrow="UPLOAD & ĐỐI CHIẾU STUDY"
        title={replacement ? "Thay thế study đã gửi" : "Tải và gửi ảnh DICOM"}
        description="Chọn các series thuộc cùng study và đối chiếu với chỉ định đang mở."
      />
      <div className="im-upload-steps">
        <span className="im-upload-current">
          <b>1</b>Chọn & đối chiếu
        </span>
        <i />
        <span>
          <b>2</b>Kiểm tra & gửi
        </span>
        <i />
        <span>
          <b>3</b>Hoàn tất
        </span>
      </div>
      <PatientHeader order={order} />
      <form onSubmit={validate}>
        <div className="im-two-column">
          <Panel
            title="Bộ ảnh DICOM"
            subtitle={`${order.service} · ${order.bodyArea}`}
          >
            <div className="im-panel-body">
              <div
                className="im-drop-zone"
                onDragOver={(e) => e.preventDefault()}
                onDrop={(e) => {
                  e.preventDefault();
                  void addFiles(e.dataTransfer.files);
                }}
              >
                <span className="im-upload-icon">
                  <Icon name="upload" size={30} />
                </span>
                <h3>Kéo thả các file DICOM vào đây</h3>
                <p>Chọn nhiều file thuộc cùng một study</p>
                <Input
                  ref={input}
                  type="file"
                  multiple
                  accept=".dcm,application/dicom"
                  className="im-sr-only"
                  aria-label="Chọn file DICOM"
                  onChange={(e) => void addFiles(e.target.files)}
                />
                <Button
                  variant="outline"
                  className="im-button"
                  type="button"
                  disabled={reading}
                  onClick={() => input.current?.click()}
                >
                  {reading ? "Đang đọc file…" : "Chọn file từ thiết bị"}
                </Button>
                <small>.dcm · Tối đa 500 MB mỗi đợt chọn</small>
              </div>
              <div className="im-inline-between im-batch-actions">
                <Button
                  variant="ghost"
                  type="button"
                  className="im-text-button"
                  onClick={useSample}
                >
                  <Icon name="layers" size={16} />
                  Dùng bộ dữ liệu mẫu
                </Button>
                <Button
                  variant="ghost"
                  type="button"
                  className="im-text-button"
                  disabled={!files.length}
                  onClick={() => {
                    setBatch(batch + 1);
                    notify(
                      `Đã mở đợt ${batch + 1}. Tiếp tục chọn file của cùng study.`,
                    );
                  }}
                >
                  Thêm đợt tải · {batch}
                </Button>
              </div>
              {formError && (
                <p role="alert" className="im-form-error">
                  {formError}
                </p>
              )}
              {files.length > 0 && (
                <div className="im-file-list">
                  {files.map((f, i) => (
                    <div key={`${f.name}-${i}`}>
                      <span
                        className={`im-file-icon ${!f.valid ? "im-danger" : ""}`}
                      >
                        <Icon name="file" />
                      </span>
                      <div>
                        <strong>{f.name}</strong>
                        <small>
                          {(f.size / 1024 / 1024).toFixed(2)} MB ·{" "}
                          {f.valid
                            ? "Đã đọc định dạng"
                            : "Định dạng chưa phù hợp"}
                        </small>
                      </div>
                      <Button
                        variant="ghost"
                        type="button"
                        className="im-icon-button"
                        aria-label={`Bỏ file ${f.name}`}
                        onClick={() =>
                          setFiles(files.filter((_, n) => n !== i))
                        }
                      >
                        <Icon name="close" size={16} />
                      </Button>
                    </div>
                  ))}
                  <p className="im-small im-muted">
                    {files.length} file · Tổng{" "}
                    {(
                      files.reduce((n, f) => n + f.size, 0) /
                      1024 /
                      1024
                    ).toFixed(2)}{" "}
                    MB
                  </p>
                </div>
              )}
            </div>
          </Panel>
          <div className="im-stack">
            <Panel
              title="Thông tin đối chiếu"
              subtitle="Nhập thông tin từ bộ ảnh đã xuất"
            >
              <div className="im-panel-body im-form-grid">
                <label className="im-field">
                  Mã bệnh nhân trong study
                  <Input
                    value={patient}
                    onChange={(e) => setPatient(e.target.value)}
                    placeholder={order.patientId}
                  />
                </label>
                <label className="im-field">
                  Mã chỉ định trong bộ ảnh
                  <Input
                    value={orderCode}
                    onChange={(e) => setOrderCode(e.target.value)}
                    placeholder={id}
                  />
                </label>
                <label className="im-field im-span-2">
                  Study Instance UID
                  <Input
                    value={uid}
                    onChange={(e) => setUid(e.target.value)}
                    placeholder="1.2.826.0.1…"
                  />
                </label>
                <label className="im-field">
                  Số series
                  <Input
                    type="number"
                    min="1"
                    value={series}
                    onChange={(e) => setSeries(Number(e.target.value))}
                  />
                </label>
                <div className="im-field">
                  <span>Phòng thực hiện</span>
                  <div className="im-readonly">{CURRENT_ROOM}</div>
                </div>
                {replacement && (
                  <label className="im-field im-span-2">
                    <span>
                      Lý do thay thế <b className="im-danger">*</b>
                    </span>
                    <Textarea
                      value={reason}
                      maxLength={1000}
                      onChange={(e) => setReason(e.target.value)}
                      placeholder="Ví dụ: Study trước bị mờ, thực hiện lại do lỗi phòng khám…"
                    />
                  </label>
                )}
                <label className="im-check im-span-2">
                  <Checkbox
                    checked={confirmed}
                    onCheckedChange={(checked) =>
                      setConfirmed(checked === true)
                    }
                  />
                  <span>
                    Tôi đã đối chiếu đúng bệnh nhân, chỉ định và các series
                    thuộc cùng study.
                  </span>
                </label>
              </div>
            </Panel>
            <details className="im-help">
              <summary>Lưu ý khi gửi và thay thế study</summary>
              <Info>
                Chụp lại do lỗi phòng khám không thu thêm phí. Thay study cần lý
                do và giữ lại phiên bản trước; lượt khám đã hoàn tất sẽ có phiên
                bản phiếu kết quả mới. Bản xem thử không gửi file lên máy chủ.
              </Info>
            </details>
          </div>
        </div>
        <div className="im-action-footer">
          <ActionLink secondary href={`/imaging/orders/${id}`}>
            Quay lại
          </ActionLink>
          <Button type="submit" className="im-button" disabled={reading}>
            Kiểm tra và tiếp tục <Icon name="arrow" size={16} />
          </Button>
        </div>
      </form>
    </>
  );
}

export function ValidationScreen({ id }: { id: string }) {
  const { state, sendStudy } = useImaging();
  const [submitting, setSubmitting] = useState(false);
  const order = state.orders.find(
    (o) => o.id === id && o.room === CURRENT_ROOM && o.kind === "imaging",
  );
  const draft = state.drafts[id];
  if (!order || !draft)
    return (
      <>
        <PageHeading
          title="Kiểm tra bộ ảnh DICOM"
          description="Kiểm tra bộ ảnh trước khi gửi cho bác sĩ."
        />
        <Panel>
          <Empty
            title="Chưa có study để kiểm tra"
            description="Chọn chỉ định và bộ ảnh trước khi tiếp tục."
          >
            <ActionLink href={`/imaging/orders/${id}/upload`}>
              Chọn bộ ảnh
            </ActionLink>
          </Empty>
        </Panel>
      </>
    );
  const valid = !draft.errors.length;
  const visit = state.visits.find((v) => v.id === order.visitId);
  const remaining = state.orders.filter(
    (o) =>
      o.visitId === order.visitId &&
      o.round === visit?.latestRound &&
      o.status !== "Cancelled" &&
      o.status !== "Results Available",
  );
  async function submit() {
    setSubmitting(true);
    await new Promise((r) => setTimeout(r, 450));
    sendStudy(id);
    setSubmitting(false);
  }
  return (
    <>
      <Link href={`/imaging/orders/${id}`} className="im-back-link">
        <Icon name="back" size={16} />
        Chi tiết {id}
      </Link>
      <PageHeading
        eyebrow="KẾT QUẢ KIỂM TRA"
        title={
          draft.sent
            ? "Study đã được gửi"
            : valid
              ? "Study sẵn sàng để gửi"
              : "Cần kiểm tra lại bộ ảnh"
        }
        description={
          draft.sent
            ? "Thông tin kết quả đã được cập nhật trong bản xem thử."
            : "Kiểm tra thông tin một lần nữa trước khi gửi cho bác sĩ yêu cầu."
        }
      />
      <div className="im-upload-steps">
        <span>
          <b>
            <Icon name="check" size={14} />
          </b>
          Chọn & đối chiếu
        </span>
        <i />
        <span className={!draft.sent ? "im-upload-current" : ""}>
          <b>2</b>Kiểm tra & gửi
        </span>
        <i />
        <span className={draft.sent ? "im-upload-current" : ""}>
          <b>3</b>Hoàn tất
        </span>
      </div>
      <PatientHeader order={order} />
      {valid && (
        <div
          className={`im-result-banner ${valid ? "im-result-success" : "im-result-error"}`}
        >
          <span>
            <Icon name={valid ? "check" : "alert"} size={30} />
          </span>
          <div>
            <h2>
              {draft.sent
                ? "Gửi study thành công"
                : valid
                  ? "Thông tin đối chiếu hợp lệ"
                  : "Bộ ảnh chưa đáp ứng điều kiện"}
            </h2>
            <p>
              {draft.sent
                ? `Study đã khả dụng cho ${order.doctor} trong bản xem thử.`
                : valid
                  ? "Thông tin file, bệnh nhân và chỉ định đã được đối chiếu trong bản xem thử."
                  : "Sửa các vấn đề bên dưới và thực hiện kiểm tra lại."}
            </p>
          </div>
        </div>
      )}
      {draft.errors.length > 0 && (
        <Panel title="Các vấn đề cần sửa">
          <ul className="im-error-list">
            {draft.errors.map((e) => (
              <li key={e}>
                <Icon name="alert" size={16} />
                {e}
              </li>
            ))}
          </ul>
        </Panel>
      )}
      <div className="im-two-column">
        <Panel title="Thông tin study">
          <dl className="im-details">
            <div>
              <dt>Mã chỉ định</dt>
              <dd>{draft.orderId || "Chưa nhập"}</dd>
            </div>
            <div>
              <dt>Mã bệnh nhân</dt>
              <dd>{draft.patientId || "Chưa nhập"}</dd>
            </div>
            <div>
              <dt>Study Instance UID</dt>
              <dd className="im-uid">{draft.uid || "Chưa nhập"}</dd>
            </div>
            <div>
              <dt>Dung lượng</dt>
              <dd>
                {(
                  draft.files.reduce((n, f) => n + f.size, 0) /
                  1024 /
                  1024
                ).toFixed(2)}{" "}
                MB
              </dd>
            </div>
            <div>
              <dt>Số series / file</dt>
              <dd>
                {draft.series} series · {draft.files.length} file
              </dd>
            </div>
            <div>
              <dt>Bác sĩ nhận</dt>
              <dd>{order.doctor}</dd>
            </div>
            {draft.reason && (
              <div>
                <dt>Lý do thay thế</dt>
                <dd>{draft.reason}</dd>
              </div>
            )}
          </dl>
        </Panel>
        <Panel title="Kiểm tra trước khi gửi">
          <div className="im-panel-body im-checklist">
            {[
              {
                label: "Đúng thông tin bệnh nhân",
                pass: draft.patientId === order.patientId,
              },
              {
                label: "Đúng chỉ định đã thanh toán",
                pass:
                  draft.orderId === id &&
                  ["Paid", "Results Available"].includes(order.status),
              },
              {
                label: "File DICOM phù hợp",
                pass:
                  draft.files.length > 0 && draft.files.every((f) => f.valid),
              },
              {
                label: "Kỹ thuật viên đã xác nhận đối chiếu",
                pass: draft.confirmed,
              },
            ].map((c) => (
              <div key={c.label}>
                <span className={c.pass ? "im-check-ok" : "im-danger"}>
                  <Icon name={c.pass ? "check" : "close"} size={18} />
                </span>
                <span>{c.label}</span>
                <Badge tone={c.pass ? "green" : "red"}>
                  {c.pass ? "Đạt" : "Cần sửa"}
                </Badge>
              </div>
            ))}
          </div>
        </Panel>
      </div>
      {draft.sent && (
        <Info>
          {visit?.status === "Completed"
            ? `Đã lưu phiên bản study hiện hành. Phiếu kết quả của lượt khám: phiên bản ${visit.sheetVersion}.`
            : visit?.reviewQueued
              ? `Đợt chỉ định đã đủ kết quả. Bệnh nhân được đưa vào hàng đợi đọc kết quả của ${visit.doctorRoom} và nhận thông báo (minh họa).`
              : `Còn ${remaining.length} chỉ định chưa có kết quả trong đợt hiện tại. Bệnh nhân chưa được chuyển sang hàng đợi đọc kết quả.`}
        </Info>
      )}
      <div className="im-action-footer">
        <ActionLink
          secondary
          href={`/imaging/orders/${id}${draft.sent ? "" : "/upload"}`}
        >
          {draft.sent ? "Xem chỉ định" : "Quay lại chỉnh sửa"}
        </ActionLink>
        {draft.sent ? (
          <ActionLink href="/imaging/worklist">
            Về danh sách chờ <Icon name="arrow" size={16} />
          </ActionLink>
        ) : (
          <Button
            className="im-button"
            disabled={
              !valid ||
              submitting ||
              !["Paid", "Results Available"].includes(order.status)
            }
            onClick={() => void submit()}
          >
            <Icon name="upload" />
            {submitting ? "Đang gửi…" : "Gửi study cho bác sĩ"}
          </Button>
        )}
      </div>
    </>
  );
}
