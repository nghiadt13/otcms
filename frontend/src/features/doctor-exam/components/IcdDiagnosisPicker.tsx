"use client";

import { Plus, X } from "lucide-react";
import { useEffect, useState } from "react";
import { apiRequest } from "@/lib/api/client";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { FieldError } from "@/components/admin/FieldError";
import { Button } from "@/components/ui/button";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import {
  addSecondary,
  primaryDiagnosis,
  removeDiagnosis,
  setPrimary,
} from "../schema/examinationRules";
import type { IcdEntry } from "../schema/icd10Catalog";
import type { Diagnosis } from "../types/doctorExam";

// Chọn chẩn đoán ICD-10 nhóm M và S (BR-39): một chẩn đoán chính, nhiều chẩn đoán phụ.
// Danh mục lấy từ BE; lỗi tải hiển thị MSG-04 và khóa nút chọn.

/** Chuẩn hoá để tìm không phân biệt hoa thường và dấu tiếng Việt. */
function foldText(text: string): string {
  return text
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .replace(/đ/g, "d")
    .toLowerCase();
}

function IcdSearchPopover({
  label,
  taken,
  disabled,
  variant,
  onPick,
  entries,
}: {
  label: string;
  taken: readonly string[];
  disabled?: boolean;
  variant: "default" | "outline";
  onPick: (code: string) => void;
  entries: readonly IcdEntry[];
}) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const needle = foldText(query.trim());
  const matches = entries.filter(
    (entry) =>
      !taken.includes(entry.code) &&
      foldText(`${entry.code} ${entry.name}`).includes(needle),
  );
  return (
    <Popover
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        if (!next) setQuery("");
      }}
    >
      <PopoverTrigger
        render={
          <Button
            type="button"
            size="sm"
            variant={variant}
            disabled={disabled}
          />
        }
      >
        <Plus aria-hidden />
        {label}
      </PopoverTrigger>
      <PopoverContent align="start" className="w-80 p-0">
        <Command shouldFilter={false}>
          <CommandInput
            value={query}
            onValueChange={setQuery}
            placeholder="Tìm theo mã hoặc tên bệnh"
            aria-label="Tìm mã ICD-10"
          />
          <CommandList>
            <CommandEmpty>Không có mã ICD-10 phù hợp.</CommandEmpty>
            <CommandGroup>
              {matches.slice(0, 40).map((entry) => (
                <CommandItem
                  key={entry.code}
                  value={entry.code}
                  onSelect={() => {
                    onPick(entry.code);
                    setOpen(false);
                  }}
                >
                  <span className="w-14 shrink-0 font-medium tabular-nums">
                    {entry.code}
                  </span>
                  <span className="truncate">{entry.name}</span>
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}

export function IcdDiagnosisPicker({
  value,
  onChange,
  error,
  disabled,
}: {
  value: readonly Diagnosis[];
  onChange: (next: Diagnosis[]) => void;
  error?: string;
  disabled?: boolean;
}) {
  const [entries, setEntries] = useState<IcdEntry[]>([]);
  const [catalogError, setCatalogError] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    apiRequest<IcdEntry[]>("/api/v1/clinical/icd10-codes", {
      signal: controller.signal,
    })
      .then(setEntries)
      .catch(() => {
        if (!controller.signal.aborted) setCatalogError(true);
      });
    return () => controller.abort();
  }, []);
  const icdLabel = (code: string) => {
    const entry = entries.find((item) => item.code === code.toUpperCase());
    return entry ? `${entry.code} · ${entry.name}` : code.toUpperCase();
  };
  const primary = primaryDiagnosis(value);
  const secondary = value.filter((d) => d.type === "SECONDARY");
  const taken = value.map((d) => d.code);
  const catalogUnavailable = catalogError || entries.length === 0;

  return (
    <div className="space-y-4">
      {catalogError ? (
        <FieldError message={formatAppMessage("MSG-04")} />
      ) : null}
      <div>
        <p className="text-sm font-medium">Chẩn đoán chính</p>
        {primary ? (
          <div className="mt-2 flex items-start justify-between gap-2 rounded-md border bg-secondary/50 px-3 py-2">
            <p className="text-sm font-medium">{icdLabel(primary.code)}</p>
            {disabled ? null : (
              <button
                type="button"
                aria-label="Bỏ chẩn đoán chính"
                className="rounded-sm text-muted-foreground hover:text-foreground"
                onClick={() => onChange(removeDiagnosis(value, primary.code))}
              >
                <X aria-hidden className="size-4" />
              </button>
            )}
          </div>
        ) : (
          <p className="mt-2 text-sm text-muted-foreground">Chưa chọn.</p>
        )}
        <div className="mt-2">
          <IcdSearchPopover
            label={primary ? "Đổi chẩn đoán chính" : "Chọn chẩn đoán chính"}
            variant={primary ? "outline" : "default"}
            taken={taken.filter((code) => code !== primary?.code)}
            disabled={disabled || catalogUnavailable}
            entries={entries}
            onPick={(code) => onChange(setPrimary(value, code))}
          />
        </div>
        <FieldError message={error} />
      </div>

      <div>
        <p className="text-sm font-medium">Chẩn đoán phụ</p>
        {secondary.length ? (
          <ul className="mt-2 space-y-1.5">
            {secondary.map((d) => (
              <li
                key={d.code}
                className="flex items-start justify-between gap-2 rounded-md border px-3 py-1.5"
              >
                <span className="text-sm">{icdLabel(d.code)}</span>
                {disabled ? null : (
                  <button
                    type="button"
                    aria-label={`Bỏ chẩn đoán ${d.code}`}
                    className="rounded-sm text-muted-foreground hover:text-foreground"
                    onClick={() => onChange(removeDiagnosis(value, d.code))}
                  >
                    <X aria-hidden className="size-4" />
                  </button>
                )}
              </li>
            ))}
          </ul>
        ) : (
          <p className="mt-2 text-sm text-muted-foreground">Không có.</p>
        )}
        <div className="mt-2">
          <IcdSearchPopover
            label="Thêm chẩn đoán phụ"
            variant="outline"
            taken={taken}
            disabled={disabled || catalogUnavailable}
            entries={entries}
            onPick={(code) => onChange(addSecondary(value, code))}
          />
        </div>
      </div>
    </div>
  );
}
