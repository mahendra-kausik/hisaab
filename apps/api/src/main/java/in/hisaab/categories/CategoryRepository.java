package in.hisaab.categories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

  List<Category> findByWorkspaceIdOrderByKindAscSortOrderAscNameAsc(UUID workspaceId);

  Optional<Category> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
