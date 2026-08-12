/*
 * Test one-time raw token return, only-hash persistence, valid authentication,
 * revoked-token rejection, owner-scoped listing/revocation, and no token/hash
 * fields in normal DeviceResponse.
 */
package com.mydrive.drive.device;

import com.mydrive.drive.account.AppUser;
import com.mydrive.drive.account.AppUserRepository;
import com.mydrive.drive.device.dto.DeviceResponse;
import com.mydrive.drive.device.dto.DeviceTokenResponse;
import com.mydrive.drive.security.CurrentUserService;
import com.mydrive.drive.folder.FolderRepository;
import com.mydrive.drive.sync.SyncChangeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceTokenServiceTests {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID DEVICE_ID = UUID.randomUUID();

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private FolderRepository folderRepository;
    @Mock
    private SyncChangeRepository syncChangeRepository;

    @InjectMocks
    private DeviceTokenService service;

    @Test
    void registerReturnsRawTokenOnceAndPersistsOnlyItsHash() throws Exception {
        when(currentUserService.requireCurrentUser()).thenReturn(user());
        when(deviceRepository.save(any(Device.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DeviceTokenResponse response = service.register("  Zoey's laptop  ", null);

        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(captor.capture());
        Device stored = captor.getValue();
        String expectedHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(response.token().getBytes(StandardCharsets.UTF_8)));

        assertThat(response.token()).isNotBlank().hasSizeGreaterThanOrEqualTo(43);
        assertThat(stored.getTokenHash()).isEqualTo(expectedHash).hasSize(64);
        assertThat(stored.getTokenHash()).isNotEqualTo(response.token());
        assertThat(stored.getUserId()).isEqualTo(USER_ID);
        assertThat(response.device().name()).isEqualTo("Zoey's laptop");
        assertThat(response.device().lastSeenAt()).isNull();
        assertThat(Arrays.stream(DeviceResponse.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .doesNotContain("token", "tokenHash");
    }

    @Test
    void authenticateValidTokenReturnsPrincipalAndTouchesDevice() throws Exception {
        String rawToken = "valid-device-token";
        String hash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        Device device = device(hash, null);
        when(deviceRepository.findByTokenHashAndRevokedAtIsNull(hash))
                .thenReturn(Optional.of(device));
        when(appUserRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(deviceRepository.save(device)).thenReturn(device);

        Optional<DevicePrincipal> principal = service.authenticate(rawToken);

        assertThat(principal).isPresent();
        assertThat(principal.get().deviceId()).isEqualTo(DEVICE_ID);
        assertThat(principal.get().userId()).isEqualTo(USER_ID);
        assertThat(principal.get().getName()).isEqualTo("user@example.com");
        assertThat(device.getLastSeenAt()).isNotNull();
        verify(deviceRepository).save(device);
    }

    @Test
    void invalidOrRevokedTokenReturnsEmptyWithoutUserLookup() {
        when(deviceRepository.findByTokenHashAndRevokedAtIsNull(any()))
                .thenReturn(Optional.empty());

        assertThat(service.authenticate("invalid-or-revoked")).isEmpty();

        verifyNoInteractions(appUserRepository);
        verify(deviceRepository, never()).save(any());
    }

    @Test
    void listingIsScopedToCurrentUserAndContainsNoSecrets() {
        Device device = device("a".repeat(64), null);
        when(currentUserService.requireCurrentUser()).thenReturn(user());
        when(deviceRepository.findAllByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of(device));

        List<DeviceResponse> result = service.listCurrentUsersDevices();

        verify(deviceRepository).findAllByUserIdOrderByCreatedAtDesc(USER_ID);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(DEVICE_ID);
        assertThat(result.getFirst().name()).isEqualTo("Laptop");
    }

    @Test
    void revokeFindsDeviceUsingBothDeviceIdAndCurrentUserId() {
        Device device = device("a".repeat(64), null);
        when(currentUserService.requireCurrentUser()).thenReturn(user());
        when(deviceRepository.findByIdAndUserId(DEVICE_ID, USER_ID))
                .thenReturn(Optional.of(device));
        when(deviceRepository.save(device)).thenReturn(device);

        service.revoke(DEVICE_ID);

        verify(deviceRepository).findByIdAndUserId(DEVICE_ID, USER_ID);
        verify(deviceRepository).save(device);
        assertThat(device.isRevoked()).isTrue();
    }

    @Test
    void foreignDeviceCannotBeRevoked() {
        when(currentUserService.requireCurrentUser()).thenReturn(user());
        when(deviceRepository.findByIdAndUserId(DEVICE_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(DEVICE_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Device not found");

        verify(deviceRepository, never()).save(any());
    }

    private AppUser user() {
        return new AppUser(USER_ID, "user@example.com", "hash", Instant.now());
    }

    private Device device(String tokenHash, Instant revokedAt) {
        return new Device(
                DEVICE_ID,
                USER_ID,
                "Laptop",
                tokenHash,
                Instant.now(),
                null,
                revokedAt);
    }
}
