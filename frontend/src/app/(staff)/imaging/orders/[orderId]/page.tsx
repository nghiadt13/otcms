import { OrderDetailsScreen } from "@/features/imaging/components/OrderScreens";

export default async function Page({
  params,
}: {
  params: Promise<{ orderId: string }>;
}) {
  const { orderId } = await params;
  return <OrderDetailsScreen id={orderId} />;
}
