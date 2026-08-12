package com.mydrive.drive.sync;

import com.mydrive.drive.account.AppUser;
import com.mydrive.drive.device.CurrentDeviceService;
import com.mydrive.drive.file.DriveFile;
import com.mydrive.drive.file.UploadStatus;
import com.mydrive.drive.security.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncChangeServiceTests {
    @Mock SyncChangeRepository repository;
    @Mock CurrentUserService currentUserService;
    @Mock CurrentDeviceService currentDeviceService;
    @Mock RelativePathService relativePathService;

    private SyncChangeService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new SyncChangeService(
                repository, currentUserService, currentDeviceService, relativePathService);
        userId = UUID.randomUUID();
    }

    @Test
    void pollIsOwnerScopedAndReturnsCursorAndHasMore() {
        when(currentUserService.requireCurrentUser()).thenReturn(user());
        SyncChange first = change(11L, "one.txt");
        SyncChange second = change(12L, "two.txt");
        when(repository.findByUserIdAndSequenceGreaterThanOrderBySequenceAsc(
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq(10L), any(Pageable.class)))
                .thenReturn(List.of(first, second));

        var result = service.poll(10, 1);

        assertThat(result.changes()).hasSize(1);
        assertThat(result.nextSequence()).isEqualTo(11);
        assertThat(result.hasMore()).isTrue();
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByUserIdAndSequenceGreaterThanOrderBySequenceAsc(
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq(10L), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(2);
    }

    @Test
    void emptyPollKeepsInputCursor() {
        when(currentUserService.requireCurrentUser()).thenReturn(user());
        when(repository.findByUserIdAndSequenceGreaterThanOrderBySequenceAsc(
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq(27L), any(Pageable.class)))
                .thenReturn(List.of());

        assertThat(service.poll(27, 100).nextSequence()).isEqualTo(27);
    }

    @Test
    void validatesCursorAndLimitBeforeQuerying() {
        assertThatThrownBy(() -> service.poll(-1, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.poll(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.poll(0, 501))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).findByUserIdAndSequenceGreaterThanOrderBySequenceAsc(
                any(), org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    void recordsSourceDeviceAndPortableFilePath() {
        UUID deviceId = UUID.randomUUID();
        DriveFile file = file();
        when(currentDeviceService.currentDeviceId()).thenReturn(Optional.of(deviceId));
        when(relativePathService.pathForFile(
                userId, null, file.getParentFolderId(), file.getName()))
                .thenReturn("Documents/report.txt");
        when(repository.save(any(SyncChange.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.recordFileChange(file, SyncOperation.UPDATED, null);

        ArgumentCaptor<SyncChange> captor = ArgumentCaptor.forClass(SyncChange.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getSourceDeviceId()).isEqualTo(deviceId);
        assertThat(captor.getValue().getRelativePath()).isEqualTo("Documents/report.txt");
        assertThat(captor.getValue().getVersionNumber()).isEqualTo(2);
    }

    @Test
    void browserChangeRecordsNullSourceDevice() {
        DriveFile file = file();
        when(currentDeviceService.currentDeviceId()).thenReturn(Optional.empty());
        when(relativePathService.pathForFile(any(), any(), any(), any()))
                .thenReturn("report.txt");
        when(repository.save(any(SyncChange.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SyncChange saved = service.recordFileChange(file, SyncOperation.CREATED, null);

        assertThat(saved.getSourceDeviceId()).isNull();
    }

    private AppUser user() {
        return new AppUser(userId, "owner@example.com", "hash", Instant.now());
    }

    private DriveFile file() {
        return new DriveFile(UUID.randomUUID(), userId, UUID.randomUUID(), "report.txt",
                "text/plain", 10, "a".repeat(64), 2, UploadStatus.READY,
                Instant.now(), Instant.now(), null);
    }

    private SyncChange change(long sequence, String path) {
        return new SyncChange(sequence, userId, null, SyncResourceType.FILE,
                UUID.randomUUID(), SyncOperation.UPDATED, path, null, 2, Instant.now());
    }
}
