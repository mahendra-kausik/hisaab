package in.hisaab.dues;

import in.hisaab.common.web.ValidationException;
import in.hisaab.transactions.TransactionType;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The rules for a transaction that pays a due. A separate small class (not DueService) so
 * TransactionService can call it without a circular dependency: DueService creates transactions.
 */
@Component
public class DuePayments {

  private final DueRepository dues;

  public DuePayments(DueRepository dues) {
    this.dues = dues;
  }

  /**
   * Locks the due and checks that a payment of {@code amountPaise} and {@code type} fits. {@code
   * alreadyCountedPaise} is what this same payment already counts towards the due (its old amount
   * when it is being edited, else 0). Returns the due's party, which the payment must carry. Called
   * after the caller's own access check.
   */
  @Transactional
  public UUID requireAcceptsPayment(
      UUID workspaceId,
      UUID dueId,
      TransactionType type,
      long amountPaise,
      long alreadyCountedPaise) {
    var due =
        dues.lockLive(dueId, workspaceId)
            .orElseThrow(() ->
                new ValidationException(
                    "invalid_due",
                    "Due not found in this workspace",
                    Map.of("fields", Map.of("due_id", "Not a due in this workspace"))));
    if (due.getCancelledAt() != null) {
      throw new ValidationException(
          "due_cancelled",
          "This due is cancelled; reopen it to record payments",
          Map.of("fields", Map.of("due_id", "Due is cancelled")));
    }
    // Getting money back for a receivable is income; paying off a payable is an expense.
    var expected =
        due.getDirection() == DueDirection.RECEIVABLE
            ? TransactionType.INCOME
            : TransactionType.EXPENSE;
    if (type != expected) {
      throw new ValidationException(
          "payment_type_mismatch",
          "A payment on a " + due.getDirection().json() + " due must be " + expected.json(),
          Map.of("fields", Map.of("type", "Must be " + expected.json() + " for this due")));
    }
    long outstanding = due.getAmountPaise() - (dues.paidFor(dueId) - alreadyCountedPaise);
    if (amountPaise > outstanding) {
      throw new ValidationException(
          "overpayment",
          "Payment is more than the outstanding amount",
          Map.of(
              "outstanding_paise",
              outstanding,
              "fields",
              Map.of("amount_paise", "More than the outstanding amount")));
    }
    return due.getPartyId();
  }
}
