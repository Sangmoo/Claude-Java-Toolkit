package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.config.ToolkitSettings;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Web controller for REST API Playground (/api-docs).
 *
 * Renders an interactive API documentation page where users can
 * view endpoint specs, example requests, and call APIs live.
 */
@Controller
@RequestMapping("/api-docs")
public class RestApiPlaygroundController {

    private final ToolkitSettings settings;

    public RestApiPlaygroundController(ToolkitSettings settings) {
        this.settings = settings;
    }

    @GetMapping
    public String show(Model model) {
        model.addAttribute("dbConfigured", settings.isDbConfigured());
        model.addAttribute("dbUrl",        settings.getDb().getUrl());
        model.addAttribute("dbUsername",   settings.getDb().getUsername());
        return "api-docs/index";
    }
}
