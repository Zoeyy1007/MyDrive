# Personal Drive and Cross-Device Sync Platform

## 1. Project Goal

Build a private cloud-drive platform for personal and family use.

The system should support:

- Account login from multiple devices
- Viewing the same files under the same account
- File and folder upload, download, rename, move, delete, restore, filtering, search, and sorting
- Private family photo albums with invited registered members
- Automatic synchronization of selected folders between macOS and Windows
- File version history
- Conflict detection with a keep-both strategy
- A browser interface and a lightweight desktop sync client

The first version prioritizes correctness, learning value, and maintainability rather than large-scale performance.

---

## 2. Agreed Scope

### Included

- Personal accounts
- Private files by default
- Upload and download
- Folder organization
- Search, filtering, and sorting
- Trash and restore
- File version history
- Private shared albums
- Album invitations through registered accounts
- Selected-folder synchronization on macOS and Windows
- Offline retry support
- Conflict detection
- Keep-both conflict resolution

### Not included initially

- Public sharing URLs
- Anonymous access
- Automatic merging of binary files
- Real-time collaborative document editing
- End-to-end encryption
- Antivirus scanning
- Advanced full-text document search
- Mobile sync clients
- Internet-scale distributed architecture

---

## 3. High-Level Architecture

```text
React Web App
      |
      | HTTPS
      v
Spring Boot Backend
      |
      |---- PostgreSQL
      |       Users, metadata, permissions, versions, jobs, sync events
      |
      |---- Object Storage
      |       Actual file bytes, thumbnails, historical versions
      |
      |---- Background Workers
      |       Thumbnails, cleanup, indexing, retries
      |
      |---- Sync API and Change Log
              ^
              |
      Java Sync Client
        /           \
     macOS         Windows
```

Users communicate with the Spring Boot backend. They do not directly access PostgreSQL or object storage.

The backend verifies authentication and authorization before allowing access to a file or album.

---

## 4. Core Technology Stack

### Backend

- Java
- Spring Boot
- Spring Web
- Spring Security
- Spring Data JPA
- Bean Validation
- Flyway
- Maven

### Database

- PostgreSQL

PostgreSQL stores:

- Users
- Password hashes
- File and folder metadata
- File-version metadata
- Album membership
- Device information
- Sync change events
- Background-job state
- Trash state

PostgreSQL does not normally store the actual file contents.

### Object Storage

Development:

- Local filesystem first
- MinIO through Docker after basic upload/download works

Deployment:

- Cloudflare R2
- Backblaze B2
- AWS S3
- Self-hosted MinIO on a VPS

Object storage contains:

- Original files
- Historical file versions
- Photo thumbnails
- Temporary uploads

### Web Frontend

- React
- TypeScript
- Vite
- Fetch API or Axios

### Desktop Sync Client

- Java
- Java `WatchService`
- Java `HttpClient`
- SQLite
- Scheduled folder scans
- SHA-256 checksums

The same client codebase should support both macOS and Windows.

### Local Development

- Docker Compose
- PostgreSQL container
- MinIO container
- Spring Boot application
- React development server

### Initial Deployment

Recommended beginner deployment:

```text
Railway:
- Spring Boot
- PostgreSQL

Cloudflare R2 or Backblaze B2:
- Actual file storage

Vercel, Cloudflare Pages, or Railway:
- React frontend
```

Later, the full system can be moved to a VPS to learn Linux, Docker, reverse proxies, HTTPS, backups, and server administration.

---

## 5. Data-Storage Design

### PostgreSQL Stores Metadata

Example file record:

```text
id
owner_id
parent_folder_id
name
content_type
size
checksum
current_version
created_at
updated_at
deleted_at
```

### Object Storage Stores File Bytes

Example object-storage key:

```text
users/{userId}/files/{fileId}/versions/{versionNumber}
```

A PostgreSQL row points to the corresponding object-storage key.

### Why Separate Them

PostgreSQL is better for:

- Relationships
- Transactions
- Queries
- Permissions
- Sorting
- Filtering
- Search
- Metadata consistency

Object storage is better for:

- Large files
- Streaming downloads
- File versions
- Scalable storage
- Resumable transfers

---

## 6. Minimum Security Design

Security can be improved gradually, but the initial version must still include a safe minimum.

### Initial Requirements

- Username or email login
- Password hashing with BCrypt
- Spring Security
- Server-side sessions for the browser
- Authentication required for all private APIs
- Owner or album-membership checks on every file request
- Filename and path validation
- Upload-size limits
- HTTPS in deployed environments
- Secure, HTTP-only cookies

