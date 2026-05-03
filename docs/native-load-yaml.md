# Native Load YAML

`native-load.yml` is the protocol consumed by `dev.oxyroid.native-load`.
Projects using the plugin should configure this file to decide which native libraries are removed from runtime APKs, packaged for distribution, and loaded at runtime.

```yaml
schemaVersion: 1
instrumentation:
  packages:
    - io.github.example.library.
  redirect:
    owner: com/example/NativeLoader
    method: loadLibrary
distribution:
  repository: owner/repository
  ref: master
  snapshotDirectory: native-packs
  runtimeVariant: release
  producerProject: :app
  runtimeConfigProject: :data
pack:
  id: example-1.0.0
  assetPrefix: example-native
  manifestPrefix: example-native
  artifacts:
    - com.example:example-aar:1.0.0
  libraries:
    - example
    - dependency
  loadOrder:
    - dependency
    - example
```

- `instrumentation.packages`: class-name prefixes whose bytecode should be scanned.
- `instrumentation.redirect`: static method that replaces `System.loadLibrary(String)`. Use JVM internal owner syntax with `/`.
- `distribution.repository`: GitHub repository used by runtime downloads, in `owner/repository` form.
- `distribution.ref`: repository ref used by runtime downloads. Use a branch if generated files are only added and never removed, or a tag if you want immutable pack snapshots.
- `distribution.snapshotDirectory`: repository directory containing generated native packs.
- `distribution.runtimeVariant`: only this build type is instrumented, has matching native libraries removed from APKs, and gets a native pack generation task. Debug builds normally leave the plugin inactive.
- `distribution.producerProject`: optional Gradle project path that owns native pack generation. Other app modules still get runtime-variant instrumentation and APK stripping, but do not register duplicate pack tasks.
- `distribution.runtimeConfigProject`: optional Android library project path that receives runtime `BuildConfig` fields. This keeps repository, ref, pack id, manifest prefix, and snapshot path out of consumer Gradle scripts.
- `pack.id`: stable versioned pack id. Change it when the AAR/native ABI changes.
- `pack.artifacts`: AAR coordinates to extract `jni/<abi>/*.so` from. The plugin resolves these non-transitively.
- `pack.libraries`: library names without `lib` and `.so`; these are excluded from APKs and included in release packs.
- `pack.manifestPrefix`: manifest file prefix used by both the generator and runtime downloader.
- `pack.loadOrder`: runtime load order written into the generated manifest.

Native pack files are placed under `<snapshotDirectory>/<pack.id>/` so old pack URLs stay stable as long as generated files are only added and never removed.
The generated manifest contains repository-relative asset paths and checksums; runtime code combines `distribution.ref` with those paths to build raw GitHub URLs.
