package com.twojo.approval.repository;

import com.twojo.approval.entity.CustomerInquiry;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 고객 문의 — 기록만 (Q-20). 조회 API가 없어(Q-42) 커스텀 메서드도 없다. */
public interface CustomerInquiryRepository extends JpaRepository<CustomerInquiry, UUID> {
}
