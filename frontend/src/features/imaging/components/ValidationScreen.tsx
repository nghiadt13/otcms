"use client";

import { Button } from "@/components/ui/button";
import Link from "next/link";
import { useState } from "react";
import { useImaging } from "../hooks/useImaging";
import { CURRENT_ROOM } from "../api/imagingMockData";
import { ActionLink } from "./ActionLink";
import { Badge } from "./Badge";
import { Empty } from "./Empty";
import { Icon } from "./Icon";
import { Info } from "./Info";
import { PageHeading } from "./PageHeading";
import { Panel } from "./Panel";
import { PatientHeader } from "./PatientHeader";

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
