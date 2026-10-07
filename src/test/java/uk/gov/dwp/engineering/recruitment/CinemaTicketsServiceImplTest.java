package uk.gov.dwp.engineering.recruitment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static uk.gov.dwp.engineering.recruitment.domain.TicketType.ADULT;
import static uk.gov.dwp.engineering.recruitment.domain.TicketType.CHILD;
import static uk.gov.dwp.engineering.recruitment.domain.TicketType.INFANT;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.dwp.engineering.recruitment.domain.BookingConfirmation;
import uk.gov.dwp.engineering.recruitment.domain.TicketRequest;
import uk.gov.dwp.engineering.recruitment.domain.TicketType;
import uk.gov.dwp.engineering.recruitment.exception.InvalidBookingException;
import uk.gov.dwp.engineering.recruitment.thirdparty.PaymentService;
import uk.gov.dwp.engineering.recruitment.thirdparty.SeatReservationService;

@ExtendWith(MockitoExtension.class)
class CinemaTicketsServiceImplTest {

  private static final Long ACCOUNT_ID = 77L;

  @Mock
  private PaymentService paymentService;

  @Mock
  private SeatReservationService seatReservationService;

  @InjectMocks
  private CinemaTicketsServiceImpl ticketService;

  //Valid cases - purchase
  @Test
  void singleAdult_paysAdultPrice_andReservesOneSeat() {
    assertBooking(new BigDecimal("25.99"), 1L, ticket(ADULT, 1));
  }

  @Test
  void mixedGroup_infantsPayNothing_andGetNoSeat() {
    assertBooking(new BigDecimal("104.48"), 5L,
        ticket(ADULT, 2), ticket(CHILD, 3), ticket(INFANT, 1));
  }

  @Test
  void exactlyMaximumTickets_isAccepted() {
    assertBooking(new BigDecimal("649.75"), 25L, ticket(ADULT, 25));
  }

  @Test
  void maximumTicketsIncludingInfants_isAccepted() {
    assertBooking(new BigDecimal("329.38"), 13L,
        ticket(ADULT, 12), ticket(CHILD, 1), ticket(INFANT, 12));
  }

  @Test
  void infantsEqualToAdults_isAccepted() {
    assertBooking(new BigDecimal("51.98"), 2L, ticket(ADULT, 2), ticket(INFANT, 2));
  }

  @Test
  void sameTypeRequestedTwice_isAddedTogether() {
    assertBooking(new BigDecimal("69.48"), 3L,
        ticket(ADULT, 1), ticket(ADULT, 1), ticket(CHILD, 1));
  }

  @Test
  void typeWithZeroCount_isIgnoredWhenOtherTicketsAreValid() {
    assertBooking(new BigDecimal("25.99"), 1L, ticket(ADULT, 1), ticket(CHILD, 0));
  }

  @Test
  void paymentIsRequestedBeforeSeatReservation() {
    ticketService.purchaseTickets(ACCOUNT_ID, ticket(ADULT, 1));

    InOrder order = inOrder(paymentService, seatReservationService);
    order.verify(paymentService).debitAccount(ACCOUNT_ID, new BigDecimal("25.99"));
    order.verify(seatReservationService).reserveSeats(ACCOUNT_ID, 1L);
  }


  //Invalid account
  @Test
  void nullAccountId_isRejected() {
    assertRejected(null, CinemaTicketsServiceImpl.INVALID_ACCOUNT, ticket(ADULT, 1));
  }

  @Test
  void zeroAccountId_isRejected() {
    assertRejected(0L, CinemaTicketsServiceImpl.INVALID_ACCOUNT, ticket(ADULT, 1));
  }

  @Test
  void negativeAccountId_isRejected() {
    assertRejected(-10L, CinemaTicketsServiceImpl.INVALID_ACCOUNT, ticket(ADULT, 1));
  }

  //Invalid request for tickets
  @Test
  void noTicketRequests_isRejected() {
    assertRejected(ACCOUNT_ID, CinemaTicketsServiceImpl.NO_TICKETS);
  }

