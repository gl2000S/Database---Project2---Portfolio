/**
Copyright (c) 2024 Sami Menik, PhD. All rights reserved.

This is a project developed by Dr. Menik to give the students an opportunity to apply database concepts learned in the class in a real world project. Permission is granted to host a running version of this software and to use images or videos of this work solely for the purpose of demonstrating the work to potential employers. Any form of reproduction, distribution, or transmission of the software's source code, in part or whole, without the prior written consent of the copyright owner, is strictly prohibited.
*/
package uga.menik.csx370.controllers;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import uga.menik.csx370.components.AuthInterceptor;
import uga.menik.csx370.models.Post;
import uga.menik.csx370.services.PostService;
import uga.menik.csx370.services.UserService;
import uga.menik.csx370.utility.Utility;

/**
 * Handles /hashtagsearch URL and possibly others.
 * At this point no other URLs.
 */
@Controller
@RequestMapping("/hashtagsearch")
public class HashtagSearchController {

    @Autowired
    private PostService postService;

    @Autowired
    private UserService userService;

    /**
     * This function handles the /hashtagsearch URL itself.
     * This URL can process a request parameter with name hashtags.
     * In the browser the URL will look something like below:
     * http://localhost:8081/hashtagsearch?hashtags=%23amazing+%23fireworks
     * Note: the value of the hashtags is URL encoded.
     *
     * Optional parameters:
     * - sort: "newest" (default) or "oldest" to control sort order
     */
    @GetMapping
    public ModelAndView webpage(@RequestParam(name = "hashtags") String hashtags,
            @RequestParam(name = "sort", defaultValue = "newest") String sort) {
        System.out.println("User is searching: " + hashtags);

        // See notes on ModelAndView in BookmarksController.java.
        ModelAndView mv = new ModelAndView("posts_page");

        // Encode search term before try block so it's available in error cases
        String encodedSearchTerm = URLEncoder.encode(hashtags, StandardCharsets.UTF_8);
        mv.addObject("searchTerm", hashtags);
        mv.addObject("searchTermEncoded", encodedSearchTerm);

        try {
            String loggedInUserId = userService.getLoggedInUser().getUserId();
            boolean newestFirst = !"oldest".equalsIgnoreCase(sort);

            List<Post> posts;

            if (hashtags.trim().contains("#")) {
                // Hashtag search — input contains # symbols
                System.out.println("User is searching by hashtag: " + hashtags);
                List<String> hashtagsList = parseHashtags(hashtags);
                if (hashtagsList.isEmpty()) {
                    mv.addObject("isNoContent", true);
                    return mv;
                }
                posts = postService.searchPostsByHashtags(hashtagsList, loggedInUserId, newestFirst);
            } else {
                // Keyword search — plain text search on post content
                System.out.println("User is searching by keyword: " + hashtags);
                posts = postService.searchPostsByKeyword(hashtags.trim(), loggedInUserId, newestFirst);
            }

            mv.addObject("posts", posts);
            mv.addObject("sortOrder", sort);
            mv.addObject("sortNewest", "newest".equalsIgnoreCase(sort));
            mv.addObject("sortOldest", "oldest".equalsIgnoreCase(sort));

            if (posts.isEmpty()) {
                mv.addObject("isNoContent", true);
            }

        } catch (SQLException e) {
            System.err.println("Database error during search: " + e.getMessage());
            mv.addObject("errorMessage",
                    "An error occurred while searching. Please try again.");
        }

        return mv;
    }

    /**
     * Parses hashtags from the input string.
     * Handles hashtags with or without # symbol.
     * Example: "#java spring" → ["java", "spring"]
     */
    private List<String> parseHashtags(String input) {
        List<String> hashtags = new ArrayList<>();

        if (input == null || input.trim().isEmpty()) {
            return hashtags;
        }

        // Split by whitespace
        String[] parts = input.trim().split("\\s+");

        for (String part : parts) {
            // Remove # if present and trim whitespace
            String tag = part.replaceAll("^#+", "").trim();

            // Only add non-empty tags
            if (!tag.isEmpty()) {
                hashtags.add(tag);
            }
        }

        return hashtags;
    }

}
