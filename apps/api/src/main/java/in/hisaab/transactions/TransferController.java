package in.hisaab.transactions;

import in.hisaab.transactions.dto.TransferRequest;
import in.hisaab.transactions.dto.TransferResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The id in these paths is the transfer's group id (see TransferResponse.id). */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/transfers")
public class TransferController {

  private final TransferService transferService;

  public TransferController(TransferService transferService) {
    this.transferService = transferService;
  }

  @GetMapping("/{transferId}")
  public TransferResponse get(@PathVariable UUID workspaceId, @PathVariable UUID transferId) {
    return transferService.get(workspaceId, transferId);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public TransferResponse create(
      @PathVariable UUID workspaceId, @Valid @RequestBody TransferRequest request) {
    return transferService.create(workspaceId, request);
  }

  @PutMapping("/{transferId}")
  public TransferResponse replace(
      @PathVariable UUID workspaceId,
      @PathVariable UUID transferId,
      @Valid @RequestBody TransferRequest request) {
    return transferService.replace(workspaceId, transferId, request);
  }

  @DeleteMapping("/{transferId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID workspaceId, @PathVariable UUID transferId) {
    transferService.delete(workspaceId, transferId);
  }

  @PostMapping("/{transferId}/restore")
  public TransferResponse restore(@PathVariable UUID workspaceId, @PathVariable UUID transferId) {
    return transferService.restore(workspaceId, transferId);
  }
}
