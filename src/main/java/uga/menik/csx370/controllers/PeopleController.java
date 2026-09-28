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

import uga.menik.csx370.models.FollowableUser;
import uga.menik.csx370.models.User;
import uga.menik.csx370.services.PeopleService;
import uga.menik.csx370.services.UserService;

/**
 * Handles /people URL and its sub URL paths.
 */
@Controller
@RequestMapping("/people")
public class PeopleController {

    private final UserService userService;
    private final PeopleService peopleService;

    @Autowired
    public PeopleController(UserService userService, PeopleService peopleService) {
        this.userService = userService;
        this.peopleService = peopleService;
    }

    /**
     * Serves the /people web page.
     */
    @GetMapping
    public ModelAndView webpage(@RequestParam(name = "error", required = false) String error) {
        ModelAndView mv = new ModelAndView("people_page");

        User loggedInUser = userService.getLoggedInUser();
        if (loggedInUser == null) {
            String message = URLEncoder.encode("Please log in to view people.", StandardCharsets.UTF_8);
            mv.setViewName("redirect:/login?error=" + message);
            return mv;
        }

        try {
            List<FollowableUser> followableUsers = peopleService.getFollowableUsers(loggedInUser.getUserId());
            mv.addObject("users", followableUsers);

            if (followableUsers == null || followableUsers.isEmpty()) {
                mv.addObject("isNoContent", true);
            }

        } catch (SQLException e) {
            mv.addObject("errorMessage", "Failed to load people. Please try again.");
            mv.addObject("isNoContent", true);
            return mv;
        }

        mv.addObject("errorMessage", error);
        return mv;
    }

    /**
     * Handles follow / unfollow action.
     * Example:
     * /people/5/follow/true  -> follow user 5
     * /people/5/follow/false -> unfollow user 5
     */
    @GetMapping("{userId}/follow/{isFollow}")
    public String followUnfollowUser(@PathVariable("userId") String userId,
                                     @PathVariable("isFollow") Boolean isFollow) {

        User loggedInUser = userService.getLoggedInUser();
        if (loggedInUser == null) {
            String message = URLEncoder.encode("Please log in first.", StandardCharsets.UTF_8);
            return "redirect:/login?error=" + message;
        }

        try {
            String targetUserId = userId;

            System.out.println("User " + loggedInUser.getUserId() + " is attempting to "
                    + (isFollow ? "follow" : "unfollow") + " user: " + targetUserId);

            if (loggedInUser.getUserId().equals(targetUserId)) {
                System.out.println("Follow/unfollow failed: user tried to follow themselves (userId: "
                        + loggedInUser.getUserId() + ")");
                String message = URLEncoder.encode("You cannot follow or unfollow yourself.",
                        StandardCharsets.UTF_8);
                return "redirect:/people?error=" + message;
            }

            peopleService.followUnfollowUser(loggedInUser.getUserId(), targetUserId, isFollow);
            System.out.println("Success: user " + loggedInUser.getUserId()
                    + (isFollow ? " is now following " : " unfollowed ") + "user " + targetUserId);
            return "redirect:/people";

        } catch (SQLException e) {
            String message = URLEncoder.encode("Failed to (un)follow the user. Please try again.",
                    StandardCharsets.UTF_8);
            return "redirect:/people?error=" + message;
        }
    }
}