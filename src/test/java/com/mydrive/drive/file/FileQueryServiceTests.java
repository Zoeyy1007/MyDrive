
package com.mydrive.drive.file;

import com.mydrive.drive.account.AppUser;
import com.mydrive.drive.common.page.PageResponse;
import com.mydrive.drive.file.dto.FileQuery;
import com.mydrive.drive.file.dto.FileResponse;
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
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileQueryServiceTests {

    @Mock
    private DriveFileRepository driveFileRepository;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private FileQueryService fileQueryService;

    @Test
    void searchUsesTrustedPaginationAndMapsResponse() {
        UUID ownerId = UUID.randomUUID();
        DriveFile file = readyFile(ownerId);
        FileQuery query = query(2, 10, FileSortField.SIZE, Sort.Direction.DESC);

        when(currentUserService.requireCurrentUser()).thenReturn(user(ownerId));
        when(driveFileRepository.findAll(
                any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(file)));

        PageResponse<FileResponse> result = fileQueryService.search(query);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(driveFileRepository)
                .findAll(any(Specification.class), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort().getOrderFor("size").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().id()).isEqualTo(file.getId());
    }

    @Test
    void searchDefaultsToNameAscendingWhenSortIsMissing() {
        UUID ownerId = UUID.randomUUID();
        when(currentUserService.requireCurrentUser()).thenReturn(user(ownerId));
        when(driveFileRepository.findAll(
                any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        fileQueryService.search(query(0, 20, null, null));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(driveFileRepository)
                .findAll(any(Specification.class), pageableCaptor.capture());
        Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("name");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void searchRejectsPageSizeAboveOneHundred() {
        FileQuery query = query(0, 101, FileSortField.NAME, Sort.Direction.ASC);

        assertThatThrownBy(() -> fileQueryService.search(query))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 100");

        verifyNoInteractions(currentUserService, driveFileRepository);
    }

    @Test
    void detailsReturnsOnlyOwnedActiveReadyFile() {
        UUID ownerId = UUID.randomUUID();
        DriveFile file = readyFile(ownerId);
        when(currentUserService.requireCurrentUser()).thenReturn(user(ownerId));
        when(driveFileRepository.findByIdAndOwnerId(file.getId(), ownerId))
                .thenReturn(Optional.of(file));

        FileResponse result = fileQueryService.details(file.getId());

        assertThat(result.id()).isEqualTo(file.getId());
        assertThat(result.name()).isEqualTo("photo.jpg");
    }

    @Test
    void detailsHidesMissingOrForeignFile() {
        UUID ownerId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(currentUserService.requireCurrentUser()).thenReturn(user(ownerId));
        when(driveFileRepository.findByIdAndOwnerId(fileId, ownerId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileQueryService.details(fileId))
                .isInstanceOf(FileNotFoundException.class);
    }

    private FileQuery query(
            int page,
            int size,
            FileSortField sort,
            Sort.Direction direction) {
        return new FileQuery(
                null, null, null, null, null, null, null,
                false, page, size, sort, direction);
    }

    private AppUser user(UUID id) {
        return new AppUser(id, "user@example.com", "hash", Instant.now());
    }

    private DriveFile readyFile(UUID ownerId) {
        Instant now = Instant.now();
        return new DriveFile(
                UUID.randomUUID(),
                ownerId,
                null,
                "photo.jpg",
                "image/jpeg",
                42,
                "a".repeat(64),
                1,
                UploadStatus.READY,
                now,
                now,
                null);
    }
}
