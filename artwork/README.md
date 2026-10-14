# Aperture branding

`aperture-logo.svg` is the approved vector master. Its six rounded blades and
solid intersections are also drawn by `ApertureBrandArtwork` in Compose.

| Part | Aperture Purple role | Value |
| --- | --- | --- |
| Blades | Inverse Primary | `#6750A4` |
| Shared intersections | On Primary | `#381E72` |
| Play symbol and wordmark | Primary | `#D0BCFF` |
| Launcher and TV banner background | Background | `#111014` |

In-app launch, sidebar and Ambient Mode marks read the active TV Material colour
scheme: Inverse Primary for blades, On Primary for intersections, and Primary
for the play symbol. Material TV deliberately retains the approved Aperture
Purple palette. Launcher assets, the wordmark and exported SVGs retain that same
purple brand identity.

Each non-purple theme now supplies its own Inverse Primary, generated at Material
tone 40 through Google's public `MaterialColors.getColorRoles` API. This role
animates alongside Primary when switching themes or changing artwork accents.
The existing other theme roles are preserved.

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
The sidebar turns the blade ring while the play symbol stays upright. Closing
and reopening retargets the spring without resetting the visible rotation.

Motion uses `MotionScheme.expressive()` through the brand methods in
`ApertureMotion`: fast/default/slow spatial specs for shape, scale and rotation;
fast/default effects specs for opacity. There is no hand-tuned bounce curve or
fixed startup delay. Compose animator-duration scaling also governs completion.

References:
- https://m3.material.io/styles/motion/overview/how-it-works
- https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme

Validation: regenerate and visually inspect assets; compile the standard debug
variant and run unit tests via the branding PR workflow. Review the launch and
rapid drawer reopen behaviour on a TV device before merging.
