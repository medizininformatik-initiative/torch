package de.medizininformatikinitiative.torch.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Component
public class ServerStartupListener implements ApplicationListener<WebServerInitializedEvent> {

    private static final Logger logger = LoggerFactory.getLogger(ServerStartupListener.class);

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        int port = event.getWebServer().getPort();
        String ipAddress = "Unknown";
        try {
            InetAddress ip = InetAddress.getLocalHost();
            ipAddress = ip.getHostAddress();
        } catch (UnknownHostException e) {
            logger.error("Unable to get host address", e);
        }
        logger.info("Server is running on IP: {}, Port: {}", ipAddress, port);
    }
}
