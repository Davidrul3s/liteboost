# LiteBoost (Fabric 1.20.4)

Tiny performance tuner. Before Minecraft starts, it lowers a few expensive video
settings in options.txt (fast graphics, minimal particles, no clouds, no entity
shadows, no smooth lighting, zero biome blend, capped render/simulation distance).

- Needs only Fabric Loader. Fabric API is NOT required.
- Settings: config/liteboost.properties (created on first launch).
- Backup of your old options: options.txt.liteboost-backup
- Applies once by default; set apply_every_launch=true to re-apply each launch.

Build: push this repo to GitHub, open the Actions tab, download the "liteboost-jar" artifact.
