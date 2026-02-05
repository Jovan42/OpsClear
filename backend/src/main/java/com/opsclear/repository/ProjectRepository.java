package com.opsclear.repository;

import com.opsclear.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Optional<Project> findByIdAndDeletedAtIsNull(UUID id);

    List<Project> findByOwnerIdAndDeletedAtIsNull(UUID ownerId);

    boolean existsByNameAndOwnerIdAndDeletedAtIsNull(String name, UUID ownerId);
}
