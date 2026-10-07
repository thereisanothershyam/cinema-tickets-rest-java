package uk.gov.dwp.engineering.recruitment;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import uk.gov.dwp.engineering.recruitment.thirdparty.PaymentService;
import uk.gov.dwp.engineering.recruitment.thirdparty.SeatReservationService;

@WebMvcTest(CinemaTicketsController.class)
@Import(CinemaTicketsServiceImpl.class)
class CinemaTicketsControllerTest {

  private static final String BOOKINGS_URL = "/cinema/bookings";

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private PaymentService paymentService;

  @MockitoBean
  private SeatReservationService seatReservationService;

  //Valid Booking - 201
  @Test
  void singleAdult_returns201_withCostAndSeats() throws Exception {
    postBooking("""
        {"accountId": 250, "ticketRequests": [{"type": "ADULT", "ticketCount": 1}]}
        """)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.accountId").value(250))
        .andExpect(jsonPath("$.seatCount").value(1))
        .andExpect(jsonPath("$.totalCost").value(25.99));

    verify(paymentService).debitAccount(250L, new BigDecimal("25.99"));
    verify(seatReservationService).reserveSeats(250L, 1L);
  }

  @Test
  void mixedGroup_returns201_infantsFreeAndWithoutSeat() throws Exception {
    postBooking("""
        {"accountId": 250, "ticketRequests": [
          {"type": "ADULT", "ticketCount": 2},
          {"type": "CHILD", "ticketCount": 3},
          {"type": "INFANT", "ticketCount": 1}
        ]}
        """)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.seatCount").value(5))
        .andExpect(jsonPath("$.totalCost").value(104.48));

    verify(paymentService).debitAccount(250L, new BigDecimal("104.48"));
    verify(seatReservationService).reserveSeats(250L, 5L);
  }

  @Test
  void exactlyMaximumTickets_returns201() throws Exception {
    postBooking("""
        {"accountId": 250, "ticketRequests": [
          {"type": "ADULT", "ticketCount": 12},
          {"type": "CHILD", "ticketCount": 1},
          {"type": "INFANT", "ticketCount": 12}
        ]}
        """)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.seatCount").value(13))
        .andExpect(jsonPath("$.totalCost").value(329.38));
  }

  //Business Rule Bad requests - 400
  @Test
  void moreThanMaximumTickets_returns400() throws Exception {
    assertRejected("""
        {"accountId": 250, "ticketRequests": [{"type": "ADULT", "ticketCount": 26}]}
        """, CinemaTicketsServiceImpl.TOO_MANY_TICKETS);
  }

  @Test
  void infantsCountTowardsMaximum_returns400() throws Exception {
    assertRejected("""
        {"accountId": 250, "ticketRequests": [
          {"type": "ADULT", "ticketCount": 25},
          {"type": "INFANT", "ticketCount": 1}
        ]}
        """, CinemaTicketsServiceImpl.TOO_MANY_TICKETS);
  }

  @Test
  void childWithoutAdult_returns400() throws Exception {
    assertRejected("""
        {"accountId": 250, "ticketRequests": [{"type": "CHILD", "ticketCount": 3}]}
        """, CinemaTicketsServiceImpl.ADULT_REQUIRED);
  }

  @Test
  void infantWithoutAdult_returns400() throws Exception {
    assertRejected("""
        {"accountId": 250, "ticketRequests": [{"type": "INFANT", "ticketCount": 1}]}
        """, CinemaTicketsServiceImpl.ADULT_REQUIRED);
  }

  @Test
  void moreInfantsThanAdults_returns400() throws Exception {
    assertRejected("""
        {"accountId": 250, "ticketRequests": [
          {"type": "ADULT", "ticketCount": 1},
          {"type": "INFANT", "ticketCount": 2}
        ]}
        """, CinemaTicketsServiceImpl.TOO_MANY_INFANTS);
  }

  //Invalid Input - 400
  @Test
  void zeroAccountId_returns400() throws Exception {
    assertRejected("""
        {"accountId": 0, "ticketRequests": [{"type": "ADULT", "ticketCount": 1}]}
        """, CinemaTicketsServiceImpl.INVALID_ACCOUNT);
  }

  @Test
  void negativeAccountId_returns400() throws Exception {
    assertRejected("""
        {"accountId": -1, "ticketRequests": [{"type": "ADULT", "ticketCount": 1}]}
        """, CinemaTicketsServiceImpl.INVALID_ACCOUNT);
  }

  @Test
  void missingAccountId_returns400() throws Exception {
    assertRejected("""
        {"ticketRequests": [{"type": "ADULT", "ticketCount": 1}]}
        """, CinemaTicketsServiceImpl.INVALID_ACCOUNT);
  }

  @Test
  void missingTicketRequests_returns400() throws Exception {
    assertRejected("""
        {"accountId": 250}
        """, CinemaTicketsServiceImpl.NO_TICKETS);
  }

  @Test
  void emptyTicketRequests_returns400() throws Exception {
    assertRejected("""
        {"accountId": 250, "ticketRequests": []}
        """, CinemaTicketsServiceImpl.NO_TICKETS);
  }

  @Test
  void nullTicketRequestEntry_returns400() throws Exception {
    assertRejected("""
        {"accountId": 250, "ticketRequests": [null]}
        """, CinemaTicketsServiceImpl.NULL_TICKET_REQUEST);
  }

  @Test
  void missingTicketType_returns400() throws Exception {
    assertRejected("""
        {"accountId": 250, "ticketRequests": [{"ticketCount": 1}]}
        """, CinemaTicketsServiceImpl.MISSING_TICKET_TYPE);
  }

  @Test
  void negativeTicketCount_returns400() throws Exception {
    assertRejected("""
        {"accountId": 250, "ticketRequests": [{"type": "ADULT", "ticketCount": -5}]}
        """, CinemaTicketsServiceImpl.NEGATIVE_TICKET_COUNT);
  }

  @Test
  void zeroTicketsInTotal_returns400() throws Exception {
    assertRejected("""
        {"accountId": 250, "ticketRequests": [{"type": "ADULT", "ticketCount": 0}]}
        """, CinemaTicketsServiceImpl.ZERO_TICKETS);
  }

  @Test
  void unknownTicketType_returns400() throws Exception {
    postBooking("""
        {"accountId": 250, "ticketRequests": [{"type": "SENIOR", "ticketCount": 1}]}
        """)
        .andExpect(status().isBadRequest());

    verifyNoInteractions(paymentService, seatReservationService);
  }

  @Test
  void malformedJson_returns400() throws Exception {
    postBooking("{ this is not json")
        .andExpect(status().isBadRequest());

    verifyNoInteractions(paymentService, seatReservationService);
  }

  private ResultActions postBooking(String json) throws Exception {
    return mockMvc.perform(post(BOOKINGS_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .content(json));
  }

  private void assertRejected(String json, String expectedDetail) throws Exception {
    postBooking(json)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").value(expectedDetail));

    verifyNoInteractions(paymentService, seatReservationService);
  }
}