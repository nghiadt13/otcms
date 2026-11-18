import { Download } from "lucide-react";
import { Button } from "@/components/ui/button";
import type { ReportExportFormat } from "../types/report";
import { EXPORT_FORMAT_LABELS } from "../types/reportLabels";

type ReportExportMenuProps = {
  exporting: boolean;
  onExport: (format: ReportExportFormat) => void;
};

/** UC-78: chỉ có một định dạng (CSV), nên là một nút; bấm là tải tệp, nút hiện "Đang xuất..." trong lúc tải. */
export function ReportExportMenu({
  exporting,
  onExport,
}: ReportExportMenuProps) {
  return (
    <Button
      variant="outline"
      disabled={exporting}
      className="w-full sm:w-auto"
      title={EXPORT_FORMAT_LABELS.csv}
      onClick={() => onExport("csv")}
    >
      <Download aria-hidden />
      {exporting ? "Đang xuất..." : "Xuất CSV"}
    </Button>
  );
}
