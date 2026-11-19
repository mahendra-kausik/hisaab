import type { Me, UpdateSettingsRequest } from "@/lib/queries/me";

/** Only the fields that differ from what is saved, so a PATCH never rewrites what the user did not touch. */
export function changedSettings(
  me: Pick<Me, "name" | "timezone" | "period_start_day" | "dues_digest_enabled">,
  form: { name: string; timezone: string; period_start_day: number; dues_digest_enabled: boolean },
): UpdateSettingsRequest {
  const body: UpdateSettingsRequest = {};
  if (form.name !== me.name) body.name = form.name;
  if (form.timezone !== me.timezone) body.timezone = form.timezone;
  if (form.period_start_day !== me.period_start_day) body.period_start_day = form.period_start_day;
  if (form.dues_digest_enabled !== me.dues_digest_enabled)
    body.dues_digest_enabled = form.dues_digest_enabled;
  return body;
}
