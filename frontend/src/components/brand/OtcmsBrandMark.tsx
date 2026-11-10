import Image from "next/image";
import { cn } from "@/lib/utils";

const BRAND_MARK_SRC = "/images/brand/otcms-emblem-mark.png";

/**
 * Biểu tượng phòng khám (phần tròn bên trong huy hiệu: chữ thập + khớp gối), dùng chung cho logo
 * trang công khai, sidebar các khu nhân viên. Ảnh trang trí: chữ "OTCMS" hoặc nhãn của liên kết
 * bọc ngoài đã nêu tên. `onBrand`: đặt trên nền teal thì thêm viền trắng để hình không chìm vào nền.
 */
export function OtcmsBrandMark({
  className,
  onBrand = false,
  priority = false,
}: {
  /** Kích thước (ví dụ `size-8`). */
  className?: string;
  onBrand?: boolean;
  priority?: boolean;
}) {
  return (
    <Image
      alt=""
      className={cn(
        "shrink-0 rounded-full",
        onBrand && "bg-white p-0.5",
        className,
      )}
      height={128}
      priority={priority}
      src={BRAND_MARK_SRC}
      width={128}
    />
  );
}
