# PLC Transfer

## Using automatic signing

Create a file in ./app/ named `signing.gradle` containing:

```
ext.plc_signing = [
        storeFilePath   : 'C:/path/to/porterlee.p12',
        storePassword   : '<store_password>',
        keyAlias        : 'plc_transfer',
        keyPassword     : '<key_password>',
        v1SigningEnabled: true,
        v2SigningEnabled: true,
]
```

## Creating the release `apk`(s)

In a terminal, run `gradlew publishRelease`

The files will be in ./app/build/release/

note: The following commands are also available:

- `gradlew publishAll`

- `gradlew publish<build_type>`
  - e.g. `gradlew publishDebug`

- `gradlew publish<system><scanner_sdk><variant>`
  - e.g. `gradlew publishLimsZebraRelease`

note: `gradlew` is referencing the file `gradlew` or `gradlew.bat` (for windows) in the project's root folder

## Selecting a build variant

Android Studio hides this feature a bit.

  - View -> Tool Windows -> Build Variants

## Updating gradle

In a terminal, run:

`gradlew wrapper --gradle-version x.y.z --distribution-type all`

note: This isn't needed very often

## Updating version

Pretty self-explanatory, check the [version file](./app/version.gradle)
