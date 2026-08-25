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

# WorkManager, which Glance 1.2 puts on the critical path for *every* widget render: a widget
# update is enqueued as an AsyncRequestWorker job rather than run inline.
#
# WorkerWrapper assembles that job's input by instantiating an InputMerger from its class name,
# via Class.forName(name).newInstance(). work-runtime ships "-keep class * extends
# androidx.work.InputMerger" -- which keeps the class but says nothing about its members -- so R8
# sees a no-arg constructor nobody calls and deletes it. The job then fails before it starts:
#
#   E WM-InputMerger:  androidx.work.OverwritingInputMerger has no zero argument constructor
#   E WM-WorkerWrapper: Could not create Input Merger androidx.work.OverwritingInputMerger
#
# provideGlance never runs, so every widget sits on glance_default_loading_layout forever. It is
# release-only (R8), permanent (each retry fails identically) and silent unless you are watching
# the WM- log tags -- which is why it shipped. Verified by pinning a widget from the minified
# build, not just by compiling it.
-keep class * extends androidx.work.InputMerger { <init>(); }

# Workers are reflected on the same way. work-runtime only keeps `public <init>(...)` on *public*
# subclasses; spelling the signature out covers Glance's internal ones too.
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
