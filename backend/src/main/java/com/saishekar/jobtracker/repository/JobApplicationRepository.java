package com.saishekar.jobtracker.repository;

import com.saishekar.jobtracker.model.ApplicationStatus;
import com.saishekar.jobtracker.model.JobApplication;
import com.saishekar.jobtracker.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    /**
     * Owner-scoped search for the dashboard. A null status or query disables
     * that filter; the query matches company, title, or location as a
     * case-insensitive substring.
     */
    @Query("""
            select a from JobApplication a
            where a.owner = :owner
              and (:status is null or a.status = :status)
              and (:query is null
                   or lower(a.company) like lower(concat('%', :query, '%'))
                   or lower(a.title) like lower(concat('%', :query, '%'))
                   or lower(coalesce(a.location, '')) like lower(concat('%', :query, '%')))
            """)
    Page<JobApplication> search(@Param("owner") User owner,
                                @Param("status") ApplicationStatus status,
                                @Param("query") String query,
                                Pageable pageable);

    Optional<JobApplication> findByIdAndOwner(Long id, User owner);

    /** Every application the owner has, in the dashboard's stable order — used by CSV export. */
    List<JobApplication> findAllByOwnerOrderByAppliedDateDescIdDesc(User owner);

    /**
     * Applications in one of the given statuses that have not been updated
     * since the cutoff, most stale first — the stale-reminder query.
     */
    List<JobApplication> findByOwnerAndStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            User owner, Collection<ApplicationStatus> statuses, Instant cutoff);

    long countByOwnerAndStatus(User owner, ApplicationStatus status);

    long countByOwnerAndAppliedDateBetween(User owner, LocalDate from, LocalDate to);
}
