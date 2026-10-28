import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { publicContainerClass } from "@/components/brand/publicLayout";
import { formatAppMessage } from "@/lib/messages/appMessages";

// Trang không tìm thấy của nhóm công khai (dịch vụ, bài viết, thông báo đã ngừng hoặc sai
// mã). Nằm trong layout có header và footer. Trang bác sĩ có trang riêng với câu MSG-24.
export default function SiteNotFound() {
  return (
    <div className={`${publicContainerClass} py-24 lg:py-32`}>
      <div className="max-w-2xl">
        <h1 className="text-4xl font-semibold tracking-tight text-balance sm:text-5xl">
          Không tìm thấy nội dung
        </h1>
        <p className="mt-4 text-lg leading-8 text-pretty text-muted-foreground">
          {formatAppMessage("MSG-50")}
        </p>
        <div className="mt-10 flex flex-wrap gap-4">
          <Link className={publicButtonClass("primary")} href="/">
            Về trang chủ
          </Link>
          <Link className={publicButtonClass("secondary")} href="/services">
            Xem dịch vụ và bảng giá
          </Link>
        </div>
      </div>
    </div>
  );
}
