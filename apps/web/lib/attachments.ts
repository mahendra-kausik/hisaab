// The same limits as the API (AttachmentService), so a bad file is caught before it is uploaded.
export const MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024;

const TYPES = ["image/jpeg", "image/png", "image/webp", "image/heic", "application/pdf"];

/** For `<input type="file" accept>`. ".heic" is there because some browsers give HEIC no type. */
export const ATTACHMENT_ACCEPT = [...TYPES, ".heic"].join(",");

/** The type to upload the file as, or null if it is not an allowed kind. */
export function attachmentType(file: { name: string; type: string }): string | null {
  const type = file.type.toLowerCase();
  if (TYPES.includes(type)) return type;
  if (type === "" && /\.heic$/i.test(file.name)) return "image/heic";
  return null;
}

/** A message to show the user, or null if the file can be uploaded. */
export function attachmentError(file: { name: string; type: string; size: number }): string | null {
  if (!attachmentType(file)) return "Attach a JPEG, PNG, WebP, HEIC or PDF file";
  if (file.size === 0) return "That file is empty";
  if (file.size > MAX_ATTACHMENT_BYTES) return "Files can be at most 10 MB";
  return null;
}
