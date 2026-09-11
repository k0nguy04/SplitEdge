package dev.splitedge.report.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.splitedge.report.PropCatalog;
import dev.splitedge.report.PropCatalogEntry;

/**
 * Deterministic, static catalog of every prop the calculator supports. Never accesses
 * PostgreSQL or any external service.
 */
@RestController
@RequestMapping("/api/props")
public class PropCatalogController {

    @GetMapping
    public List<PropCatalogEntry> props() {
        return PropCatalog.entries();
    }
}
