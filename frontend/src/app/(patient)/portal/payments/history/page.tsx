import { MyPaymentHistory } from "@/features/billing/components/portal/MyPaymentHistory";
import {
  readDateParam,
  readStringParam,
} from "@/features/billing/components/portal/patientBillingPresentation";

// Lịch sử thanh toán đã hoàn tất: `?from=&to=&profileId=`. Ngày sai dạng bị bỏ qua.
export default async function PaymentHistoryPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const params = await searchParams;
  return (
    <MyPaymentHistory
      from={readDateParam(params.from)}
      profileId={readStringParam(params.profileId)}
      to={readDateParam(params.to)}
    />
  );
}
