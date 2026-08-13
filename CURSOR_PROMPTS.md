# Cursor prompt guide

Use one small milestone prompt at a time. Ask Cursor to cite the files it changed and the tests it ran.

## Implement a scoped task

> Read the project rules and relevant PRD section. Implement only [task]. Preserve the modular-monolith boundary and $0/month constraints. Add tests for normal, edge, and failure cases. Summarize changed files, assumptions, and verification.

## Review a change

> Review this change against the PRD, architecture decisions, calculation rules, data integrity requirements, responsible language, and free-tier constraints. Identify correctness or scope problems before style suggestions. Do not edit files unless asked.

## Debug a failure

> Reproduce the failure, identify the first incorrect state, and explain the cause with evidence. Propose the smallest fix and the regression test that would prevent recurrence. Do not apply the fix until requested.

## Guard against scope expansion

> Before implementing, classify each requested behavior as MVP, deferred, or out of scope using the PRD. Implement only MVP behavior and list deferred ideas separately.
