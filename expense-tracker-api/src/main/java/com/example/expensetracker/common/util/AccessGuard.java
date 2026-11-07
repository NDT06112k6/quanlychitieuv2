package com.example.expensetracker.common.util;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;

@Slf4j
public final class AccessGuard {

    private static final int EXPECTED_WRITE_COUNT = 1;
    private static final String ACCESS_DENIED = "Resource is unavailable for this user";

    private AccessGuard() {
    }

    public static <T> T requireOwned(Optional<T> resource) {
        if (resource.isEmpty()) {
            throw denied();
        }
        T ownedResource = resource.get();
        return ownedResource;
    }

    public static void requireSuccessfulWrite(int affectedRows) {
        if (affectedRows != EXPECTED_WRITE_COUNT) {
            throw denied();
        }
    }

    private static AccessDeniedException denied() {
        log.warn("Rejected access to an unavailable or unowned resource.");
        AccessDeniedException exception = new AccessDeniedException(ACCESS_DENIED);
        return exception;
    }
}
