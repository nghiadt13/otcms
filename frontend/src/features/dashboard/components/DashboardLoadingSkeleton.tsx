import { TableFrame } from "@/components/admin/TableFrame";
import { TableStateRows } from "@/components/admin/TableStateRows";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody } from "@/components/ui/table";

const STRIP_CELLS = 5;
const BLOCKS = 7;

/** Hình dạng gần giống nội dung thật: dải 5 ô và 7 khối, không spinner. */
export function DashboardLoadingSkeleton() {
  return (
    <div aria-hidden>
      <div className="mb-8 grid grid-cols-2 gap-px overflow-hidden rounded-lg border bg-border lg:grid-cols-5">
        {Array.from({ length: STRIP_CELLS }, (_, index) => (
          <div
            key={index}
            className={`space-y-2 bg-card p-4 ${index === STRIP_CELLS - 1 ? "col-span-2 lg:col-span-1" : ""}`}
          >
            <Skeleton className="h-4 w-20" />
            <Skeleton className="h-7 w-24" />
            <Skeleton className="h-3 w-32" />
          </div>
        ))}
      </div>
      <div className="divide-y">
        {Array.from({ length: BLOCKS }, (_, index) => (
          <div
            key={index}
            className="grid gap-4 py-6 first:pt-0 lg:grid-cols-[16rem_minmax(0,1fr)] lg:gap-8"
          >
            <div className="space-y-2">
              <Skeleton className="h-6 w-40" />
              <Skeleton className="h-4 w-52" />
            </div>
            <TableFrame>
              <Table>
                <TableBody>
                  <TableStateRows
                    colSpan={3}
                    loading
                    error={null}
                    empty={false}
                    emptyText={null}
                    skeletonRows={3}
                  />
                </TableBody>
              </Table>
            </TableFrame>
          </div>
        ))}
      </div>
    </div>
  );
}
