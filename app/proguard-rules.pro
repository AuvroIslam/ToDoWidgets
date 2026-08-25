# Glance / AppWidget: receivers, actions and activities are referenced from the
# manifest or from RemoteViews at runtime, so keep them and their no-arg ctors.
-keep class com.simpletodo.widget.** { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-keep class * implements androidx.glance.appwidget.action.ActionCallback { *; }

# Glance uses reflection to resolve generated RemoteViews layouts.
-keep class androidx.glance.appwidget.protobuf.** { *; }
-dontwarn androidx.glance.**

# Room, reached through WorkManager, which glance-appwidget pulls in to schedule widget updates.
# Room builds its database class by name at runtime -- it appends "_Impl" to the abstract class
# and reflects on the result -- so R8 sees no reference to the generated class and strips it.
# Without this the release build dies at startup in InitializationProvider with
# "Failed to create an instance of androidx.work.impl.WorkDatabase", long before any of our code
# runs. Verified by launching the minified build, not just by compiling it.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**
