# Keep the shared network layer's optional challenge/WebView entry points when
# consumed as a library. App-level R8 rules live in app/proguard-rules.pro.
-keep class app.kurenai.core.network.challenge.** { *; }
