# Project instructions

Before changing code, read:

1. `doc/PROJECT_STRUCTURE.md` — package layout, service interface/impl and import rules.
2. `doc/AI_COLLABORATION_RULES.md` — ownership and collaboration workflow.
3. `doc/MODULE_INTEGRATION_STATUS.md` — current contracts and integration readiness.

Implement business services behind interfaces in `service`, implementations in `service.impl`. Inject interfaces; never implement or inject another module's `*ServiceImpl`. Use correctly spelled `dto.request` and `dto.response`, plus `entity`, `enums`, `repository`, `exception` as appropriate. Use explicit imports and Lombok constructor injection. Do not add empty layers only to match a diagram.

Preserve changes belonging to other members. Limit semantic edits to the requested module; update dependent imports mechanically when a module's public package moves. Preserve endpoints, JSON fields and schema during structural refactors.

After each meaningful milestone, update `doc/MODULE_INTEGRATION_STATUS.md` with the actual changes and verification. Run `gradlew.bat compileJava compileTestJava` and relevant tests. Do not report skipped checks as passed or work around database migration failures by silently disabling validation.
