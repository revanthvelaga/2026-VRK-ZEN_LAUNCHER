# AGP already keeps every class declared in AndroidManifest.xml (activities, services,
# receivers) automatically, and Compose/Kotlin coroutines ship their own consumer rules
# inside their AARs — so this file is a safety net, not load-bearing.

# Telecom callbacks are invoked by the system via these exact class/method signatures.
-keep class com.vrk.dialer.CallService { *; }
-keep class com.vrk.dialer.CallActionReceiver { *; }

# Keep line numbers in stack traces from a sideloaded build's crash report readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
