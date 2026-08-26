package com.twojo.global.response;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 목록 공통 응답 (docs/08-dto.md §0 · Q-39).
 * <p>요청 파라미터 표준: {@code ?page=0&size=20} — 0-base · 기본 20 · 최대 100(초과 시 절삭).
 * 정렬은 엔드포인트별 기본값 고정, 클라이언트 지정 없음.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
