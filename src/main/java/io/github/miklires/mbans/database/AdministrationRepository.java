package io.github.miklires.mbans.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AdministrationRepository {

    private final DatabaseManager db;

    public AdministrationRepository(DatabaseManager db) {
        this.db = db;
    }

    public List<Long> rollback(String staff, Instant since, String revokedBy) throws SQLException {
        String find = "SELECT id FROM mbans_punishments WHERE LOWER(issued_by_name) = LOWER(?) AND issued_at >= ? AND active = TRUE";
        List<Long> ids = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(find)) {
            ps.setString(1, staff);
            ps.setLong(2, since.getEpochSecond());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getLong(1));
            }
        }
        String update = "UPDATE mbans_punishments SET active = FALSE, revoked_by_name = ?, revoked_at = ?, revoke_reason = ? WHERE id = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(update)) {
            for (long id : ids) {
                ps.setString(1, revokedBy);
                ps.setLong(2, Instant.now().getEpochSecond());
                ps.setString(3, "staff rollback");
                ps.setLong(4, id);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        return ids;
    }

    public boolean allow(long punishmentId, UUID playerUuid) throws SQLException {
        String check = "SELECT id FROM mbans_ip_allowlist WHERE punishment_id = ? AND player_uuid = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(check)) {
            ps.setLong(1, punishmentId);
            ps.setString(2, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return false;
            }
        }
        String insert = "INSERT INTO mbans_ip_allowlist (punishment_id, player_uuid) VALUES (?, ?)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(insert)) {
            ps.setLong(1, punishmentId);
            ps.setString(2, playerUuid.toString());
            ps.executeUpdate();
            return true;
        }
    }

    public boolean isAllowed(long punishmentId, UUID playerUuid) throws SQLException {
        String sql = "SELECT id FROM mbans_ip_allowlist WHERE punishment_id = ? AND player_uuid = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, punishmentId);
            ps.setString(2, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public long addNote(UUID playerUuid, UUID authorUuid, String authorName, String note) throws SQLException {
        String sql = "INSERT INTO mbans_staff_notes (player_uuid, author_uuid, author_name, note, created_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, authorUuid == null ? null : authorUuid.toString());
            ps.setString(3, authorName);
            ps.setString(4, note);
            ps.setLong(5, Instant.now().getEpochSecond());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    public List<StaffNote> notes(UUID playerUuid, int limit) throws SQLException {
        String sql = "SELECT id, author_name, note, created_at FROM mbans_staff_notes "
                + "WHERE player_uuid = ? ORDER BY created_at DESC LIMIT ?";
        List<StaffNote> notes = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) notes.add(new StaffNote(rs.getLong("id"), rs.getString("author_name"),
                        rs.getString("note"), Instant.ofEpochSecond(rs.getLong("created_at"))));
            }
        }
        return notes;
    }

    public StaffStats stats(String staff) throws SQLException {
        String sql = "SELECT COUNT(*) AS total, "
                + "SUM(CASE WHEN active = FALSE AND revoked_at IS NOT NULL THEN 1 ELSE 0 END) AS revoked, "
                + "SUM(CASE WHEN type = 'BAN' THEN 1 ELSE 0 END) AS bans, "
                + "SUM(CASE WHEN type IN ('MUTE','IP_MUTE','SHADOW_MUTE') THEN 1 ELSE 0 END) AS mutes, "
                + "SUM(CASE WHEN type = 'WARN' THEN 1 ELSE 0 END) AS warns "
                + ", AVG(CASE WHEN expires_at IS NOT NULL THEN expires_at - issued_at ELSE NULL END) AS avg_duration "
                + "FROM mbans_punishments WHERE LOWER(issued_by_name) = LOWER(?)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, staff);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int total = rs.getInt("total");
                    int revoked = rs.getInt("revoked");
                    return new StaffStats(total, revoked, rs.getInt("bans"), rs.getInt("mutes"), rs.getInt("warns"),
                            rs.getDouble("avg_duration"), total == 0 ? 0.0 : (double) revoked / total);
                }
            }
        }
        return new StaffStats(0, 0, 0, 0, 0, 0.0, 0.0);
    }

    public long submitAppeal(long punishmentId,UUID playerUuid,String playerName,String message)throws SQLException{
        String existing="SELECT id FROM mbans_appeals WHERE punishment_id=? AND player_uuid=? LIMIT 1";
        try(Connection c=db.getConnection();PreparedStatement ps=c.prepareStatement(existing)){ps.setLong(1,punishmentId);ps.setString(2,playerUuid.toString());try(ResultSet rs=ps.executeQuery()){if(rs.next())return -1;}}
        String sql="INSERT INTO mbans_appeals (punishment_id,player_uuid,player_name,message,status,created_at) VALUES (?,?,?,?,?,?)";
        try(Connection c=db.getConnection();PreparedStatement ps=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){ps.setLong(1,punishmentId);ps.setString(2,playerUuid.toString());ps.setString(3,playerName);ps.setString(4,message);ps.setString(5,"OPEN");ps.setLong(6,Instant.now().getEpochSecond());ps.executeUpdate();try(ResultSet rs=ps.getGeneratedKeys()){return rs.next()?rs.getLong(1):0;}}
    }

    public List<Appeal> openAppeals(int limit,int offset)throws SQLException{
        List<Appeal> out=new ArrayList<>();String sql="SELECT * FROM mbans_appeals WHERE status='OPEN' ORDER BY created_at ASC LIMIT ? OFFSET ?";
        try(Connection c=db.getConnection();PreparedStatement ps=c.prepareStatement(sql)){ps.setInt(1,limit);ps.setInt(2,offset);try(ResultSet rs=ps.executeQuery()){while(rs.next())out.add(mapAppeal(rs));}}return out;
    }

    public java.util.Optional<Appeal> appeal(long id)throws SQLException{try(Connection c=db.getConnection();PreparedStatement ps=c.prepareStatement("SELECT * FROM mbans_appeals WHERE id=?")){ps.setLong(1,id);try(ResultSet rs=ps.executeQuery()){return rs.next()?java.util.Optional.of(mapAppeal(rs)):java.util.Optional.empty();}}}

    public boolean reviewAppeal(long id,String status,String reviewer,String note)throws SQLException{
        String sql="UPDATE mbans_appeals SET status=?,reviewed_by=?,reviewed_at=?,review_note=? WHERE id=? AND status='OPEN'";
        try(Connection c=db.getConnection();PreparedStatement ps=c.prepareStatement(sql)){ps.setString(1,status);ps.setString(2,reviewer);ps.setLong(3,Instant.now().getEpochSecond());ps.setString(4,note);ps.setLong(5,id);return ps.executeUpdate()==1;}
    }

    private Appeal mapAppeal(ResultSet rs)throws SQLException{return new Appeal(rs.getLong("id"),rs.getLong("punishment_id"),UUID.fromString(rs.getString("player_uuid")),rs.getString("player_name"),rs.getString("message"),rs.getString("status"),Instant.ofEpochSecond(rs.getLong("created_at")));}

    public record StaffStats(int total, int revoked, int bans, int mutes, int warns,
                             double averageDurationSeconds, double revocationRate) {}
    public record StaffNote(long id, String author, String text, Instant createdAt) {}
    public record Appeal(long id,long punishmentId,UUID playerUuid,String playerName,String message,String status,Instant createdAt){}
}
