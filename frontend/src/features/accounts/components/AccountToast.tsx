"use client";

import { CheckCircle2, X } from "lucide-react";
import { useEffect } from "react";
import { Button } from "@/components/ui/button";

type AccountToastProps = {
  message: string | null;
  onClose: () => void;
};

export function AccountToast({ message, onClose }: AccountToastProps) {
  useEffect(() => {
    if (!message) return;
    const timer = setTimeout(() => {
      onClose();
    }, 4000);
    return () => clearTimeout(timer);
  }, [message, onClose]);

  if (!message) return null;

  return (
    <div
      role="status"
      aria-live="polite"
      className="fixed bottom-5 right-5 z-50 flex max-w-md items-center gap-3 rounded-lg border border-border bg-card p-4 text-sm text-foreground shadow-lg animate-in fade-in slide-in-from-bottom-3 duration-200"
    >
      <CheckCircle2 className="size-5 shrink-0 text-primary" />
      <span className="flex-1 font-medium">{message}</span>
      <Button
        variant="ghost"
        size="icon-sm"
        onClick={onClose}
        className="size-6 p-0 text-muted-foreground hover:text-foreground"
        aria-label="Đóng thông báo"
      >
        <X className="size-4" />
      </Button>
    </div>
  );
}
