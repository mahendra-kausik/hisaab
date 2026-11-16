package in.hisaab.auth;

import in.hisaab.auth.TokenService.IssuedTokens;
import in.hisaab.auth.dto.LoginRequest;
import in.hisaab.auth.dto.SignupRequest;
import in.hisaab.common.security.CurrentUser;
import in.hisaab.common.web.ConflictException;
import in.hisaab.common.web.UnauthorizedException;
import in.hisaab.common.web.ValidationException;
import in.hisaab.users.User;
import in.hisaab.users.UserService;
import in.hisaab.users.dto.MeResponse;
import in.hisaab.workspaces.WorkspaceKind;
import in.hisaab.workspaces.WorkspaceService;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Signup and login. Both finish by starting a session, so they return the profile and the tokens.
 *
 * <p>This class opens its transactions with a {@link TransactionTemplate} instead of
 * {@code @Transactional}, because it must first say <em>who</em> the transaction is for (see {@link
 * #signup}).
 */
@Service
public class AuthService {

  /** bcrypt only reads the first 72 bytes of a password. */
  private static final int BCRYPT_MAX_BYTES = 72;

  public record AuthResult(MeResponse me, IssuedTokens tokens) {}

  private final UserService userService;
  private final WorkspaceService workspaceService;
  private final TokenService tokenService;
  private final PasswordEncoder passwordEncoder;
  private final CurrentUser currentUser;
  private final TransactionTemplate tx;

  // Checked against when the email is unknown, so "no such user" costs as much time as "wrong
  // password" and the response time does not reveal which emails have accounts.
  private final String dummyHash;

  public AuthService(
      UserService userService,
      WorkspaceService workspaceService,
      TokenService tokenService,
      PasswordEncoder passwordEncoder,
      CurrentUser currentUser,
      TransactionTemplate tx) {
    this.userService = userService;
    this.workspaceService = workspaceService;
    this.tokenService = tokenService;
    this.passwordEncoder = passwordEncoder;
    this.currentUser = currentUser;
    this.tx = tx;
    this.dummyHash = passwordEncoder.encode("not-a-real-password");
  }

  public AuthResult signup(SignupRequest request) {
    if (tooLongForBcrypt(request.password())) {
      throw new ValidationException(
          "password_too_long",
          "Password is too long",
          Map.of("fields", Map.of("password", "Use at most 72 bytes")));
    }
    String email = normalize(request.email());
    // Slow on purpose (bcrypt), so it runs before the transaction and holds no connection.
    String hash = passwordEncoder.encode(request.password());

    // The new user's id is made here so the transaction can be tagged with it from the start.
    // runAs binds the id, then the transaction begins and RlsTransactionManager tells Postgres
    // who it is. The workspace insert is protected by RLS, so without this it would be refused.
    UUID id = UUID.randomUUID();
    try {
      return currentUser.runAs(
          id,
          () ->
              tx.execute(status -> {
                userService.create(id, email, hash, request.name().trim());
                workspaceService.create("Personal", WorkspaceKind.PERSONAL);
                return new AuthResult(userService.me(), tokenService.startSession(id));
              }));
    } catch (DataIntegrityViolationException e) {
      // The UNIQUE constraint on users.email: the database decides, so two simultaneous
      // signups with one email cannot both succeed.
      throw new ConflictException("email_taken", "An account with this email already exists");
    }
  }

  public AuthResult login(LoginRequest request) {
    var found = userService.findByEmail(normalize(request.email()));
    String hash = found.map(User::getPasswordHash).orElse(dummyHash);
    boolean passwordOk =
        !tooLongForBcrypt(request.password()) && passwordEncoder.matches(request.password(), hash);
    if (found.isEmpty() || !passwordOk) {
      // One message for both cases, so the response never says which part was wrong.
      throw new UnauthorizedException("invalid_credentials", "Wrong email or password");
    }
    UUID id = found.get().getId();
    return currentUser.runAs(
        id,
        () ->
            tx.execute(status -> new AuthResult(userService.me(), tokenService.startSession(id))));
  }

  public IssuedTokens refresh(String rawRefreshToken) {
    return tokenService.rotate(rawRefreshToken);
  }

  public void logout(String rawRefreshToken) {
    tokenService.endSession(rawRefreshToken);
  }

  private static String normalize(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private static boolean tooLongForBcrypt(String password) {
    return password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES;
  }
}
