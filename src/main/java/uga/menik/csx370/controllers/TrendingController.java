package uga.menik.csx370.controllers;

import java.sql.SQLException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import uga.menik.csx370.models.Post;
import uga.menik.csx370.services.PostService;
import uga.menik.csx370.services.UserService;

/**
 * Handles /trending URL.
 * Shows all posts ordered by upvote count (most upvoted first).
 */
@Controller
@RequestMapping("/trending")
public class TrendingController {

    private final PostService postService;
    private final UserService userService;

    @Autowired
    public TrendingController(PostService postService, UserService userService) {
        this.postService = postService;
        this.userService = userService;
    }

    @GetMapping
    public ModelAndView webpage(
            @RequestParam(name = "error", required = false) String error) {
        System.out.println("User is viewing the trending page.");

        ModelAndView mv = new ModelAndView("posts_page");
        String loggedInUserId = userService.getLoggedInUser().getUserId();

        try {
            List<Post> posts = postService.getTrendingPosts(loggedInUserId);
            mv.addObject("posts", posts);

            if (posts.isEmpty()) {
                mv.addObject("isNoContent", true);
            }
        } catch (SQLException e) {
            System.err.println("Error loading trending posts: " + e.getMessage());
            mv.addObject("errorMessage", "Failed to load trending posts. Please try again.");
        }

        mv.addObject("errorMessage", error);
        return mv;
    }

}
