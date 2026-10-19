import { Bone } from "lucide-react";
import Link from "next/link";

// Logo OTCMS: ô có biểu tượng xương + chữ, bấm về trang chủ. Dùng chung cho khu
// public (header/footer trang chủ, panel và màn hẹp của màn xác thực); `tone`
// chọn bản trên nền teal (`onBrand`) hay nền sáng (`onLight`).
export function OtcmsLogo({ tone }: { tone: "onBrand" | "onLight" }) {
  const onBrand = tone === "onBrand";
  return (
    <Link
      className={`inline-flex items-center gap-2.5 rounded-md focus-visible:outline-2 focus-visible:outline-offset-4 ${
        onBrand ? "focus-visible:outline-white" : "focus-visible:outline-brand"
      }`}
      href="/"
    >
      <span
        className={`flex size-9 items-center justify-center rounded-lg ${
          onBrand ? "bg-white text-brand" : "bg-brand text-white"
        }`}
      >
        <Bone aria-hidden className="size-5" />
      </span>
      <span
        className={`text-lg font-bold tracking-[0.15em] ${
          onBrand ? "text-white" : "text-brand"
        }`}
      >
        OTCMS
      </span>
    </Link>
  );
}
