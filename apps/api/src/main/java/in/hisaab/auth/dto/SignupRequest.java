package in.hisaab.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The password limit of 72 is bcrypt's: it ignores anything past 72 <em>bytes</em>, so longer
 * passwords would silently be cut short. {@code AuthService} also checks the byte length, because
 * {@code @Size} counts characters and one character can take several bytes.
 */
public record SignupRequest(
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(min = 8, max = 72) String password,
    @NotBlank @Size(max = 100) String name) {}
