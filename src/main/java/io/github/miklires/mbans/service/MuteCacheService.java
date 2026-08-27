package io.github.miklires.mbans.service;

import io.github.miklires.mbans.model.Punishment;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MuteCacheService {
    private final ConcurrentHashMap<UUID,Punishment> active=new ConcurrentHashMap<>();
    public void update(UUID uuid,Optional<Punishment> punishment){if(uuid==null)return;if(punishment.isPresent()&&punishment.get().isActive()&&!punishment.get().isExpired())active.put(uuid,punishment.get());else active.remove(uuid);}
    public void put(Punishment punishment){if(punishment.getTargetUuid()!=null)active.put(punishment.getTargetUuid(),punishment);}
    public void put(UUID uuid,Punishment punishment){if(uuid!=null)active.put(uuid,punishment);}
    public void replace(Punishment punishment){active.replaceAll((uuid,current)->current.getId()==punishment.getId()?punishment:current);}
    public void invalidatePunishment(long punishmentId){active.entrySet().removeIf(entry->entry.getValue().getId()==punishmentId);}
    public void invalidate(UUID uuid){if(uuid!=null)active.remove(uuid);}
    public Optional<Punishment> get(UUID uuid){Punishment punishment=active.get(uuid);if(punishment==null)return Optional.empty();if(!punishment.isActive()||(punishment.getExpiresAt()!=null&&!punishment.getExpiresAt().isAfter(Instant.now()))){active.remove(uuid,punishment);return Optional.empty();}return Optional.of(punishment);}
    public void remove(UUID uuid){active.remove(uuid);}
}
