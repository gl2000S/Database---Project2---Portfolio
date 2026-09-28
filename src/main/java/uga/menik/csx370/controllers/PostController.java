/**
Copyright (c) 2024 Sami Menik, PhD. All rights reserved.

This is a project developed by Dr. Menik to give the students an opportunity to apply database concepts learned in the class in a real world project. Permission is granted to host a running version of this software and to use images or videos of this work solely for the purpose of demonstrating the work to potential employers. Any form of reproduction, distribution, or transmission of the software's source code, in part or whole, without the prior written consent of the copyright owner, is strictly prohibited.
*/
package uga.menik.csx370.controllers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.beans.factory.annotation.Autowired;


import uga.menik.csx370.models.ExpandedPost;
import uga.menik.csx370.utility.Utility;
import uga.menik.csx370.services.PostService;
import uga.menik.csx370.services.UserService;

/**
 * Handles /post URL and its sub urls.
 */
@Controller
@RequestMapping("/post")
public class PostController {

    // PostService for all DB operations on posts.
    // UserService to get the logged in user ID.
    private final PostService postService;
    private final UserService userService;

    @Autowired
    public PostController(PostService postService, UserService userService) {
        this.postService = postService;
        this.userService = userService;
    }

    /**
     * This function handles the /post/{postId} URL.
     * This handlers serves the web page for a specific post.
     * Note there is a path variable {postId}.
     * An example URL handled by this function looks like below:
     * http://localhost:8081/post/1
     * The above URL assigns 1 to postId.
     * 
     * See notes from HomeController.java regardig error URL parameter.
     */
    @GetMapping("/{postId}")
    public ModelAndView webpage(@PathVariable("postId") String postId,
            @RequestParam(name = "error", required = false) String error) {
        System.out.println("The user is attempting to view post with id: " + postId);
        // See notes on ModelAndView in BookmarksController.java.
        ModelAndView mv = new ModelAndView("posts_page");
        String loggedInUserId = userService.getLoggedInUser().getUserId();

        try {
        // Following line populates sample data.
        // You should replace it with actual data from the database.
        List<ExpandedPost> posts = postService.getExpandedPost(postId, loggedInUserId);
        mv.addObject("posts", posts);

        // If no posts are found, you can set a flag to show a "no content" message.
        if (posts.isEmpty()) {
            mv.addObject("isNoContent", true);
        }
        } catch (Exception e) {
            // Log the exception for debugging purposes.
            System.err.println("Error fetching post data: " + e.getMessage());
            // You can set an error message to show to the user.
            String message = URLEncoder.encode("Failed to load the post. Please try again.",
                    StandardCharsets.UTF_8);
            return new ModelAndView("redirect:/post/" + postId + "?error=" + message);
        }

        // If an error occured, you can set the following property with the
        // error message to show the error message to the user.
        // An error message can be optionally specified with a url query parameter too.
        mv.addObject("errorMessage", error);

        // Enable the following line if you want to show no content message.
        // Do that if your content list is empty.
        // mv.addObject("isNoContent", true);

        return mv;
    }

    /**
     * Handles comments added on posts.
     * See comments on webpage function to see how path variables work here.
     * This function handles form posts.
     * See comments in HomeController.java regarding form submissions.
     */
    @PostMapping("/{postId}/comment")
    public String postComment(@PathVariable("postId") String postId,
            @RequestParam(name = "comment") String comment) {

        String loggedInUserId = userService.getLoggedInUser().getUserId();
        System.out.println("User " + loggedInUserId + " is attempting to comment on post: " + postId);

        try {
            boolean success = postService.addComment(postId, loggedInUserId, comment);
            if (success) {
                System.out.println("Success: comment added by user " + loggedInUserId + " on post " + postId);
                return "redirect:/post/" + postId;
            }
        } catch (Exception e) {
            System.err.println("Error adding comment: " + e.getMessage());
        }

        // Redirect the user with an error message if there was an error.
        String message = URLEncoder.encode("Failed to post the comment. Please try again.",
                StandardCharsets.UTF_8);
        return "redirect:/post/" + postId + "?error=" + message;
    }

