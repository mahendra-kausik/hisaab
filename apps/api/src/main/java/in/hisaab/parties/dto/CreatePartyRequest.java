package in.hisaab.parties.dto;

import in.hisaab.parties.PartyRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Phone and notes are optional; blank ones are stored as null. */
public record CreatePartyRequest(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 20) String phone,
    @NotNull PartyRole role,
    @Size(max = 500) String notes) {}
