package in.hisaab.attachments;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

  Optional<Attachment> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

  List<Attachment> findByWorkspaceIdAndTransactionIdOrderByCreatedAt(
      UUID workspaceId, UUID transactionId);

  List<Attachment> findByWorkspaceIdAndDueIdOrderByCreatedAt(UUID workspaceId, UUID dueId);
}
