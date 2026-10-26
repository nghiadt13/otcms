import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { publicContainerClass } from "@/components/brand/publicLayout";
import { formatAppMessage } from "@/lib/messages/appMessages";

// Bác sĩ không còn hiển thị công khai (MSG-24): nói rõ và đưa người xem về danh bạ.
export default function DoctorNotFound() {
  return (
    <div className={`${publicContainerClass} py-24 lg:py-32`}>
      <div className="max-w-2xl">
        <h1 className="text-4xl font-semibold tracking-tight text-balance sm:text-5xl">
          Không tìm thấy bác sĩ
        </h1>
        <p className="mt-4 text-lg leading-8 text-pretty text-muted-foreground">
          {formatAppMessage("MSG-24")}
        </p>
        <Link
          className={publicButtonClass("primary") + " mt-10"}
          href="/doctors"
        >
          Xem đội ngũ bác sĩ
        </Link>
      </div>
    </div>
  );
}
