"use client";

import { StatusBadge } from "@/components/admin/StatusBadge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { getDoctorExamApi } from "../api/doctorExamApi";
import { useState } from "react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useDoctorDemoMode, useLabResults } from "../hooks/useDoctorData";
import { BackendGapNotice } from "./BackendGapNotice";

export function LabResultsPanel({
  visitId,
  available,
}: {
  visitId: string;
  available: boolean;
}) {
  const { data, error, loading, refresh } = useLabResults(visitId);
  const demo = useDoctorDemoMode();
  const [assessmentError, setAssessmentError] = useState("");
  const [assessmentOrderId, setAssessmentOrderId] = useState<string | null>(
    null,
  );
  const [assessmentText, setAssessmentText] = useState("");
  const [savingAssessment, setSavingAssessment] = useState(false);

  async function submitAssessment(orderId: string) {
    if (!assessmentText.trim()) {
      setAssessmentError("Vui lòng nhập nhận xét kết quả.");
      return;
    }
    setAssessmentError("");
    setSavingAssessment(true);
    try {
      await getDoctorExamApi().assessLabResult(
        visitId,
        orderId,
        assessmentText.trim(),
      );
      setAssessmentOrderId(null);
      setAssessmentText("");
      await refresh();
    } catch {
      setAssessmentError("Lưu đánh giá thất bại. Vui lòng thử lại.");
    } finally {
      setSavingAssessment(false);
    }
  }

  return (
    <section aria-labelledby="lab-title" className="space-y-3">
      <h2 id="lab-title" className="text-lg font-semibold">
        Kết quả xét nghiệm
      </h2>
      {!available ? (
        <BackendGapNotice title="Chưa có API xét nghiệm">
          Chỉ định và kết quả xét nghiệm sẽ hiện ở đây khi backend hỗ trợ.
        </BackendGapNotice>
      ) : loading ? (
        <Skeleton className="h-24 w-full" />
      ) : error ? (
        <div
          role="alert"
          className="rounded-lg border bg-card px-4 py-6 text-sm"
        >
          <p>Không thể tải kết quả xét nghiệm.</p>
          <Button
            type="button"
            variant="outline"
            onClick={refresh}
            className="mt-3"
          >
            Thử lại
          </Button>
        </div>
      ) : !data || data.length === 0 ? (
        <p className="rounded-lg border bg-card px-4 py-6 text-center text-sm text-muted-foreground">
          Chưa có kết quả xét nghiệm nào được duyệt.
        </p>
      ) : (
        data.map((lab) => (
          <div
            key={lab.orderId}
            className="overflow-x-auto rounded-lg border bg-card"
          >
            <div className="flex flex-wrap items-center justify-between gap-2 border-b px-4 py-2.5">
              <p className="text-sm font-medium">{lab.serviceName}</p>
              <div className="flex flex-wrap gap-2">
                {!demo &&
                  !lab.assessment &&
                  assessmentOrderId !== lab.orderId && (
                    <Button
                      type="button"
                      size="sm"
                      onClick={() => {
                        setAssessmentOrderId(lab.orderId);
                        setAssessmentText("");
                        setAssessmentError("");
                      }}
                    >
                      Đánh giá kết quả
                    </Button>
                  )}
              </div>
            </div>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Chỉ số</TableHead>
                  <TableHead>Giá trị</TableHead>
                  <TableHead>Đơn vị</TableHead>
                  <TableHead>Khoảng tham chiếu</TableHead>
                  <TableHead>Trạng thái</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {lab.lines.map((line) => (
                  <TableRow key={line.indicator}>
                    <TableCell className="font-medium">
                      {line.indicator}
                    </TableCell>
                    <TableCell
                      className={
                        line.abnormal ? "font-semibold text-destructive" : ""
                      }
                    >
                      {line.value}
                    </TableCell>
                    <TableCell>{line.unit}</TableCell>
                    <TableCell>{line.reference}</TableCell>
                    <TableCell>
                      <StatusBadge
                        tone={
                          line.abnormal === null
                            ? "neutral"
                            : line.abnormal
                              ? "danger"
                              : "success"
                        }
                      >
                        {line.abnormal === null
                          ? "Chưa xác định"
                          : line.abnormal
                            ? "Bất thường"
                            : "Bình thường"}
                      </StatusBadge>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            {lab.assessment ? (
              <div className="space-y-1 border-t px-4 py-3 text-sm">
                <p className="font-medium">Đánh giá của bác sĩ</p>
                <p className="whitespace-pre-wrap">{lab.assessment.text}</p>
                <p className="text-xs text-muted-foreground">
                  Đã ghi nhận lúc{" "}
                  {new Date(lab.assessment.assessedAt).toLocaleString("vi-VN")}
                </p>
              </div>
            ) : assessmentOrderId === lab.orderId ? (
              <div className="space-y-2 border-t px-4 py-3">
                <label
                  htmlFor={`lab-assessment-${lab.orderId}`}
                  className="text-sm font-medium"
                >
                  Nhận xét lâm sàng của bác sĩ
                </label>
                <Textarea
                  id={`lab-assessment-${lab.orderId}`}
                  maxLength={4000}
                  value={assessmentText}
                  onChange={(event) => setAssessmentText(event.target.value)}
                  placeholder="Ghi nhận đánh giá kết quả trong bối cảnh lâm sàng của người bệnh."
                  aria-describedby={
                    assessmentError ? "lab-assessment-error" : undefined
                  }
                />
                {assessmentError && (
                  <p
                    id="lab-assessment-error"
                    role="alert"
                    className="text-sm text-destructive"
                  >
                    {assessmentError}
                  </p>
                )}
                <div className="flex gap-2">
                  <Button
                    type="button"
                    onClick={() => void submitAssessment(lab.orderId)}
                    disabled={savingAssessment}
                  >
                    {savingAssessment ? "Đang lưu…" : "Lưu đánh giá"}
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => setAssessmentOrderId(null)}
                    disabled={savingAssessment}
                  >
                    Hủy
                  </Button>
                </div>
              </div>
            ) : null}
          </div>
        ))
      )}
    </section>
  );
}
