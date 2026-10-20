# FartMC notices

Copyright (c) 2026 FartMC contributors.
The new implementation, tests, scripts and documentation in this repository
are licensed under GPL-3.0-only. See LICENSE for the complete terms.
This grant does not cover third-party assets or the original FartPlugin binary.

## Original project

The user supplied FartPlugin 1.2 by Denji as a behavioral reference.
Its metadata and bytecode were inspected; this repository contains a new
implementation, not the original classes or a decompiled source distribution.
It is not a clean-room development: the same developer inspected the bytecode.
The original project is All Rights Reserved on Modrinth:
https://modrinth.com/plugin/fartplugin
Hangar lists an unspecified license:
https://hangar.papermc.io/Denji/FartPlugin
No permission to distribute the original JAR, its Dropbox pack, bStats bundle
or decompiled code is claimed. They are excluded from Git and CI artifacts.
The plugin name and commands remain compatible; Denji is credited here for the
reference project and is not represented as the author of this new version.

## Public sound pack

The public pack is generated from mathematical oscillators and seeded noise
by scripts/SynthesizeSounds.java. It contains no sampled third-party audio.
The generated audio and pack metadata are dedicated under CC0-1.0;
see licenses/CC0-1.0.txt. The generator code remains GPL-3.0-only.

Myinstants recordings are separate local inputs, not covered by these grants.
No redistribution permission was found for the seven previously selected
recordings. They must not enter public artifacts without permission from the
relevant rights holders. Attribution alone does not supply that permission.
https://www.myinstants.com/en/terms_of_use.html

## Build and runtime dependencies (not bundled)

Paper API is a provided dependency. Paper's upstream licensing is GPLv3,
with some contributions additionally available under MIT:
https://github.com/PaperMC/Paper/blob/main/LICENSE.md
Adventure API is provided by Paper (MIT):
https://github.com/KyoriPowered/adventure/blob/main/LICENSE
JUnit Jupiter is a test-only dependency (EPL-2.0):
https://github.com/junit-team/junit5/blob/main/LICENSE.md
Maven is a build tool (Apache-2.0):
https://www.apache.org/licenses/LICENSE-2.0
FFmpeg is an external conversion tool, not linked into the plugin or included
in the pack; its build may be LGPL-2.1-or-later or GPL:
https://ffmpeg.org/legal.html
Java and GitHub Actions are build/runtime tooling, not distributed in artifacts.
Minecraft/Paper server binaries, worlds, caches and EULA acceptance are excluded.

For distributed plugin binaries, provide the corresponding source at the same
commit under GPLv3. CI uploads a source archive alongside every plugin artifact.
This is not an official Minecraft product and is not approved by or associated
with Mojang or Microsoft.
