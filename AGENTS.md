# FartMC

Read README.md, docs/PLAN.md and docs/REVERSE_ENGINEERING.md before changes.
Update docs/PLAN.md with completed work, evidence and remaining checks during work.
Target Paper 26.2+, Java 25. No client mods, NMS or required resource pack.
Do not claim support for untested future versions. Do not commit downloaded JARs,
worlds, logs, caches or EULA acceptance. Original FartPlugin.jar is user input;
preserve it locally. Do not redistribute it or assume its license.
Build/test: powershell -ExecutionPolicy Bypass -File scripts/build.ps1
Server: powershell -ExecutionPolicy Bypass -File test-server/start.ps1 -AcceptEula
Use only loopback networking for the test environment. Keep online-mode=true.
