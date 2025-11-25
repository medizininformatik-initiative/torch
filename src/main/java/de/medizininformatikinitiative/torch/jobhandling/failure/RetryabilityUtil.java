package de.medizininformatikinitiative.torch.jobhandling.failure;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.PrematureCloseException;

import java.io.IOException;

public final class RetryabilityUtil {

    private RetryabilityUtil() {
    }

    public static boolean isRetryable(Throwable e) {
        Throwable cause = rootCause(e);

        if (isRetryableTransportCause(cause)) {
            return true;
        }

        if (e instanceof WebClientResponseException wcre) {
            return shouldRetry(wcre.getStatusCode());
        }

        return false;
    }

    public static boolean shouldRetry(HttpStatusCode code) {
        return code.is5xxServerError() || code.value() == 404 || code.value() == 429;
    }

    private static boolean isRetryableTransportCause(Throwable cause) {
        if (cause instanceof PrematureCloseException) return true;
        if (cause instanceof IOException) return true;

        String msg = (cause.getMessage() == null ? "" : cause.getMessage()).toLowerCase();
        return msg.contains("premature")
                || msg.contains("connection reset")
                || msg.contains("broken pipe")
                || msg.contains("closed channel");
    }

    public static Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
    }

    public static String rootCauseMessage(Throwable t) {
        Throwable rc = rootCause(t);
        String name = rc.getClass().getSimpleName();
        String msg = rc.getMessage();
        return msg == null ? name : (name + ": " + msg);
    }
}
