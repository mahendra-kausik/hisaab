package in.hisaab.users.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * PATCH body: any field left out (null) stays unchanged. {@code Integer} and {@code Boolean} are
 * the boxed types, which can be null, unlike {@code int}.
 */
public record UpdateSettingsRequest(
    @Size(min = 1, max = 100) String name,
    @Size(min = 1, max = 64) String timezone,
    @Min(1) @Max(28) Integer periodStartDay,
    Boolean duesDigestEnabled) {}
