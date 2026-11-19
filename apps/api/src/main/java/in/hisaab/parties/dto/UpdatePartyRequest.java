package in.hisaab.parties.dto;

import in.hisaab.parties.PartyRole;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH body: any field left out (null) stays unchanged. To clear phone or notes, send an empty
 * string.
 */
public record UpdatePartyRequest(
    @Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name,
    @Size(max = 20) String phone,
    PartyRole role,
    @Size(max = 500) String notes,
    Boolean archived) {}
