package in.hisaab.users;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

  /** Spring builds the query from the method name: {@code SELECT ... WHERE email = ?}. */
  Optional<User> findByEmail(String email);
}
