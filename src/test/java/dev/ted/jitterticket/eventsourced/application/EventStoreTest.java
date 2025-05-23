package dev.ted.jitterticket.eventsourced.application;

import dev.ted.jitterticket.eventsourced.domain.TicketOrderId;
import dev.ted.jitterticket.eventsourced.domain.concert.Concert;
import dev.ted.jitterticket.eventsourced.domain.concert.ConcertEvent;
import dev.ted.jitterticket.eventsourced.domain.concert.ConcertFactory;
import dev.ted.jitterticket.eventsourced.domain.concert.ConcertId;
import dev.ted.jitterticket.eventsourced.domain.concert.ConcertRescheduled;
import dev.ted.jitterticket.eventsourced.domain.concert.ConcertScheduled;
import dev.ted.jitterticket.eventsourced.domain.concert.TicketsSold;
import dev.ted.jitterticket.eventsourced.domain.customer.Customer;
import dev.ted.jitterticket.eventsourced.domain.customer.CustomerEvent;
import dev.ted.jitterticket.eventsourced.domain.customer.CustomerId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

class EventStoreTest {

    @Test
    void findByIdForNonExistingConcertReturnsEmptyOptional() {
        var concertStore = EventStore.forConcerts();

        ConcertId concertId = new ConcertId(UUID.fromString("123e4567-e89b-42d3-a456-556642440000"));
        assertThat(concertStore.findById(concertId))
                .as("Should not be able to find a non-existent Concert by ID")
                .isEmpty();
    }

    @Test
    void findByIdReturnsSavedConcert() {
        var concertStore = EventStore.forConcerts();
        ConcertId concertId = ConcertId.createRandom();
        Concert concert = Concert.schedule(concertId,
                                           "Headliner",
                                           99,
                                           LocalDateTime.now(),
                                           LocalTime.now().minusHours(1),
                                           100,
                                           4
        );

        concertStore.save(concert);

        assertThat(concertStore.findById(concertId))
                .as("Should be able to find a saved Concert by its ConcertId")
                .isPresent()
                .get()
                .extracting(Concert::artist)
                .isEqualTo("Headliner");
    }

    @Test
    void findByIdReturnsDifferentInstanceOfConcert() {
        var concertStore = EventStore.forConcerts();
        Concert savedConcert = ConcertFactory.createConcert();
        concertStore.save(savedConcert);

        Optional<Concert> foundConcert = concertStore.findById(savedConcert.getId());

        assertThat(foundConcert)
                .get()
                .isNotSameAs(savedConcert);
    }

    @Test
    void eventStoreCanStoreCustomers() {
        var customerStore = EventStore.forCustomers();
        Customer savedCustomer = Customer.register(CustomerId.createRandom(), "name", "email@example.com");
        customerStore.save(savedCustomer);

        Optional<Customer> foundCustomer = customerStore.findById(savedCustomer.getId());

        assertThat(foundCustomer)
                .isPresent()
                .get()
                .isNotSameAs(savedCustomer);
    }

    @Test
    void eventStoreReturnsAllEventsAcrossAllSavedAggregatesInOrder() {
        var concertStore = EventStore.forConcerts();
        Concert originalConcert = ConcertFactory.createConcert();
        concertStore.save(originalConcert);
        Concert rescheduledConcert = concertStore.findById(originalConcert.getId()).orElseThrow();
        rescheduledConcert.rescheduleTo(LocalDateTime.now(), LocalTime.now().minusHours(1));
        concertStore.save(rescheduledConcert);

        Stream<ConcertEvent> concertEventStream = concertStore.allEvents();

        assertThat(concertEventStream)
                .hasExactlyElementsOfTypes(ConcertScheduled.class,
                                           ConcertRescheduled.class);
    }

    @Test
    void exactlyAllEventsForSpecifiedConcertAggregateAreReturned() {
        var concertStore = EventStore.forConcerts();
        ConcertId concertId = ConcertId.createRandom();
        Concert concert = Concert.schedule(concertId, "artist", 30, LocalDateTime.now(), LocalTime.now().minusHours(1), 100, 8);
        concert.rescheduleTo(LocalDateTime.now(), LocalTime.now().minusHours(1));
        concert.rescheduleTo(LocalDateTime.now().plusMonths(2), LocalTime.now().minusHours(1));
        concert.sellTicketsTo(CustomerId.createRandom(), 4);
        concert.sellTicketsTo(CustomerId.createRandom(), 2);
        concert.sellTicketsTo(CustomerId.createRandom(), 4);
        concert.sellTicketsTo(CustomerId.createRandom(), 1);
        List<ConcertEvent> allEventsForConcert = concert.uncommittedEvents();
        concertStore.save(concert);

        List<ConcertEvent> eventsForAggregate =
                concertStore.eventsForAggregate(concertId);

        assertThat(eventsForAggregate)
                .containsExactlyElementsOf(allEventsForConcert);
    }

    @Test
    void exactlyAllEventsForSpecifiedCustomerAggregateAreReturned() {
        var customerStore = EventStore.forCustomers();
        CustomerId customerId = CustomerId.createRandom();
        Customer customer = Customer.register(customerId, "customer name", "customer@example.com");
        Concert concert = ConcertFactory.createConcert();
        customer.purchaseTickets(concert, TicketOrderId.createRandom(), 4);
        customer.purchaseTickets(concert, TicketOrderId.createRandom(), 2);
        List<CustomerEvent> allEventsForCustomer = customer.uncommittedEvents();
        customerStore.save(customer);

        List<CustomerEvent> eventsForAggregate = customerStore.eventsForAggregate(customerId);

        assertThat(eventsForAggregate)
                .containsExactlyElementsOf(allEventsForCustomer);
    }

    @Test
    void savingEventsDirectlyStoresThemCorrectly() {
        var concertStore = EventStore.forConcerts();
        ConcertId concertId = ConcertId.createRandom();
        LocalDateTime originalShowDateTime = LocalDateTime.of(2025, 4, 22, 19, 0);
        LocalTime originalDoorsTime = LocalTime.of(18, 0);
        ConcertScheduled concertScheduled = new ConcertScheduled(
                concertId, 0L, "Headliner", 45,
                originalShowDateTime, originalDoorsTime,
                150, 8);
        TicketsSold ticketsSold = new TicketsSold(concertId, 0L, 4, 4 * 45);
        ConcertRescheduled concertRescheduled = new ConcertRescheduled(
                concertId, 0L, originalShowDateTime.plusMonths(2).plusHours(1),
                originalDoorsTime.plusHours(1));

        concertStore.save(concertId, List.of(concertScheduled, ticketsSold, concertRescheduled));

        assertThat(concertStore.eventsForAggregate(concertId))
                .containsExactly(concertScheduled, ticketsSold, concertRescheduled);
    }
}
