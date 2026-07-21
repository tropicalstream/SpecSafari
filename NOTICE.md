# License and third-party notices

SpecSafari
Copyright (C) 2026 tropicalstream

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program (see `LICENSE`). If not, see
<https://www.gnu.org/licenses/>.

## Why GPLv3

`app/src/main/java/com/specsafari/render/MapRenderer.kt` (see the notice
below) adapts the on-screen design of the minimap widget from Théophile
Gaudin's [Everyday](https://github.com/TheophileGaudin/Everyday), which is
dual-licensed and defaults to GPLv3. GPLv3 is copyleft: a program that
incorporates GPLv3-covered code, compiled and distributed as a single
work, must itself be distributed under GPLv3. That's why this repository
as a whole carries the GPLv3 license above, rather than a permissive one.

## Third-party attribution

### Minimap rendering — `app/src/main/java/com/specsafari/render/MapRenderer.kt`
### and `app/src/main/java/com/specsafari/gl/HologramView.kt` (same design, GL layer)

Adapted from `MapWidget.kt` in Théophile Gaudin's **Everyday**
(<https://github.com/TheophileGaudin/Everyday>), used with the author's
permission. The heading-up circular disc, the screen-stable forward
marker whose geometry stays fixed at the top while the world rotates
beneath it, the halo-plus-core styling for major vs. minor roads, and
the offscreen-target edge arrow with live distance are all carried over
from that design. HologramView.kt independently re-derives the same
heading-rotation projection to align its 3D creatures with the minimap
disc. The label de-confliction, game logic, and every other file in
this project are original to SpecSafari.

  Everyday — Copyright (C) 2026 Théophile Gaudin <gaudin.theophile@gmail.com>
  Licensed under the GNU General Public License v3.0 (GPLv3).
  Full text: https://www.gnu.org/licenses/gpl-3.0.txt

### Map data

© [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors,
fetched via the Overpass API. ODbL license.

### Creature voice recordings

See `phone/VOICE_CREDITS.md` for the per-species real animal recordings
sourced from Wikimedia Commons (CC0, CC BY, CC BY-SA, and public domain).
