package com.jobbeacon.notification.web;

import java.util.List;
import org.springframework.data.domain.Page;

/** A page of items; {@code page} is 1-based like the backend's paging. */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber() + 1, page.getSize(),
            page.getTotalElements(), page.getTotalPages());
    }
}
