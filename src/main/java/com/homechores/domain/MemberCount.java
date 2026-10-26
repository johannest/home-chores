package com.homechores.domain;

/** One member's all-time approved-completion count, grouped in the database (see
 *  {@code CompletionRepository.approvedCountsByMember}). */
public record MemberCount(Long memberId, Long count) {
}
