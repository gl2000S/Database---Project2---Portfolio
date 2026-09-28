package uga.menik.csx370.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import uga.menik.csx370.models.Post;
import uga.menik.csx370.models.User;
import uga.menik.csx370.models.Comment;
import uga.menik.csx370.models.ExpandedPost;


/**
 * Service class that handles all post-related database operations.
 * This includes creating posts, parsing and storing hashtags,
 * and fetching posts ordered newest to oldest.
 */
@Service
public class PostService {

    private final DataSource dataSource;

    @Autowired
    public PostService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Creates a new post for the given user, then parses and stores
     * any hashtags found in the post content.
     * Returns true if the post was created successfully, false otherwise.
     */
    public boolean createPost(String content, String userId) throws SQLException {
        final String insertPostSql = "insert into post (userId, content) values (?, ?)";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(insertPostSql,
                        Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, userId);
            pstmt.setString(2, content);

            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected > 0) {
                // Get the auto-generated postId.
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int postId = generatedKeys.getInt(1);
                        // Parse hashtags from content and store them.
                        List<String> hashtags = parseHashtags(content);
                        storeHashtags(postId, hashtags, conn);
                    }
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Parses hashtags from post content using regex.
     * For example: "Hello #world #2025" returns ["world", "2025"].
     * Hashtags are stored in lowercase for consistent searching.
     */
    private List<String> parseHashtags(String content) {
        List<String> hashtags = new ArrayList<>();
        // Regex matches # followed by one or more word characters.
        Pattern pattern = Pattern.compile("#(\\w+)");
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            hashtags.add(matcher.group(1).toLowerCase());
        }
        return hashtags;
    }

    /**
     * Stores hashtags in the hashtag and post_hashtag tables.
     * If a hashtag already exists, it reuses the existing record.
     * Uses the same connection as createPost to keep things efficient.
     */
    private void storeHashtags(int postId, List<String> hashtags, Connection conn)
            throws SQLException {
        // Insert hashtag if it doesn't exist, ignore if it does (due to UNIQUE constraint).
        final String insertHashtagSql = "insert ignore into hashtag (tag) values (?)";
        // Get the hashtagId for a given tag.
        final String selectHashtagSql = "select hashtagId from hashtag where tag = ?";
        // Link the hashtag to the post.
        final String insertPostHashtagSql = "insert ignore into post_hashtag (postId, hashtagId) values (?, ?)";

        for (String tag : hashtags) {
            // Insert the hashtag (or ignore if it already exists).
            try (PreparedStatement insertStmt = conn.prepareStatement(insertHashtagSql)) {
                insertStmt.setString(1, tag);
                insertStmt.executeUpdate();
            }

            // Get the hashtagId.
            int hashtagId = -1;
            try (PreparedStatement selectStmt = conn.prepareStatement(selectHashtagSql)) {
                selectStmt.setString(1, tag);
                try (ResultSet rs = selectStmt.executeQuery()) {
                    if (rs.next()) {
                        hashtagId = rs.getInt("hashtagId");
                    }
                }
            }

            // Link the hashtag to the post.
            if (hashtagId != -1) {
                try (PreparedStatement linkStmt = conn.prepareStatement(insertPostHashtagSql)) {
                    linkStmt.setInt(1, postId);
                    linkStmt.setInt(2, hashtagId);
                    linkStmt.executeUpdate();
                }
            }
        }
    }

    /**
     * Fetches all posts ordered from newest to oldest.
     * Each post includes the hearts count, comments count,
     * and whether the logged in user has hearted or bookmarked it.
     */
    public List<Post> getPostsNewestFirst(String loggedInUserId) throws SQLException {
        final String sql =
            "select p.postId, p.content, " +
            "date_format(p.postDate, '%b %d, %Y, %h:%i %p') as postDate, " +
            "u.userId, u.firstName, u.lastName, " +
            "count(distinct h.userId) as heartsCount, " +
            "count(distinct c.commentId) as commentsCount, " +
            "max(case when h.userId = ? then 1 else 0 end) as isHearted, " +
            "max(case when b.userId = ? then 1 else 0 end) as isBookmarked, " +
            "count(distinct uv.userId) as upvotesCount, " +
            "max(case when uv.userId = ? then 1 else 0 end) as isUpvoted " +
            "from post p " +
            "join user u on p.userId = u.userId " +
            "left join heart h on p.postId = h.postId " +
            "left join comment c on p.postId = c.postId " +
            "left join bookmark b on p.postId = b.postId and b.userId = ? " +
            "left join upvote uv on p.postId = uv.postId " +
            "where p.userId = ? " +
            "or p.userId in ( " +
            "   select followedId from follow where followerId = ? " +
            ") " +
            "group by p.postId, p.content, p.postDate, u.userId, u.firstName, u.lastName " +
            "order by p.postDate desc";

        return fetchPosts(sql, loggedInUserId);
    }

    /**
     * Fetches posts made by a specific user, ordered newest to oldest.
     * Used for the profile page.
     */
    public List<Post> getPostsByUser(String profileUserId, String loggedInUserId)
            throws SQLException {
        final String sql =
            "select p.postId, p.content, " +
            "date_format(p.postDate, '%b %d, %Y, %h:%i %p') as postDate, " +
            "u.userId, u.firstName, u.lastName, " +
            "count(distinct h.userId) as heartsCount, " +
            "count(distinct c.commentId) as commentsCount, " +
            "max(case when h.userId = ? then 1 else 0 end) as isHearted, " +
            "max(case when b.userId = ? then 1 else 0 end) as isBookmarked, " +
            "count(distinct uv.userId) as upvotesCount, " +
            "max(case when uv.userId = ? then 1 else 0 end) as isUpvoted " +
            "from post p " +
            "join user u on p.userId = u.userId " +
            "left join heart h on p.postId = h.postId " +
            "left join comment c on p.postId = c.postId " +
            "left join bookmark b on p.postId = b.postId and b.userId = ? " +
            "left join upvote uv on p.postId = uv.postId " +
            "where p.userId = ? " +
            "group by p.postId, p.content, p.postDate, u.userId, u.firstName, u.lastName " +
            "order by p.postDate desc";

        return fetchPostsByUser(sql, loggedInUserId, profileUserId);
    }

    /**
     * Helper method that runs a post-fetching SQL query for getPostsNewestFirst.
     * Maps each result row to a Post model object.
     */
    private List<Post> fetchPosts(String sql, String loggedInUserId) throws SQLException {
        List<Post> posts = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, loggedInUserId); // isHearted check
            pstmt.setString(2, loggedInUserId); // isBookmarked check
            pstmt.setString(3, loggedInUserId); // isUpvoted check
            pstmt.setString(4, loggedInUserId); // bookmark join

            pstmt.setString(5, loggedInUserId); // my posts
            pstmt.setString(6, loggedInUserId); // followed users

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    posts.add(mapRowToPost(rs));
                }
            }
        }
        return posts;
    }

    /**
     * Helper method that runs a post-fetching SQL query for getPostsByUser.
     * Maps each result row to a Post model object.
     */
    private List<Post> fetchPostsByUser(String sql, String loggedInUserId, String profileUserId)
            throws SQLException {
        List<Post> posts = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, loggedInUserId); // isHearted check
            pstmt.setString(2, loggedInUserId); // isBookmarked check
            pstmt.setString(3, loggedInUserId); // isUpvoted check
            pstmt.setString(4, loggedInUserId); // bookmark join
            pstmt.setString(5, profileUserId);  // filter by profile user

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    posts.add(mapRowToPost(rs));
                }
            }
        }
        return posts;
    }

    /**
     * Maps a single ResultSet row to a Post model object.
     * Reused across all post-fetching queries.
     */
    private Post mapRowToPost(ResultSet rs) throws SQLException {
        String postId = rs.getString("postId");
        String content = rs.getString("content");
        String postDate = rs.getString("postDate");
        String userId = rs.getString("userId");
        String firstName = rs.getString("firstName");
        String lastName = rs.getString("lastName");
        int heartsCount = rs.getInt("heartsCount");
        int commentsCount = rs.getInt("commentsCount");
        boolean isHearted = rs.getInt("isHearted") == 1;
        boolean isBookmarked = rs.getInt("isBookmarked") == 1;

        User user = new User(userId, firstName, lastName);
        Post post = new Post(postId, content, postDate, user,
                heartsCount, commentsCount, isHearted, isBookmarked);
        post.setUpvotesCount(rs.getInt("upvotesCount"));
        post.setUpvoted(rs.getInt("isUpvoted") == 1);
        return post;
    }

    public List<Post> getPostsByUserId(String userId) throws SQLException {
            return getPostsByUser(userId, userId);
    }


    public List<ExpandedPost> getExpandedPost(String postId, String loggedInUserId) throws SQLException {
        final String postSql =
            "select p.postId, p.content, " +
            "date_format(p.postDate, '%b %d, %Y, %h:%i %p') as postDate, " +
            "u.userId, u.firstName, u.lastName, " +
            "count(distinct h.userId) as heartsCount, " +
            "count(distinct c.commentId) as commentsCount, " +
            "max(case when h.userId = ? then 1 else 0 end) as isHearted, " +
            "max(case when b.userId = ? then 1 else 0 end) as isBookmarked, " +
            "count(distinct uv.userId) as upvotesCount, " +
            "max(case when uv.userId = ? then 1 else 0 end) as isUpvoted " +
            "from post p " +
            "join user u on p.userId = u.userId " +
            "left join heart h on p.postId = h.postId " +
            "left join comment c on p.postId = c.postId " +
            "left join bookmark b on p.postId = b.postId and b.userId = ? " +
            "left join upvote uv on p.postId = uv.postId " +
            "where p.postId = ? " +
            "group by p.postId, p.content, p.postDate, u.userId, u.firstName, u.lastName ";

        final String commentsSql =
            "select c.commentId, c.content, " +
            "date_format(c.commentDate, '%b %d, %Y, %h:%i %p') as postDate, " +
            "u.userId, u.firstName, u.lastName " +
            "from comment c " +
            "join user u on c.userId = u.userId " +
            "where c.postId = ? " +
            "order by c.commentDate asc";

        List<ExpandedPost> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {

            // Fetch the post.
            Post post = null;
            try (PreparedStatement pstmt = conn.prepareStatement(postSql)) {
                pstmt.setString(1, loggedInUserId);
                pstmt.setString(2, loggedInUserId);
                pstmt.setString(3, loggedInUserId);
                pstmt.setString(4, loggedInUserId);
                pstmt.setString(5, postId);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        post = mapRowToPost(rs);
                    }
                }
            }

            // Post not found, so return empty list.
            if (post == null) {
                return result;
            }

            // Fetch comment from old to new.
            List<Comment> comments = new ArrayList<>();
            try (PreparedStatement pstmt = conn.prepareStatement(commentsSql)) {
                pstmt.setString(1, postId);

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        String commentId = rs.getString("commentId");
                        String content = rs.getString("content");
                        String commentDate = rs.getString("postDate");
                        String userId = rs.getString("userId");
                        String firstName = rs.getString("firstName");
                        String lastName = rs.getString("lastName");

                        User user = new User(userId, firstName, lastName);
                        comments.add(new Comment(commentId, content, commentDate, user));
                    }
                }
            }

            // Combine into ExpandedPost.
            ExpandedPost expandedPost = new ExpandedPost(
                post.getPostId(),
                post.getContent(),
                post.getPostDate(),
                post.getUser(),
                post.getHeartsCount(),
                post.getCommentsCount(),
                post.getHearted(),
                post.isBookmarked(),
                comments
            );
            expandedPost.setUpvotesCount(post.getUpvotesCount());
            expandedPost.setUpvoted(post.isUpvoted());
            result.add(expandedPost);
        }
        return result;
    }


    public boolean addOrRemoveHeart(String postId, String userId, boolean isAdd) throws SQLException {

        final String heartSql = isAdd ?
            "insert ignore into heart (postId, userId) values (?, ?)" :
            "delete from heart where postId = ? and userId = ?";

        try (Connection conn = dataSource.getConnection();

        PreparedStatement pstmt = conn.prepareStatement(heartSql)) {
            pstmt.setString(1, postId);
            pstmt.setString(2, userId);
            int rows = pstmt.executeUpdate();
            if (rows > 0) {
                System.out.println("[DB] Heart " + (isAdd ? "inserted" : "deleted")
                        + ": user " + userId + " -> post " + postId);
            } else {
                System.out.println("[DB] Heart " + (isAdd ? "already exists" : "not found")
                        + ": user " + userId + " -> post " + postId);
            }
            return rows >= 0;
        }
    }

    public boolean addOrRemoveBookmark(String postId, String userId, boolean isAdd) throws SQLException {

        final String bookmarkSql = isAdd ?
            "insert ignore into bookmark (postId, userId) values (?, ?)" :
            "delete from bookmark where postId = ? and userId = ?";

        try (Connection conn = dataSource.getConnection();

        PreparedStatement pstmt = conn.prepareStatement(bookmarkSql)) {
            pstmt.setString(1, postId);
            pstmt.setString(2, userId);
            int rows = pstmt.executeUpdate();
            if (rows > 0) {
                System.out.println("[DB] Bookmark " + (isAdd ? "inserted" : "deleted")
                        + ": user " + userId + " -> post " + postId);
            } else {
                System.out.println("[DB] Bookmark " + (isAdd ? "already exists" : "not found")
                        + ": user " + userId + " -> post " + postId);
            }
            return rows >= 0;
        }
    }

    public boolean addComment(String postId, String userId, String content) throws SQLException {

        final String commentSql = "insert into comment (postId, userId, content) values (?, ?, ?)";

        try (Connection conn = dataSource.getConnection();

        PreparedStatement pstmt = conn.prepareStatement(commentSql)) {
            pstmt.setString(1, postId);
            pstmt.setString(2, userId);
            pstmt.setString(3, content);
            boolean success = pstmt.executeUpdate() > 0;
            if (success) {
                System.out.println("[DB] Comment inserted: user " + userId + " -> post " + postId);
            } else {
                System.out.println("[DB] Comment insert failed: user " + userId + " -> post " + postId);
            }
            return success;
        }
    }


    public List<Post> getBookmarkedPosts(String loggedInUserId) throws SQLException {

        final String bookSql =
            "select p.postId, p.content, " +
            "date_format(p.postDate, '%b %d, %Y, %h:%i %p') as postDate, " +
            "u.userId, u.firstName, u.lastName, " +
            "count(distinct h.userId) as heartsCount, " +
            "count(distinct c.commentId) as commentsCount, " +
            "max(case when h.userId = ? then 1 else 0 end) as isHearted, " +
            "1 as isBookmarked, " +
            "count(distinct uv.userId) as upvotesCount, " +
            "max(case when uv.userId = ? then 1 else 0 end) as isUpvoted " +
            "from post p " +
            "join user u on p.userId = u.userId " +
            "join bookmark b on p.postId = b.postId and b.userId = ? " +
            "left join heart h on p.postId = h.postId " +
            "left join comment c on p.postId = c.postId " +
            "left join upvote uv on p.postId = uv.postId " +
            "group by p.postId, p.content, p.postDate, u.userId, u.firstName, u.lastName " +
            "order by p.postDate desc";

        List<Post> posts = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();

        PreparedStatement pstmt = conn.prepareStatement(bookSql)) {
            pstmt.setString(1, loggedInUserId); // isHearted
            pstmt.setString(2, loggedInUserId); // isUpvoted
            pstmt.setString(3, loggedInUserId); // bookmark join

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    posts.add(mapRowToPost(rs));
                }
            }
        }
        return posts;
    }

    public boolean addOrRemoveUpvote(String postId, String userId, boolean isAdd) throws SQLException {
        final String sql = isAdd ?
            "insert ignore into upvote (postId, userId) values (?, ?)" :
            "delete from upvote where postId = ? and userId = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, postId);
            pstmt.setString(2, userId);
            int rows = pstmt.executeUpdate();
            System.out.println("[DB] Upvote " + (isAdd ? "inserted" : "deleted")
                    + ": user " + userId + " -> post " + postId + " (" + rows + " rows)");
            return rows >= 0;
        }
    }

    /**
     * Fetches all posts ordered by upvote count (most upvoted first) for the trending page.
     * Includes each post's upvote count and whether the logged-in user has upvoted it.
     */
    public List<Post> getTrendingPosts(String loggedInUserId) throws SQLException {
        final String sql =
            "select p.postId, p.content, " +
            "date_format(p.postDate, '%b %d, %Y, %h:%i %p') as postDate, " +
            "u.userId, u.firstName, u.lastName, " +
            "count(distinct h.userId) as heartsCount, " +
            "count(distinct c.commentId) as commentsCount, " +
            "max(case when h.userId = ? then 1 else 0 end) as isHearted, " +
            "max(case when b.userId = ? then 1 else 0 end) as isBookmarked, " +
            "count(distinct uv.userId) as upvotesCount, " +
            "max(case when uv.userId = ? then 1 else 0 end) as isUpvoted " +
            "from post p " +
            "join user u on p.userId = u.userId " +
            "left join heart h on p.postId = h.postId " +
            "left join comment c on p.postId = c.postId " +
            "left join bookmark b on p.postId = b.postId and b.userId = ? " +
            "left join upvote uv on p.postId = uv.postId " +
            "group by p.postId, p.content, p.postDate, u.userId, u.firstName, u.lastName " +
            "order by upvotesCount desc, p.postDate desc";

        List<Post> posts = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, loggedInUserId); // isHearted
            pstmt.setString(2, loggedInUserId); // isBookmarked
            pstmt.setString(3, loggedInUserId); // isUpvoted
            pstmt.setString(4, loggedInUserId); // bookmark join

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    posts.add(mapRowToPost(rs));
                }
            }
        }
        return posts;
    }

    /**
     * Searches for posts whose content contains the given keyword (case-insensitive).
     */
    public List<Post> searchPostsByKeyword(String keyword, String loggedInUserId,
            boolean newestFirst) throws SQLException {

        String orderBy = newestFirst ? "order by p.postDate desc" : "order by p.postDate asc";

        final String sql =
            "select p.postId, p.content, " +
            "date_format(p.postDate, '%b %d, %Y, %h:%i %p') as postDate, " +
            "u.userId, u.firstName, u.lastName, " +
            "count(distinct h.userId) as heartsCount, " +
            "count(distinct c.commentId) as commentsCount, " +
            "max(case when h.userId = ? then 1 else 0 end) as isHearted, " +
            "max(case when b.userId = ? then 1 else 0 end) as isBookmarked, " +
            "count(distinct uv.userId) as upvotesCount, " +
            "max(case when uv.userId = ? then 1 else 0 end) as isUpvoted " +
            "from post p " +
            "join user u on p.userId = u.userId " +
            "left join heart h on p.postId = h.postId " +
            "left join comment c on p.postId = c.postId " +
            "left join bookmark b on p.postId = b.postId and b.userId = ? " +
            "left join upvote uv on p.postId = uv.postId " +
            "where p.content like ? " +
            "group by p.postId, p.content, p.postDate, u.userId, u.firstName, u.lastName " +
            orderBy;

        List<Post> posts = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, loggedInUserId); // isHearted
            pstmt.setString(2, loggedInUserId); // isBookmarked
            pstmt.setString(3, loggedInUserId); // isUpvoted
            pstmt.setString(4, loggedInUserId); // bookmark join
            pstmt.setString(5, "%" + keyword + "%");

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    posts.add(mapRowToPost(rs));
                }
            }
        }
        return posts;
    }

    /**
     * Searches for posts containing ALL of the specified hashtags.
     * For example, searching ["#java", "#spring"] returns only posts that have both hashtags.
     * Results are ordered by date (newest first by default, oldest first if newestFirst is false).
     */
    public List<Post> searchPostsByHashtags(List<String> hashtags, String loggedInUserId,
            boolean newestFirst) throws SQLException {

        if (hashtags == null || hashtags.isEmpty()) {
            return new ArrayList<>();
        }

        // Convert hashtags to lowercase to match how they're stored
        for (int i = 0; i < hashtags.size(); i++) {
            hashtags.set(i, hashtags.get(i).toLowerCase().replaceAll("^#+", ""));
        }

        // Build the WHERE clause with placeholders for each hashtag
        StringBuilder whereClause = new StringBuilder();
        whereClause.append("where h.tag in (");
        for (int i = 0; i < hashtags.size(); i++) {
            if (i > 0) whereClause.append(", ");
            whereClause.append("?");
        }
        whereClause.append(") ");
        whereClause.append("group by p.postId having count(distinct h.hashtagId) = ? ");

        String orderBy = newestFirst ? "order by p.postDate desc" : "order by p.postDate asc";

        final String sql =
            "select p.postId, p.content, " +
            "date_format(p.postDate, '%b %d, %Y, %h:%i %p') as postDate, " +
            "u.userId, u.firstName, u.lastName, " +
            "count(distinct hrt.userId) as heartsCount, " +
            "count(distinct c.commentId) as commentsCount, " +
            "max(case when hrt.userId = ? then 1 else 0 end) as isHearted, " +
            "max(case when b.userId = ? then 1 else 0 end) as isBookmarked, " +
            "count(distinct uv.userId) as upvotesCount, " +
            "max(case when uv.userId = ? then 1 else 0 end) as isUpvoted " +
            "from post p " +
            "join user u on p.userId = u.userId " +
            "join post_hashtag ph on p.postId = ph.postId " +
            "join hashtag h on ph.hashtagId = h.hashtagId " +
            "left join heart hrt on p.postId = hrt.postId " +
            "left join comment c on p.postId = c.postId " +
            "left join bookmark b on p.postId = b.postId and b.userId = ? " +
            "left join upvote uv on p.postId = uv.postId " +
            whereClause.toString() +
            orderBy;

        List<Post> posts = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            int paramIndex = 1;
            pstmt.setString(paramIndex++, loggedInUserId); // isHearted
            pstmt.setString(paramIndex++, loggedInUserId); // isBookmarked
            pstmt.setString(paramIndex++, loggedInUserId); // isUpvoted
            pstmt.setString(paramIndex++, loggedInUserId); // bookmark join

            // Set all hashtag parameters
            for (String tag : hashtags) {
                pstmt.setString(paramIndex++, tag);
            }

            // Set the count of hashtags (HAVING clause)
            pstmt.setInt(paramIndex++, hashtags.size());

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    posts.add(mapRowToPost(rs));
                }
            }
        }
        return posts;
    }

}
