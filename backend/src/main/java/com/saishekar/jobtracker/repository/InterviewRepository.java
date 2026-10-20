package com.saishekar.jobtracker.repository;

import com.saishekar.jobtracker.model.Interview;
import com.saishekar.jobtracker.model.JobApplication;
import com.saishekar.jobtracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InterviewRepository extends JpaRepository<Interview, Long> {

    List<Interview> findByApplicationOrderByScheduledAtAsc(JobApplication application);

    Optional<Interview> findByIdAndApplication(Long id, JobApplication application);

    /**
     * Interviews owned by one user inside a time window, soonest first.
     * The owner is matched through the parent application, which is the
     * record that carries ownership; interviews have no owner column of
     * their own. Only SCHEDULED rounds qualify: a round that already
     * happened, was cancelled, or ended in a no-show is not a reminder.
     * The application is fetched with the interview so callers can read
     * company and title without a second query per row.
     */
    @Query("""
            SELECT i FROM Interview i
            JOIN FETCH i.application a
            WHERE a.owner = :owner
              AND i.outcome = :outcome
              AND i.scheduledAt >= :from
              AND i.scheduledAt <= :to
            ORDER BY i.scheduledAt ASC, i.id ASC
            """)
    List<Interview> findUpcoming(@Param("owner") User owner,
                                 @Param("outcome") Interview.Outcome outcome,
                                 @Param("from") Instant from,
                                 @Param("to") Instant to);

    /** Every future SCHEDULED interview for one user, soonest first. */
    @Query("""
            SELECT i FROM Interview i
            JOIN FETCH i.application a
            WHERE a.owner = :owner
              AND i.outcome = :outcome
              AND i.scheduledAt >= :from
            ORDER BY i.scheduledAt ASC, i.id ASC
            """)
    List<Interview> findAllUpcoming(@Param("owner") User owner,
                                    @Param("outcome") Interview.Outcome outcome,
                                    @Param("from") Instant from);
}
