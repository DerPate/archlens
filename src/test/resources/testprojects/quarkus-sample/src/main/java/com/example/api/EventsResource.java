package com.example.api;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

@Path("/events")
public class EventsResource {

    @GET
    @Path("/stream")
    @Produces("text/event-stream")
    public String stream() {
        return null;
    }
}
