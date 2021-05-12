package com.github.cornerstonews.grizzly;

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.websocket.CloseReason;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@ServerEndpoint(value = "/websocket")
public class WebsocketServerEndpoint {

    private static final Logger LOG = LogManager.getLogger(WebsocketServerEndpoint.class);
    
    public WebsocketServerEndpoint() {}
    
    /**
     * This method is invoked once new connection is established.
     * After socket has been opened, it allows us to intercept the creation of a new session.
     * The session class allows us to send data to the user. In the method onOpen,
     * we'll let the client know that the handshake was successful.
     *
     * @param session
     * @param EndpointConfig config
     */
    @OnOpen
    public void onOpen(Session session, EndpointConfig config) {
        LOG.debug("Connection opened.");
    }
    
    /**
     * This method is invoked when the client closes a WebSocket connection.
     *
     * @param session
     * @return
     */
    @OnClose
    public void onClose(Session session, CloseReason reason) throws Exception {
        LOG.debug("Connection closed.");
    }
    
    /**
     * This method is invoked when an error is detected on the connection.
     *
     * @param session
     * @param t
     * @return
     */
    @OnError
    public void onError(Session session, Throwable t) {
        LOG.debug("Connection Error.");
    }
    
    /**
     * This method is invoked each time that the server receives a text WebSocket message.
     *
     * @param session
     * @param message
     * @return
     * @throws IOException
     */
    @OnMessage
    public void onMessage(Session session, String message, boolean isLast) {
        LOG.debug("String Message received: {}", message);
        try {
            session.getBasicRemote().sendText("Server received message '" + message + "'");
        } catch (IOException e) {
            LOG.error("Error sending message to client: {}", e.getMessage(), e);
        }
    }
    
    /**
     * This method is invoked each time that the server receives a binary WebSocket message.
     *
     * @param session
     * @param message
     * @return
     * @throws IOException
     */
    @OnMessage
    public void onMessage(Session session, ByteBuffer message, boolean isLast) {
        LOG.debug("Binary Message received.");
    }
    
}
