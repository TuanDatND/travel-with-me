# Module 5: architecture review and implementation decisions

Date: 2026-10-21. Sources: team Google Doc (also copied in doc/doc.md),
AI_COLLABORATION_RULES.md, MODULE_INTEGRATION_STATUS.md, tour/SCHEDULE_INTEGRATION.md,
existing Account/Branch/Inventory security, V1 schema and Payment callback implementation.

## Architecture

This is a Spring Boot modular monolith, organized by feature, with layers inside each
module. Follow Branch's service interface + service/impl convention. Controllers use
DTOs and ApiResponse; transactions belong to service implementations; SQL belongs
to repositories/adapters. JPA relationships stay internal to schedule. Account and
Branch are IN_PROGRESS: keep external IDs as Long and read their contracts through
a module-owned adapter, without modifying their tables, entities or repositories.
EventService.reference and GuideService.reference are existing public integration points.

## Compatibility and boundaries

* Keep endpoint paths and list response arrays; add bounded page/size query parameters.
* Own schedule security in a higher-priority chain, scoped only to schedules/participants.
  Leave member 4's chain unchanged. Enforce method roles and resource policies too.
* ADMIN manages all; active STAFF only their active assigned branch; active assigned
  GUIDE reads rosters/history and checks attendance; CUSTOMER books for self.
* All writes lock schedule before participant. Use managed entity capacity mutations
  under that lock, avoiding bulk JPQL clearing/detaching entities mid-transaction.
* FULL is derived from capacity. Opening a CLOSED schedule chooses OPEN/FULL.
* Registration closes at a configurable cutoff (default departure time). Holds default
  to 15 minutes, bounded by cutoff. Expiry uses the same schedule lock as cancellation.
* Re-registration preserves participant_id (existing unique schedule/user contract),
  but issues a new registration reference. Payment must send that reference to prevent
  a previous attempt confirming a new booking. History preserves previous attempts.
* Payment currently marks transactions SUCCEEDED without invoking ParticipantService.
  Provide an internal validated confirmation contract; do not claim the existing
  ZaloPay callback is connected. Manual confirmation remains available to managers.
* Cancellation records durable refund-review actions, not completed refunds. Payment
  owns executing/refunding/reconciling transactions. Do not change its implementation.
* New migration changes only schedule-owned tables and adds schedule-owned history
  and payment-action tables. Preserve applied migrations, PKs and external FK IDs.

## Validation

Use a dedicated disposable PostgreSQL database, never the shared .env database.
Cover method/API authorization, branch isolation, schedule state/time rules,
attendance windows, holds, duplicate callbacks, re-registration references and races.
Run compileJava and test tasks; document actual results and any environment blocker.
