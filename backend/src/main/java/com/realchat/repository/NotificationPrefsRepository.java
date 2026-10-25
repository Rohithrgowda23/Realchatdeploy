package com.realchat.repository;

import com.realchat.domain.NotificationPrefs;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPrefsRepository extends JpaRepository<NotificationPrefs, Long> {}