    /**
     * Handles likes added on posts.
     * See comments on webpage function to see how path variables work here.
     * See comments in PeopleController.java in followUnfollowUser function regarding 
     * get type form submissions and how path variables work.
     */
    @GetMapping("/{postId}/heart/{isAdd}")
    public String addOrRemoveHeart(@PathVariable("postId") String postId,
            @PathVariable("isAdd") Boolean isAdd) {

            String loggedInUserId = userService.getLoggedInUser().getUserId();
            System.out.println("User " + loggedInUserId + " is attempting to "
                    + (isAdd ? "heart" : "unheart") + " post: " + postId);

            try {
                boolean success = postService.addOrRemoveHeart(postId, loggedInUserId, isAdd);
                if (success) {
                    System.out.println("Success: user " + loggedInUserId
                            + (isAdd ? " hearted " : " unhearted ") + "post " + postId);
                    return "redirect:/post/" + postId;
                }
            } catch (Exception e) {
                System.err.println("Error adding/removing heart: " + e.getMessage());
            }


            // Redirect the user if the comment adding is a success.
            // return "redirect:/post/" + postId;
            String message = URLEncoder.encode("Failed to (un)like the post. Please try again.",
                    StandardCharsets.UTF_8);
            return "redirect:/post/" + postId + "?error=" + message;
    }


    /**
     * Handles bookmarking posts.
     * See comments on webpage function to see how path variables work here.
     * See comments in PeopleController.java in followUnfollowUser function regarding
     * get type form submissions.
     */
    @GetMapping("/{postId}/bookmark/{isAdd}")
    public String addOrRemoveBookmark(@PathVariable("postId") String postId,
            @PathVariable("isAdd") Boolean isAdd) {

        String loggedInUserId = userService.getLoggedInUser().getUserId();
        System.out.println("User " + loggedInUserId + " is attempting to "
                + (isAdd ? "bookmark" : "unbookmark") + " post: " + postId);

        try {
            boolean success = postService.addOrRemoveBookmark(postId, loggedInUserId, isAdd);
            if (success) {
                System.out.println("Success: user " + loggedInUserId
                        + (isAdd ? " bookmarked " : " unbookmarked ") + "post " + postId);
                return "redirect:/post/" + postId;
            }
        } catch (Exception e) {
            System.err.println("Error adding/removing bookmark: " + e.getMessage());
        }

        // Redirect the user with an error message if there was an error.
        String message = URLEncoder.encode("Failed to (un)bookmark the post. Please try again.",
                StandardCharsets.UTF_8);
        return "redirect:/post/" + postId + "?error=" + message;
    }

    /**
     * Handles upvoting posts. Redirects back to the page the user came from.
     */
    @GetMapping("/{postId}/upvote/{isAdd}")
    public String addOrRemoveUpvote(@PathVariable("postId") String postId,
            @PathVariable("isAdd") Boolean isAdd,
            HttpServletRequest request) {

        String loggedInUserId = userService.getLoggedInUser().getUserId();
        System.out.println("User " + loggedInUserId + " is attempting to "
                + (isAdd ? "upvote" : "remove upvote from") + " post: " + postId);

        String referer = request.getHeader("Referer");
        String redirectTo = (referer != null && !referer.isEmpty()) ? referer : "/trending";

        try {
            boolean success = postService.addOrRemoveUpvote(postId, loggedInUserId, isAdd);
            if (success) {
                System.out.println("Success: user " + loggedInUserId
                        + (isAdd ? " upvoted " : " removed upvote from ") + "post " + postId);
                return "redirect:" + redirectTo;
            }
        } catch (Exception e) {
            System.err.println("Error adding/removing upvote: " + e.getMessage());
        }

        String message = URLEncoder.encode("Failed to (un)upvote the post. Please try again.",
                StandardCharsets.UTF_8);
        return "redirect:" + redirectTo + "?error=" + message;
    }

}
