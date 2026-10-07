# Cinema Tickets Code Test

A Spring Boot REST API for buying cinema tickets. It works out the cost and the number of
seats for a purchase, asks the `PaymentService` to take payment and asks the
`SeatReservationService` to reserve seats. Any request that breaks the business rules is
rejected with `400 Bad Request`.

## Running

Requires JDK 21 and Maven 3.9+.

```shell
mvn clean test           # run all tests
mvn spring-boot:run      # start the API on http://localhost:8080
```

Example request:

```shell
curl -i -X POST http://localhost:8080/cinema/bookings \
  -H "Content-Type: application/json" \
  -d '{"accountId":250,"ticketRequests":[{"type":"ADULT","ticketCount":2},{"type":"CHILD","ticketCount":1}]}'
```

Successful response (`201 Created`):

```json
{"accountId":250,"seatCount":3,"totalCost":69.48}
```

Rejected response (`400 Bad Request`, RFC 9457 problem detail):

```json
{"detail":"Child or Infant tickets require an Adult ticket.","instance":"/cinema/bookings",
 "status":400,"title":"Bad Request"}
```

## Approach

`CinemaTicketsServiceImpl` handles a purchase in four steps:

1. **Validate the account.** The ID must be present and greater than zero.
2. **Count tickets by type.** Each request is checked (not null, has a type, count is not
   negative), and counts for the same type are added together. Counts are held as `long`
   so that very large values cannot overflow and get past the 25-ticket limit.
3. **Apply the business rules.** At least one ticket, at most 25 in total, an adult is
   required for any child or infant, and no more infants than adults.
4. **Calculate, pay and reserve.** The cost uses `BigDecimal` so pence are never lost.
   Infants are free and get no seat. Payment is requested first, then seats are reserved.
   Neither external service is called if the request is invalid.

Prices are kept in one map, so a price change only touches one line. Each error message
is a named constant, which the tests reuse, so a message cannot drift from its test.

`BookingConfirmation` was extended in a non-breaking way, as the brief allows. It now also
returns `seatCount` and `totalCost`, and the original one-argument constructor is kept.
No other class in the `domain` package and no part of the `CinemaTicketsService`
interface was changed.

## Business rules and how each one is met

| Rule | Implementation |
| --- | --- |
| Prices: Adult £25.99, Child £17.50, Infant £0 | `TICKET_PRICES` map, `BigDecimal` maths |
| Multiple tickets per purchase | Any number of `TicketRequest`s; same types are added up |
| Maximum 25 tickets per purchase | Total of all types, including infants, must be ≤ 25 |
| Infants don't pay and get no seat | Price £0; seats = adults + children |
| Child and Infant need an Adult | Rejected if there are no adult tickets |
| Accounts with ID > 0 are valid | Null, zero and negative IDs are rejected |

## Assumptions and ambiguities

The brief leaves some points open. These are the decisions I made:

1. **Infants count towards the 25-ticket limit.** An infant still has a ticket, even
   though it is free and has no seat. So 25 adults plus 1 infant is rejected.
2. **No more infants than adults.** Each infant sits on an adult's lap, and one adult has
   one lap. 2 adults with 2 infants is fine; 1 adult with 2 infants is rejected.
3. **There is no limit on children per adult.** The brief does not give one.
4. **A request with zero tickets in total is invalid.** A request with a zero count for
   one type is fine if other tickets make the total valid (for example 1 adult, 0 children).
5. **Negative ticket counts are invalid**, even when the total would still be positive.
6. **A null ticket request, or one with no type, is invalid.** It is rejected, not
   skipped, because skipping it would hide a client mistake.
7. **The same type can appear more than once.** For example two separate `ADULT` entries;
   their counts are added together.
8. **Unknown ticket types and malformed JSON return 400.** Spring's standard handler
   returns these as problem details too.
9. **Payment happens before seat reservation.** Both services are assumed never to fail,
   as the brief says, so no rollback is needed.
10. **Total cost is returned in pounds with two decimal places**, for example `104.48`.
11. **The supplied OpenAPI file (`cinema-tickets.yaml`) uses different field names**
    (`tickets` and `count`) from the domain records (`ticketRequests` and `ticketCount`).
    The domain records must not be changed, so the API uses the record names. I treated
    the code and the brief as the source of truth over the OpenAPI file. The response does
    match the spec: `accountId`, `seatCount` and `totalCost`.

## Testing

- **`CinemaTicketsServiceImplTest`** unit tests the service with mocked external services.
  It covers every rule, the boundaries (exactly 25, 26, infants equal to and more than
  adults), the payment amount, the seat count and the call order. It also checks that
  nothing is charged or reserved when a request is rejected.
- **`CinemaTicketsControllerTest`** tests the HTTP API through the real service, with only
  `PaymentService` and `SeatReservationService` mocked. It checks every rejected request
  returns `400` with the right `detail`, and valid requests return `201` with the right
  body. It also covers invalid JSON and unknown ticket types.
