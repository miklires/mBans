package io.github.miklires.mbans.service;

import io.github.miklires.mbans.model.Punishment;
import io.github.miklires.mbans.model.PunishmentType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MuteCacheServiceTest {
    @Test void storesAndInvalidatesActiveMute(){MuteCacheService cache=new MuteCacheService();UUID uuid=UUID.randomUUID();Punishment mute=mute(uuid,null);cache.put(mute);assertSame(mute,cache.get(uuid).orElseThrow());cache.invalidate(uuid);assertTrue(cache.get(uuid).isEmpty());}
    @Test void expiredMuteEvictsItself(){MuteCacheService cache=new MuteCacheService();UUID uuid=UUID.randomUUID();cache.put(mute(uuid,Instant.now().minusSeconds(1)));assertTrue(cache.get(uuid).isEmpty());}
    @Test void emptyPreloadClearsPreviousValue(){MuteCacheService cache=new MuteCacheService();UUID uuid=UUID.randomUUID();cache.put(mute(uuid,null));cache.update(uuid,Optional.empty());assertTrue(cache.get(uuid).isEmpty());}
    private static Punishment mute(UUID uuid,Instant expires){Punishment value=new Punishment();value.setType(PunishmentType.MUTE);value.setTargetUuid(uuid);value.setActive(true);value.setExpiresAt(expires);return value;}
}
