/**
Copyright (c) 2024 Sami Menik, PhD. All rights reserved.

This is a project developed by Dr. Menik to give the students an opportunity to apply database concepts learned in the class in a real world project. Permission is granted to host a running version of this software and to use images or videos of this work solely for the purpose of demonstrating the work to potential employers. Any form of reproduction, distribution, or transmission of the software's source code, in part or whole, without the prior written consent of the copyright owner, is strictly prohibited.
*/
package uga.menik.csx370.controllers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import uga.menik.csx370.models.Post;
import uga.menik.csx370.models.User;
import uga.menik.csx370.services.PostService;
import uga.menik.csx370.services.UserService;

/**
 * This controller handles the home page and some of its sub URLs.
 */
@Controller
@RequestMapping
public class HomeController {

    private final UserService userService;
    private final PostService postService;

    /**
     * PostService and UserService are injected by Spring Boot.
     */
    @Autowired
    public HomeController(UserService userService, PostService postService) {
        this.userService = userService;
        this.postService = postService;
    }

    /**
     * Handles the root URL (/).
     * Fetches all posts ordered newest to oldest and displays them on the home page.
     * Accepts an optional error URL parameter to show error messages.
     */
    @GetMapping
    public ModelAndView webpage(@RequestParam(name = "error", required = false) String error) {
        ModelAndView mv = new ModelAndView("home_page");

        User loggedInUser = userService.getLoggedInUser();
        if (loggedInUser == null) {
            String message = URLEncoder.encode("Please log in to view the home page.",
                    StandardCharsets.UTF_8);
            mv.setViewName("redirect:/login?error=" + message);
            return mv;
        }

        try {
            List<Post> posts = postService.getPostsNewestFirst(loggedInUser.getUserId());
            mv.addObject("posts", posts);

            if (posts == null || posts.isEmpty()) {
                mv.addObject("isNoContent", true);
            }

        } catch (SQLException e) {
            mv.addObject("errorMessage", "Failed to load posts. Please try again.");
            mv.addObject("isNoContent", true);
            return mv;
        }

        mv.addObject("errorMessage", error);
        return mv;
    }

    /**
     * Handles the /createpost form submission.
     * Reads the post text, saves it to the database via PostService,
     * and parses/stores any hashtags found in the content.
     */
    @PostMapping("/createpost")
    public String createPost(@RequestParam(name = "posttext") String postText) {
        System.out.println("User is creating post: " + postText);

        if (postText == null || postText.trim().isEmpty()) {
            String message = URLEncoder.encode("Post content cannot be empty.",
                    StandardCharsets.UTF_8);
            return "redirect:/?error=" + message;
        }

        User loggedInUser = userService.getLoggedInUser();
        if (loggedInUser == null) {
            String message = URLEncoder.encode("You must be logged in to create a post.",
                    StandardCharsets.UTF_8);
            return "redirect:/login?error=" + message;
        }

        try {
            boolean success = postService.createPost(postText, loggedInUser.getUserId());
            if (success) {
                return "redirect:/";
            } else {
                String message = URLEncoder.encode("Failed to create the post. Please try again.",
                        StandardCharsets.UTF_8);
                return "redirect:/?error=" + message;
            }
        } catch (SQLException e) {
            String message = URLEncoder.encode("A database error occurred: " + e.getMessage(),
                    StandardCharsets.UTF_8);
            return "redirect:/?error=" + message;
        }
    }
}