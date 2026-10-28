# Module 5: architecture review and implementation decisions

Date: 2026-10-21; source/structure review updated 2026-10-28. Sources: team Google Doc (also copied in doc/doc.md),
AI_COLLABORATION_RULES.md, MODULE_INTEGRATION_STATUS.md, tour/SCHEDULE_INTEGRATION.md,
existing Account/Branch/Inventory security, V1 schema and Payment callback implementation.

## Architecture

This is a Spring Boot modular monolith, organized by feature, with layers inside each
module. Follow Branch's service interface + service/impl convention. Controllers use
DTOs and ApiResponse; transactions belong to service implementations; SQL belongs
to repositories/adapters. JPA relationships stay internal to schedule. External IDs remain Long in this structural refactor; Account/Branch readiness is
recorded in MODULE_INTEGRATION_STATUS.md and does not mandate a mapping change here.
Read existing contracts through the current module-owned adapter without modifying
other modules' tables, entities or repositories.
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
* Source review 2026-10-28: Payment's ParticipantPaymentBridgeImpl invokes
  ParticipantPaymentService.confirm with SCHEDULE_PAYMENT; TransactionsServiceImpl
  routes successful participant payments through that bridge. Preserve this contract
  and locking order. Source connectivity does not establish live provider/end-to-end
  verification. Manual confirmation remains available to managers.
* Cancellation records durable refund-review actions, not completed refunds. Payment
  owns executing/refunding/reconciling transactions. Do not change its implementation.
* New migration changes only schedule-owned tables and adds schedule-owned history
  and payment-action tables. Preserve applied migrations, PKs and external FK IDs.

## Validation

Use a dedicated disposable PostgreSQL database, never the shared .env database.
Cover method/API authorization, branch isolation, schedule state/time rules,
attendance windows, holds, duplicate callbacks, re-registration references and races.
Run compileJava and test tasks; document actual results and any environment blocker.

## Structure standardization (2026-10-28)

DTOs live in dto.request/response, statuses in enums, advice in exception, pure
lifecycle rules in validator, page request validation in util, and expiry job in
scheduler. ParticipantLifecycle is an internal transactional component in
service.impl; it does not need a new public service interface.

See [MODULE_5_STRUCTURE_STANDARDIZATION.md](MODULE_5_STRUCTURE_STANDARDIZATION.md)
for the captured baseline, naming conventions, moved types and actual checks.

## Spring Data repositories (2026-10-28)

GuideScheduleRepository and ScheduleIntegrationRepository are Spring Data Repository interfaces,
using native @Query and interface projections rather than direct JDBC. Pageable is applied by
Spring Data after the SQL assignment scope. Native timestamp projections use Instant; DTO mapping
uses UTC OffsetDateTime and payment checks compare instants. The PostgreSQL advisory transaction
lock keeps its original key and requires an existing transaction. API/schema remain unchanged.

## Legacy API retirement (2026-10-28)

At the user's explicit request, the 11 legacy HTTP operations have been removed.
The initial compatibility decision above is historical; only canonical endpoints
are supported now. See MODULE_5_API_MIGRATION.md for the removed/replacement mapping.
No schema or Payment backend contract changes.

## Resource URI convention (2026-10-28)

Per the user's naming requirement, Schedule endpoints have no me segments.
Guide assignments are a JWT-scoped collection at /api/guide-assignments;
/api/participants defaults to the authenticated user, with an authorized optional userId filter.
Rescheduling/cancellation operations use reschedulings/cancellations resources;
path parameters are scheduleId/participantId. No aliases or schema changes.
