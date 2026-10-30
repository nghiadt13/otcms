"use client";

import type { ReactNode } from "react";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { cn } from "@/lib/utils";
import { Icon } from "./Icon";

export function Info({
  children,
  tone = "blue",
}: {
  children: ReactNode;
  tone?: string;
}) {
  const warning = tone === "amber";
  const danger = tone === "red";
  return (
    <Alert
      variant={danger ? "destructive" : "default"}
      className={cn(
        "im-info",
        warning && "border-warning/30 bg-warning-soft text-warning",
        !warning && !danger && "bg-secondary/50",
      )}
    >
      <Icon name={danger || warning ? "alert" : "shield"} />
      <AlertDescription
        className={cn(
          warning
            ? "text-warning"
            : danger
              ? "text-destructive"
              : "text-muted-foreground",
        )}
      >
        {children}
      </AlertDescription>
    </Alert>
  );
}
