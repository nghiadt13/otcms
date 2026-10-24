import type { Metadata } from "next";
import { ReceptionQueueBoard } from "@/features/reception/components/ReceptionQueueBoard";

export const metadata: Metadata = {
  title: "Hàng đợi tiếp nhận",
};

// Route mỏng: ghép giao diện của feature reception. Bộ lọc nằm trong state, không đưa lên URL.
export default function ReceptionQueuePage() {
  return <ReceptionQueueBoard />;
}
