# TapHunter 🐾🗺️

A creature-hunting walking game for the **RayNeo X3 Pro** smart glasses.
Your real neighborhood is the realm: real streets, real parks, real
cafes — renamed by the game into taverns, glades, and waygates — and
somewhere out there, always, a creature is waiting.

## The hunt

- **A level 1 creature always waits within 50 m** of wherever you start.
- Catch it, and the next one lairs **twice as far from your start** —
  100 m, 200 m, 400 m, onward — always placed **in the direction you are
  already walking**, so the hunt follows your stroll instead of dragging
  you backward.
- Creatures anchor to real places pulled live from OpenStreetMap: the
  town park becomes a **GLADE**, the coffee shop a **TAVERN**, the train
  station a **WAYGATE**, the trailhead a **GATE OF THE WILDS**.
- Between creatures, **treasure chests** appear at nearby real places —
  there is always something of interest on the map within your current
  hunt distance.

## The realm map

A big, bright, heading-up minimap (half again the size of the Everyday
minimap that inspired it) fills the view: every road and footpath drawn,
major ways in amber, trails in green, and all of it wearing RPG names —
the same street always gets the same fantasy name. Creatures pulse in
their species color with a level badge; chests glint gold; whatever you
target shows an edge arrow and live distance when it's off-map.

## The creatures

Twelve original species, each haunting a kind of real place:

| Species | Haunts |
|---|---|
| LEAFLING | parks and glades |
| THORNPUP | wild trails and reserves |
| PUDDLIM | fountains, springs, beaches |
| EMBERLING | cafes, taverns, hearths |
| BOOKWYRM | schools and libraries |
| LUXMOTH | sanctums and historic places |
| COINIX | shops and markets |
| FERROKIT | stations and waygates |
| VOLTLING | fuel depots and apothecaries |
| GUSTRIL | overlooks and attractions |
| SHADEPAW | streets at large |
| PRISMKIN | anywhere — rarely (the lure charm helps) |

**Catching one** is an 18-second timing duel: an orb circles the creature;
tap when it crosses the green arc. Three clean hits and it joins your
box; three slips and it flees to lair elsewhere at the same level.

**Treasure** opens in seconds and pays **essence**, spent on four upgrade
tracks: ORB POLISH (wider, slower arc), SCANNER (engage from farther),
LURE CHARM (rare PRISMKIN odds), SATCHEL (richer chests). Every
interaction is designed to finish in under 20 seconds — this is a
walking game, not a phone game.

## Controls (suite standard)

| Input | Hunt | Menus |
|---|---|---|
| **Swipe up/down** | zoom | move selection |
| **Swipe left/right** | cycle target | adjust value |
| **Tap** | engage / open / sonar ping | select / buy |
| **Double-tap** | menu | back |

## Where location comes from (important)

The X3's own location stack is empty — the OS exposes only a `passive`
provider and a `fused` stub that never produces a fix (verified with
`dumpsys location`; the Everyday project reached the same conclusion and
solved it with its phone companion). So TapHunter ships in two parts:

- **TapHunter** (glasses) — the game.
- **TapHunter Beam** (phone) — a tiny companion that streams the phone's
  GPS to the glasses over Bluetooth RFCOMM, on TapHunter's own service
  UUID so it coexists with Everyday's channel. Pair phone ↔ glasses in
  Bluetooth settings (already done if you use Everyday), open the Beam
  app, tap **START BEAM**, and hunt. The glasses also listen to every
  system provider, so if the firmware ever grows real GNSS it will be
  used automatically.

## Build & install

```bash
cd ~/Projects/TapHunter
./gradlew :app:assembleDebug :phone:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk        # glasses
adb shell pm grant com.taphunter android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.taphunter android.permission.BLUETOOTH_CONNECT
# sideload phone/build/outputs/apk/debug/phone-debug.apk on the phone
```

Prebuilt: `TapHunter.apk` (glasses) and `TapHunterBeam.apk` (phone) at
the repo root.

JDK 17, Kotlin, zero dependencies, no binary assets — sprites are Canvas
vectors, sounds are synthesized at first launch. Side-by-side 3D enables
itself on RayNeo hardware. Map data fetches from Overpass in ~600 m
tiles and is **cached on disk for a week**, so a route you have walked
once keeps working offline.

### Desk demo (no walking)

```bash
adb shell am start -n com.taphunter/.MainActivity --ez demo true \
  [--ef lat 37.7694 --ef lon -122.4862]
```

A simulated hunter strolls toward the current target by himself, and
capture taps always land — it tours the whole loop from a chair. The
real game (GPS, real timing duel) is unaffected.

## Credits

- Map data © [OpenStreetMap](https://www.openstreetmap.org/copyright)
  contributors, fetched via the Overpass API.
- The RayNeo X3 compass conventions (geomagnetic rotation vector, optical
  forward axes), the phone-feeds-location architecture, and the RFCOMM
  topology (phone serves, glasses connect to a bonded device's service
  UUID) come from Théophile Gaudin's excellent open
  [Everyday](https://github.com/TheophileGaudin/Everyday) HUD project,
  adopted here with the author's permission. TapHunter's implementation
  is fresh, dependency-free code in this suite's house style, on its own
  service UUID.
