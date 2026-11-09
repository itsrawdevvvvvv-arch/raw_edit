-keep class com.rawedit.app.render.** { *; }

# raw_edit render pipeline is driven from Kotlin only; keep its public surface.
-keep class com.rawedit.app.render.** { *; }
-keep class com.rawedit.app.service.** { *; }
-keepclassmembers class com.rawedit.app.data.** { *; }