  @Test
  void nullTicketRequestArray_isRejected() {
    assertRejected(ACCOUNT_ID, CinemaTicketsServiceImpl.NO_TICKETS, (TicketRequest[]) null);
  }

  @Test
  void nullTicketRequestEntry_isRejected() {
    assertRejected(ACCOUNT_ID, CinemaTicketsServiceImpl.NULL_TICKET_REQUEST,
        ticket(ADULT, 1), null);
  }

  @Test
  void missingTicketType_isRejected() {
    assertRejected(ACCOUNT_ID, CinemaTicketsServiceImpl.MISSING_TICKET_TYPE, ticket(null, 1));
  }

  @Test
  void negativeTicketCount_isRejected() {
    assertRejected(ACCOUNT_ID, CinemaTicketsServiceImpl.NEGATIVE_TICKET_COUNT,
        ticket(ADULT, -5));
  }

  @Test
  void zeroTicketsInTotal_isRejected() {
    assertRejected(ACCOUNT_ID, CinemaTicketsServiceImpl.ZERO_TICKETS,
        ticket(ADULT, 0), ticket(CHILD, 0));
  }

  @Test
  void moreThanMaximumTickets_isRejected() {
    assertRejected(ACCOUNT_ID, CinemaTicketsServiceImpl.TOO_MANY_TICKETS, ticket(ADULT, 26));
  }

  @Test
  void infantsCountTowardsMaximum() {
    assertRejected(ACCOUNT_ID, CinemaTicketsServiceImpl.TOO_MANY_TICKETS,
        ticket(ADULT, 13), ticket(INFANT, 13));
  }

  @Test
  void hugeCountsDoNotOverflowPastTheLimit() {
    assertRejected(ACCOUNT_ID, CinemaTicketsServiceImpl.TOO_MANY_TICKETS,
        ticket(ADULT, Integer.MAX_VALUE), ticket(ADULT, Integer.MAX_VALUE), ticket(ADULT, 2));
  }

  @Test
  void childWithoutAdult_isRejected() {
    assertRejected(ACCOUNT_ID, CinemaTicketsServiceImpl.ADULT_REQUIRED, ticket(CHILD, 2));
  }

  @Test
  void infantWithoutAdult_isRejected() {
    assertRejected(ACCOUNT_ID, CinemaTicketsServiceImpl.ADULT_REQUIRED, ticket(INFANT, 1));
  }

  @Test
  void childAndInfantWithoutAdult_isRejected() {
    assertRejected(ACCOUNT_ID, CinemaTicketsServiceImpl.ADULT_REQUIRED,
        ticket(CHILD, 1), ticket(INFANT, 1));
  }

  @Test
  void moreInfantsThanAdults_isRejected() {
    assertRejected(ACCOUNT_ID, CinemaTicketsServiceImpl.TOO_MANY_INFANTS,
        ticket(ADULT, 1), ticket(INFANT, 2));
  }

  private static TicketRequest ticket(TicketType type, int count) {
    return new TicketRequest(type, count);
  }

  private void assertBooking(BigDecimal expectedCost, long expectedSeats,
      TicketRequest... requests) {
    BookingConfirmation confirmation = ticketService.purchaseTickets(ACCOUNT_ID, requests);

    assertEquals(ACCOUNT_ID, confirmation.accountId());
    assertEquals(expectedSeats, confirmation.seatCount());
    assertEquals(expectedCost, confirmation.totalCost());

    InOrder order = inOrder(paymentService, seatReservationService);
    order.verify(paymentService).debitAccount(ACCOUNT_ID, expectedCost);
    order.verify(seatReservationService).reserveSeats(ACCOUNT_ID, expectedSeats);
  }

  private void assertRejected(Long accountId, String expectedMessage,
      TicketRequest... requests) {
    InvalidBookingException ex = assertThrows(InvalidBookingException.class,
        () -> ticketService.purchaseTickets(accountId, requests));

    assertEquals(expectedMessage, ex.getMessage());
    verifyNoInteractions(paymentService, seatReservationService);
  }
}