### Later Improvements

- Refresh-token rotation for sync clients
- Device-management page
- Revoke individual devices
- Rate limiting
- Two-factor authentication
- Audit logs
- Antivirus scanning
- Suspicious-login detection
- Server-side encryption configuration
- Optional client-side encryption

Passwords must never be stored as plain text.

---

## 7. Main Features

## 7.1 Accounts

Users can:

- Register
- Log in
- Log out
- Use the same account on multiple devices
- Access the same remote file tree
- Join invited family albums

Initial browser authentication should use a Spring Security session cookie.

The desktop sync client can initially use a device token. A stronger access-token and refresh-token design can be added later.

---

## 7.2 File and Folder Management

Core operations:

- Upload file
- Download file
- Create folder
- Rename file or folder
- Move file or folder
- Copy file
- Delete file
- Restore file from trash
- Permanently delete file
- List folder contents
- Sort and filter results

Permanent IDs should use UUIDs. Filenames may change, but file IDs remain stable.

---

## 7.3 Search, Filtering, and Sorting

Initial search:

- Filename search using PostgreSQL

Sorting:

- Name
- Created time
- Modified time
- File size
- File type

Filtering:

- File type
- Date range
- Size range
- Owner
- Active or deleted
- Personal file or album item

Future improvement:

- Extract text from Markdown, PDF, and Office files
- PostgreSQL full-text search
- Optional OpenSearch or Elasticsearch

---

## 7.4 Private Family Albums

Albums are private and only visible to authenticated invited members.

Roles:

```text
OWNER
EDITOR
VIEWER
```

Features:

- Create album
- Invite an existing registered account
- Accept invitation
- Upload photos and videos
- View thumbnails
- Download originals
- Sort by upload time or capture time
- Add captions
- Remove an item
- Manage album membership

Suggested tables:

```text
albums
album_members
album_invitations
album_items
files
```

An album item references a normal stored file. This avoids maintaining a separate storage system for album photos.

Public sharing links are intentionally excluded from the initial design.

---

## 7.5 Selected-Folder Device Sync

Users can choose any local folder, such as:

```text
~/Documents/School
~/ObsidianVault
C:\Users\User\Pictures
```

Each selected local folder maps to a remote folder.

### Client Responsibilities

- Watch for local changes
- Periodically scan the folder
- Upload new and modified files
- Download remote changes
- Propagate renames and deletions
- Maintain local synchronization state
- Retry failed operations
- Avoid upload/download loops
- Detect conflicts
- Apply ignore rules

### Local SQLite State

```text
relative_path
remote_file_id
local_checksum
remote_version
modified_time
sync_status
last_synced_at
```

### Change Detection

Use both:

1. Java `WatchService` for fast event notification
2. Periodic full scans for reliability

A filesystem watcher alone is not sufficient because events may be duplicated or missed.

### Ignore Rules

Support configurable exclusions, such as:

```text
.DS_Store
Thumbs.db
.trash/
*.tmp
.obsidian/workspace.json
```

---

## 7.6 Server Sync Change Log

The backend maintains an ordered log of changes.

Example:

```text
sequence | user_id | file_id | operation | version
1001     | u1      | f20     | UPDATED   | 4
1002     | u1      | f33     | DELETED   | 2
```

Each client stores the last processed sequence.

Initial client request:

```http
GET /api/sync/changes?after=1000
```

Initial implementation should use polling.

Future improvement:

- Server-Sent Events
- WebSocket notifications

Notifications should only tell the client that something changed. Actual file transfer should still use HTTP.

---

## 7.7 Version History

Every successful update creates an immutable file version.

Version metadata:

```text
file_id
version_number
storage_key
checksum
size
created_by
created_at
source_device_id
```

Features:

- View version history
- Download an older version
- Restore an older version
- Show source device
- Keep deleted files for a retention period

Initial design:

- Store complete file copies for every version
- Use a configurable retention policy later

Future improvement:

- Deduplicate identical content by checksum
- Retain only recent versions
- Compress old versions
- Use block-level storage for very large files

---

## 7.8 Conflict Detection and Resolution

A conflict occurs when two devices modify the same base version.

Example:

```text
Mac and Windows both start with version 5.
Mac uploads version 6.
Windows later uploads a change based on version 5.
```

The client sends:

```text
baseVersion = 5
```

The server compares it with the current version.

If the current version is already 6, the upload is treated as a conflict.

