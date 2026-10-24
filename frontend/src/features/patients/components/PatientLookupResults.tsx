import { StatusBadge } from "@/components/admin/StatusBadge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { formatDate } from "@/lib/format/formatDate";
import {
  SEX_LABELS,
  type PatientLookupResponse,
  type PatientMatch,
  type PatientSearchMode,
} from "../types/patient";
import type { SearchState } from "../types/patientSearchState";

type PatientLookupResultsProps = {
  mode: PatientSearchMode;
  state: SearchState;
  onSelect: (match: PatientMatch) => void;
  /** Mở dialog tạo hồ sơ (chỉ khi tìm theo SĐT + ngày sinh không thấy ai). */
  onCreate: () => void;
};

const IDLE_TEXT: Record<PatientSearchMode, string> = {
  phone: "Nhập số điện thoại và ngày sinh, rồi bấm Tìm.",
  code: "Nhập mã bệnh nhân, rồi bấm Tìm.",
};

/**
 * Khối kết quả tìm. Mọi trạng thái (chờ, đang tìm, có kết quả, không thấy, nhiều hồ sơ)
 * nằm trong một vùng aria-live để trình đọc màn hình báo đúng lúc.
 */
export function PatientLookupResults({
  mode,
  state,
  onSelect,
  onCreate,
}: PatientLookupResultsProps) {
  return (
    <div aria-live="polite" aria-busy={state.kind === "loading"}>
      {state.kind === "idle" ? (
        <p className="text-sm text-muted-foreground">{IDLE_TEXT[mode]}</p>
      ) : null}
      {state.kind === "loading" ? <ResultSkeleton /> : null}
      {state.kind === "ready" ? (
        <ReadyResults
          mode={mode}
          response={state.response}
          onSelect={onSelect}
          onCreate={onCreate}
        />
      ) : null}
    </div>
  );
}

function ResultSkeleton() {
  return (
    <ul className="divide-y rounded-lg border" aria-hidden="true">
      {[0, 1].map((row) => (
        <li
          key={row}
          className="flex items-center justify-between gap-3 px-3 py-2.5"
        >
          <div className="grid gap-2">
            <Skeleton className="h-4 w-40" />
            <Skeleton className="h-3 w-56" />
          </div>
          <Skeleton className="h-8 w-16" />
        </li>
      ))}
    </ul>
  );
}

function ReadyResults({
  mode,
  response,
  onSelect,
  onCreate,
}: {
  mode: PatientSearchMode;
  response: PatientLookupResponse;
  onSelect: (match: PatientMatch) => void;
  onCreate: () => void;
}) {
  if (response.matches.length > 0) {
    return (
      <ul aria-label="Kết quả tìm kiếm" className="divide-y rounded-lg border">
        {response.matches.map((match) => (
          <MatchRow key={match.id} match={match} onSelect={onSelect} />
        ))}
      </ul>
    );
  }
  if (response.result === "MULTIPLE") {
    return (
      <p className="text-sm">
        Có nhiều hồ sơ khớp số điện thoại và ngày sinh. Nhập họ và tên ở ô trên
        rồi bấm Tìm.
      </p>
    );
  }
  if (mode === "code") {
    return (
      <p className="text-sm">
        Không có hồ sơ nào mang mã này. Kiểm tra lại mã trên phiếu hoặc tìm bằng
        số điện thoại và ngày sinh.
      </p>
    );
  }
  return (
    <div className="flex flex-wrap items-center gap-3">
      <p className="text-sm">Chưa có hồ sơ khớp số điện thoại và ngày sinh này.</p>
      <Button variant="outline" onClick={onCreate}>
        Tạo hồ sơ bệnh nhân
      </Button>
    </div>
  );
}

function MatchRow({
  match,
  onSelect,
}: {
  match: PatientMatch;
  onSelect: (match: PatientMatch) => void;
}) {
  return (
    <li className="flex flex-wrap items-center justify-between gap-3 px-3 py-2.5">
      <div className="min-w-0">
        <p className="text-sm font-medium">{match.fullName}</p>
        <p className="text-xs text-muted-foreground tabular-nums">
          {SEX_LABELS[match.sex]} · {formatDate(match.dateOfBirth)} ·{" "}
          {match.patientCode}
        </p>
      </div>
      <div className="flex items-center gap-2">
        <StatusBadge tone={match.hasAccount ? "info" : "neutral"}>
          {match.hasAccount ? "Đã có tài khoản" : "Chưa có tài khoản"}
        </StatusBadge>
        <Button
          variant="outline"
          size="sm"
          aria-label={`Chọn ${match.fullName}`}
          onClick={() => onSelect(match)}
        >
          Chọn
        </Button>
      </div>
    </li>
  );
}
