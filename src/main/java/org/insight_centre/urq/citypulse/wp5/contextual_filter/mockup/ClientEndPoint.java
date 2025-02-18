package org.insight_centre.urq.citypulse.wp5.contextual_filter.mockup;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

/**
 * @author Thu-Le Pham
 *
 *         This class to communicate with Query Component
 */

@ClientEndpoint
public class ClientEndPoint {

	public static Logger logger = Logger.getLogger(ClientEndPoint.class
			.getPackage().getName());
	Session userSession = null;
    private MessageHandler messageHandler;
    private static CountDownLatch latch = new CountDownLatch(1);


    public ClientEndPoint(URI endpointURI) {
        try {
            final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(this, endpointURI);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }



    public ClientEndPoint() {
		super();
	}



	/**
     * Callback hook for Connection open events.
     *
     * @param userSession the userSession which is opened.
     */
    @OnOpen
    public void onOpen(Session userSession) {
        logger.info("Opening websocket. Id = " + userSession.getId());
        this.userSession = userSession;
    }

    /**
     * Callback hook for Connection close events.
     *
     * @param userSession the userSession which is getting closed.
     * @param reason the reason for connection close
     */
    @OnClose
    public void onClose(Session userSession, CloseReason reason) {
        logger.info("closing websocket. Id = " + userSession.getId());
        this.userSession = null;
    }

    /**
     * Callback hook for Message Events. This method will be invoked when a client send a message.
     *
     * @param message The text message
     */
    @OnMessage
    public void onMessage(String message) {
        if (this.messageHandler != null) {
            this.messageHandler.handleMessage(message);
        }
    }

    /**
     * register message handler
     *
     * @param message
     */
    public void addMessageHandler(MessageHandler msgHandler) {
        this.messageHandler = msgHandler;
    }

    /**
     * Send a message.
     *
     * @param user
     * @param message
     */
    public void sendMessage(String message) {
        this.userSession.getAsyncRemote().sendText(message);
    }

    /**
     * Message handler.
     *
     * @author Jiji_Sasidharan
     */
    public static interface MessageHandler {
        public void handleMessage(String message);
    }

	public Session getUserSession() {
		return userSession;
	}

	public void setUserSession(Session userSession) {
		this.userSession = userSession;
	}

	public static CountDownLatch getLatch() {
		return latch;
	}

	public static void setLatch(CountDownLatch latch) {
		ClientEndPoint.latch = latch;
	}





}
