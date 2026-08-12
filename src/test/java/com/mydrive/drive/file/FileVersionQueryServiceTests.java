/*
 * PHASE 5 TESTS for FileVersionQueryService.
 *
 * Use JUnit 5 + MockitoExtension with mocked repositories/current-user service.
 * Test:
 *   - only the authenticated owner's file is accepted
 *   - foreign/missing/deleted file becomes FileNotFoundException
 *   - versions are requested newest first with page + size
 *   - page size above 100 is rejected before repository access
 *   - exactly one response has current=true
 *   - storageKey is not present in FileVersionResponse
 */
package com.mydrive.drive.file;

import com.mydrive.drive.account.AppUser;
import com.mydrive.drive.common.page.PageResponse;
import com.mydrive.drive.file.dto.FileVersionResponse;
import com.mydrive.drive.security.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileVersionQueryServiceTests {
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID FILE_ID = UUID.randomUUID();

    @Mock
    private DriveFileRepository driveFileRepository;
    @Mock
    private FileVersionRepository fileVersionRepository;
    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private FileVersionQueryService service;

    @Test
    void listsOwnedVersionsNewestFirstAndMarksOnlyCurrentVersion() {
        DriveFile file = driveFile(3, false, UploadStatus.READY);
        FileVersion versionThree = version(3);
        FileVersion versionTwo = version(2);
        when(currentUserService.requireCurrentUser()).thenReturn(user());
        when(driveFileRepository.findByIdAndOwnerId(FILE_ID, OWNER_ID))
                .thenReturn(Optional.of(file));
        when(fileVersionRepository.findAllByFileId(eq(FILE_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(versionThree, versionTwo)));

        PageResponse<FileVersionResponse> response = service.listVersions(FILE_ID, 0, 20);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(fileVersionRepository).findAllByFileId(eq(FILE_ID), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        Sort.Order order = pageable.getSort().getOrderFor("versionNumber");

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(response.content()).extracting(FileVersionResponse::versionNumber)
                .containsExactly(3, 2);
        assertThat(response.content()).filteredOn(FileVersionResponse::current).hasSize(1);
        assertThat(response.content().getFirst().current()).isTrue();
        assertThat(Arrays.stream(FileVersionResponse.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .doesNotContain("storageKey");
    }

    @Test
    void foreignOrMissingFileReturnsNotFoundBeforeVersionLookup() {
        when(currentUserService.requireCurrentUser()).thenReturn(user());
        when(driveFileRepository.findByIdAndOwnerId(FILE_ID, OWNER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listVersions(FILE_ID, 0, 20))
                .isInstanceOf(FileNotFoundException.class);

        verifyNoInteractions(fileVersionRepository);
    }

    @Test
    void deletedFileReturnsNotFoundBeforeVersionLookup() {
        when(currentUserService.requireCurrentUser()).thenReturn(user());
        when(driveFileRepository.findByIdAndOwnerId(FILE_ID, OWNER_ID))
                .thenReturn(Optional.of(driveFile(3, true, UploadStatus.READY)));

        assertThatThrownBy(() -> service.listVersions(FILE_ID, 0, 20))
                .isInstanceOf(FileNotFoundException.class);

        verifyNoInteractions(fileVersionRepository);
    }

    @Test
    void pageSizeAboveOneHundredIsRejectedBeforeRepositoryAccess() {
        assertThatThrownBy(() -> service.listVersions(FILE_ID, 0, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 100");

        verifyNoInteractions(currentUserService, driveFileRepository, fileVersionRepository);
    }

    private AppUser user() {
        return new AppUser(OWNER_ID, "user@example.com", "hash", Instant.now());
    }

    private DriveFile driveFile(int currentVersion, boolean deleted, UploadStatus status) {
        Instant now = Instant.now();
        return new DriveFile(
                FILE_ID, OWNER_ID, null, "document.txt", "text/plain", 10,
                "a".repeat(64), currentVersion, status, now, now,
                deleted ? now : null);
    }

    private FileVersion version(int number) {
        return new FileVersion(
                UUID.randomUUID(), FILE_ID, number,
                "users/owner/files/file/versions/" + number,
                "a".repeat(64), number * 10L, OWNER_ID, Instant.now(), null);
    }
}
