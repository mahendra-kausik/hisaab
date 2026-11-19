package in.hisaab.reports;

import in.hisaab.reports.dto.MonthlyReport;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/reports")
public class ReportController {

  private final ReportService reportService;

  public ReportController(ReportService reportService) {
    this.reportService = reportService;
  }

  /** {@code period} is any date inside the wanted period; omitted means the current one. */
  @GetMapping("/monthly")
  public MonthlyReport monthly(
      @PathVariable UUID workspaceId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate period) {
    return reportService.monthly(workspaceId, period);
  }
}
