---
description: Learn how to publish an Android application built with the Kotlin Toolchain.
---
# Publishing an Android application

Publish an [`android/app`](user-guide/product-types/android-app.md) module to Google Play as a signed Android App
Bundle (AAB).

You need a [Google Play Console](https://play.google.com/console/) developer account. Create the application in Play
Console before preparing its first release.

## Configure the application

Set a unique application ID, the initial version, and enable release signing in `android-app/module.yaml`:

```yaml title="android-app/module.yaml"
product: android/app

settings:
  android:
    applicationId: com.example.myapp
    versionCode: 1
    versionName: "1.0"
    signing: enabled
```

The `applicationId` uniquely identifies the application in Google Play and cannot be changed after you upload the
first artifact.

!!! warning "Use a new version code for every upload"

    Google Play rejects another bundle with the same `versionCode`. Increase it before every upload. Update the
    user-facing `versionName` when publishing a new application version.

## Create an upload key

Generate a private upload key for signing bundles before uploading them to Google Play. For example, run this command
from the Kotlin project root and enter strong passwords when prompted:

```bash
keytool -genkeypair \
  -keystore android-app/upload-keystore.p12 \
  -storetype PKCS12 \
  -alias upload \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Create `android-app/keystore.properties` with the values used to generate the key:

```properties title="android-app/keystore.properties"
storeFile=upload-keystore.p12
storePassword=REPLACE_WITH_A_STRONG_PASSWORD
keyAlias=upload
keyPassword=REPLACE_WITH_A_STRONG_PASSWORD
```

The `storeFile` path is relative to the Android application module.

!!! danger "Keep the signing files secure"

    Never commit `upload-keystore.p12` or `keystore.properties` to version control. Back up both files in a secure,
    access-controlled location. Losing the upload key requires an upload-key reset in Play Console.

## Build a signed bundle

Run the following command from the Kotlin project root:

```bash
./kotlin package -m android-app -p android -v release -f aab
```

The command prints the path to the generated AAB. For a module named `android-app`, the default path is:

```text
build/tasks/_android-app_bundleAndroid/gradle-project-release.aab
```

## Upload the bundle

Open the application in Google Play Console, go to **Test and release**, and select the appropriate testing or
production track. Create a release and upload the generated `.aab` file.

Google Play validates and processes the bundle before making the release available to testers or users.

## Build from IntelliJ IDEA or Android Studio

Generating a signed bundle from **Build | Generate Signed App Bundle or APK** is not supported for Kotlin Toolchain
projects yet. Use the Kotlin CLI to create the AAB.
