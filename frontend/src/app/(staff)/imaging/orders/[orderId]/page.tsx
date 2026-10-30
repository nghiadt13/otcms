import { OrderDetailsScreen } from "@/features/imaging/components/OrderDetailsScreen";

export default async function Page({
  params,
}: {
  params: Promise<{ orderId: string }>;
}) {
  const { orderId } = await params;
  return <OrderDetailsScreen id={orderId} />;
}
