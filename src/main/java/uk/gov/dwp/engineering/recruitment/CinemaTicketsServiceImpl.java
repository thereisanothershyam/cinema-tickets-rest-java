package uk.gov.dwp.engineering.recruitment;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import uk.gov.dwp.engineering.recruitment.domain.BookingConfirmation;
import uk.gov.dwp.engineering.recruitment.domain.TicketRequest;
import uk.gov.dwp.engineering.recruitment.domain.TicketType;
import uk.gov.dwp.engineering.recruitment.exception.InvalidBookingException;
import uk.gov.dwp.engineering.recruitment.thirdparty.PaymentService;
import uk.gov.dwp.engineering.recruitment.thirdparty.SeatReservationService;

@Service
public class CinemaTicketsServiceImpl implements CinemaTicketsService {

  static final int MAX_TICKETS_PER_PURCHASE = 25;

  static final String INVALID_ACCOUNT = "Invalid Account ID.";
  static final String NO_TICKETS = "No tickets requested.";
  static final String NULL_TICKET_REQUEST = "Ticket request must not be null.";
  static final String MISSING_TICKET_TYPE = "Ticket type is required.";
  static final String NEGATIVE_TICKET_COUNT = "Negative ticket counts are invalid.";
  static final String ZERO_TICKETS = "Total ticket count is zero.";
  static final String TOO_MANY_TICKETS =
      "Cannot purchase more than " + MAX_TICKETS_PER_PURCHASE + " tickets.";
  static final String ADULT_REQUIRED = "Child or Infant tickets require an Adult ticket.";
  static final String TOO_MANY_INFANTS =
      "Infants cannot outnumber Adults (lap seating restriction).";

  private static final Map<TicketType, BigDecimal> TICKET_PRICES = Map.of(
      TicketType.ADULT, new BigDecimal("25.99"),
      TicketType.CHILD, new BigDecimal("17.50"),
      TicketType.INFANT, BigDecimal.ZERO);

  private final PaymentService paymentService;

  private final SeatReservationService seatReservationService;

  public CinemaTicketsServiceImpl(PaymentService paymentService,
      SeatReservationService seatReservationService) {
    this.paymentService = paymentService;
    this.seatReservationService = seatReservationService;
  }

  @Override
  public BookingConfirmation purchaseTickets(final Long accountId,
      final TicketRequest... ticketRequests) throws InvalidBookingException {

    validateAccountId(accountId);
    final Map<TicketType, Long> ticketCounts = countTicketsByType(ticketRequests);
    validateBusinessRules(ticketCounts);

    final BigDecimal totalCost = calculateTotalCost(ticketCounts);
    final long seatCount = calculateSeatCount(ticketCounts);

    paymentService.debitAccount(accountId, totalCost);
    seatReservationService.reserveSeats(accountId, seatCount);

    return new BookingConfirmation(accountId, seatCount, totalCost);
  }

  private void validateAccountId(final Long accountId) {
    if (accountId == null || accountId <= 0) {
      throw new InvalidBookingException(INVALID_ACCOUNT);
    }
  }

  private Map<TicketType, Long> countTicketsByType(final TicketRequest... ticketRequests) {
    if (ticketRequests == null || ticketRequests.length == 0) {
      throw new InvalidBookingException(NO_TICKETS);
    }

    final Map<TicketType, Long> counts = new EnumMap<>(TicketType.class);
    for (TicketType type : TicketType.values()) {
      counts.put(type, 0L);
    }

    for (TicketRequest request : ticketRequests) {
      if (request == null) {
        throw new InvalidBookingException(NULL_TICKET_REQUEST);
      }
      if (request.type() == null) {
        throw new InvalidBookingException(MISSING_TICKET_TYPE);
      }
      if (request.ticketCount() < 0) {
        throw new InvalidBookingException(NEGATIVE_TICKET_COUNT);
      }
      counts.merge(request.type(), (long) request.ticketCount(), Long::sum);
    }
    return counts;
  }

  private void validateBusinessRules(final Map<TicketType, Long> counts) {
    final long adults = counts.get(TicketType.ADULT);
    final long children = counts.get(TicketType.CHILD);
    final long infants = counts.get(TicketType.INFANT);
    final long totalTickets = adults + children + infants;

    if (totalTickets == 0) {
      throw new InvalidBookingException(ZERO_TICKETS);
    }
    if (totalTickets > MAX_TICKETS_PER_PURCHASE) {
      throw new InvalidBookingException(TOO_MANY_TICKETS);
    }
    if (adults == 0) {
      throw new InvalidBookingException(ADULT_REQUIRED);
    }
    if (infants > adults) {
      throw new InvalidBookingException(TOO_MANY_INFANTS);
    }
  }

  private BigDecimal calculateTotalCost(final Map<TicketType, Long> counts) {
    return counts.entrySet().stream()
        .map(entry -> TICKET_PRICES.get(entry.getKey())
            .multiply(BigDecimal.valueOf(entry.getValue())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private long calculateSeatCount(final Map<TicketType, Long> counts) {
    return counts.get(TicketType.ADULT) + counts.get(TicketType.CHILD);
  }
}