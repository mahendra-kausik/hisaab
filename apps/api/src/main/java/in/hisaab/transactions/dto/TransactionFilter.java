package in.hisaab.transactions.dto;

import in.hisaab.transactions.TransactionType;
import java.time.LocalDate;
import java.util.UUID;

/** Every field is optional; null means "don't filter on this". */
public record TransactionFilter(
    LocalDate from,
    LocalDate to,
    TransactionType type,
    UUID accountId,
    UUID categoryId,
    UUID partyId,
    String tag,
    String q) {}
