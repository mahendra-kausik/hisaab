package in.hisaab.common.db;

import in.hisaab.common.security.CurrentUser;
import jakarta.persistence.EntityManagerFactory;
import java.util.UUID;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The transaction manager Spring uses for every {@code @Transactional} method. It behaves exactly
 * like the standard {@link JpaTransactionManager}, plus one step: right after a transaction begins
 * it tells Postgres who the current user is, so row-level security can filter rows. Because every
 * transaction goes through here, no code can forget to do it.
 *
 * <p>The bean must be named {@code transactionManager}: Spring Data repositories look it up by that
 * name, and Spring Boot's own default backs off once we define one.
 */
@Component("transactionManager")
public class RlsTransactionManager extends JpaTransactionManager {

  // JpaTransactionManager is Serializable, so the compiler wants a version id and
  // non-serializable fields marked transient. Nothing ever serializes it.
  private static final long serialVersionUID = 1L;

  private final transient CurrentUser currentUser;

  public RlsTransactionManager(EntityManagerFactory entityManagerFactory, CurrentUser currentUser) {
    super(entityManagerFactory);
    this.currentUser = currentUser;
  }

  @Override
  protected void doBegin(Object transaction, TransactionDefinition definition) {
    super.doBegin(transaction, definition);
    // With no current user nothing is set, and RLS then returns no rows.
    currentUser.id().ifPresent(userId -> setRlsUser(transaction, userId));
  }

  private void setRlsUser(Object transaction, UUID userId) {
    try {
      EntityManagerHolder holder =
          (EntityManagerHolder)
              TransactionSynchronizationManager.getResource(obtainEntityManagerFactory());
      // set_config(name, value, true): the `true` makes it transaction-local, so the
      // value disappears at commit/rollback and is safe with connection pooling. It runs
      // on the transaction's connection, which JdbcTemplate shares.
      holder
          .getEntityManager()
          .createNativeQuery("SELECT set_config('app.user_id', ?1, true)")
          .setParameter(1, userId.toString())
          .getSingleResult();
    } catch (RuntimeException e) {
      // The transaction already began; release its resources before reporting the failure.
      doCleanupAfterCompletion(transaction);
      throw e;
    }
  }
}
