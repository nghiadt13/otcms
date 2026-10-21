import Image from "next/image";
import { cn } from "@/lib/utils";

const SIZE_CLASS = { sm: "size-8 text-xs", lg: "size-16 text-xl" } as const;
const SIZE_PX = { sm: 32, lg: 64 } as const;

/** Ảnh đại diện tròn; chưa có ảnh thì hiện chữ cái cuối của họ tên hiển thị. */
export function StaffAvatar({
  name,
  photoUrl,
  size = "sm",
}: {
  name: string;
  photoUrl: string | null;
  size?: keyof typeof SIZE_CLASS;
}) {
  const initial = name.trim().slice(-1).toUpperCase();
  return (
    <span
      className={cn(
        "relative inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full bg-secondary font-medium text-secondary-foreground",
        SIZE_CLASS[size],
      )}
    >
      {photoUrl ? (
        <Image
          alt=""
          className="size-full object-cover"
          height={SIZE_PX[size]}
          src={photoUrl}
          unoptimized
          width={SIZE_PX[size]}
        />
      ) : (
        <span aria-hidden>{initial}</span>
      )}
    </span>
  );
}
