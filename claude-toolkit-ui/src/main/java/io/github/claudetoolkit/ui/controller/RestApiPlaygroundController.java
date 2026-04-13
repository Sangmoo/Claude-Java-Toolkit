package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.config.ToolkitSettings;
import org.springframework.stereotype.Controller;
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

    // All page-rendering GET methods removed — SpaViewResolver handles routing.
    // This controller retained as placeholder; DB config is now fetched via React API calls.
}
