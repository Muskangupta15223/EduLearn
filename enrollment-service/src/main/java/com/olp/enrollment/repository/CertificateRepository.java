package com.olp.enrollment.repository;

import com.olp.enrollment.model.Certificate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {
    Optional<Certificate> findByUserIdAndCourseId(Long userId, Long courseId);
    Optional<Certificate> findByVerificationCode(String verificationCode);
    java.util.List<Certificate> findAllByOrderByIssuedAtDesc();
}
