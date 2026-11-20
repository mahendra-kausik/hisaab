import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";
import { attachmentType } from "@/lib/attachments";

export type Attachment = components["schemas"]["AttachmentResponse"];

/** What a file is attached to: a transaction or a due, by id. */
export type AttachmentParent = { kind: "transaction" | "due"; id: string };

const workspace = (workspaceId: string) => `/api/v1/workspaces/${workspaceId}`;

const listPath = (workspaceId: string, parent: AttachmentParent) =>
  `${workspace(workspaceId)}/${parent.kind === "due" ? "dues" : "transactions"}/${parent.id}/attachments`;

export const attachmentsKey = (workspaceId: string, parent: AttachmentParent) =>
  ["attachments", workspaceId, parent.kind, parent.id] as const;

/** Where a link or `<a href>` can open the file; the browser sends the session cookie itself. */
export const attachmentUrl = (workspaceId: string, attachmentId: string) =>
  `${workspace(workspaceId)}/attachments/${attachmentId}/content`;

export function useAttachments(workspaceId: string, parent: AttachmentParent) {
  return useQuery({
    queryKey: attachmentsKey(workspaceId, parent),
    queryFn: async () =>
      (
        await apiFetch<components["schemas"]["ListResponseAttachmentResponse"]>(
          listPath(workspaceId, parent),
        )
      ).items,
  });
}

/** Uploads the file as the raw request body; the API reads its type, size and name from the request. */
export function useUploadAttachment(workspaceId: string, parent: AttachmentParent) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (file: File) =>
      apiFetch<Attachment>(`${listPath(workspaceId, parent)}?file_name=${encodeURIComponent(file.name)}`, {
        method: "POST",
        headers: { "Content-Type": attachmentType(file) ?? file.type },
        body: file,
      }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: attachmentsKey(workspaceId, parent) }),
  });
}

export function useDeleteAttachment(workspaceId: string, parent: AttachmentParent) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (attachmentId: string) =>
      apiFetch<void>(`${workspace(workspaceId)}/attachments/${attachmentId}`, { method: "DELETE" }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: attachmentsKey(workspaceId, parent) }),
  });
}
