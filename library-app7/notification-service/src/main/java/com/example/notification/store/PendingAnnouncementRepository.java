package com.example.notification.store;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PendingAnnouncementRepository extends JpaRepository<PendingAnnouncement, Long> {

    boolean existsByBookId(Long bookId);

    List<PendingAnnouncement> findByAnnouncedAtIsNullOrderByIdAsc(Pageable pageable);
}
