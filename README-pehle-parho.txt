AdFree Tube - GitHub se APK banane ke steps
===========================================
1. Is zip ko extract karein.
2. GitHub par apni repository kholein -> Add file -> Upload files.
3. Extract hue folder ke ANDAR ki saari cheezen drag karein (app, .github, build.gradle.kts,
   gradle.properties, settings.gradle.kts). Commit se pehle list mein dekh lein ke
   app/src/main/res/drawable/ic_launcher.xml aur app/src/main/java/com/adfree/tube/*.kt nazar aa rahi hon.
4. Commit changes dabayen. Actions tab mein build chalegi (3-6 minute).
5. Hara tick aaye to Artifacts se AdFreeTube-apk download karein.

Agar .github folder upload na ho: build-yml-copy.txt ka text copy karke GitHub par
Add file -> Create new file -> naam: .github/workflows/build.yml -> paste -> Commit.

Sources badalne hon (YouTube server, TV playlists, movies link):
app/src/main/java/com/adfree/tube/Config.kt ko GitHub par edit karein.
