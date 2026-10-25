# enhancedGM Agent Rules

1. **CHANGELOG IN ENGLISH**: Always maintain `CHANGELOG.md` in English.
2. **SEQUENTIAL VERSIONS**: Increment version numbers sequentially (e.g. 0.2.4 -> 0.2.5 -> 0.3.0). Never skip or regress.
3. **DIRECT FILE EDITING**: Prefer editing files directly with `edit_file` / `write_file`. Python scripts are permissible only if strictly necessary (e.g. complex bulk operations).
4. **NO GITHUB PUSH**: Do not push to GitHub unless explicitly told to do so. Keep commits on local `dev` branch.
5. **ZERO EMOJIS**: No emojis anywhere (code, commits, responses, in-game strings).
6. **BUILD VERIFICATION**: Always test changes with `.\gradlew.bat test build`.
