"use client";

import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";

export function SectionTabs({
  value,
  items,
  onChange,
}: {
  value: string;
  items: { id: string; label: string }[];
  onChange: (value: string) => void;
}) {
  return (
    <Tabs value={value} onValueChange={(next) => onChange(String(next))}>
      <TabsList aria-label="Nhóm thông tin">
        {items.map((item) => (
          <TabsTrigger key={item.id} value={item.id}>
            {item.label}
          </TabsTrigger>
        ))}
      </TabsList>
    </Tabs>
  );
}
