package com.olp.course.dto;

import com.olp.course.model.Course;
import java.util.List;
import java.util.stream.Collectors;

public class CourseMapper {
    private CourseMapper() {}

    public static CourseDto toDto(Course course) {
        if (course == null) return null;
        
        return new CourseDto(
            course.getId(),
            course.getTitle(),
            course.getDescription(),
            course.getPrice(),
            course.getInstructorId(),
            course.getInstructorName(),
            course.getCategory(),
            course.getLevel(),
            course.getLanguage(),
            course.getStatus(),
            course.getReviewStatus(),
            course.getReviewComment(),
            course.getThumbnail(),
            course.getRating(),
            course.getStudentsCount(),
            course.getDuration(),
            course.getInstructorVerified(),
            course.getInstructorVerificationStatus(),
            course.getCreatedAt(),
            course.getUpdatedAt()
        );
    }

    public static List<CourseDto> toDtoList(List<Course> courses) {
        if (courses == null) return List.of();
        return courses.stream().map(CourseMapper::toDto).collect(Collectors.toList());
    }
}
