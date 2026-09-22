---
description: Learn how to publish an iOS application built with the Kotlin Toolchain.
---
# Publishing an iOS application

The Kotlin Toolchain creates a regular Xcode project for an [`ios/app`](user-guide/product-types/ios-app.md) module.
Use that project to configure signing, create a release archive, and upload the application to App Store Connect.

You need a Mac with Xcode and an [Apple Developer Program](https://developer.apple.com/programs/) membership.

## Prepare the application

Open the `module.xcodeproj` file of the iOS application module in Xcode, for example:

```bash
open ios-app/module.xcodeproj
```

In the project navigator, select the project and then the application target. Configure the following settings before
creating an archive:

1. Under **Signing & Capabilities**, select your development team and replace the default bundle identifier with a
   unique identifier for your application.
2. Under **General | Minimum Deployments**, select the oldest iOS version that your application supports.
3. Under **Info | Custom iOS Target Properties | Supported interface orientations**, keep only the orientations your
   application supports.
4. Add an `AppIcon` asset catalog under the module's `src` directory and provide the application icon. A 1024×1024
   image is sufficient when the asset uses Xcode's single-size app icon format.
5. Under **General | Identity**, set the public-facing **Version** and the unique **Build** number.

!!! warning "Use a new build number for every upload"

    App Store Connect rejects another upload with the same version and build number. Increase the **Build** value
    (`CFBundleVersion`) before every upload. Increase **Version** (`CFBundleShortVersionString`) when publishing a new
    application version.

Register the same bundle identifier in
[Certificates, Identifiers & Profiles](https://developer.apple.com/account/resources/identifiers/bundleId/add/bundle),
then [create the application in App Store Connect](https://appstoreconnect.apple.com/apps). The bundle identifier in
Xcode, the registered identifier, and the App Store Connect application must match.

## Distribute from the command line

You can ask your coding agent to archive and upload the iOS application to TestFlight, or run the commands below
manually. Run them from the Kotlin project root and replace the module path and scheme if they differ in your project.
You can list the available schemes with:

```bash
xcodebuild -list -project ios-app/module.xcodeproj
```

Create a release archive:

```bash
xcodebuild \
  -project ios-app/module.xcodeproj \
  -scheme app \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  -archivePath build/release/ios-app.xcarchive \
  -allowProvisioningUpdates \
  archive
```

Create `ios-app/ExportOptions.plist` to configure an App Store Connect upload:

```xml title="ios-app/ExportOptions.plist"
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>method</key>
    <string>app-store-connect</string>
    <key>destination</key>
    <string>upload</string>
    <key>signingStyle</key>
    <string>automatic</string>
</dict>
</plist>
```

Export and upload the archive:

```bash
xcodebuild -exportArchive \
  -archivePath build/release/ios-app.xcarchive \
  -exportOptionsPlist ios-app/ExportOptions.plist \
  -exportPath build/release/export \
  -allowProvisioningUpdates
```

With `destination` set to `upload`, the export command uploads the application to App Store Connect. After Apple
finishes processing the build, it becomes available in App Store Connect and can be assigned to TestFlight testers.

## Distribute from Xcode

You can perform the same archive and upload steps in Xcode:

1. In the macOS menu bar, select **Product | Archive**.
2. After the archive is created successfully, Xcode opens the Organizer window. Select the new archive and click
   **Distribute App**.
3. Select **App Store Connect**, then follow the upload flow.

After Apple finishes processing the build, continue in App Store Connect to distribute it through TestFlight or submit
it for App Store review.