### Initial Resolution Policy

Keep both files:

```text
report.md
report (conflict from Windows 2026-08-04).md
```

The system should never silently overwrite one conflicting edit.

Future improvement:

- Conflict-resolution screen
- Compare metadata
- Three-way merge for Markdown and plain-text files
- Always keep both for images, PDFs, ZIP files, and other binary formats

---

## 7.9 Trash and Deletion

Initial deletion should be soft deletion:

```text
deleted_at = timestamp
```

Flow:

1. User deletes a file.
2. The file moves to trash.
3. Sync clients receive a deletion event.
4. The user can restore it.
5. A background cleanup job permanently deletes it after the retention period.

Suggested initial retention:

```text
30 days
```

---

## 8. File-Transfer Concepts

## 8.1 SHA-256

SHA-256 produces a fixed-length hash from file contents.

Uses:

- Detect whether content changed
- Verify upload and download integrity
- Detect duplicate content
- Compare local and remote files

It is not encryption and cannot reconstruct the file.

## 8.2 Integrity Verification

The client calculates a checksum before upload.

The server verifies that the stored file produces the same checksum.

If the values differ, the upload is incomplete or corrupted and should not be finalized.

## 8.3 Database Transactions

Related metadata changes should happen inside a Spring `@Transactional` method.

Example operations:

- Create file-version record
- Update current version
- Add sync-change event

If one operation fails, all database changes roll back.

PostgreSQL and object storage do not share one simple transaction. The practical design is:

1. Upload to a temporary object key.
2. Verify the object.
3. Commit the PostgreSQL transaction.
4. Mark or move the object to its final state.
5. Periodically remove abandoned temporary objects.

## 8.4 Range Requests

Range requests download only part of a file.

Future uses:

- Resume interrupted downloads
- Stream videos
- Seek within media
- Transfer large files in segments

Normal full-file downloads are sufficient for the first version.

## 8.5 Idempotency Keys

An idempotency key prevents retries from creating duplicate operations.

Example:

```http
Idempotency-Key: upload-550e8400-e29b-41d4
```

If the client retries the same completed upload, the server returns the original result instead of creating a duplicate file.

This is most important when automatic synchronization and retry queues are introduced.

---

## 9. Background Workers

Background workers perform work that should not block the user request.

Examples:

- Generate photo thumbnails
- Clean expired trash
- Delete abandoned uploads
- Retry failed processing
- Extract searchable text
- Scan files
- Remove expired versions

### Initial Implementation

Use:

- Spring `@Async`
- A configured thread pool
- Spring `@Scheduled`

This is acceptable for early development.

### Limitation

An in-memory asynchronous task may disappear if the application crashes.

### Future Improvement

Introduce durable jobs:

```text
Upload API
   |
   v
jobs table or RabbitMQ
   |
   v
Worker claims job
   |
   v
Process, retry, or mark failed
```

Recommended progression:

1. `@Async`
2. PostgreSQL-backed jobs table
3. RabbitMQ with retry and dead-letter queues
4. Separate worker service if necessary

Kafka is not necessary for the initial project.

---

## 10. API Outline

### Authentication

```text
POST /api/auth/register
POST /api/auth/login
POST /api/auth/logout
GET  /api/auth/me
```

### Files and Folders

```text
GET    /api/files
POST   /api/files/upload
GET    /api/files/{id}
GET    /api/files/{id}/download
PATCH  /api/files/{id}
DELETE /api/files/{id}
POST   /api/files/{id}/restore
POST   /api/folders
```

### Versions

```text
GET  /api/files/{id}/versions
GET  /api/files/{id}/versions/{version}/download
POST /api/files/{id}/versions/{version}/restore
```

### Albums

```text
POST   /api/albums
GET    /api/albums
GET    /api/albums/{id}
POST   /api/albums/{id}/invitations
POST   /api/albums/{id}/invitations/{invitationId}/accept
POST   /api/albums/{id}/items
DELETE /api/albums/{id}/items/{itemId}
```

### Sync

```text
POST /api/devices
POST /api/sync/folders
GET  /api/sync/changes
POST /api/sync/upload
POST /api/sync/delete
POST /api/sync/move
GET  /api/sync/conflicts
POST /api/sync/conflicts/{id}/resolve
```

---

## 11. Development Phases

## Phase 0: Project Setup and Spring Fundamentals

### Goal

Create a stable development environment and learn the Spring concepts needed for the first API.

### Build

