package com.mastek.pulse.common;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: UserRepository.java
 * Journey: Shared Identity across all Mastek Pulse experiences.
 * Why it is designed this way:
 *   Spring Data JPA repository providing fast lookup of employee personas and
 *   their explicit interests, roles, and host availability status.
 * ============================================================================
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, String> {
}
