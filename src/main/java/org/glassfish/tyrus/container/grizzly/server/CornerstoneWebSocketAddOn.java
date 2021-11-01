package org.glassfish.tyrus.container.grizzly.server;

import org.glassfish.tyrus.spi.ServerContainer;

public class CornerstoneWebSocketAddOn extends WebSocketAddOn {

    public CornerstoneWebSocketAddOn(ServerContainer serverContainer, String contextPath) {
        super(serverContainer, contextPath);
    }

}
