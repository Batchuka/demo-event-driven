package com.demo.orchestrator;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.demo.eventbridge.EventEnvelope;

/** Entry point of the events sent by the event-bridge library of the other applications. */
@RestController
public class EventController {

    private final ImportWorkflow workflow;

    public EventController(ImportWorkflow workflow) {
        this.workflow = workflow;
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void receive(@RequestBody EventEnvelope event) {
        workflow.onEvent(event);
    }
}
