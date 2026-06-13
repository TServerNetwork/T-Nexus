package network.tserver.tnexus.database.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceWorldResetRepositoryTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldFindScheduledResetByWorldAndNextResetAt() throws Exception {
        TNexus plugin = loadPlugin();
        ResourceWorldResetRepository repository = new ResourceWorldResetRepository(plugin.getDatabaseManager());
        LocalDateTime resetAt = LocalDateTime.of(2026, 6, 14, 3, 0);
        LocalDateTime nextResetAt = LocalDateTime.of(2026, 6, 21, 3, 0);

        long insertedId = repository.insert(ResourceWorldResetRepository.ResourceWorldResetRecord.scheduled(
                        "resource",
                        resetAt,
                        nextResetAt,
                        123456789L))
                .get(5, TimeUnit.SECONDS);
        repository.insert(ResourceWorldResetRepository.ResourceWorldResetRecord.scheduled(
                        "resource_nether",
                        resetAt.plusHours(1),
                        nextResetAt.plusDays(1),
                        null))
                .get(5, TimeUnit.SECONDS);

        Optional<ResourceWorldResetRepository.ResourceWorldResetEntry> found = repository
                .findByWorldNameAndNextResetAt("resource", nextResetAt)
                .get(5, TimeUnit.SECONDS);

        assertTrue(found.isPresent());
        assertEquals(insertedId, found.get().id());
        assertEquals("resource", found.get().worldName());
        assertEquals(ResourceWorldResetRepository.ResetStatus.SCHEDULED, found.get().status());
        assertEquals(123456789L, found.get().seed());
        assertNull(found.get().errorMessage());
    }

    @Test
    void shouldUpdateStatusesAcrossResetLifecycle() throws Exception {
        TNexus plugin = loadPlugin();
        ResourceWorldResetRepository repository = new ResourceWorldResetRepository(plugin.getDatabaseManager());
        LocalDateTime baseTime = LocalDateTime.of(2026, 6, 14, 4, 0);

        long completedFlowId = repository.insert(ResourceWorldResetRepository.ResourceWorldResetRecord.scheduled(
                        "resource",
                        baseTime,
                        baseTime.plusDays(7),
                        987654321L))
                .get(5, TimeUnit.SECONDS);
        assertStatus(repository, "resource", baseTime.plusDays(7), ResourceWorldResetRepository.ResetStatus.SCHEDULED);

        assertTrue(repository.updateStatus(
                        completedFlowId,
                        ResourceWorldResetRepository.ResetStatus.IN_PROGRESS,
                        null)
                .get(5, TimeUnit.SECONDS));
        assertStatus(repository, "resource", baseTime.plusDays(7), ResourceWorldResetRepository.ResetStatus.IN_PROGRESS);

        assertTrue(repository.updateStatus(
                        completedFlowId,
                        ResourceWorldResetRepository.ResetStatus.COMPLETED,
                        null)
                .get(5, TimeUnit.SECONDS));
        assertStatus(repository, "resource", baseTime.plusDays(7), ResourceWorldResetRepository.ResetStatus.COMPLETED);

        LocalDateTime failedNextResetAt = baseTime.plusDays(14);
        long failedFlowId = repository.insert(ResourceWorldResetRepository.ResourceWorldResetRecord.scheduled(
                        "resource",
                        baseTime.plusDays(7),
                        failedNextResetAt,
                        null))
                .get(5, TimeUnit.SECONDS);

        assertTrue(repository.updateStatus(
                        failedFlowId,
                        ResourceWorldResetRepository.ResetStatus.FAILED,
                        "FAWE rollback failed")
                .get(5, TimeUnit.SECONDS));

        ResourceWorldResetRepository.ResourceWorldResetEntry failedEntry = repository
                .findByWorldNameAndNextResetAt("resource", failedNextResetAt)
                .get(5, TimeUnit.SECONDS)
                .orElseThrow();
        assertEquals(ResourceWorldResetRepository.ResetStatus.FAILED, failedEntry.status());
        assertEquals("FAWE rollback failed", failedEntry.errorMessage());
    }

    @Test
    void shouldUpdateCompletedSeedOnExistingResetEntry() throws Exception {
        TNexus plugin = loadPlugin();
        ResourceWorldResetRepository repository = new ResourceWorldResetRepository(plugin.getDatabaseManager());
        LocalDateTime resetAt = LocalDateTime.of(2026, 6, 14, 4, 0);
        LocalDateTime nextResetAt = resetAt.plusDays(7);

        long id = repository.insert(ResourceWorldResetRepository.ResourceWorldResetRecord.scheduled(
                        "resource",
                        resetAt,
                        nextResetAt,
                        null))
                .get(5, TimeUnit.SECONDS);

        assertTrue(repository.updateStatusAndSeed(
                        id,
                        ResourceWorldResetRepository.ResetStatus.COMPLETED,
                        987654321L)
                .get(5, TimeUnit.SECONDS));

        ResourceWorldResetRepository.ResourceWorldResetEntry entry = repository
                .findByWorldNameAndNextResetAt("resource", nextResetAt)
                .get(5, TimeUnit.SECONDS)
                .orElseThrow();
        assertEquals(ResourceWorldResetRepository.ResetStatus.COMPLETED, entry.status());
        assertEquals(987654321L, entry.seed());
        assertNull(entry.errorMessage());
    }

    @Test
    void shouldUpsertLatestNextResetTimeByWorld() throws Exception {
        TNexus plugin = loadPlugin();
        ResourceWorldResetRepository repository = new ResourceWorldResetRepository(plugin.getDatabaseManager());
        LocalDateTime firstReset = LocalDateTime.of(2026, 7, 1, 4, 0);
        LocalDateTime updatedReset = firstReset.plusDays(90);

        repository.upsertScheduledReset("resource", firstReset, 13579L).get(5, TimeUnit.SECONDS);
        assertEquals(firstReset, repository.findNextResetTime("resource").get(5, TimeUnit.SECONDS).orElseThrow());

        repository.upsertScheduledReset("resource", updatedReset, 24680L).get(5, TimeUnit.SECONDS);
        assertEquals(updatedReset, repository.findNextResetTime("resource").get(5, TimeUnit.SECONDS).orElseThrow());

        Optional<ResourceWorldResetRepository.ResourceWorldResetEntry> entry = repository
                .findByWorldNameAndNextResetAt("resource", updatedReset)
                .get(5, TimeUnit.SECONDS);
        assertTrue(entry.isPresent());
        assertEquals(ResourceWorldResetRepository.ResetStatus.SCHEDULED, entry.get().status());
        assertEquals(24680L, entry.get().seed());
    }

    private TNexus loadPlugin() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        return TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2DatabaseOnlyTNexus.class);
    }

    private void assertStatus(
            ResourceWorldResetRepository repository,
            String worldName,
            LocalDateTime nextResetAt,
            ResourceWorldResetRepository.ResetStatus expectedStatus) throws Exception {
        ResourceWorldResetRepository.ResourceWorldResetEntry entry = repository
                .findByWorldNameAndNextResetAt(worldName, nextResetAt)
                .get(5, TimeUnit.SECONDS)
                .orElseThrow();
        assertEquals(expectedStatus, entry.status());
    }

    @Test
    void shouldFindLatestEntriesByWorldAndCompletionStatus() throws Exception {
        TNexus plugin = loadPlugin();
        ResourceWorldResetRepository repository = new ResourceWorldResetRepository(plugin.getDatabaseManager());
        LocalDateTime firstReset = LocalDateTime.of(2026, 6, 14, 4, 0);
        LocalDateTime secondReset = firstReset.plusDays(7);
        LocalDateTime thirdReset = secondReset.plusDays(7);

        long firstId = repository.insert(ResourceWorldResetRepository.ResourceWorldResetRecord.scheduled(
                        "resource",
                        firstReset,
                        secondReset,
                        null))
                .get(5, TimeUnit.SECONDS);
        repository.updateStatus(firstId, ResourceWorldResetRepository.ResetStatus.COMPLETED, null)
                .get(5, TimeUnit.SECONDS);

        long latestId = repository.insert(ResourceWorldResetRepository.ResourceWorldResetRecord.scheduled(
                        "resource",
                        secondReset,
                        thirdReset,
                        null))
                .get(5, TimeUnit.SECONDS);
        repository.updateStatus(latestId, ResourceWorldResetRepository.ResetStatus.FAILED, "boom")
                .get(5, TimeUnit.SECONDS);

        ResourceWorldResetRepository.ResourceWorldResetEntry latestEntry = repository.findLatestEntry("resource")
                .get(5, TimeUnit.SECONDS)
                .orElseThrow();
        ResourceWorldResetRepository.ResourceWorldResetEntry latestCompleted = repository
                .findLatestCompletedEntry("resource")
                .get(5, TimeUnit.SECONDS)
                .orElseThrow();

        assertEquals(latestId, latestEntry.id());
        assertEquals(ResourceWorldResetRepository.ResetStatus.FAILED, latestEntry.status());
        assertEquals(firstId, latestCompleted.id());
        assertEquals(ResourceWorldResetRepository.ResetStatus.COMPLETED, latestCompleted.status());
    }
}
