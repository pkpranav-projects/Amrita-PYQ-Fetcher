# Amrita PYQ Fetcher (Android)

Native Android port of the Go terminal app. Package: `com.amrita.pyqfetcher`.

- Kotlin, Jetpack Compose (Material 3), Hilt, Room, DataStore, WorkManager, OkHttp, Jsoup.
- `app/src/main/assets/catalog_seed.json` is generated from `cmd/internal/menu/cys_catalog.go`
  by `python tools/gen_seed.py` (161 verified bitstream paths, nothing synthesised).
- Cleartext HTTP is allowed only for `dspace.amritanet.edu` and `172.17.18.57`
  (`res/xml/network_security_config.xml`).

Build and test (JDK 17, Android SDK 36):

    ./gradlew testDebugUnitTest assembleDebug

On Windows, if Gradle fails with "Unable to establish loopback connection", set
`JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:/jtmp -Djava.io.tmpdir=C:/jtmp` (any space-free
temp dir); a short 8.3 `TEMP` path breaks the JDK's AF_UNIX pipe.
