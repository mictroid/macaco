# Code Brief: Map camera — frame ALL pins reliably (v9, ending the v1–v8 saga)

## The recurring symptom
On opening the Map (Adventures) tab, the camera should frame **every** geocoded entry pin
("show me my whole travel map"). It keeps failing one of two ways: (a) some pins fall off-screen,
or (b) it collapses to a useless zoomed-out world view centered on empty ocean.

This file has regressed **8 times** (v1–v8). The point of this brief is to not repeat any of them.

## The two hard constraints that fight each other
Every prior attempt satisfied one and broke the other — **none satisfied both at once**:

1. **Antimeridian / >180° longitude spans.** The user's real data spans the globe —
   **Argentina + Iceland + Japan + Germany** cover >180° of longitude. `LatLngBounds.builder().include(...)`
   builds a box that wraps the *wrong* way for such sets, centering on the empty Pacific so all pins
   fall off-screen.
2. **Exact viewport-accurate zoom.** The zoom must fit the pins to the *actual map area* (which is
   smaller than the screen — there's a header and bottom nav), accounting for Mercator latitude
   distortion.

## Attempt history (what was tried, why it failed)

| Ver | Approach | Why it failed |
|-----|----------|---------------|
| v1–v4 (vc25–vc33) | `LatLngBounds` + 1-arg `newLatLngBounds(bounds, padding)`; Null-Island exclusion; "global fit" iterations | The 1-arg overload **requires the map to be laid out** → threw `"Map size can't be 0"` when run too early; also flew across the Atlantic on every return. |
| v5 (vc36) | bounds-fit + manual zoom table | Cross-continental spans collapsed to ~zoom-2 world view. |
| v6 (vc38) | **Gave up on fit-all** — centered on most-recent entry only, `newLatLngZoom(target, 6f)` | Dodged the bug but is wrong by design: parked on Patagonia, ignored every other pin. User explicitly wants all pins. |
| v7 (vc39) | Back to fit-all: `LatLngBounds.builder().include(each)` + **4-arg** `newLatLngBounds(bounds, widthPx, heightPx, padding)` (explicit pixel dims, so no layout requirement; exact zoom) | **Antimeridian bug**: for the >180° span, `LatLngBounds` wrapped the wrong way → centered on the empty Pacific, pins off-screen. Also used `displayMetrics` full-screen size, not the map's actual smaller box. |
| **v8 (vc40, CURRENT)** | Abandoned `LatLngBounds`. Hand-rolled: latitude = simple min/max; longitude = find **largest empty gap** between sorted pins, center on the complement arc (antimeridian-correct); zoom from a **discrete degree-span lookup table** | Antimeridian now correct, **but the zoom is approximate** — a coarse `when` table keyed on raw degree span. It ignores Mercator latitude distortion and the real map viewport aspect ratio, so it under-/over-zooms and pins near edges still clip. |

Current code: `app/src/main/java/com/houseofmmminq/macaco/ui/screens/MapScreen.kt:138-202`.

## Root cause of the persistence
**v7 had exact zoom but wrong antimeridian. v8 has correct antimeridian but approximate zoom.**
Nobody has combined the two. The v8 zoom table (`MapScreen.kt:182-192`) is the remaining defect.

## Recommended fix (combine both correctly)
Keep v8's largest-empty-gap logic for the **center/arc** (it is antimeridian-correct), then replace
the zoom table with an **exact fit** via the `LatLngBounds(SW, NE)` **constructor** — NOT the builder.

> **Why not `LatLngBounds.builder().include(...)`** (corrects an earlier draft of this brief):
> the builder always picks the *smaller* of the two longitude arcs, so it can never represent a span
> wider than 180°. The user's data spans **209°** (arc from Argentina −69° east to Japan 140°; the
> empty Pacific gap is the other 151°). The builder picks that 151° Pacific arc and centers on empty
> ocean — the exact bug. "Shifting longitudes first" does NOT help: those 4 pins are already
> continuous in [−69, 140], nothing shifts, and the builder still minimizes to 151°. The fix is to
> bypass the builder and construct the bounds from the corners directly, which v8 already has the
> numbers for.

