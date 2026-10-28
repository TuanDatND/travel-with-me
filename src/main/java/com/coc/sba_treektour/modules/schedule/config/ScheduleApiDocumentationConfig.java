package com.coc.sba_treektour.modules.schedule.config;

import io.swagger.v3.oas.models.PathItem;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Mark legacy operations individually, without deprecating canonical aliases. */
@Configuration
public class ScheduleApiDocumentationConfig {
    private record LegacyRoute(String path, PathItem.HttpMethod method, String replacement) {}

    @Bean
    OpenApiCustomizer scheduleLegacyOperations() {
        var routes = List.of(
                new LegacyRoute("/api/schedules/{scheduleId}/register", PathItem.HttpMethod.POST, "POST /api/schedules/{scheduleId}/participants"),
                new LegacyRoute("/api/schedules/my-assignments", PathItem.HttpMethod.GET, "GET /api/schedules/assigned-to-me"),
                new LegacyRoute("/api/schedules/my-assignments/{id}", PathItem.HttpMethod.GET, "GET /api/schedules/assigned-to-me/{id}"),
                new LegacyRoute("/api/schedules/{id}/histories", PathItem.HttpMethod.GET, "GET /api/schedules/{id}/history"),
                new LegacyRoute("/api/schedules/{id}", PathItem.HttpMethod.PUT, "PATCH /api/schedules/{id}; PATCH distinguishes omitted guideId from null"),
                new LegacyRoute("/api/participants/by-user/{userId}", PathItem.HttpMethod.GET, "GET /api/participants?userId={userId} or GET /api/participants/me"),
                new LegacyRoute("/api/participants/{id}/confirm-payment", PathItem.HttpMethod.PATCH, "POST /api/participants/{id}/manual-payment-confirmations"),
                new LegacyRoute("/api/participants/{id}/check-in", PathItem.HttpMethod.PATCH, "PATCH /api/participants/{id}/attendance with status CHECKED_IN"),
                new LegacyRoute("/api/participants/{id}/no-show", PathItem.HttpMethod.PATCH, "PATCH /api/participants/{id}/attendance with status NO_SHOW"),
                new LegacyRoute("/api/participants/{id}/histories", PathItem.HttpMethod.GET, "GET /api/participants/{id}/history"),
                new LegacyRoute("/api/participants/payment-reviews", PathItem.HttpMethod.GET, "GET /api/schedule-payment-reviews"));
        return openApi -> {
            if (openApi.getPaths() == null) return;
            for (var route : routes) {
                var path = openApi.getPaths().get(route.path());
                if (path == null) continue;
                var operation = path.readOperationsMap().get(route.method());
                if (operation != null) {
                    operation.setDeprecated(true);
                    operation.setDescription("Legacy operation remains supported. Use " + route.replacement()
                            + ". No removal date is scheduled; see doc/MODULE_5_API_MIGRATION.md.");
                }
            }
        };
    }
}
