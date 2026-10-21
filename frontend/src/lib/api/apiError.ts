// Lỗi từ API theo ProblemDetail (RFC 9457) + `code` MSG. Mock phải ném đúng hình này.
export type MsgCode = `MSG-${string}`;

export type ApiFieldError = { field: string; code: MsgCode };

export type ApiErrorInit = {
  code?: MsgCode;
  params?: Record<string, string | number>;
  fieldErrors?: ApiFieldError[];
  detail?: string;
};

export class ApiError extends Error {
  readonly status: number;
  readonly code?: MsgCode;
  readonly params?: Record<string, string | number>;
  readonly fieldErrors?: ApiFieldError[];

  constructor(status: number, init: ApiErrorInit = {}) {
    super(init.detail ?? init.code ?? `API trả về mã ${status}.`);
    this.name = "ApiError";
    this.status = status;
    this.code = init.code;
    this.params = init.params;
    this.fieldErrors = init.fieldErrors;
  }
}

export function isApiError(value: unknown): value is ApiError {
  return value instanceof ApiError;
}

/** `fieldErrors` → bản đồ path → mã MSG; path dạng `indicators[2].refMale.max`. */
export function toFieldErrorMap(
  fieldErrors: ApiFieldError[] | undefined,
): Record<string, MsgCode> {
  const map: Record<string, MsgCode> = {};
  for (const item of fieldErrors ?? []) map[item.field] ??= item.code;
  return map;
}
