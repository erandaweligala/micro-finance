package com.mfin.common.web;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Stable pagination envelope.
 *
 * <p>Spring's {@code Page} serialises its internal structure, which then becomes an accidental
 * part of the public contract and changes between Spring versions. This record is the contract
 * the mobile app codes against.</p>
 */
@Schema(name = "PageResponse", description = "A page of results")
public record PageResponse<T>(
        @Schema(description = "Items on this page") List<T> content,
        @Schema(description = "Zero-based page index", example = "0") int page,
        @Schema(description = "Requested page size", example = "20") int size,
        @Schema(description = "Total matching records", example = "137") long totalElements,
        @Schema(description = "Total number of pages", example = "7") int totalPages,
        @Schema(description = "True when this is the last page") boolean last
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    /** Maps entities to DTOs without materialising an intermediate page. */
    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isLast());
    }
}
