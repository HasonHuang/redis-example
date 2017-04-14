package com.hason.redis.controller;

import com.hason.redis.service.HelloService;
import org.beetl.core.Template;
import org.beetl.ext.spring.BeetlGroupUtilConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Created by Hason on 2017/3/17.
 */
@Controller
public class HelloController {

    @Autowired
    private BeetlGroupUtilConfiguration configuration;
    @Autowired
    private HelloService helloService;

    @RequestMapping("/hello")
    public String hello(Model model) {
        model.addAttribute("total", "总数");
        helloService.say();
        return "index";
    }
}
