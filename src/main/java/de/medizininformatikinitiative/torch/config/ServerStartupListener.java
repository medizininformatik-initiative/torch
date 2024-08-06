package de.medizininformatikinitiative.torch.config;

import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class ServerStartupListener implements ApplicationListener<WebServerInitializedEvent> {

    private static final Logger logger = Logger.getLogger(ServerStartupListener.class.getName());

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        int port = event.getWebServer().getPort();
        String ipAddress = "Unknown";

        try {
            InetAddress ip = InetAddress.getLocalHost();
            ipAddress = ip.getHostAddress();
        } catch (UnknownHostException e) {
            logger.log(Level.SEVERE, "Unable to get host address", e);
        }

        logger.info("Server is running on IP: " + ipAddress + ", Port: " + port);
    }
}
