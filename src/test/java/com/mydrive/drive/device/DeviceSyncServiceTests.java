package com.mydrive.drive.device;

import com.mydrive.drive.account.AppUser;
import com.mydrive.drive.folder.Folder;
import com.mydrive.drive.folder.FolderRepository;
import com.mydrive.drive.security.CurrentUserService;
import com.mydrive.drive.sync.SyncChange;
import com.mydrive.drive.sync.SyncChangeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceSyncServiceTests {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID DEVICE_ID = UUID.randomUUID();

    @Mock DeviceRepository deviceRepository;
    @Mock FolderRepository folderRepository;
    @Mock SyncChangeRepository syncChangeRepository;
    @Mock CurrentUserService currentUserService;
    @Mock CurrentDeviceService currentDeviceService;
    @InjectMocks DeviceSyncService service;

    @Test
    void deviceTokenCanReportAProcessedServerCursor() {
        Device device = device();
        SyncChange latest = org.mockito.Mockito.mock(SyncChange.class);
        when(latest.getSequence()).thenReturn(12L);
        when(currentDeviceService.currentDeviceId()).thenReturn(Optional.of(DEVICE_ID));
        when(currentUserService.requireCurrentUser()).thenReturn(user());
        when(deviceRepository.findByIdAndUserId(DEVICE_ID, USER_ID)).thenReturn(Optional.of(device));
        when(syncChangeRepository.findFirstByUserIdOrderBySequenceDesc(USER_ID))
                .thenReturn(Optional.of(latest));

        service.reportProgress(12);

        assertThat(device.getLastProcessedSequence()).isEqualTo(12);
        assertThat(device.getLastSyncAt()).isNotNull();
        verify(deviceRepository).save(device);
    }

    @Test
    void browserSessionCannotPretendToBeASyncDevice() {
        when(currentDeviceService.currentDeviceId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reportProgress(0))
                .isInstanceOf(AccessDeniedException.class);
        verify(deviceRepository, never()).save(any());
    }

    @Test
    void cannotReportCursorBeyondLatestServerChange() {
        Device device = device();
        SyncChange latest = org.mockito.Mockito.mock(SyncChange.class);
        when(latest.getSequence()).thenReturn(4L);
        when(currentDeviceService.currentDeviceId()).thenReturn(Optional.of(DEVICE_ID));
        when(currentUserService.requireCurrentUser()).thenReturn(user());
        when(deviceRepository.findByIdAndUserId(DEVICE_ID, USER_ID)).thenReturn(Optional.of(device));
        when(syncChangeRepository.findFirstByUserIdOrderBySequenceDesc(USER_ID))
                .thenReturn(Optional.of(latest));

        assertThatThrownBy(() -> service.reportProgress(5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("latest server sequence");
        verify(deviceRepository, never()).save(any());
    }

    private AppUser user() {
        return new AppUser(USER_ID, "user@example.com", "hash", Instant.now());
    }

    private Device device() {
        return new Device(
                DEVICE_ID, USER_ID, "Laptop", "a".repeat(64), Instant.now(),
                null, null, UUID.randomUUID(), 0, null);
    }
}