- Spring Boot project
- Basic controller
- Service layer
- Dependency injection
- PostgreSQL connection
- Flyway migration
- Docker Compose
- Basic error handling

### Stack

- Spring Boot
- Spring Web
- PostgreSQL
- Spring Data JPA
- Flyway
- Docker Compose

### Preparation for Later

- Use layered packages from the start
- Use DTOs instead of directly exposing entities
- Add database migrations instead of automatic schema changes
- Use UUIDs for IDs
- Keep storage behind a `StorageService` interface

---

## Phase 1: Single-User Basic Drive

### Goal

Upload, list, and download files through a web application.

### Build

- Basic file metadata
- Local filesystem storage
- Upload endpoint
- Download endpoint
- File listing
- Simple React page
- Rename and delete
- Folder creation

### Stack

- Spring Boot
- React and TypeScript
- PostgreSQL
- Local filesystem

### Algorithms and Techniques

- Multipart upload
- Streaming download
- UUID file identifiers
- Basic pagination
- Filename validation

### Preparation for Later

Define a storage abstraction:

```java
public interface StorageService {
    StoredObject save(...);
    InputStream load(...);
    void delete(...);
}
```

This allows local storage to be replaced by MinIO or cloud object storage without rewriting business logic.

---

## Phase 2: Multi-User Accounts and Minimum Security

### Goal

Allow multiple registered users to access private file spaces.

### Build

- Registration
- Login
- Logout
- BCrypt password hashing
- Spring Security sessions
- Ownership checks
- User-specific file trees

### Stack

- Spring Security
- PostgreSQL
- HTTP-only session cookies

### Preparation for Later

- Separate authentication logic from file logic
- Record `owner_id` on files and folders
- Introduce authorization service methods
- Design device-token support without implementing full refresh-token rotation yet

---

## Phase 3: MinIO and Reliable File Storage

### Goal

Move actual file content out of the application directory.

### Build

- MinIO in Docker
- Object-storage bucket
- Storage keys
- Temporary upload objects
- SHA-256 checksums
- Integrity verification
- Metadata transactions
- Cleanup for abandoned uploads

### Stack

- MinIO
- MinIO Java SDK
- PostgreSQL
- Spring transactions

### Preparation for Later

- Keep object-storage implementation behind `StorageService`
- Avoid provider-specific object keys
- Store checksum, size, and content type
- Design upload state such as `PENDING`, `READY`, and `FAILED`

This prepares the project for R2, B2, S3, or self-hosted MinIO.

---

## Phase 4: Normal Drive Features

### Goal

Provide the expected functionality of a personal drive.

### Build

- Move and copy
- Trash and restore
- Sorting
- Filtering
- Filename search
- Pagination
- File details
- Improved frontend navigation

### Stack

- PostgreSQL indexes
- Spring Data queries
- React file browser

### Preparation for Later

- Keep queries paginated
- Use stable sort fields
- Add database indexes deliberately
- Represent deletion as soft deletion
- Add a scheduled permanent-deletion process

---

## Phase 5: File Version History

### Goal

Preserve previous file versions and support restoration.

### Build

- Immutable version records
- Current-version pointer
- Version-history page
- Restore older versions
- Show source device and creator
- Version retention settings

### Stack

- PostgreSQL
- Object storage
- Spring transactions

### Preparation for Later

- Use checksum-based duplicate detection
- Keep retention policy configurable
- Separate logical files from physical versions
- Avoid overwriting objects in storage

---

## Phase 6: Private Family Albums

### Goal

Allow invited registered family members to share photos privately.

### Build

- Albums
- Album invitations
- Member roles
- Photo and video upload
- Thumbnail gallery
- Download originals
- Captions
- Membership management

### Stack

- Spring Security authorization
- PostgreSQL
- Object storage
- React gallery
- Thumbnailator or ImageMagick

### Background Work

- Thumbnail generation
- Image metadata extraction

### Preparation for Later

- Treat album items as references to normal files
- Keep album permissions separate from file ownership
- Store processing status for thumbnails
- Avoid public URLs

---

## Phase 7: Basic Java Sync Client

### Goal

Synchronize one selected folder between macOS, Windows, and the server.

### Build

- Java command-line client
- Login or device token
- Folder configuration
- Java `WatchService`
- Periodic full scan
- SQLite local state
- Upload local changes
- Poll remote change log
- Download remote changes
- Propagate deletions

### Stack

- Java
- SQLite
- Java `WatchService`
- Java `HttpClient`

### Algorithms and Techniques

