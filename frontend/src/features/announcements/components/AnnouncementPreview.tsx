import type { AnnouncementDraft } from "../types/announcement";
import { audienceLabel, channelsText } from "./announcementLabels";

/** Xem trước chỉ-client, chỉ đọc. Văn bản thuần, giữ xuống dòng. */
export function AnnouncementPreview({ draft }: { draft: AnnouncementDraft }) {
  return (
    <section
      aria-label="Xem trước"
      className="rounded-lg border bg-muted/40 p-4 text-sm"
    >
      <h3 className="text-xs font-medium text-muted-foreground">
        Xem trước (chỉ đọc)
      </h3>
      <p className="mt-3 font-medium break-words">
        {draft.title.trim() || (
          <span className="font-normal text-muted-foreground">
            Tiêu đề thông báo
          </span>
        )}
      </p>
      <p className="mt-2 break-words whitespace-pre-wrap">
        {draft.body.trim() || (
          <span className="text-muted-foreground">Nội dung thông báo</span>
        )}
      </p>
      <dl className="mt-4 grid gap-1 text-xs text-muted-foreground">
        <div>
          <dt className="inline">Gửi tới: </dt>
          <dd className="inline">
            {draft.audience ? audienceLabel(draft.audience) : "Chưa chọn nhóm"}
          </dd>
        </div>
        <div>
          <dt className="inline">Qua: </dt>
          <dd className="inline">
            {draft.channels.length
              ? channelsText(draft.channels, " và ")
              : "Chưa chọn kênh"}
          </dd>
        </div>
      </dl>
    </section>
  );
}
