package in.hisaab.common.security;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Who is running the current code? The id lives in a {@link ScopedValue}: a value bound for the
 * duration of one block of code and restored automatically when the block ends, even on an
 * exception. Unlike a ThreadLocal it can never leak into the next request that reuses the thread.
 *
 * <p>{@link in.hisaab.common.db.RlsTransactionManager} reads it to tell Postgres who is asking.
 * Signup, login, tests and the background worker call {@code runAs}. Ordinary web requests never
 * do: there the id comes from the signed-in user's access token, which Spring Security has placed
 * in the {@link SecurityContextHolder} for the duration of the request.
 */
@Component
public class CurrentUser {

  private static final ScopedValue<UUID> USER_ID = ScopedValue.newInstance();

  public Optional<UUID> id() {
    if (USER_ID.isBound()) {
      return Optional.of(USER_ID.get());
    }
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken jwt) {
      // We signed this token ourselves, with the user's id as its subject.
      return Optional.of(UUID.fromString(jwt.getToken().getSubject()));
    }
    return Optional.empty();
  }

  /** For code that cannot run without a user; a missing user is a programming error. */
  public UUID require() {
    return id().orElseThrow(() -> new IllegalStateException("No current user"));
  }

  public <T> T runAs(UUID userId, Supplier<T> work) {
    return ScopedValue.where(USER_ID, userId).call(work::get);
  }

  public void runAs(UUID userId, Runnable work) {
    ScopedValue.where(USER_ID, userId).run(work);
  }
}
