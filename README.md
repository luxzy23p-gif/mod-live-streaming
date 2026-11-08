# X Five Live 0.3.1 (Minecraft 26.2)

Client-side Fabric HUD prototype for Minecraft Java 26.2.

## Target
- Minecraft Java: 26.2
- Fabric Loader: 0.20.1
- Java: 25
- Fabric API: 0.161.0+26.2 (required by the current HUD event/keybinding implementation)
- Sodium: **not required**. The mod does not declare Sodium as a dependency.

## Build the JAR

The build requires internet access to resolve Minecraft/Fabric/Gradle dependencies and JDK 25.

```sh
gradle --no-daemon clean build
```

Output: `build/libs/xfive-live-0.3.1.jar`

A GitHub Actions workflow is included at `.github/workflows/build.yml`. Push this project to a GitHub repository, open **Actions → Build X Five Live → Run workflow**, then download the artifact named `XFiveLive-0.3.1-Minecraft-26.2`.

## Install

Put the built JAR in the instance's `mods` folder. Keep Fabric API 0.161.0+26.2 in `mods`. Sodium is optional and not needed by this mod.

## Current feature status / limitations

- F8 toggles the HUD.
- Translucent faux-glass music and chat panels are rendered.
- The displayed chat, gift, track and lyric values are placeholders until a companion bridge is implemented.
- Spotify/YouTube search and playback, TikTok/SocialBuzz live connection, profile pictures, timed lyric synchronization, and real popup animations are **not implemented yet**. This build must not be advertised as having those integrations.
- The HUD uses translucent panels, not a true background blur; true blur would require a compatible rendering implementation.
