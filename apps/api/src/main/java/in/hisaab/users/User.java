package in.hisaab.users;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of {@code users}. Unlike {@code Workspace}, the id is chosen by the app (a random UUID
 * made at signup), because signup must know the new user's id <em>before</em> its transaction
 * starts, so the transaction can be tagged with that user for row-level security.
 *
 * <p>Hibernate inserts every mapped column, so the constructor sets the same defaults the database
 * would (timezone, period start day, digest on). created_at is not mapped; the database fills it.
 */
@Entity
@Table(name = "users")
public class User {

  @Id private UUID id;

  private String email;
  private String passwordHash;
  private String name;
  private String timezone;
  private int periodStartDay;
  private boolean duesDigestEnabled;
  private Instant updatedAt;

  protected User() {}

  public User(UUID id, String email, String passwordHash, String name, Instant now) {
    this.id = id;
    this.email = email;
    this.passwordHash = passwordHash;
    this.name = name;
    this.timezone = "Asia/Kolkata";
    this.periodStartDay = 1;
    this.duesDigestEnabled = true;
    this.updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getName() {
    return name;
  }

  public String getTimezone() {
    return timezone;
  }

  public int getPeriodStartDay() {
    return periodStartDay;
  }

  public boolean isDuesDigestEnabled() {
    return duesDigestEnabled;
  }

  /** Only the settings fields are changeable; id, email and password hash are not. */
  public void updateSettings(
      String name, String timezone, int periodStartDay, boolean duesDigestEnabled, Instant now) {
    this.name = name;
    this.timezone = timezone;
    this.periodStartDay = periodStartDay;
    this.duesDigestEnabled = duesDigestEnabled;
    this.updatedAt = now;
  }
}
