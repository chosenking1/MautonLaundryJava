package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.AppUser;
import com.work.mautonlaundry.data.model.SupportTicket;
import com.work.mautonlaundry.data.model.enums.SupportTicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, String> {

    /** A customer's own tickets, most recently active first. */
    List<SupportTicket> findByUserOrderByLastMessageAtDesc(AppUser user);

    /** The staff queue. Oldest activity first: the longest-waiting is the most overdue. */
    Page<SupportTicket> findByStatusInOrderByLastMessageAtAsc(
            List<SupportTicketStatus> statuses, Pageable pageable);

    Page<SupportTicket> findAllByOrderByLastMessageAtDesc(Pageable pageable);

    long countByStatusIn(List<SupportTicketStatus> statuses);
}
