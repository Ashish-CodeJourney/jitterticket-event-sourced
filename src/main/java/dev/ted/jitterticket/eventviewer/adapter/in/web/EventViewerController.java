package dev.ted.jitterticket.eventviewer.adapter.in.web;

import dev.ted.jitterticket.eventsourced.application.ConcertSummaryProjector;
import dev.ted.jitterticket.eventsourced.application.EventStore;
import dev.ted.jitterticket.eventsourced.domain.concert.Concert;
import dev.ted.jitterticket.eventsourced.domain.concert.ConcertEvent;
import dev.ted.jitterticket.eventsourced.domain.concert.ConcertId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/event-viewer")
public class EventViewerController {

    private final ConcertSummaryProjector concertSummaryProjector;
    private final EventStore<ConcertId, ConcertEvent, Concert> concertStore;

    @Autowired
    public EventViewerController(ConcertSummaryProjector concertSummaryProjector, EventStore<ConcertId, ConcertEvent, Concert> concertStore) {
        this.concertSummaryProjector = concertSummaryProjector;
        this.concertStore = concertStore;
    }

    @GetMapping
    public String listConcerts(Model model) {
        List<ConcertListView> concertListViews =
                concertSummaryProjector.allConcertSummaries()
                                       .map(ConcertListView::from)
                                       .toList();
        model.addAttribute("concerts", concertListViews);
        return "event-viewer/concert-aggregates";
    }

    @GetMapping("/{concertId}")
    public String showConcertEvents(@PathVariable("concertId") String concertIdString,
                                    @RequestParam(value = "selectedIndex", required = false, defaultValue = "0") int selectedIndex,
                                    Model model) {
        model.addAttribute("concertId", concertIdString);
        model.addAttribute("selectedIndex", selectedIndex);
        ConcertId concertId = new ConcertId(UUID.fromString(concertIdString));
        ConcertSummaryProjector.ConcertWithEvents concertWithEvents = concertSummaryProjector.concertWithEventsFor(concertId);
        model.addAttribute("events", concertWithEvents.concertEvents().reversed());
        Concert concert = concertWithEvents.concert();
        model.addAttribute("projectedState",
                           List.of(
                                   "Artist: " + concert.artist(),
                                   "Show Time: " + concert.showDateTime(),
                                   "Doors Time: " + concert.doorsTime(),
                                   "Tickets Remaining: " + concert.availableTicketCount()
                           ));
        return "event-viewer/concert-events";
    }

}
