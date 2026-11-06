import { redirect } from "next/navigation";

export default async function Page({
  params,
}: {
  params: Promise<{ orderId: string }>;
}) {
  const { orderId } = await params;
  redirect(`/imaging/orders/${encodeURIComponent(orderId)}/upload`);
}
