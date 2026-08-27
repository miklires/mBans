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
    @Test void invalidatesOneNetworkPunishmentAcrossPlayers(){MuteCacheService cache=new MuteCacheService();UUID first=UUID.randomUUID();UUID second=UUID.randomUUID();Punishment ipMute=mute(first,null);ipMute.setId(41);cache.put(first,ipMute);cache.put(second,ipMute);cache.invalidatePunishment(41);assertTrue(cache.get(first).isEmpty());assertTrue(cache.get(second).isEmpty());}
    @Test void reasonReplacementUpdatesEveryCachedCopy(){MuteCacheService cache=new MuteCacheService();UUID first=UUID.randomUUID();UUID second=UUID.randomUUID();Punishment old=mute(first,null);old.setId(42);cache.put(first,old);cache.put(second,old);Punishment updated=mute(first,null);updated.setId(42);updated.setReason("corrected");cache.replace(updated);assertEquals("corrected",cache.get(first).orElseThrow().getReason());assertSame(updated,cache.get(second).orElseThrow());}
    private static Punishment mute(UUID uuid,Instant expires){Punishment value=new Punishment();value.setId(1);value.setType(PunishmentType.MUTE);value.setTargetUuid(uuid);value.setActive(true);value.setExpiresAt(expires);return value;}
}
