package uga.menik.csx370.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.SessionScope;

import uga.menik.csx370.models.FollowableUser;

@Service
@SessionScope
public class PeopleService {

    private final DataSource dataSource;
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("MMM dd, yyyy, hh:mm a");

    @Autowired
    public PeopleService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<FollowableUser> getFollowableUsers(String userIdToExclude) throws SQLException {
        final String sql = """
                SELECT 
                    u.userId,
                    u.firstName,
                    u.lastName,
                    CASE 
                        WHEN f.followerId IS NULL THEN false
                        ELSE true
                    END AS isFollowed,
                    MAX(p.postDate) AS lastPostTime
                FROM user u
                LEFT JOIN post p
                    ON u.userId = p.userId
                LEFT JOIN follow f
                    ON f.followedId = u.userId
                   AND f.followerId = ?
                WHERE u.userId <> ?
                GROUP BY u.userId, u.firstName, u.lastName, f.followerId
                ORDER BY u.firstName, u.lastName
                """;

        List<FollowableUser> followableUsers = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, Integer.parseInt(userIdToExclude));
            pstmt.setString(2, userIdToExclude);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String userId = rs.getString("userId");
                    String firstName = rs.getString("firstName");
                    String lastName = rs.getString("lastName");
                    boolean isFollowed = rs.getBoolean("isFollowed");

                    Timestamp lastPostTimestamp = rs.getTimestamp("lastPostTime");
                    String lastPostText = formatLastPostTime(lastPostTimestamp);

                    followableUsers.add(
                            new FollowableUser(userId, firstName, lastName, isFollowed, lastPostText)
                    );
                }
            }
        }

        return followableUsers;
    }

    public void followUnfollowUser(String loggedInUserId, String targetUserId, boolean isFollow)
            throws SQLException {

        if (loggedInUserId == null || targetUserId == null || loggedInUserId.equals(targetUserId)) {
            return;
        }

        if (isFollow) {
            followUser(loggedInUserId, targetUserId);
        } else {
            unfollowUser(loggedInUserId, targetUserId);
        }
    }

    private void followUser(String loggedInUserId, String targetUserId) throws SQLException {
        final String sql = """
                INSERT INTO follow (followerId, followedId)
                SELECT ?, ?
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM follow
                    WHERE followerId = ?
                      AND followedId = ?
                )
                """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, Integer.parseInt(loggedInUserId));
            pstmt.setInt(2, Integer.parseInt(targetUserId));
            pstmt.setInt(3, Integer.parseInt(loggedInUserId));
            pstmt.setInt(4, Integer.parseInt(targetUserId));

            int rows = pstmt.executeUpdate();
            if (rows > 0) {
                System.out.println("[DB] Follow inserted: user " + loggedInUserId + " -> user " + targetUserId);
            } else {
                System.out.println("[DB] Follow already exists: user " + loggedInUserId + " -> user " + targetUserId);
            }
        }
    }

    private void unfollowUser(String loggedInUserId, String targetUserId) throws SQLException {
        final String sql = """
                DELETE FROM follow
                WHERE followerId = ?
                  AND followedId = ?
                """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, Integer.parseInt(loggedInUserId));
            pstmt.setInt(2, Integer.parseInt(targetUserId));

            int rows = pstmt.executeUpdate();
            if (rows > 0) {
                System.out.println("[DB] Unfollow deleted: user " + loggedInUserId + " -/-> user " + targetUserId);
            } else {
                System.out.println("[DB] Unfollow skipped (no record found): user " + loggedInUserId + " -/-> user " + targetUserId);
            }
        }
    }

    private String formatLastPostTime(Timestamp timestamp) {
        if (timestamp == null) {
            return "Unknown";
        }

        LocalDateTime dateTime = timestamp.toLocalDateTime();
        return dateTime.format(DATE_FORMATTER);
    }
}