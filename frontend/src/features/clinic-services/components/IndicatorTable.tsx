"use client";

import type { ReactNode } from "react";
import { Plus, Trash2 } from "lucide-react";
import { FieldError } from "@/components/admin/FieldError";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { APP_MESSAGES } from "@/lib/messages/appMessages";
import type { FieldErrorMap } from "../schema/serviceFieldRules";
import {
  emptyIndicatorDraft,
  type IndicatorDraft,
} from "../schema/serviceFormDraft";

type RangeKey = "refMale" | "refFemale";
type RangeBound = "min" | "max";

type IndicatorTableProps = {
  rows: IndicatorDraft[];
  /** Có `onChange` thì sửa được; không có thì chỉ đọc (Sheet chi tiết). */
  onChange?: (rows: IndicatorDraft[]) => void;
  errors?: FieldErrorMap;
  disabled?: boolean;
};

const rowErrorKeys = (errors: FieldErrorMap, index: number) =>
  Object.keys(errors).filter((key) => key.startsWith(`indicators[${index}].`));

/** Bảng chỉ số xét nghiệm: tên, đơn vị, khoảng tham chiếu nam và nữ (BR-58). */
export function IndicatorTable({
  rows,
  onChange,
  errors = {},
  disabled,
}: IndicatorTableProps) {
  const editable = Boolean(onChange) && !disabled;

  const update = (
    index: number,
    patch: (row: IndicatorDraft) => IndicatorDraft,
  ) => onChange?.(rows.map((row, i) => (i === index ? patch(row) : row)));

  const setRange = (
    index: number,
    key: RangeKey,
    bound: RangeBound,
    value: string,
  ) =>
    update(index, (row) => ({
      ...row,
      [key]: { ...row[key], [bound]: value },
    }));

  const cellInput = (
    index: number,
    path: string,
    label: string,
    props: {
      value: string;
      onChange: (value: string) => void;
      numeric?: boolean;
    },
  ) => (
    <Input
      value={props.value}
      onChange={(event) => props.onChange(event.target.value)}
      aria-label={`${label} ${index + 1}`}
      aria-invalid={errors[path] ? true : undefined}
      aria-describedby={errors[path] ? `indicator-error-${index}` : undefined}
      inputMode={props.numeric ? "decimal" : undefined}
      disabled={disabled}
      className={
        props.numeric ? "min-w-20 text-right tabular-nums" : "min-w-32"
      }
    />
  );

  return (
    <div className="space-y-2">
      <div className="overflow-x-auto rounded-lg border">
        <Table className="min-w-[680px]">
          <TableHeader>
            <TableRow>
              <TableHead>Tên chỉ số</TableHead>
              <TableHead>Đơn vị</TableHead>
              <TableHead className="text-right">Nam từ</TableHead>
              <TableHead className="text-right">Nam đến</TableHead>
              <TableHead className="text-right">Nữ từ</TableHead>
              <TableHead className="text-right">Nữ đến</TableHead>
              {editable ? <TableHead className="w-10" /> : null}
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((row, index) => {
              const rowErrors = rowErrorKeys(errors, index);
              const prefix = `indicators[${index}]`;
              const numberCell = (
                key: RangeKey,
                bound: RangeBound,
                label: string,
              ) => (
                <TableCell className="text-right tabular-nums">
                  {editable
                    ? cellInput(index, `${prefix}.${key}.${bound}`, label, {
                        value: row[key][bound],
                        onChange: (value) => setRange(index, key, bound, value),
                        numeric: true,
                      })
                    : row[key][bound]}
                </TableCell>
              );
              return (
                <RowGroup
                  key={index}
                  errorId={`indicator-error-${index}`}
                  hasError={rowErrors.length > 0}
                  colSpan={editable ? 7 : 6}
                  message={APP_MESSAGES["MSG-66"]}
                >
                  <TableCell className="whitespace-normal">
                    {editable
                      ? cellInput(index, `${prefix}.name`, "Tên chỉ số", {
                          value: row.name,
                          onChange: (value) =>
                            update(index, (r) => ({ ...r, name: value })),
                        })
                      : row.name}
                  </TableCell>
                  <TableCell>
                    {editable
                      ? cellInput(index, `${prefix}.unit`, "Đơn vị", {
                          value: row.unit,
                          onChange: (value) =>
                            update(index, (r) => ({ ...r, unit: value })),
                        })
                      : row.unit}
                  </TableCell>
                  {numberCell("refMale", "min", "Nam từ")}
                  {numberCell("refMale", "max", "Nam đến")}
                  {numberCell("refFemale", "min", "Nữ từ")}
                  {numberCell("refFemale", "max", "Nữ đến")}
                  {editable ? (
                    <TableCell>
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon-sm"
                        aria-label={`Xóa chỉ số ${row.name.trim() || index + 1}`}
                        onClick={() =>
                          onChange?.(rows.filter((_, i) => i !== index))
                        }
                      >
                        <Trash2 />
                      </Button>
                    </TableCell>
                  ) : null}
                </RowGroup>
              );
            })}
          </TableBody>
        </Table>
      </div>
      <FieldError
        message={errors.indicators ? APP_MESSAGES[errors.indicators] : null}
      />
      {editable ? (
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => onChange?.([...rows, emptyIndicatorDraft()])}
        >
          <Plus data-icon="inline-start" />
          Thêm chỉ số
        </Button>
      ) : null}
    </div>
  );
}

/** Một dòng chỉ số kèm dòng lỗi (nếu có) ngay bên dưới, để thông báo không bị bó trong ô hẹp. */
function RowGroup({
  children,
  errorId,
  hasError,
  colSpan,
  message,
}: {
  children: ReactNode;
  errorId: string;
  hasError: boolean;
  colSpan: number;
  message: string;
}) {
  return (
    <>
      <TableRow className={hasError ? "border-b-0" : undefined}>
        {children}
      </TableRow>
      {hasError ? (
        <TableRow>
          <TableCell colSpan={colSpan} className="pt-0">
            <FieldError id={errorId} message={message} />
          </TableCell>
        </TableRow>
      ) : null}
    </>
  );
}
