package com.zlz.word2picture.word2picture.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class GenerateController {

    @GetMapping("/ui/generate")
    public String generatePage() {
        return "generate"; // 返回 templates/generate.html
    }
}
