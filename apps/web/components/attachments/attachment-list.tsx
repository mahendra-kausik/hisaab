"use client";

import { Paperclip, Trash2 } from "lucide-react";
import { useRef, useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/api/client";
import { ATTACHMENT_ACCEPT, attachmentError } from "@/lib/attachments";
import { formatBytes } from "@/lib/format";
import {
  attachmentUrl,
  useAttachments,
  useDeleteAttachment,
  useUploadAttachment,
  type AttachmentParent,
} from "@/lib/queries/attachments";
import { useWorkspace } from "@/lib/workspace";

const message = (e: Error, fallback: string) => (e instanceof ApiError ? e.message : fallback);

/**
 * Files on a saved transaction or due. Files are uploaded and removed at once, not when the form
 * is saved. `parent` is null while the item does not exist yet, so there is nothing to attach to.
 * Every button is `type="button"` because this sits inside the dialog's form.
 */
export function AttachmentList({ parent }: { parent: AttachmentParent | null }) {
  if (!parent) {
    return (
      <p className="text-xs text-muted-foreground">
        Save first, then edit to attach photos or PDFs.
      </p>
    );
  }
  return <Attached parent={parent} />;
}

function Attached({ parent }: { parent: AttachmentParent }) {
  const { active } = useWorkspace();
  const list = useAttachments(active.id, parent);
  const upload = useUploadAttachment(active.id, parent);
  const remove = useDeleteAttachment(active.id, parent);
  const input = useRef<HTMLInputElement>(null);
  const [error, setError] = useState<string | null>(null);
  // The attachment whose Remove button was clicked once; a second click confirms.
  const [confirming, setConfirming] = useState<string | null>(null);

  function onPick(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = ""; // so picking the same file again still fires onChange
    if (!file) return;
    const problem = attachmentError(file);
    if (problem) return setError(problem);
    setError(null);
    upload.mutate(file, {
      onSuccess: () => toast("File attached"),
      onError: (e) => setError(message(e, "Could not upload the file. Try again.")),
    });
  }

  function onRemove(id: string) {
    setError(null);
    remove.mutate(id, {
      onSuccess: () => {
        setConfirming(null);
        toast("File removed");
      },
      onError: (e) => setError(message(e, "Could not remove the file. Try again.")),
    });
  }

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center justify-between gap-3">
        <span className="text-sm font-medium">Attachments</span>
        <Button
          type="button"
          variant="outline"
          className="h-11"
          disabled={upload.isPending}
          onClick={() => input.current?.click()}
        >
          <Paperclip aria-hidden />
          {upload.isPending ? "Uploading…" : "Attach file"}
        </Button>
        <input
          ref={input}
          type="file"
          accept={ATTACHMENT_ACCEPT}
          onChange={onPick}
          tabIndex={-1}
          aria-label="Choose a file to attach"
          className="sr-only"
        />
      </div>

      {list.isError && (
        <p role="alert" className="text-sm text-destructive">
          Could not load the attachments.
        </p>
      )}

      {list.data && list.data.length > 0 ? (
        <ul className="flex flex-col gap-1.5">
          {list.data.map((a) => (
            <li
              key={a.id}
              className="flex min-h-11 items-center justify-between gap-2 rounded-md border pl-3"
            >
              <a
                href={attachmentUrl(active.id, a.id)}
                target="_blank"
                rel="noopener noreferrer"
                className="flex min-w-0 items-baseline gap-2 text-sm hover:underline"
              >
                <span className="truncate">{a.file_name}</span>
                <span className="shrink-0 text-xs text-muted-foreground">
                  {formatBytes(a.size_bytes)}
                </span>
              </a>
              {confirming === a.id ? (
                <span className="flex shrink-0 items-center gap-1 pr-1">
                  <Button
                    type="button"
                    variant="outline"
                    className="h-11"
                    onClick={() => setConfirming(null)}
                  >
                    Keep
                  </Button>
                  <Button
                    type="button"
                    variant="destructive"
                    className="h-11"
                    disabled={remove.isPending}
                    onClick={() => onRemove(a.id)}
                  >
                    Remove
                  </Button>
                </span>
              ) : (
                <Button
                  type="button"
                  variant="ghost"
                  className="size-11 shrink-0 p-0"
                  aria-label={`Remove ${a.file_name}`}
                  onClick={() => setConfirming(a.id)}
                >
                  <Trash2 aria-hidden />
                </Button>
              )}
            </li>
          ))}
        </ul>
      ) : (
        !list.isPending && (
          <p className="text-xs text-muted-foreground">
            Photos or PDFs of receipts, cheques or agreements. Files are saved as soon as you add
            them.
          </p>
        )
      )}

      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}
