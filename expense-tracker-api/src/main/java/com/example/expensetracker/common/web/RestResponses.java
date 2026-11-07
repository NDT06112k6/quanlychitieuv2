package com.example.expensetracker.common.web;

import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

public final class RestResponses {

    private RestResponses() {
    }

    public static <T> ResponseEntity<T> created(String collectionPath, Long resourceId, T body) {
        URI location = UriComponentsBuilder.fromPath(collectionPath)
                .pathSegment(resourceId.toString())
                .build()
                .toUri();
        ResponseEntity<T> response = ResponseEntity.created(location).body(body);
        return response;
    }
}
