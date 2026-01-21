# Multi-Version Build Guide

This project is configured to build for multiple Minecraft versions (1.21.4 through 1.21.11) using a single codebase. This is achieved through dynamic Gradle configuration and a compatibility utility class.

## Building the Mod

### Build All Versions
To build the mod for **all** supported Minecraft versions at once, run the following command in your terminal:

**Windows:**
```powershell
.\gradlew buildAll
```

**Linux/macOS:**
```bash
./gradlew buildAll
```

This task will sequentially build the mod for each defined version to ensure stability.

### Build a Specific Version
You can also build a specific version using the generated convenience tasks. This is much easier than manually passing parameters.

For example, to build only for **1.21.11**:
```powershell
.\gradlew build_v1.21.11
```

Or for **1.21.4**:
```powershell
.\gradlew build_v1.21.4
```

These tasks automatically set the correct `mcVer`, `yarnVer`, and `meteorVer` for you.

## Output Artifacts

The build artifacts (JAR files) for each version are stored in separate directories to avoid conflicts:

`build/versions/<minecraft_version>/libs/`

For example:
- `build/versions/1.21.4/libs/PlumbillerPublic-1.21.4-0.0.6.jar`
- `build/versions/1.21.11/libs/PlumbillerPublic-1.21.11-0.0.6.jar`

## Adding New Minecraft Versions

To add support for a new Minecraft version:

1.  Open `build.gradle.kts`.
2.  Locate the `targets` map:
    ```kotlin
    val targets = mapOf(
        "1.21.4" to "1.21.4+build.8",
        // ...
        "1.21.11" to "1.21.11+build.1"
    )
    ```
3.  Add the new Minecraft version and its corresponding Yarn mapping version to the map.
4.  Ensure `gradle/libs.versions.toml` has a compatible `loom` version (currently `1.14-SNAPSHOT` to support newer versions).

## Code Compatibility (`MultiVersionCompat`)

Since different Minecraft versions have different obfuscated names or API changes (e.g., `getWorld` vs `getEntityWorld`), this project uses a utility class `com.Plumbiller.publicaddon.util.MultiVersionCompat`.

**Do not call these methods directly:**
- `GameProfile.getName()`
- `GameProfile.getId()`
- `Entity.getWorld()`

**Instead, use:**
- `MultiVersionCompat.getProfileName(profile)`
- `MultiVersionCompat.getProfileId(profile)`
- `MultiVersionCompat.getEntityWorld(entity)`

This class uses reflection to dynamically find the correct method at runtime, ensuring the same JAR works (logic-wise) or at least compiles for the target environment, although strict compilation checks per version are handled by the build script.

## Troubleshooting

-   **"Mod was built with a newer version of Loom":** Ensure `gradle/libs.versions.toml` uses a Loom version compatible with the newest Minecraft version you are targeting.
-   **Runtime Crashes (MethodNotFound):** If the mod crashes claiming a method is missing (e.g., `getWorld`), ensure you are using `MultiVersionCompat` for that call. The utility is designed to check for multiple method names (Intermediary, hashed, named) to prevent this.
