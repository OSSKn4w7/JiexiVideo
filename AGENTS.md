# AGENTS.md — AIDE Pro Android Project

## Build & run

- Open in AIDE Pro, press Run
- No remote Maven dependencies (AIDE Maven unreliable, fails silently)
- `android.useAndroidX=false` — never import `androidx.*` or `com.google.android.material.*`
- Third-party libs go as JARs into `app/libs/` (loaded by `compile fileTree`)

## Compiler constraints (ECJ — Eclipse Compiler for Java)

**Do NOT use:**
- Lambda expressions → anonymous inner classes
- Method references (`Class::method`) → anonymous inner classes
- Stream API → for/while loops
- try-with-resources → try-finally
- `var` keyword → explicit type declarations
- Text blocks → string concatenation

**Allowed packages:** `android.*`, `java.*`, `javax.*`, `org.w3c.*`, `org.xml.*`

## Architecture

- **Single module** — AIDE doesn't support multi-module
- App launcher: `BilibiliActivity` (extends `android.app.Activity`, not AppCompatActivity)
- Package: `com.jiexi.apppp`
- Layouts: `app/src/main/res/layout/`
- Manifest: no `FileProvider` (requires androidx)

## Layout rules

- Only native widgets: `Button`, `TextView`, `Switch`, `ScrollView`, `LinearLayout`, `ListView`
- No `SwitchCompat`, no `RecyclerView`, no Material components

## Gradle config

- Dependencies use `compile`, never `implementation`/`api`
- `compileSdkVersion 26`, `targetSdkVersion 24`, `buildToolsVersion "26.0.0"`
- ProGuard: `proguard-android.txt`, not `-optimize.txt`

## Common errors

| Symptom | Cause |
|---------|-------|
| `unknown type or package` | Missing JAR / import of `androidx` |
| `Unexpected end of declaration` | Lambda / method reference / try-with-resources |
| `wrong "` / `missing )` | Fullwidth quotes from copy-paste |
| Local var in inner class must be `final` | ECJ targets Java 7 — mark it final |


## File encoding

UTF-8 only. Fullwidth quotes (`"` `"`) from external sources → ECJ rejects.
