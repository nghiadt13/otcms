import type { LucideIcon } from "lucide-react";
import { MoreHorizontal } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

export type RowAction = {
  key: string;
  label: string;
  icon?: LucideIcon;
  destructive?: boolean;
  onSelect: () => void;
};

/**
 * Menu ⋯ ở cuối dòng. Chỉ truyền các hành động có trong `allowedActions` của bản ghi;
 * danh sách rỗng thì không vẽ gì. Nhóm "destructive" được tách bằng vạch ngăn.
 */
export function RowActionsMenu({
  actions,
  label,
}: {
  actions: RowAction[];
  /** Đối tượng của dòng, ví dụ tên người, để đọc "Thao tác cho …". */
  label: string;
}) {
  if (actions.length === 0) return null;
  const normal = actions.filter((action) => !action.destructive);
  const destructive = actions.filter((action) => action.destructive);
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label={`Thao tác cho ${label}`}
          />
        }
      >
        <MoreHorizontal />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="min-w-44">
        {normal.map(({ key, label: text, icon: Icon, onSelect }) => (
          <DropdownMenuItem key={key} onClick={onSelect}>
            {Icon ? <Icon /> : null}
            {text}
          </DropdownMenuItem>
        ))}
        {normal.length > 0 && destructive.length > 0 ? (
          <DropdownMenuSeparator />
        ) : null}
        {destructive.map(({ key, label: text, icon: Icon, onSelect }) => (
          <DropdownMenuItem key={key} variant="destructive" onClick={onSelect}>
            {Icon ? <Icon /> : null}
            {text}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
