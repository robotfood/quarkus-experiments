package org.acme.websockets;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Gauge;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.Session;

import jakarta.enterprise.event.Observes;

import java.util.concurrent.TimeUnit;

import org.acme.websockets.ChatSocket;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

//https://quarkus.io/guides/websockets
//https://quarkus.io/guides/smallrye-metrics#quarkus-smallrye-metrics_quarkus.smallrye-metrics.jaxrs.enabled

// https://quarkus.io/guides/lifecycle#listening-for-startup-and-shutdown-events

@ServerEndpoint("/chat/{username}")         
@ApplicationScoped
public class ChatSocket {

    private final static int SIGTERM_SLEEP_WAIT_MS = 5000;

    private Map<String, Session> sessions = new ConcurrentHashMap<>(); 

    private int activeWebsockets = 0;

    @Gauge(name = "activeWebsockets", unit = MetricUnits.NONE, description = "The total number of active websockets.")
    public int highestPrimeNumberSoFar() {
        return activeWebsockets;
    }
    
    @OnOpen
    @Counted(name = "websocketsOpenedTotal", description = "A count of the total number of websocket connections opened.")
    public void onOpen(Session session, @PathParam("username") String username) {
        broadcast("User " + username + " joined");
        System.out.println("User " + username + " joined");
        sessions.put(username, session);
        activeWebsockets++;
    }

    @OnClose
    @Counted(name = "websocketsClosedTotal", description = "A count of the total number of websocket connections closed.")
    public void onClose(Session session, CloseReason reason, @PathParam("username") String username) {
        System.out.println("CloseReason " + reason.getReasonPhrase());
        sessions.remove(username);
        activeWebsockets--;
        broadcast("User " + username + " left");
        System.out.println("User " + username + " left");
    }

    @OnError
    public void onError(Session session, @PathParam("username") String username, Throwable throwable) {
        sessions.remove(username);
        activeWebsockets--;
        broadcast("User " + username + " left on error: " + throwable);
        System.out.println("User " + username + " left on error: " + throwable);

    }

    @OnMessage
    public void onMessage(String message, @PathParam("username") String username) {
        broadcast(">> " + username + ": " + message);
        System.out.println(">> " + username + ": " + message);
    }

    private void broadcast(String message) {
        sessions.values().forEach(s -> {
            s.getAsyncRemote().sendObject(message, result ->  {
                if (result.getException() != null) {
                    System.out.println("Unable to send message: " + result.getException());
                }
            });
        });
    }

    void onStart(@Observes StartupEvent ev) {               
        System.out.println("The application is starting...");
    }

    void onStop(@Observes ShutdownEvent ev) throws InterruptedException {               
        System.out.println("The application is stopping...");
        System.out.println("activeSessions:" + sessions.size());

        // Wait for active sessions to close or for a timeout
        long timeoutMillis = TimeUnit.SECONDS.toMillis(30000);
        long endTime = System.currentTimeMillis() + timeoutMillis;

        while (!sessions.isEmpty() && System.currentTimeMillis() < endTime) {
            try {
                System.out.println("Waiting " + SIGTERM_SLEEP_WAIT_MS + " for websockets to close...");
                Thread.sleep(SIGTERM_SLEEP_WAIT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}