# FartMC

Read README.md, docs/PLAN.md and docs/REVERSE_ENGINEERING.md before changes.
Read docs/LICENSING.md before publishing. Code: GPL-3.0-only; public synthesized
audio: CC0-1.0. Myinstants audio and the ARR original JAR must never enter CI artifacts.
The owner approved the 21 supplied sounds for publication on 2026-10-21;
their provenance and exact hashes are in sounds/PROVENANCE.json and sounds/RIGHTS.txt.
The complete public pack combines those recordings with seven CC0 synthesized sounds.
Public pack: scripts/build-public-pack.ps1. Distribution gate: scripts/prepare-distribution.ps1.
Update docs/PLAN.md with completed work, evidence and remaining checks during work.
Target Paper 26.3 only, Java 25. No client mods, NMS or required resource pack.
Do not claim support for untested future versions. Do not commit downloaded JARs,
worlds, logs, caches or EULA acceptance. Original FartPlugin.jar is user input;
preserve it locally. Do not redistribute it or assume its license.
Build/test: powershell -ExecutionPolicy Bypass -File scripts/build.ps1
Server: powershell -ExecutionPolicy Bypass -File test-server/start.ps1 -AcceptEula
Use only loopback networking for the test environment. Keep online-mode=true.
