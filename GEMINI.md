# GEMINI.md — PKWMmap

@AGENTS.md

## Gemini-Specific Instructions

- **Source of Truth**: Follow `AGENTS.md` for KMP mobile architecture and ownership rules, `documentation.md` for MVP scope, and `docs/api-contract.md` for backend REST contracts.
- **Strict Scope Boundary**: You are assisting the **Frontend / Mobile Leader**. Never modify `backend/`. When backend changes are needed, update `documentation.md` and `docs/api-contract.md` to communicate requirements to the backend team.
- **Language**: `documentation.md` is written in Polish; code, identifiers, API fields, and commit messages should be in English unless requested otherwise. Reply in the language the user uses.
- **Verification**: Do not claim a build or test passes without running it. If Gradle/JDK or KMP build scripts are not yet initialized, state that clearly.
