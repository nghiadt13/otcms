import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { INLINE_LINK_CLASS } from "./patientBillingPresentation";

/** Quay lại màn cha, ví dụ "‹ Thanh toán". Icon là lucide, không dùng ký tự Unicode. */
export function PaymentBackLink({ href, label }: { href: string; label: string }) {
  return (
    <Link className={INLINE_LINK_CLASS} href={href}>
      <ChevronLeft aria-hidden className="size-5" />
      {label}
    </Link>
  );
}
