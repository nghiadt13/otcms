import Link from "next/link";
import { OtcmsBrandMark } from "./OtcmsBrandMark";

// Logo OTCMS: biểu tượng phòng khám + chữ, bấm về trang chủ. Dùng chung cho khu public (header/
// footer trang chủ, header khu bệnh nhân, panel và màn hẹp của màn xác thực); `tone` chọn bản
// trên nền teal (`onBrand`) hay nền sáng (`onLight`).
export function OtcmsLogo({ tone }: { tone: "onBrand" | "onLight" }) {
  const onBrand = tone === "onBrand";
  return (
    <Link
      className={`inline-flex items-center gap-3 rounded-md focus-visible:outline-2 focus-visible:outline-offset-4 ${
        onBrand ? "focus-visible:outline-white" : "focus-visible:outline-brand"
      }`}
      href="/"
    >
      <OtcmsBrandMark
        className="size-12 lg:size-16"
        onBrand={onBrand}
        priority
      />
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
