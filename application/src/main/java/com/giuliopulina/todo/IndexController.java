package com.giuliopulina.todo;

import org.springframework.stereotype.Controller;

@Controller
public class IndexController {

    public String index() {
        return "index";
    }

}
