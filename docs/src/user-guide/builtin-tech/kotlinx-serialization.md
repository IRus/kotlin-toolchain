---
description: |
  Kotlin Serialization is the official multiplatform and multi-format serialization library for Kotlin.
  The Kotlin Toolchain provides support for it out of the box.
---
# Kotlin Serialization

[Kotlin Serialization](https://github.com/Kotlin/kotlinx.serialization) is the official multiplatform and multi-format
serialization library for Kotlin.

To (de)serialize Kotlin classes to/from JSON, enable Kotlin Serialization and add the format dependency:
```yaml
settings:
  kotlin:
    serialization: enabled # configures the compiler and serialization runtime library

dependencies:
  - $kotlin.serialization.json  # JSON or other format
```
This configures the compiler to process `@Serializable` classes, and adds dependencies on the serialization
runtime and JSON format libraries.

You can also customize the version of the Kotlin Serialization libraries using the full form:

```yaml
settings:
  kotlin:
    serialization:
      enabled: true
      version: 1.11.0
```

!!! warning

    The `serialization: json` shortcut and the `serialization.format` property are deprecated.
    Use `serialization: enabled` with catalog dependencies instead.

## Serialization formats

`serialization: enabled` only configures the compiler and the serialization runtime library, but doesn't add any format
dependency. It adds a built-in catalog with official serialization formats libraries, which you can use in
your `dependencies` section. This gives you control in multiple cases:

* if you need a format dependency only in tests:
  ```yaml
  settings:
    kotlin:
      serialization: enabled
  
  test-dependencies:
    - $kotlin.serialization.json
  ```

* if you need to customize the scope of the format dependencies:
  ```yaml
  settings:
    kotlin:
      serialization: enabled
  
  dependencies:
    - $kotlin.serialization.json: compile-only
  ```

* if you need to expose format dependencies transitively:
  ```yaml
  settings:
    kotlin:
      serialization: enabled
  
  dependencies:
    - $kotlin.serialization.json: exported
  ```

* if you need multiple formats:
  ```yaml
  settings:
    kotlin:
      serialization: enabled
  
  dependencies:
    - $kotlin.serialization.json
    - $kotlin.serialization.protobuf
  ```
