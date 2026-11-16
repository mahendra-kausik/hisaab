package in.hisaab.common.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Slows down password guessing and signup spam: each client IP gets a "token bucket" per route.
 * Every attempt takes a token; tokens trickle back over time; with none left the request is
 * answered 429 before any password hashing happens.
 *
 * <p>This is a plain filter object that {@code SecurityConfig} adds to the chain. It is
 * deliberately not a Spring bean, because Boot would then also register it a second time with the
 * servlet container.
 */
// ponytail: in-memory, so each API instance counts separately and a restart resets the counts.
// Fine for one instance; move the buckets to Postgres or Redis if the API ever scales out.
public class AuthRateLimitFilter extends OncePerRequestFilter {

  private static final int MAX_TRACKED_CLIENTS = 10_000;

  private enum Limit {
    LOGIN("/api/v1/auth/login", 10, Duration.ofMinutes(1)),
    SIGNUP("/api/v1/auth/signup", 5, Duration.ofMinutes(10));

    final String path;
    final Bandwidth bandwidth;

    Limit(String path, int attempts, Duration per) {
      this.path = path;
      this.bandwidth = Bandwidth.builder().capacity(attempts).refillGreedy(attempts, per).build();
    }
  }

  /** Keeps only the most recently used entries, so memory stays bounded. */
  private static final class LruMap extends LinkedHashMap<String, Bucket> {
    private static final long serialVersionUID = 1L;

    LruMap() {
      super(16, 0.75f, true); // true = order by last access
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
      return size() > MAX_TRACKED_CLIENTS;
    }
  }

  private final Map<String, Bucket> buckets = new LruMap();

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Limit limit = limitFor(request);
    if (limit == null) {
      chain.doFilter(request, response);
      return;
    }
    // getRemoteAddr() is the real client IP: with forward-headers-strategy=native Tomcat
    // swaps in X-Forwarded-For, but only from a trusted proxy.
    var probe = bucketFor(limit, request.getRemoteAddr()).tryConsumeAndReturnRemaining(1);
    if (probe.isConsumed()) {
      chain.doFilter(request, response);
      return;
    }
    long retryAfterSeconds =
        Math.max(1, (probe.getNanosToWaitForRefill() + 999_999_999) / 1_000_000_000);
    response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
    SecurityErrors.write(response, 429, "rate_limited", "Too many attempts. Try again later.");
  }

  private static Limit limitFor(HttpServletRequest request) {
    if (!"POST".equals(request.getMethod())) {
      return null;
    }
    for (Limit limit : Limit.values()) {
      if (limit.path.equals(request.getRequestURI())) {
        return limit;
      }
    }
    return null;
  }

  private Bucket bucketFor(Limit limit, String clientIp) {
    synchronized (buckets) {
      return buckets.computeIfAbsent(
          limit.name() + ":" + clientIp, key -> Bucket.builder().addLimit(limit.bandwidth).build());
    }
  }
}
