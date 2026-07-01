# GitHub Actions Setup Guide

I've automatically created the GitHub Actions workflow file for you at `.github/workflows/android.yml`.

When you commit and push this file to your repository on GitHub, it will automatically trigger a build on every push and pull request to the `main` or `master` branch.

## What the workflow does:
1. **Checks out the repository:** Pulls your code into the CI environment.
2. **Sets up JDK 21:** Configures Java 21, which is required for your Gradle 9.1 setup. It also enables Gradle caching to speed up subsequent builds.
3. **Builds the project:** Runs `./gradlew build` to compile the app and run any unit tests.

## Important Notes for CI

### 1. `debug.keystore`
In your `app/build.gradle.kts`, the debug signing config looks for a keystore in the project root:
`storeFile = file("${rootDir}/debug.keystore")`
Since `debug.keystore` is typically ignored by `.gitignore` and not pushed to GitHub, the CI build may fail if it can't find this file. You have two options:
* **Option A:** Generate a `debug.keystore` in your project root and commit it to Git (it's safe to commit a debug keystore).
* **Option B:** Modify `app/build.gradle.kts` to fall back to the default keystore if the local one is missing.

### 2. Release Keystore Secrets
Your `app/build.gradle.kts` expects environment variables for the release build:
* `KEYSTORE_PATH`
* `STORE_PASSWORD`
* `KEY_PASSWORD`

If you want GitHub Actions to build signed release APKs/AABs in the future, you will need to add these as [GitHub Repository Secrets](https://docs.github.com/en/actions/security-guides/encrypted-secrets) and update the `android.yml` workflow to decode the keystore and pass the passwords as environment variables.
