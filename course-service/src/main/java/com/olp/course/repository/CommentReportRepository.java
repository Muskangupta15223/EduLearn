package com.olp.course.repository;

import com.olp.course.model.CommentReport;
import com.olp.course.model.ReportStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentReportRepository extends JpaRepository<CommentReport, Long> {
    List<CommentReport> findByStatusOrderByCreatedAtDesc(ReportStatus status);
    List<CommentReport> findAllByOrderByCreatedAtDesc();
}
