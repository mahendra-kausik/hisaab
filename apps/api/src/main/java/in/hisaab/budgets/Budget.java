package in.hisaab.budgets;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** One row of {@code budgets}. Only the amount changes after creation. */
@Entity
@Table(name = "budgets")
public class Budget {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private UUID id;

  private UUID workspaceId;
  private UUID categoryId;
  private long amountPaise;
  private LocalDate effectiveFrom;

  protected Budget() {}

  public Budget(UUID workspaceId, UUID categoryId, long amountPaise, LocalDate effectiveFrom) {
    this.workspaceId = workspaceId;
    this.categoryId = categoryId;
    this.amountPaise = amountPaise;
    this.effectiveFrom = effectiveFrom;
  }

  public void setAmountPaise(long amountPaise) {
    this.amountPaise = amountPaise;
  }
}
