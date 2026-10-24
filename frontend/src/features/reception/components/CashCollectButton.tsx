import { Button } from "@/components/ui/button";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";

interface CashCollectButtonProps {
  amount: number;
  pending: boolean;
  onCollect: () => void;
}

/** Xác nhận đã nhận đủ tiền mặt. Khoản thu trọn vẹn, không đặt cọc, không giảm (BR-49). */
export function CashCollectButton({ amount, pending, onCollect }: CashCollectButtonProps) {
  return (
    <Button variant="outline" disabled={pending} onClick={onCollect} className="w-full sm:w-auto">
      {pending ? "Đang ghi nhận..." : `Đã nhận đủ ${formatCurrencyVnd(amount)}`}
    </Button>
  );
}
