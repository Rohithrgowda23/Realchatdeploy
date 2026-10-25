package com.realchat;

import com.realchat.domain.*;
import com.realchat.exception.ApiException;
import com.realchat.repository.*;
import com.realchat.service.FileStorage;
import com.realchat.service.RateLimitService;
import com.realchat.service.StoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoryServiceTest {
    @Mock StoryRepository stories;
    @Mock StoryViewRepository views;
    @Mock StorySettingsRepository settings;
    @Mock StoryHiddenRepository hiddenRepo;
    @Mock BlockRepository blocks;
    @Mock ConversationRepository convs;
    @Mock UserRepository users;
    @Mock FileStorage storage;
    @Mock RateLimitService limiter;
    StoryService service;

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
    private static final byte[] MP4 = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 0, 0};

    @BeforeEach void setUp() { service = new StoryService(stories, views, settings, hiddenRepo, blocks, convs, users, storage, limiter); }

    private void contact(Long a, Long b) { when(convs.findByDirectKey(Math.min(a, b) + "_" + Math.max(a, b))).thenReturn(Optional.of(new Conversation())); }
    private void audience(Long author, String aud) { StorySettings s = new StorySettings(); s.setUserId(author); s.setAudience(aud); when(settings.findById(author)).thenReturn(Optional.of(s)); }
    private static Story story(long id, long author, Instant expires, String stored) {
        Story s = new Story(); s.setId(id); s.setAuthorId(author); s.setType(stored == null ? "TEXT" : "IMAGE"); s.setExpiresAt(expires); s.setStoredName(stored); s.setMimeType("image/png"); s.setCreatedAt(Instant.now()); return s;
    }

    // ---- who can see
    @Test void authorAlwaysSeesOwnStories() { assertTrue(service.canView(1L, 1L)); }
    @Test void contactsSeeStoriesByDefault() { contact(1L, 2L); assertTrue(service.canView(1L, 2L)); }
    @Test void strangersDoNotSeeStories() { assertFalse(service.canView(1L, 2L)); }
    @Test void blockedEitherWayCannotSee() {
        when(blocks.existsByBlockerIdAndBlockedId(1L, 2L)).thenReturn(false);
        when(blocks.existsByBlockerIdAndBlockedId(2L, 1L)).thenReturn(true);
        assertFalse(service.canView(1L, 2L));
    }
    @Test void hiddenFromListOverridesContactStatus() {
        when(hiddenRepo.existsByUserIdAndHiddenUserId(2L, 1L)).thenReturn(true);
        assertFalse(service.canView(1L, 2L));
        verify(convs, never()).findByDirectKey(any());
    }
    @Test void nobodyAudienceHidesEverythingFromEveryone() { audience(2L, "NOBODY"); assertFalse(service.canView(1L, 2L)); }
    @Test void groupmatesOnlySeeWhenTheAuthorAllowsGroups() {
        when(stories.sharedGroups(1L, 2L)).thenReturn(1L);
        assertFalse(service.canView(1L, 2L)); // default CONTACTS: groupmate is not enough
        audience(2L, "CONTACTS_AND_GROUPS");
        assertTrue(service.canView(1L, 2L));
    }

    // ---- creating
    @Test void textStoryNeedsTextAndValidColour() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class, () -> service.create(1L, "TEXT", "  ", null, null, null)).getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class, () -> service.create(1L, "TEXT", "hi", "red;}", null, null)).getStatus());
        verify(stories, never()).save(any());
    }
    @Test void lifetimeIsLimited() {
        assertThrows(ApiException.class, () -> service.create(1L, "TEXT", "hi", null, 0, null));
        assertThrows(ApiException.class, () -> service.create(1L, "TEXT", "hi", null, 49, null));
    }
    @Test void expiryComesFromTheServerClock() {
        when(stories.save(any(Story.class))).thenAnswer(i -> { Story s = i.getArgument(0); s.setId(9L); return s; });
        Instant before = Instant.now();
        var dto = service.create(1L, "text", "hello", "#112233", null, null);
        assertTrue(Duration.between(before, dto.expiresAt()).toHours() >= 23 && Duration.between(before, dto.expiresAt()).toHours() <= 24); // default 24 h
    }
    @Test void imageStoryRejectsDisguisedHtmlAndStoresNothing() {
        var f = new MockMultipartFile("file", "a.png", "image/png", "<html>x</html>".getBytes());
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, assertThrows(ApiException.class, () -> service.create(1L, "IMAGE", null, null, null, f)).getStatus());
        verifyNoInteractions(storage);
    }
    @Test void typeMustMatchTheFileContent() {
        var png = new MockMultipartFile("file", "a.png", "video/mp4", PNG);
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, assertThrows(ApiException.class, () -> service.create(1L, "VIDEO", null, null, null, png)).getStatus());
        var mp4 = new MockMultipartFile("file", "a.mp4", "image/png", MP4);
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, assertThrows(ApiException.class, () -> service.create(1L, "IMAGE", null, null, null, mp4)).getStatus());
        verifyNoInteractions(storage);
    }
    @Test void validImageIsStoredOnDisk() {
        when(storage.store(any())).thenReturn("stored-uuid");
        when(stories.save(any(Story.class))).thenAnswer(i -> { Story s = i.getArgument(0); s.setId(9L); return s; });
        var dto = service.create(1L, "IMAGE", " caption ", null, 6, new MockMultipartFile("file", "a.png", "application/octet-stream", PNG));
        assertEquals("image/png", dto.mimeType());
        verify(storage).store(any());
    }
    @Test void tooManyActiveStoriesRejected() {
        when(stories.countByAuthorIdAndExpiresAtAfter(eq(1L), any())).thenReturn(30L);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, () -> service.create(1L, "TEXT", "hi", null, null, null)).getStatus());
    }
    @Test void failedSaveRemovesTheUploadedFile() {
        when(storage.store(any())).thenReturn("stored-uuid");
        when(stories.save(any(Story.class))).thenThrow(new RuntimeException("db"));
        assertThrows(RuntimeException.class, () -> service.create(1L, "IMAGE", null, null, null, new MockMultipartFile("file", "a.png", "image/png", PNG)));
        verify(storage).deleteQuietly("stored-uuid");
    }

    // ---- reading media, views, viewers, deleting
    @Test void expiredStoryMediaIsGoneEvenForTheAuthor() {
        when(stories.findById(5L)).thenReturn(Optional.of(story(5, 1, Instant.now().minusSeconds(1), "f")));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.openMedia(5L, 1L)).getStatus());
        verifyNoInteractions(storage);
    }
    @Test void mediaIsOnlyServedToPeopleWhoMaySeeIt() {
        when(stories.findById(5L)).thenReturn(Optional.of(story(5, 2, Instant.now().plusSeconds(600), "file-1")));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.openMedia(5L, 9L)).getStatus()); // stranger
        verifyNoInteractions(storage);
        contact(1L, 2L);
        service.openMedia(5L, 1L);
        verify(storage).load("file-1");
    }
    @Test void authorsAreNotCountedAsViewersOfTheirOwnStory() {
        when(stories.findById(5L)).thenReturn(Optional.of(story(5, 1, Instant.now().plusSeconds(600), null)));
        service.markViewed(5L, 1L);
        verify(views, never()).markViewed(any(), any(), any());
    }
    @Test void unauthorisedViewersAreNotRecorded() {
        when(stories.findById(5L)).thenReturn(Optional.of(story(5, 2, Instant.now().plusSeconds(600), null)));
        assertThrows(ApiException.class, () -> service.markViewed(5L, 9L));
        verify(views, never()).markViewed(any(), any(), any());
    }
    @Test void allowedViewerIsRecorded() {
        when(stories.findById(5L)).thenReturn(Optional.of(story(5, 2, Instant.now().plusSeconds(600), null)));
        contact(1L, 2L);
        service.markViewed(5L, 1L);
        verify(views).markViewed(eq(5L), eq(1L), any());
    }
    @Test void onlyTheAuthorSeesTheViewerList() {
        when(stories.findById(5L)).thenReturn(Optional.of(story(5, 2, Instant.now().plusSeconds(600), null)));
        contact(1L, 2L);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> service.viewers(5L, 1L)).getStatus());
        verify(views, never()).findByStoryIdOrderByViewedAtDesc(any());
    }
    @Test void onlyTheAuthorCanDeleteAndTheFileGoesToo() {
        Story s = story(5, 2, Instant.now().plusSeconds(600), "file-1");
        when(stories.findById(5L)).thenReturn(Optional.of(s));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.delete(5L, 1L)).getStatus());
        verify(stories, never()).delete(any());
        service.delete(5L, 2L);
        verify(stories).delete(s);
        verify(storage).deleteQuietly("file-1");
    }
    @Test void cleanupRemovesExpiredRowsAndFiles() {
        Story a = story(1, 1, Instant.now().minusSeconds(10), "file-a");
        Story b = story(2, 1, Instant.now().minusSeconds(5), null);
        when(stories.expired(any(), any(Pageable.class))).thenReturn(List.of(a, b));
        assertEquals(2, service.cleanupExpired());
        verify(stories).delete(a); verify(stories).delete(b);
        verify(storage).deleteQuietly("file-a");
        verify(storage, times(1)).deleteQuietly(any());
    }

    // ---- feed and settings
    @Test void feedHidesStoriesFromPeopleTheViewerMayNotSee() {
        when(stories.directPartners(1L)).thenReturn(List.of(2L, 3L));
        when(stories.groupmates(1L)).thenReturn(List.of());
        contact(1L, 2L);                         // 2 is a contact
        when(hiddenRepo.existsByUserIdAndHiddenUserId(3L, 1L)).thenReturn(true); // 3 hid their stories from me
        Story own = story(10, 1, Instant.now().plusSeconds(600), null);
        Story theirs = story(11, 2, Instant.now().plusSeconds(600), null);
        when(stories.active(argThat(c -> c.contains(1L) && c.contains(2L) && !c.contains(3L)), any())).thenReturn(List.of(own, theirs));
        when(views.viewedIds(eq(1L), any())).thenReturn(List.of());
        User u1 = new User(); u1.setId(1L); u1.setName("Me"); User u2 = new User(); u2.setId(2L); u2.setName("Ann");
        when(users.findAllById(any())).thenReturn(List.of(u1, u2));
        var feed = service.feed(1L);
        assertEquals(2, feed.size());
        assertTrue(feed.get(0).own());
        assertTrue(feed.get(1).hasUnviewed());
    }
    @Test void settingsValidateAudienceAndUsers() {
        assertThrows(ApiException.class, () -> service.setSettings(1L, "EVERYONE", List.of()));
        when(users.findAllById(any())).thenReturn(List.of());
        assertThrows(ApiException.class, () -> service.setSettings(1L, "CONTACTS", List.of(99L)));
    }
}