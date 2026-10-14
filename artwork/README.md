# Aperture branding

`aperture-logo.svg` is the approved vector master. Its six rounded blades and
solid intersections are also drawn by `ApertureBrandArtwork` in Compose.

| Part | Aperture Purple role | Value |
| --- | --- | --- |
| Blades | Inverse Primary | `#6750A4` |
| Shared intersections | On Primary | `#381E72` |
| Play symbol and wordmark | Primary | `#D0BCFF` |
| Launcher and TV banner background | Background | `#111014` |

In-app launch, sidebar and poster-wall marks read the active TV Material colour
scheme: Inverse Primary for blades, On Primary for intersections, and Primary
for the play symbol. Material TV deliberately retains the approved Aperture
Purple palette. Launcher assets, the wordmark and exported SVGs retain that same
purple brand identity.

Each non-purple theme now supplies its own Inverse Primary, generated at Material
tone 40 through Google's public `MaterialColors.getColorRoles` API. This role
animates alongside Primary when switching themes or changing artwork accents.
The existing other theme roles are preserved.

Cinematic Ambient Mode overrides the logo with the current backdrop's extracted
accent, independently of the app theme (including Material TV). Material tonal
roles keep the blades at tone 40, intersections at tone 20 and play/wordmark at
tone 80. All three colours crossfade with the title. Failed extraction falls
back to the selected theme's logo palette. Brand and clock cards have symmetric
rounded corners, a restrained dark/artwork tint and a faint edge highlight.
On Android 12 and newer, the actual artwork behind each card is frosted with a
32 dp blur; the foreground logo and text remain sharp. One artwork-only graphics
layer is shared by the cards, avoiding extra image requests or bitmap copies.
Older TVs use a stronger tint for legibility when native blur is unavailable.

The monochrome version is a fully opaque single-colour silhouette. Internal
overlap shading is omitted because a themed launcher icon supplies one tint.
Adaptive foregrounds retain the existing safe-area padding; legacy square and
round icons are generated separately. The TV banner uses the app's own Outfit
SemiBold font, converted into vector paths in `aperture-banner.svg`.

Run `python tools/generate_brand_assets.py` from the repository root with Python,
Pillow, fontTools and Inkscape installed to regenerate every checked-in density,
legacy asset export, store image, README icon and TV banner. The old glass SVG
path is retained as a compatibility copy of the new mark.

Launch morphs a circle into a soft square, rounds it back into a seed, then
unfolds it into the exact approved blade paths. Dark intersections are computed
from the current geometry; the play symbol emerges as the ring settles.
The sidebar turns the blade ring while the play symbol stays upright. Sidebar
and Ambient Mode use the original default spatial spring for a full turn.
Closing lets a turn settle; reopening retargets the same spring while preserving
its current velocity. Launch once again ties its 90°/180°/360° rotations to the
shape morphs, using the original default/slow spatial springs.

Motion uses `MotionScheme.expressive()` through the brand methods in
`ApertureMotion`: fast/default/slow spatial specs for shape, scale and rotation;
fast/default effects specs for opacity. Compose animator-duration scaling also
governs completion.

References:
- https://m3.material.io/styles/motion/overview/how-it-works
- https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme

Validation: regenerate and visually inspect assets; compile the standard debug
variant and run unit tests via the branding PR workflow. Review the launch and
rapid drawer reopen behaviour on a TV device before merging.
