# Project Rules & Development Guidelines (enhancedGM)

This document contains mandatory guidelines and workflow requirements for developers and AI coding assistants working on the **enhancedGM** codebase.

---

## 1. Changelog & Documentation Language
- **Always write `CHANGELOG.md` in English**: All version entries, feature descriptions, bug fixes, and release notes must be written in clear, professional English.
- **Project Documentation**: Keep `README.md` and documentation files updated and synchronized with the latest module capabilities.

## 2. Version Numbering
- **Strict Semantic Versioning & Sequential Increments**: Always increment version numbers sequentially for releases (e.g. `0.2.4` -> `0.2.5` -> `0.3.0`).
- Update `mod_version` in `gradle.properties` whenever a new release version is prepared.
- Never reset or regress version numbers.

## 3. File Editing & Tooling
- **Direct File Editing**: Prefer editing source code files directly using standard file modification tools (`edit_file` / `write_file`).
- **Python Scripts**: Use only if strictly necessary (e.g. for complex multi-file migrations or low-level binary fixes where standard file tools are insufficient).
- **Line Endings**: Ensure standard `LF` line endings across all Java and resource files (configured via `.gitattributes`).

## 4. Git & GitHub Remote Discipline
- **Local Commits Only by Default**: Do NOT push to GitHub (`git push`) or create remote GitHub releases unless explicitly requested by the user.
- **Branch Strategy**: Work on branch `dev`. Ensure commits have clear, descriptive commit messages.

## 5. UI & Styling Rules
- **Zero Emojis**: Never use emojis in code, comments, commit messages, chat responses, or in-game HUD strings. Replace any icons with clean text labels (e.g. use `Tank XX%` instead of fuel emojis).
- **Pixel-Perfect Alignment**: Ensure rendered HUD widget dimensions always match their `HudWidgetDescriptor` bounding boxes (`getBaseWidth()`, `getBaseHeight()`) and editor selection frames.
- **Responsive Text Clamping**: All HUD widget text fields must dynamically scale or truncate so text never overflows the boundaries of its card background.

## 6. Build Verification
- Always run `./gradlew test build` to verify compilation, test passes, and artifact generation before finishing tasks.
- Keep the test profile JAR (`%APPDATA%\ModrinthApp\profiles\GM 26.3\mods\enhancedGM-X.Y.Z.jar`) synchronized with the latest build output.