- Relative-path mapping
- File-size and modified-time comparison
- SHA-256 verification
- Atomic temporary-file download
- Last-processed sequence cursor
- Ignore patterns

### Preparation for Later

- Separate filesystem detection from sync decisions
- Store all sync operations in a local queue
- Assign every client a unique device ID
- Make operations retryable and idempotent
- Do not depend only on watcher events

---

## Phase 8: Conflict Handling and Offline Reliability

### Goal

Prevent data loss when multiple devices edit the same file.

### Build

- Base-version checks
- Conflict records
- Keep-both behavior
- Conflict naming
- Offline operation queue
- Retry with backoff
- Idempotency keys
- Sync status interface

### Initial Conflict Policy

Always preserve both versions.

### Preparation for Later

- Store base, local, and remote version references
- Keep conflict logic behind a dedicated service
- Mark content types that may support text merging
- Never silently overwrite a conflicting edit

---

## Phase 9: Persistent Background Jobs

### Goal

Make asynchronous processing reliable across server restarts.

### Build

- PostgreSQL jobs table
- Job claiming
- Retry count
- Failure status
- Scheduled recovery of abandoned jobs
- Thumbnail and cleanup jobs

### Stack

Initial:

- PostgreSQL job queue
- Spring scheduled worker

Later:

- RabbitMQ
- Dedicated worker process
- Dead-letter queue

### Preparation for Later

- Make every job idempotent
- Record attempts and errors
- Separate job creation from job execution
- Avoid passing large file bytes through the message queue

---

## Phase 10: Deployment

### Goal

Make the system available to multiple devices and family members.

### Initial Deployment

- Railway for Spring Boot
- Railway PostgreSQL
- Cloudflare R2 or Backblaze B2
- Vercel or Cloudflare Pages for React
- HTTPS and environment variables

### Later VPS Deployment

A VPS can host:

```text
Docker Compose
├── Spring Boot
├── PostgreSQL
├── MinIO
├── React or static files
├── Nginx or Caddy
└── Backup jobs
```

### Preparation for Later

- Use environment variables
- Keep secrets outside source control
- Add health-check endpoints
- Add structured logging
- Back up PostgreSQL
- Back up object storage
- Use separate development and production configuration
- Add database connection pooling
- Document restore procedures

---

## 12. Future Improvements

Potential improvements after the core system works:

- Chunked and resumable uploads
- HTTP range downloads
- WebSocket or SSE sync notifications
- Full-text document search
- OCR
- Photo EXIF extraction
- Duplicate-content storage reduction
- Three-way text merge
- Device-management dashboard
- Two-factor authentication
- Audit logs
- Rate limiting
- Antivirus scanning
- Client-side encryption
- Native tray application
- Mobile album application
- Storage quotas
- Shared normal folders
- Link sharing
- Collaborative editing
- Distributed workers
- Metrics and tracing

These features should not be implemented until the basic upload, storage, authentication, versioning, and synchronization flows are stable.

---

## 13. Important Design Principles

### Use Interfaces Around Replaceable Infrastructure

Examples:

- `StorageService`
- `ThumbnailService`
- `AuthenticationTokenService`
- `JobQueue`
- `ChecksumService`

This makes it easier to replace local storage, MinIO, R2, or queue implementations.

### Keep Controllers Thin

Controllers should:

- Validate requests
- Call services
- Return responses

Business logic belongs in services.

### Separate Logical Files From Physical Versions

A file is the object users recognize.

A file version is one immutable snapshot of its content.

### Make Operations Retry-Safe

Uploads, deletes, sync operations, and background jobs should eventually support idempotency.

### Prefer Correctness Before Optimization

Start with:

- Complete file versions
- Polling
- Full-file transfers
- Keep-both conflicts
- One Spring Boot service

Optimize only after the behavior is correct.

### Preserve User Data

Never silently discard:

- Conflicting edits
- Failed uploads
- Old versions within retention
- Files in trash before expiration

---

## 14. Recommended First Milestone

The first meaningful milestone is:

> A single-user browser-based drive that can upload, list, organize, download, rename, and delete files, using Spring Boot, PostgreSQL, and local filesystem storage.

After that works:

1. Add accounts and security.
2. Replace local storage with MinIO.
3. Add version history.
4. Add private family albums.
5. Build the Java sync client.
6. Add conflict handling and offline retries.
7. Deploy publicly.

This order allows development to begin while Spring is still being learned and avoids starting with the most difficult synchronization features.