1. Compute the arc via largest-gap exactly as v8 does (`MapScreen.kt:163-177`) — gives `arcStart`
   (western edge = pin just east of the gap), `lngSpan`, and lat min/max.
2. Build the bounds straight from those corners. The `LatLngBounds(southwest, northeast)` constructor
   takes the corners literally and treats `NE.lng < SW.lng` as **antimeridian-crossing** (it does not
   minimize), so it can represent a >180° span:
   ```kotlin
   // v8 already computed: arcStart, lngSpan, latMin (lats.min()), latMax (lats.max())
   val neLng = arcStart + lngSpan
   val bounds = if (neLng <= 180.0) {
       // Normal case — user data: arcStart=-69, neLng=140 → literal 209° box, no crossing.
       LatLngBounds(LatLng(latMin, arcStart), LatLng(latMax, neLng))
   } else {
       // Antimeridian-crossing — Fiji+Hawaii: arcStart=178, neLng=203 → NE.lng = 203-360 = -157,
       // and SW.lng(178) > NE.lng(-157) tells the SDK the box crosses the date line.
       LatLngBounds(LatLng(latMin, arcStart), LatLng(latMax, neLng - 360.0))
   }
   ```
   `arcStart` is always an actual pin longitude (∈ [−180,180]); `neLng - 360` lands back in range,
   so both corners are valid `LatLng`s. The hand-rolled center + zoom table (`MapScreen.kt:159-193`)
   is deleted — the bounds + 4-arg overload below replace it.
3. Feed that bounds to the **4-arg** `newLatLngBounds(bounds, width, height, padding)` — the overload
   that needs no layout (this was v7's one good idea; the 1-arg form must NOT be used). It computes
   the precise zoom to frame the box in the given pixel area.
4. **Use the map composable's actual measured size**, not `displayMetrics` (the map is shorter than
   the screen — header + bottom nav). Capture it via `BoxWithConstraints` / `onSizeChanged` around the
   `GoogleMap` and pass those px to the 4-arg overload; gate the camera move on a non-zero size.
5. Keep single-location → `newLatLngZoom(point, 6f)`, the `cameraPositioned` scrim gate, and the
   8-second `revealTimedOut` safety net — all unchanged and working.

## Must-test cases (regression set)
- **Globe-spanning** (the breaker): Argentina + Iceland + Japan + Germany → all 4 visible, centered
  ~Eurasia, NOT the Pacific.
- **Single location** → country-level zoom 6, no throw.
- **Tight cluster** (one city, few km apart) → not over-zoomed into the street.
- **Two pins straddling the antimeridian** (e.g. Fiji + Hawaii) → framed across the date line, not
  the long way round.
- **Cold open** (no geocode cache yet) + **warm return open** → never shows the default `(20,0) z2`
  world view, never "Map size can't be 0".

## Hard-won rules (do NOT regress)
- ❌ Never use `LatLngBounds.builder().include()` at all here — it always picks the smaller longitude
  arc, so it can't represent the user's >180° span and re-creates the Pacific-centered bug (killed
  v1–v5, v7; shifting longitudes does NOT rescue it). Use the `LatLngBounds(SW, NE)` constructor.
- ❌ Never use the 1-arg `newLatLngBounds(bounds, padding)` — it throws if the map is not laid out
  (killed v1–v4).
- ❌ Never use a discrete zoom lookup table — it is the v5/v8 defect.
- ✅ Antimeridian handling via largest-gap is correct — keep it.
- ⚠️ This file has regressed **8 times** — treat as high-risk, verify on-device (A53) before
  shipping, and ship it **alone** (no batch) so a regression is easy to bisect.
