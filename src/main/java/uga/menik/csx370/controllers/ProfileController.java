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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import uga.menik.csx370.models.Post;
import uga.menik.csx370.models.User;
import uga.menik.csx370.services.PostService;
import uga.menik.csx370.services.UserService;

/**
 * Handles /profile URL and its sub URLs.
 */
@Controller
@RequestMapping("/profile")
public class ProfileController {

    // UserService has user login and registration related functions.
    private final UserService userService;
    private final PostService postService;

    /**
     * See notes in AuthInterceptor.java regarding how this works 
     * through dependency injection and inversion of control.
     */
    @Autowired
    public ProfileController(UserService userService, PostService postService) {
        this.userService = userService;
        this.postService = postService;
    }

    /**
     * This function handles /profile URL itself.
     * This serves the webpage that shows posts of the logged in user.
     */
    @GetMapping
    public ModelAndView profileOfLoggedInUser(@RequestParam(name = "error", required = false) String error) {
        System.out.println("User is attempting to view profile of the logged in user.");

        User loggedInUser = userService.getLoggedInUser();
        if (loggedInUser == null) {
            ModelAndView mv = new ModelAndView();
            String message = URLEncoder.encode("Please log in to view your profile.", StandardCharsets.UTF_8);
            mv.setViewName("redirect:/login?error=" + message);
            return mv;
        }

        return profileOfSpecificUser(loggedInUser.getUserId(), error);
    }

    /**
     * This function handles /profile/{userId} URL.
     * This serves the webpage that shows posts of a speific user given by userId.
     * See comments in PeopleController.java in followUnfollowUser function regarding 
     * how path variables work.
     */
    @GetMapping("/{userId}")
    public ModelAndView profileOfSpecificUser(@PathVariable("userId") String userId,
            @RequestParam(name = "error", required = false) String error) {
        System.out.println("User is attempting to view profile: " + userId);

        ModelAndView mv = new ModelAndView("posts_page");

        try {
            List<Post> posts = postService.getPostsByUserId(userId);
            mv.addObject("posts", posts);

            if (posts == null || posts.isEmpty()) {
                mv.addObject("isNoContent", true);
            }
        } catch (SQLException e) {
            String message = URLEncoder.encode("Failed to load profile posts. Please try again.",
                    StandardCharsets.UTF_8);
            mv.setViewName("redirect:/home?error=" + message);
            return mv;
        }

        mv.addObject("errorMessage", error);
        return mv;
    }
}