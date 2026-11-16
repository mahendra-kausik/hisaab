package in.hisaab.auth;

import in.hisaab.common.web.UnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates and checks tokens. Two kinds:
 *
 * <ul>
 *   <li>Access token: a short-lived signed JWT sent with every request. Nothing is stored; the
 *       signature proves it is ours.
 *   <li>Refresh token: a long-lived random string used only to get a new access token. It is stored
 *       (hashed) so it can be revoked, and it is replaced on every use ("rotation").
 * </ul>
 */
@Service
public class TokenService {

  public static final Duration ACCESS_TTL = Duration.ofMinutes(15);
  public static final Duration REFRESH_TTL = Duration.ofDays(30);

  /** What the client receives. The raw refresh token exists only here and in the cookie. */
  public record IssuedTokens(String accessToken, String refreshToken) {}

  private final SecureRandom random = new SecureRandom();
  private final RefreshTokenRepository refreshTokens;
  private final JwtEncoder jwtEncoder;
  private final Clock clock;

  public TokenService(RefreshTokenRepository refreshTokens, JwtEncoder jwtEncoder, Clock clock) {
    this.refreshTokens = refreshTokens;
    this.jwtEncoder = jwtEncoder;
    this.clock = clock;
  }

  /** Starts a new session (a new token family). Joins the caller's transaction if there is one. */
  @Transactional
  public IssuedTokens startSession(UUID userId) {
    return issue(userId, UUID.randomUUID());
  }

  /**
   * Swaps a refresh token for a new pair. Presenting a token that was already swapped means it was
   * copied or replayed, so every token in its family is revoked.
   */
  // noRollbackFor: we throw after revoking the family, and that revocation must be kept. By
  // default a RuntimeException would roll the whole transaction back and undo it.
  @Transactional(noRollbackFor = UnauthorizedException.class)
  public IssuedTokens rotate(String rawRefreshToken) {
    var token =
        refreshTokens.findByTokenHash(hash(rawRefreshToken)).orElseThrow(this::invalidToken);
    var now = Instant.now(clock);
    if (token.getRevokedAt() != null) {
      refreshTokens.revokeFamily(token.getFamilyId(), now);
      throw invalidToken();
    }
    if (!token.getExpiresAt().isAfter(now)) {
      throw invalidToken();
    }
    token.revoke(now);
    return issue(token.getUserId(), token.getFamilyId());
  }

  /** Logout: revokes the whole session. Unknown or missing tokens are ignored. */
  @Transactional
  public void endSession(String rawRefreshToken) {
    if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
      return;
    }
    refreshTokens
        .findByTokenHash(hash(rawRefreshToken))
        .ifPresent(t -> refreshTokens.revokeFamily(t.getFamilyId(), Instant.now(clock)));
  }

  private IssuedTokens issue(UUID userId, UUID familyId) {
    var now = Instant.now(clock);
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    // Expired rows are never purged yet; a cleanup job can delete them later.
    refreshTokens.save(new RefreshToken(userId, hash(raw), familyId, now.plus(REFRESH_TTL)));

    var claims =
        JwtClaimsSet.builder()
            .subject(userId.toString())
            .issuedAt(now)
            .expiresAt(now.plus(ACCESS_TTL))
            .build();
    var header = JwsHeader.with(MacAlgorithm.HS256).build();
    String access = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    return new IssuedTokens(access, raw);
  }

  // SHA-256 is enough here (no bcrypt): the token is 256 random bits, so it cannot be guessed
  // or brute-forced from its hash the way a human password can.
  private static String hash(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new UnauthorizedException("invalid_refresh_token", "Sign in again");
    }
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is always available", e);
    }
  }

  private UnauthorizedException invalidToken() {
    return new UnauthorizedException("invalid_refresh_token", "Sign in again");
  }
}
