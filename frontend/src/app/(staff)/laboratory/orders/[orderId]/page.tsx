import { LaboratoryPreview } from "@/features/laboratory/components/LaboratoryPreview";

export default async function LaboratoryOrderDetailPage({
  params,
  searchParams,
}: {
  params: Promise<{ orderId: string }>;
  searchParams: Promise<{ from?: string }>;
}) {
  const [{ orderId }, { from }] = await Promise.all([params, searchParams]);
  const initialScreen =
    from === "queue"
      ? "Danh sách chờ"
      : from === "history"
        ? "Trạng thái chỉ định"
        : "Tổng quan";
  return (
    <LaboratoryPreview
      initialScreen={initialScreen}
      orderId={orderId}
    />
  );
}
