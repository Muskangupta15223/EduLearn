package com.olp.course.service;

import com.olp.course.model.CommentReport;
import com.olp.course.model.Course;
import com.olp.course.model.CourseReport;
import com.olp.course.model.ModerationAction;
import com.olp.course.model.ReportStatus;
import com.olp.course.repository.CommentReportRepository;
import com.olp.course.repository.CourseReportRepository;
import com.olp.course.repository.CourseRepository;
import com.olp.course.repository.ModerationActionRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ModerationService {
    private final CourseReportRepository courseReportRepository;
    private final CommentReportRepository commentReportRepository;
    private final ModerationActionRepository moderationActionRepository;
    private final CourseRepository courseRepository;
    private final AccessControlService accessControlService;

    public ModerationService(
            CourseReportRepository courseReportRepository,
            CommentReportRepository commentReportRepository,
            ModerationActionRepository moderationActionRepository,
            CourseRepository courseRepository,
            AccessControlService accessControlService) {
        this.courseReportRepository = courseReportRepository;
        this.commentReportRepository = commentReportRepository;
        this.moderationActionRepository = moderationActionRepository;
        this.courseRepository = courseRepository;
        this.accessControlService = accessControlService;
    }

    public CourseReport reportCourse(Long courseId, CourseReport report, Long reporterId) {
        if (!courseRepository.existsById(courseId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");
        }
        report.setCourseId(courseId);
        report.setReporterId(reporterId);
        report.setStatus(ReportStatus.OPEN);
        return courseReportRepository.save(report);
    }

    public CommentReport reportComment(Long courseId, CommentReport report, Long reporterId) {
        report.setCourseId(courseId);
        report.setReporterId(reporterId);
        report.setStatus(ReportStatus.OPEN);
        return commentReportRepository.save(report);
    }

    public List<CourseReport> getCourseReports(ReportStatus status, String role) {
        requireAdmin(role);
        return status == null
                ? courseReportRepository.findAllByOrderByCreatedAtDesc()
                : courseReportRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    public List<CommentReport> getCommentReports(ReportStatus status, String role) {
        requireAdmin(role);
        return status == null
                ? commentReportRepository.findAllByOrderByCreatedAtDesc()
                : commentReportRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    public CourseReport reviewCourseReport(Long reportId, ReportStatus status, String comment, Long reviewerId, String role) {
        requireAdmin(role);
        return courseReportRepository.findById(reportId).map(report -> {
            report.setStatus(status == null ? ReportStatus.IN_REVIEW : status);
            report.setReviewComment(comment);
            report.setReviewedBy(reviewerId);
            report.setReviewedAt(LocalDateTime.now());
            return courseReportRepository.save(report);
        }).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
    }

    public CommentReport reviewCommentReport(Long reportId, ReportStatus status, String comment, Long reviewerId, String role) {
        requireAdmin(role);
        return commentReportRepository.findById(reportId).map(report -> {
            report.setStatus(status == null ? ReportStatus.IN_REVIEW : status);
            report.setReviewComment(comment);
            report.setReviewedBy(reviewerId);
            report.setReviewedAt(LocalDateTime.now());
            return commentReportRepository.save(report);
        }).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
    }

    public ModerationAction createAction(ModerationAction action, Long moderatorId, String role) {
        requireAdmin(role);
        action.setModeratorId(moderatorId);
        ModerationAction saved = moderationActionRepository.save(action);
        if ("COURSE".equalsIgnoreCase(saved.getTargetType()) && "SUSPEND".equalsIgnoreCase(saved.getAction())) {
            courseRepository.findById(saved.getTargetId()).ifPresent(course -> suspendCourse(course, saved.getReason(), moderatorId));
        }
        return saved;
    }

    private void suspendCourse(Course course, String reason, Long reviewerId) {
        course.setStatus("SUSPENDED");
        course.setReviewStatus("SUSPENDED");
        course.setReviewComment(reason == null || reason.isBlank() ? "Suspended by moderation" : reason);
        course.setReviewedBy(reviewerId);
        course.setReviewedAt(LocalDateTime.now());
        courseRepository.save(course);
    }

    private void requireAdmin(String role) {
        if (!accessControlService.isAdmin(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }
}
