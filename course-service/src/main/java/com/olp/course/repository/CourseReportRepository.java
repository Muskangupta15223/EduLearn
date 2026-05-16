package com.olp.course.repository;

import com.olp.course.model.CourseReport;
import com.olp.course.model.ReportStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseReportRepository extends JpaRepository<CourseReport, Long> {
    List<CourseReport> findByStatusOrderByCreatedAtDesc(ReportStatus status);
    List<CourseReport> findAllByOrderByCreatedAtDesc();
}
