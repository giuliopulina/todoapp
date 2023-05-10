package com.giuliopulina.todo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class IndexController {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @GetMapping
    @RequestMapping("/")
    public String index(@AuthenticationPrincipal OidcUser user) {
        if (user != null) {
            logger.info("Authenticated user:" + user);
        }
        else {
            logger.info("User not authenticated");
        }

        return "index";
    }

}
