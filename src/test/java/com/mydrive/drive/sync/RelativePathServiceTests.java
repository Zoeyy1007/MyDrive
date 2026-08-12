package com.mydrive.drive.sync;

import com.mydrive.drive.folder.Folder;
import com.mydrive.drive.folder.FolderNotFoundException;
import com.mydrive.drive.folder.FolderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelativePathServiceTests {
    @Mock FolderRepository folderRepository;

    @Test
    void buildsNestedFilePathFromUserRoot() {
        UUID owner = UUID.randomUUID();
        Folder photos = folder(owner, null, "Photos");
        Folder trips = folder(owner, photos.getId(), "Trips");
        stub(owner, photos, trips);

        String result = service().pathForFile(owner, null, trips.getId(), "image.jpg");

        assertThat(result).isEqualTo("Photos/Trips/image.jpg");
    }

    @Test
    void excludesSelectedRootFromRelativePath() {
        UUID owner = UUID.randomUUID();
        Folder photos = folder(owner, null, "Photos");
        Folder trips = folder(owner, photos.getId(), "Trips");
        stub(owner, photos, trips);

        assertThat(service().pathForFolder(owner, photos.getId(), trips.getId()))
                .isEqualTo("Trips");
        assertThat(service().pathForFile(owner, photos.getId(), photos.getId(), "cover.jpg"))
                .isEqualTo("cover.jpg");
    }

    @Test
    void rejectsFolderOutsideSelectedRoot() {
        UUID owner = UUID.randomUUID();
        Folder photos = folder(owner, null, "Photos");
        Folder documents = folder(owner, null, "Documents");
        stub(owner, photos, documents);

        assertThatThrownBy(() -> service().pathForFolder(
                owner, photos.getId(), documents.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside");
    }

    @Test
    void foreignFolderIsIndistinguishableFromMissingFolder() {
        UUID owner = UUID.randomUUID();
        UUID foreignFolder = UUID.randomUUID();
        when(folderRepository.findByIdAndOwnerId(foreignFolder, owner))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().pathForFolder(owner, null, foreignFolder))
                .isInstanceOf(FolderNotFoundException.class);
    }

    @Test
    void rejectsUnsafePathSegments() {
        UUID owner = UUID.randomUUID();

        assertThatThrownBy(() -> service().pathForFile(owner, null, null, "../secret"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service().pathForFile(owner, null, null, ".."))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service().pathForFile(owner, null, null, "bad\\name"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service().pathForFile(owner, null, null, "CON.txt"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service().pathForFile(owner, null, null, "bad:name.txt"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void detectsParentCycles() {
        UUID owner = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        Folder first = new Folder(firstId, secondId, "first", Instant.now(), Instant.now(), owner, null);
        Folder second = new Folder(secondId, firstId, "second", Instant.now(), Instant.now(), owner, null);
        stub(owner, first, second);

        assertThatThrownBy(() -> service().pathForFolder(owner, null, firstId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cycle");
    }

    private RelativePathService service() {
        return new RelativePathService(folderRepository);
    }

    private Folder folder(UUID owner, UUID parentId, String name) {
        return new Folder(UUID.randomUUID(), parentId, name,
                Instant.now(), Instant.now(), owner, null);
    }

    private void stub(UUID owner, Folder... folders) {
        for (Folder folder : folders) {
            when(folderRepository.findByIdAndOwnerId(folder.getId(), owner))
                    .thenReturn(Optional.of(folder));
        }
    }
}
