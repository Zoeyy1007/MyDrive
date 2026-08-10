package com.mydrive.drive.trash;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrashCleanupJobTests {

    @Test
    void scheduledMethodDelegatesToTrashService() {
        TrashService trashService = mock(TrashService.class);
        when(trashService.deleteExpiredTrash()).thenReturn(2);
        TrashCleanupJob job = new TrashCleanupJob(trashService);

        job.cleanupExpiredTrash();

        verify(trashService).deleteExpiredTrash();
    }
}
