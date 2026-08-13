package io.github.miklires.mbans.model;

import java.time.Instant;
import java.util.UUID;

public class Punishment {

    private long id;
    private PunishmentType type;
    private UUID targetUuid;
    private String targetName;
    private String targetIp;
    private String reason;
    private UUID issuedByUuid;
    private String issuedByName;
    private Instant issuedAt;
    private Instant expiresAt;
    private boolean active;
    private String revokedByName;
    private Instant revokedAt;
    private String revokeReason;
    private String evidence;
    private String appealId;
    private boolean silent;
    private String serverName;

    public Punishment() {}

    public boolean isPermanent() {
        return expiresAt == null;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public PunishmentType getType() { return type; }
    public void setType(PunishmentType type) { this.type = type; }
    public UUID getTargetUuid() { return targetUuid; }
    public void setTargetUuid(UUID targetUuid) { this.targetUuid = targetUuid; }
    public String getTargetName() { return targetName; }
    public void setTargetName(String targetName) { this.targetName = targetName; }
    public String getTargetIp() { return targetIp; }
    public void setTargetIp(String targetIp) { this.targetIp = targetIp; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public UUID getIssuedByUuid() { return issuedByUuid; }
    public void setIssuedByUuid(UUID issuedByUuid) { this.issuedByUuid = issuedByUuid; }
    public String getIssuedByName() { return issuedByName; }
    public void setIssuedByName(String issuedByName) { this.issuedByName = issuedByName; }
    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getRevokedByName() { return revokedByName; }
    public void setRevokedByName(String revokedByName) { this.revokedByName = revokedByName; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public String getRevokeReason() { return revokeReason; }
    public void setRevokeReason(String revokeReason) { this.revokeReason = revokeReason; }
    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }
    public String getAppealId() { return appealId; }
    public void setAppealId(String appealId) { this.appealId = appealId; }
    public boolean isSilent() { return silent; }
    public void setSilent(boolean silent) { this.silent = silent; }
    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName; }
}
