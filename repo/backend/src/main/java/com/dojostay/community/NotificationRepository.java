package com.dojostay.community;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientUserIdOrderByIdDesc(Long recipientUserId);

    long countByRecipientUserIdAndReadAtIsNull(Long recipientUserId);
}
