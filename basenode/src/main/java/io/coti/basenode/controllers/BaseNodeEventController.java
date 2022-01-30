package io.coti.basenode.controllers;

import io.coti.basenode.data.Event;
import io.coti.basenode.http.interfaces.IResponse;
import io.coti.basenode.services.interfaces.IEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/event")
public class BaseNodeEventController {

    @Autowired
    IEventService eventService;

    @GetMapping(path = "/multi-currency/confirmed")
    public ResponseEntity<IResponse> getConfirmedMultiCurrencyEvent() {
        return eventService.getConfirmedEventTransactionDataResponse(Event.MULTI_CURRENCY);
    }

    @GetMapping(path = "/multi-currency")
    public ResponseEntity<IResponse> getMultiCurrencyEventTransactionData() {
        return eventService.getEventTransactionDataResponse(Event.MULTI_CURRENCY);
    }
}