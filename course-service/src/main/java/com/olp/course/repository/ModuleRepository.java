package com.olp.course.repository;

import com.olp.course.model.Module;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ModuleRepository extends JpaRepository<Module, Long> {
    List<Module> findByCourseIdOrderByModuleOrderAsc(Long courseId);
    List<Module> findByCourseIdInOrderByCourseIdAscModuleOrderAsc(List<Long> courseIds);
}
