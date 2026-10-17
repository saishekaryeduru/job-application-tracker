package com.saishekar.jobtracker.repository;

import com.saishekar.jobtracker.model.Interview;
import com.saishekar.jobtracker.model.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InterviewRepository extends JpaRepository<Interview, Long> {

    List<Interview> findByApplicationOrderByScheduledAtAsc(JobApplication application);

    Optional<Interview> findByIdAndApplication(Long id, JobApplication application);
}
