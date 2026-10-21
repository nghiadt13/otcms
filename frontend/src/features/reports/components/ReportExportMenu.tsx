import { ChevronDown, Download } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import type { ReportExportFormat } from "../types/report";
import { EXPORT_FORMAT_LABELS } from "../types/reportLabels";

type ReportExportMenuProps = {
  exporting: boolean;
  onExport: (format: ReportExportFormat) => void;
};

/** Nút "Xuất báo cáo" mở menu hai định dạng; chọn xong menu đóng, nút hiện "Đang xuất...". */
export function ReportExportMenu({ exporting, onExport }: ReportExportMenuProps) {
  const [open, setOpen] = useState(false);
  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger
        render={
          <Button
            variant="outline"
            disabled={exporting}
            className="w-full sm:w-auto"
          />
        }
      >
        <Download aria-hidden />
        {exporting ? "Đang xuất..." : "Xuất báo cáo"}
        <ChevronDown aria-hidden />
      </PopoverTrigger>
      <PopoverContent
        align="start"
        className="w-52 max-w-[calc(100vw-2rem)] gap-1 p-1"
      >
        {(Object.keys(EXPORT_FORMAT_LABELS) as ReportExportFormat[]).map(
          (format) => (
            <Button
              key={format}
              variant="ghost"
              className="justify-start"
              onClick={() => {
                setOpen(false);
                onExport(format);
              }}
            >
              {EXPORT_FORMAT_LABELS[format]}
            </Button>
          ),
        )}
      </PopoverContent>
    </Popover>
  );
}
