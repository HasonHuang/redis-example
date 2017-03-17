/*
 * Copyright 2014 - 2016 珠宝易 All Rights Reserved
 */
package com.hason.redis.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Created by Hason on 2017/3/17.
 */
@Controller
public class HelloController {

    @RequestMapping("/hello")
    public String hello(Model model) {
        model.addAttribute("total", "总数");
        return "index";
    }
}
