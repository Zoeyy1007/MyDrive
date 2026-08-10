
package com.mydrive.drive.file;

import com.mydrive.drive.file.dto.FileQuery;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class DriveFileSpecification {

    private static final char LIKE_ESCAPE = '\\';

    private DriveFileSpecification() {
    }

    /**
     * Builds the complete specification used by file browsing. Ownership and
     * READY status are mandatory; the remaining predicates are added only when
     * their corresponding query values are present.
     */
    public static Specification<DriveFile> from(UUID ownerId, FileQuery fileQuery) {
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        Objects.requireNonNull(fileQuery, "fileQuery must not be null");

        List<Specification<DriveFile>> filters = new ArrayList<>();
        filters.add(ownedBy(ownerId));
        filters.add(hasUploadStatus(UploadStatus.READY));
        filters.add(isDeleted(fileQuery.deleted()));

        if (fileQuery.parentFolderId() != null) {
            filters.add(inParentFolder(fileQuery.parentFolderId()));
        }
        if (hasText(fileQuery.search())) {
            filters.add(nameContains(fileQuery.search()));
        }
        if (hasText(fileQuery.contentType())) {
            filters.add(hasContentType(fileQuery.contentType()));
        }
        if (fileQuery.minSize() != null) {
            filters.add(sizeAtLeast(fileQuery.minSize()));
        }
        if (fileQuery.maxSize() != null) {
            filters.add(sizeAtMost(fileQuery.maxSize()));
        }
        if (fileQuery.createdAfter() != null) {
            filters.add(createdAtOrAfter(fileQuery.createdAfter()));
        }
        if (fileQuery.createdBefore() != null) {
            filters.add(createdAtOrBefore(fileQuery.createdBefore()));
        }

        return Specification.allOf(filters);
    }

    public static Specification<DriveFile> ownedBy(UUID ownerId) {
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("ownerId"), ownerId);
    }

    public static Specification<DriveFile> inParentFolder(UUID parentFolderId) {
        Objects.requireNonNull(parentFolderId, "parentFolderId must not be null");
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("parentFolderId"), parentFolderId);
    }

    public static Specification<DriveFile> isDeleted(boolean deleted) {
        return (root, query, criteriaBuilder) -> deleted
                ? criteriaBuilder.isNotNull(root.get("deletedAt"))
                : criteriaBuilder.isNull(root.get("deletedAt"));
    }

    public static Specification<DriveFile> nameContains(String search) {
        String escapedSearch = escapeLike(search.trim().toLowerCase(Locale.ROOT));
        String pattern = "%" + escapedSearch + "%";
        return (root, query, criteriaBuilder) -> criteriaBuilder.like(
                criteriaBuilder.lower(root.get("name")),
                pattern,
                LIKE_ESCAPE);
    }

    public static Specification<DriveFile> hasContentType(String contentType) {
        String normalizedContentType = contentType.trim().toLowerCase(Locale.ROOT);
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(
                criteriaBuilder.lower(root.get("contentType")),
                normalizedContentType);
    }

    public static Specification<DriveFile> sizeAtLeast(long minSize) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.greaterThanOrEqualTo(root.get("size"), minSize);
    }

    public static Specification<DriveFile> sizeAtMost(long maxSize) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.lessThanOrEqualTo(root.get("size"), maxSize);
    }

    public static Specification<DriveFile> createdAtOrAfter(Instant createdAfter) {
        Objects.requireNonNull(createdAfter, "createdAfter must not be null");
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), createdAfter);
    }

    public static Specification<DriveFile> createdAtOrBefore(Instant createdBefore) {
        Objects.requireNonNull(createdBefore, "createdBefore must not be null");
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), createdBefore);
    }

    public static Specification<DriveFile> hasUploadStatus(UploadStatus uploadStatus) {
        Objects.requireNonNull(uploadStatus, "uploadStatus must not be null");
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("uploadStatus"), uploadStatus);
    }

    static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
