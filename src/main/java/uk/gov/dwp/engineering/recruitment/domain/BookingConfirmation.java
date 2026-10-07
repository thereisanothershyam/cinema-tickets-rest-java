package uk.gov.dwp.engineering.recruitment.domain;

import java.math.BigDecimal;

public record BookingConfirmation(Long accountId, long seatCount, BigDecimal totalCost) {

  public BookingConfirmation(Long accountId) {
    this(accountId, 0L, BigDecimal.ZERO);
  }
}
