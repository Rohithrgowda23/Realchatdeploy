package com.realchat;

import com.realchat.domain.Notification;
import com.realchat.domain.NotificationPrefs;
import com.realchat.exception.ApiException;
import com.realchat.repository.NotificationPrefsRepository;
import com.realchat.repository.NotificationRepository;
import com.realchat.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @Mock NotificationRepository repo;
    @Mock NotificationPrefsRepository prefs;
    @Mock SimpMessagingTemplate ws;
    NotificationService service;

    @BeforeEach void setUp() { service = new NotificationService(repo, prefs, ws); }

    @Test void notificationIsSavedAndPushedToTheRecipientOnly() {
        when(prefs.findById(7L)).thenReturn(Optional.empty()); // defaults: everything on
        when(repo.save(any(Notification.class))).thenAnswer(i -> { Notification n = i.getArgument(0); n.setId(1L); return n; });
        service.notify(7L, "MENTION", "Ann mentioned you", "hi", 3L, 4L);
        verify(repo).save(argThat(n -> n.getUserId().equals(7L) && n.getConversationId().equals(3L)));
        verify(ws).convertAndSendToUser(eq("7"), eq("/queue/events"), any());
    }

    @Test void disabledPreferenceSuppressesThatTypeOnly() {
        NotificationPrefs p = new NotificationPrefs(); p.setUserId(7L); p.setMentions(false);
        when(prefs.findById(7L)).thenReturn(Optional.of(p));
        service.notify(7L, "MENTION", "x", null, 1L, 1L);
        verify(repo, never()).save(any());
        verifyNoInteractions(ws);
    }

    @Test void dismissingSomeoneElsesNotificationIsNotFound() {
        when(repo.deleteByIdAndUserId(5L, 7L)).thenReturn(0L);
        assertThrows(ApiException.class, () -> service.dismiss(7L, 5L));
        when(repo.deleteByIdAndUserId(6L, 7L)).thenReturn(1L);
        assertDoesNotThrow(() -> service.dismiss(7L, 6L));
    }

    @Test void markReadIsAlwaysScopedToTheCaller() {
        service.markRead(7L, List.of(1L, 2L));
        verify(repo).markRead(eq(7L), eq(List.of(1L, 2L)), any());
        service.markRead(7L, null);
        verify(repo).markAllRead(eq(7L), any());
        service.clear(7L);
        verify(repo).deleteByUserId(7L);
    }

    @Test void listAndUnreadCountUseTheCallersId() {
        when(repo.findByUserIdAndIdLessThanOrderByIdDesc(eq(7L), anyLong(), any())).thenReturn(List.of());
        when(repo.countByUserIdAndReadAtIsNull(7L)).thenReturn(3L);
        assertTrue(service.list(7L, null, 20, false).isEmpty());
        assertEquals(3L, service.unreadCount(7L));
    }
}