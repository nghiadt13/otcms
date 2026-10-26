import { PaymentsOverview } from "@/features/billing/components/portal/PaymentsOverview";
import { readStringParam } from "@/features/billing/components/portal/patientBillingPresentation";

// Trang "Thanh toán": khoản cần trả theo nguồn. `?profileId=` là người khám đang lọc.
export default async function PaymentsPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const params = await searchParams;
  return <PaymentsOverview profileId={readStringParam(params.profileId)} />;
}
