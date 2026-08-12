# MyDrive Sync Client

## Dashboard setup

With the Spring Boot server running, open `http://localhost:8080`. The dashboard
can register a device, choose its remote folder, and download a ready-to-use
`sync-client.properties` file. Move that downloaded file beside the client JAR
or pass its path as the first command-line argument.

The dashboard never stores the raw device token or the local folder path. A
device now reports its successfully processed cursor after every poll so the
dashboard can show `UP TO DATE`, `BEHIND`, or `NEVER SYNCED` truthfully.

This is a separate Java 21 desktop process. It watches one local directory and
synchronizes it with one remote MyDrive folder. It uses portable `/` paths, an
embedded SQLite state database, atomic downloads, SHA-256 verification, and a
separate revocable token for each computer.

## Build and test

From the repository root:

```powershell
.\mvnw.cmd -f sync-client\pom.xml test
.\mvnw.cmd -f sync-client\pom.xml package
```

The packaged runnable jar is:

```text
sync-client/target/sync-client-0.0.1-SNAPSHOT.jar
```

No SQLite installation is needed. The JDBC driver creates `.mydrive/state.db`
inside the selected local sync directory.

## Server preparation

1. Start PostgreSQL and MinIO, then start the Spring Boot server so Flyway runs
   the Phase 7 migration.
2. Log in with the normal user account.
3. Create or choose one remote folder to be the sync root and copy its UUID.
4. Register each computer separately with `POST /api/devices`, for example:

   ```json
   {"name":"Zoey Windows laptop"}
   ```

5. Save both the returned device id and one-time token. The raw token cannot be
   retrieved again. If it is lost, revoke that device and register a new one.

Use a different device id/token pair on the Mac. This lets the server identify
which computer caused a change and lets you revoke only one computer.

## Windows configuration

Copy `sync-client.properties.example` to `sync-client.properties` beside the
jar, then use values similar to:

```properties
server.base-url=http://192.168.1.20:8080
sync.local-root=C:/Users/zoeyy/MyDriveSyncTest
sync.remote-folder-id=REMOTE_FOLDER_UUID
device.id=WINDOWS_DEVICE_UUID
device.token=WINDOWS_ONE_TIME_TOKEN
sync.poll-seconds=10
sync.full-scan-seconds=60
sync.max-change-batch=100
sync.ignore=.DS_Store,Thumbs.db,.trash/**,*.tmp,.obsidian/workspace.json
```

Run:

```powershell
java -jar sync-client-0.0.1-SNAPSHOT.jar sync-client.properties
```

## macOS configuration

Copy the jar and example properties to the Mac. Use the same server URL and
remote folder UUID, but a Mac path and the Mac's own device credentials:

```properties
server.base-url=http://192.168.1.20:8080
sync.local-root=/Users/zoey/MyDriveSyncTest
sync.remote-folder-id=REMOTE_FOLDER_UUID
device.id=MAC_DEVICE_UUID
device.token=MAC_ONE_TIME_TOKEN
sync.poll-seconds=10
sync.full-scan-seconds=60
sync.max-change-batch=100
sync.ignore=.DS_Store,Thumbs.db,.trash/**,*.tmp,.obsidian/workspace.json
```

Run:

```bash
java -jar sync-client-0.0.1-SNAPSHOT.jar sync-client.properties
```

The server address must be reachable from both computers. `localhost` works
only when the server runs on that same computer. Open port 8080 only on a
trusted network and allow Java through the host firewall if necessary.

## Safe first test

Use two new, disposable directories—not Documents, Desktop, a home directory,
or this Git repository. Start with one client, create a small text file, confirm
it appears through the server, then start the second client and confirm it is
downloaded. Test edits and deletes only after that succeeds.

## Phase 7 limits

- It is intended for controlled learning tests, not production data.
- Simultaneous edits do not yet create conflict copies; robust conflict
  resolution and durable retry/idempotency are Phase 8 work.
- Empty local directories are not uploaded until they contain a file.
- Existing server files created before the Phase 7 change journal are not an
  automatic initial snapshot. Begin with a new remote test folder.
- Plain HTTP exposes bearer tokens to the network. Use HTTPS or a trusted VPN
  before syncing across an untrusted network or the internet.
- Run it from a terminal for now; OS login startup/service installers are not
  included yet.
