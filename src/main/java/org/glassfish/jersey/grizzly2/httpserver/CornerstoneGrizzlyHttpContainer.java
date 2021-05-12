package org.glassfish.jersey.grizzly2.httpserver;

import org.glassfish.jersey.server.ResourceConfig;

public class CornerstoneGrizzlyHttpContainer {

    public static GrizzlyHttpContainer getGrizzlyHttpContainer(ResourceConfig jerseyWebserviceApplication) {
        return new GrizzlyHttpContainer(jerseyWebserviceApplication);
    }
    
}
