import { Construction } from "lucide-react";
import type { ReactNode } from "react";
import { PageHeader } from "@/components/PageHeader";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { DoctorScreenPart } from "../doctorNavigation";

// Trang chờ dùng chung cho mọi màn của khu bác sĩ: mỗi phần liên quan trong màn là một thẻ,
// để thấy rõ màn này sẽ gồm những gì (theo Report 3) trước khi BE xong.
export function DoctorScreenPlaceholder({
  title,
  description,
  parts,
  context,
}: {
  title: string;
  description: string;
  parts: DoctorScreenPart[];
  /** Ghi chú ngữ cảnh, ví dụ mã lượt khám. */
  context?: ReactNode;
}) {
  return (
    <>
      <PageHeader title={title} description={description} actions={context} />
      <div className="space-y-4">
        {parts.map((part) => (
          <Card key={part.title}>
            <CardHeader className="flex-row items-center justify-between gap-3">
              <CardTitle>{part.title}</CardTitle>
              {part.ucs ? <Badge variant="secondary">{part.ucs}</Badge> : null}
            </CardHeader>
            <CardContent className="flex items-start gap-3 text-sm text-muted-foreground">
              <Construction aria-hidden className="mt-0.5 size-4 shrink-0" />
              <div>
                <p>{part.description}</p>
                <p className="mt-1 font-medium text-foreground">
                  Màn hình đang được xây dựng
                </p>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </>
  );
}
