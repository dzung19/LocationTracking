# GitHub Actions Setup Guide

I've automatically created the GitHub Actions workflow file for you at `.github/workflows/android.yml`.

When you commit and push this file to your repository on GitHub, it will automatically trigger a build on every push and pull request to the `main` or `master` branch.

## What the workflow does:
1. **Checks out the repository:** Pulls your code into the CI environment.
2. **Sets up JDK 21:** Configures Java 21, which is required for your Gradle 9.1 setup. It also enables Gradle caching to speed up subsequent builds.
3. **Builds the project:** Runs `./gradlew build` to compile the app and run any unit tests.

## Required GitHub Repository Secrets

Go to your GitHub repository: **Settings** -> **Secrets and variables** -> **Actions** -> **New repository secret**.

### 1. Google Services (`google-services.json`)
- **Secret Name:** `GOOGLE_SERVICES_JSON`
- **Secret Value:** Copy and paste the entire raw contents of your [app/google-services.json](file:///c:/Users/phung/New%20folder/LocationTracking/app/google-services.json) file.
*(Alternatively, you can base64 encode it and save it as `GOOGLE_SERVICES_JSON_BASE64`)*

### 2. API Keys & Ads (for `local.properties`)
- `MAPS_API_KEY`: Your Google Maps API key.
- `OPENWEATHER_API_KEY`: Your OpenWeatherMap API key.
- `APP_OPEN_AD_ID`: Your AdMob App Open ad unit ID.
- `BANNER_AD_ID`: Your AdMob Banner ad unit ID.
- `INTERSTITIAL_AD_ID`: Your AdMob Interstitial ad unit ID.

### 3. Keystores (Optional for Signed Builds)
- `DEBUG_KEYSTORE_BASE64`: Base64 string of your `debug.keystore` (if omitted, Gradle uses standard debug signing).
- `RELEASE_KEYSTORE_BASE64`: Base64 string of `my-upload-key.jks` (if added, GitHub Actions will automatically build and upload `app-release.aab`).
- `STORE_PASSWORD`: Keystore store password.
- `KEY_PASSWORD`: Key password.

```powershell
# To get the base64 string on Windows (PowerShell):
[Convert]::ToBase64String([IO.File]::ReadAllBytes("my-upload-key.jks")) | Set-Clipboard
```

