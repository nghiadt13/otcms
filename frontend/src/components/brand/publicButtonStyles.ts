import { buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";

// Nút của khu public dựa trên `buttonVariants` của shadcn (components/ui/button),
// chỉ phóng to cho người dùng phổ thông, kể cả người lớn tuổi: `md` cao h-12 chữ
// text-base, `lg` cao h-14 cho hero. Dùng cho <Button className=...> lẫn <Link>
// dạng nút. Mỗi màn chỉ một nút `primary`; thao tác khác dùng `secondary`.
const sizes = {
  md: "h-12 gap-2 px-5 text-base",
  lg: "h-14 gap-2 px-7 text-lg",
};

const variants = {
  primary: cn(buttonVariants({ variant: "default" }), "hover:bg-brand-hover"),
  secondary: cn(
    buttonVariants({ variant: "outline" }),
    "border-input bg-card hover:border-primary hover:bg-card hover:text-primary",
  ),
};

export function publicButtonClass(
  variant: keyof typeof variants,
  size: keyof typeof sizes = "md",
) {
  return cn(
    variants[variant],
    sizes[size],
    "font-semibold [&_svg:not([class*='size-'])]:size-5",
  );
}

export const publicPrimaryButtonClass = publicButtonClass("primary");
export const publicSecondaryButtonClass = publicButtonClass("secondary");
