package in.hisaab.transactions;

import in.hisaab.common.web.ListResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only: tags are created by using them on a transaction. Lists names for autocomplete. */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/tags")
public class TagController {

  private final TransactionService transactionService;

  public TagController(TransactionService transactionService) {
    this.transactionService = transactionService;
  }

  @GetMapping
  public ListResponse<String> list(@PathVariable UUID workspaceId) {
    return ListResponse.of(transactionService.listTags(workspaceId));
  }
}
