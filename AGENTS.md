# AGENTS.md — AIDE Pro Android Project

## Master constraints document

**Read `开始写项目之前先看一下这个.md` first.** It contains non-negotiable AIDE Pro compiler and SDK constraints. Everything below is a distilled subset; the `.md` is authoritative when in doubt.

## Build & run

- Run from project root: `./gradlew assembleDebug`
- No remote Maven dependencies (AIDE Maven is unreliable, fails silently)
- `android.useAndroidX=false` — never import `androidx.*` or `com.google.android.material.*`
- Third-party libs go as JARs into `app/libs/` (populated by `compile fileTree`)

## Compiler constraints (ECJ — Eclipse Compiler for Java)

**Do NOT use:**
- Lambda expressions → use anonymous inner classes
- Method references (`Class::method`) → use anonymous inner classes
- Stream API → use for/while loops
- try-with-resources → use try-finally
- `var` keyword → explicit type declarations
- Text blocks → string concatenation

**Allowed packages only:** `android.*`, `java.*`, `javax.*`, `org.w3c.*`, `org.xml.*`

## Architecture

- **Single module** (multi-module unsupported in AIDE)
- App entrypoint: `app/src/main/java/com/jiexi/apppp/MainActivity.java`
- `MainActivity` extends `android.app.Activity` (not AppCompatActivity)
- Layouts in `app/src/main/res/layout/`
- Manifest declares no `FileProvider` (requires androidx)

## Layout rules

- Only native Android widgets: `Button`, `TextView`, `Switch`, `ScrollView`, `LinearLayout`, etc.
- No `SwitchCompat`, no `RecyclerView` (use `ListView`)

## Common errors quick reference

| Symptom | Likely cause |
|---------|-------------|
| `unknown type or package` | Missing JAR / import of `androidx` |
| `Unexpected end of declaration` | Lambda / method reference / try-with-resources |
| `wrong "` / `missing )` | Fullwidth quotes copied from external sources |

## File encoding

UTF-8 only. Watch for fullwidth quotes (`"` `"`) from copy-paste — ECJ rejects them.
