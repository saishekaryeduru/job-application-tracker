package com.saishekar.jobtracker.repository;

import com.saishekar.jobtracker.model.ApplicationStatus;
import com.saishekar.jobtracker.model.JobApplication;
import com.saishekar.jobtracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    List<JobApplication> findByOwnerOrderByAppliedDateDesc(User owner);

    List<JobApplication> findByOwnerAndStatusOrderByAppliedDateDesc(User owner, ApplicationStatus status);

    Optional<JobApplication> findByIdAndOwner(Long id, User owner);

    long countByOwnerAndStatus(User owner, ApplicationStatus status);

    long countByOwnerAndAppliedDateBetween(User owner, LocalDate from, LocalDate to);

    List<JobApplication> findByOwnerAndAppliedDateBetween(User owner, LocalDate from, LocalDate to);
}
