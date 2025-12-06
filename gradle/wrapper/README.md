# Note about Gradle Wrapper

The `gradle-wrapper.jar` file is required to build this project but is not included in the repository due to its binary nature.

## How to generate the Gradle wrapper

If you have Gradle installed, run:

```bash
gradle wrapper --gradle-version 8.0
```

This will generate:
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties` (already present)
- `gradlew` (already present)
- `gradlew.bat` (already present)

## Alternative: Use Android Studio

Android Studio will automatically download and set up the Gradle wrapper when you open the project.

## Manual download

You can also manually download the wrapper from:
https://services.gradle.org/distributions/gradle-8.0-bin.zip

And use the `gradle wrapper` command or let Android Studio handle it.
