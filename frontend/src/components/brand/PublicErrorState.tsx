import Link from "next/link";
import { Button } from "@/components/ui/button";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { publicButtonClass } from "./publicButtonStyles";

// Nội dung khi trang công khai không tải được. Dùng chung cho error.tsx của khu và của
// nhóm trang giới thiệu (có header/footer). Câu lỗi lấy từ bảng MSG chung (MSG-04), nút
// "Thử lại" gọi lại lời tải.
export function PublicErrorState({ retry }: { retry: () => void }) {
  return (
    <div className="flex flex-col items-center gap-6 text-center">
      <h1 className="text-3xl font-semibold tracking-tight text-balance sm:text-4xl">
        Không tải được nội dung
      </h1>
      <p className="max-w-xl text-lg leading-8 text-pretty text-muted-foreground" role="alert">
        {formatAppMessage("MSG-04")}
      </p>
      <div className="flex flex-wrap justify-center gap-4">
        <Button className={publicButtonClass("primary")} onClick={() => retry()} type="button">
          Thử lại
        </Button>
        <Link className={publicButtonClass("secondary")} href="/">
          Về trang chủ
        </Link>
      </div>
    </div>
  );
}
