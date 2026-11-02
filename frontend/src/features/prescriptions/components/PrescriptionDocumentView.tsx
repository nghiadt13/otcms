import { Printer } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatDate } from "@/lib/format/formatDate";
import { cn } from "@/lib/utils";
import type { PrescriptionDocument } from "../types/prescription";
import styles from "./PrescriptionDocumentView.module.css";

/**
 * Đơn thuốc dạng trang HTML để in (không có tệp PDF riêng). Đầu đơn gồm phòng khám,
 * bác sĩ, ngày, người bệnh, chẩn đoán; bảng thuốc gồm tên, liều, số lần, cách dùng.
 */
export function PrescriptionDocumentView({
  document,
}: {
  document: PrescriptionDocument;
}) {
  const { patient } = document;
  return (
    <>
      <div className="flex justify-end print:hidden">
        <Button
          variant="outline"
          className="h-12 px-5 text-base motion-reduce:transition-none"
          onClick={() => window.print()}
        >
          <Printer aria-hidden />
          In đơn thuốc
        </Button>
      </div>
      <article
        aria-label="Đơn thuốc"
        className={cn(
          styles.printArea,
          "mt-4 space-y-6 rounded-lg border bg-card p-4 text-base sm:p-6",
        )}
      >
        <header className="space-y-4 border-b pb-4">
          <p className="text-lg font-semibold">{document.clinicName}</p>
          <dl className="grid gap-x-6 gap-y-3 sm:grid-cols-2">
            <div>
              <dt className="text-muted-foreground">Ngày kê đơn</dt>
              <dd className="tabular-nums">{formatDate(document.issuedAt)}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Bác sĩ kê đơn</dt>
              <dd>{document.doctorName}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Người bệnh</dt>
              <dd className="break-words">
                {patient.fullName}, sinh ngày{" "}
                <span className="tabular-nums">
                  {formatDate(patient.dateOfBirth)}
                </span>
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Mã bệnh nhân</dt>
              <dd className="tabular-nums">{patient.patientCode}</dd>
            </div>
            <div className="sm:col-span-2">
              <dt className="text-muted-foreground">Chẩn đoán</dt>
              <dd className="break-words">{document.diagnosis}</dd>
            </div>
          </dl>
        </header>

        <section
          aria-labelledby="prescription-items-title"
          className="space-y-3"
        >
          <h2 id="prescription-items-title" className="text-lg font-semibold">
            Thuốc đã kê
          </h2>
          <Table className="text-base">
            <TableHeader>
              <TableRow>
                <TableHead className="text-muted-foreground">Thuốc</TableHead>
                <TableHead className="text-muted-foreground">
                  Liều dùng
                </TableHead>
                <TableHead className="text-muted-foreground">Số lần</TableHead>
                <TableHead className="text-muted-foreground">
                  Cách dùng
                </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {document.items.map((item) => (
                <TableRow key={item.medicine}>
                  <TableCell className="font-medium whitespace-normal">
                    {item.medicine}
                  </TableCell>
                  <TableCell className="whitespace-normal">
                    {item.dose}
                  </TableCell>
                  <TableCell className="whitespace-normal">
                    {item.frequency}
                  </TableCell>
                  <TableCell className="whitespace-normal">
                    {item.usage}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </section>
      </article>
    </>
  );
}
