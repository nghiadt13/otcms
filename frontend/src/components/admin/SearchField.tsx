import { Search } from "lucide-react";
import { Input } from "@/components/ui/input";

type SearchFieldProps = {
  value: string;
  onChange: (value: string) => void;
  placeholder: string;
  /** Nhãn đọc cho trình đọc màn hình (ô tìm không có nhãn hiển thị). */
  label: string;
};

export function SearchField({
  value,
  onChange,
  placeholder,
  label,
}: SearchFieldProps) {
  return (
    <div className="relative w-full sm:w-72">
      <Search
        aria-hidden
        className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground"
      />
      <Input
        type="search"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        aria-label={label}
        className="pl-8"
      />
    </div>
  );
}
