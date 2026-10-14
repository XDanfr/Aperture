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
rounded corners and a flat, dark surface with restrained artwork colour influence.
On Android 12 and newer, the actual artwork behind each card is frosted with a
32 dp blur; the foreground logo and text remain sharp. One artwork-only graphics
layer is shared by the cards, avoiding extra image requests or bitmap copies.
Older TVs use a stronger tint for legibility when native blur is unavailable.
The surface has no reflective rim or lighting gradient.

The grayscale monochrome artwork keeps the play symbol white, blades mid-grey
and intersections dark grey. Android themed-icon launchers may tint the
`<monochrome>` adaptive layer as one colour; the tonal source artwork is also
useful anywhere the grayscale icon is shown directly.
Adaptive foregrounds retain the existing safe-area padding; legacy square and
round icons are generated separately. The TV banner uses the app's own Outfit
SemiBold font, converted into vector paths in `aperture-banner.svg`. Its layered
purple-spectrum backdrop is defined in `aperture-banner-background.svg`; the
adaptive banner uses the same artwork as a separate background layer so Google
TV can sample the palette without baking it into the transparent logo layer.

The adaptive banner foreground and background have their own expanded viewport
masters. Android expands adaptive child layers by 25% on every side; compensating
in the source keeps the complete visible artwork aligned inside the banner.
Both adaptive layers and flat fallbacks are exported for all five densities. The
onboarding drawable keeps its full-size artwork, separate from the padded
adaptive launcher layers. Run `python tools/check_brand_assets.py` to check every
density against Android's enlarged adaptive layers; the branding PR workflow
runs the same checks.

Run `python tools/generate_brand_assets.py` from the repository root with Python,
Pillow, fontTools and Inkscape installed to regenerate every checked-in density,
legacy asset export, store image, README icon and TV banner. The old glass SVG
path is retained as a compatibility copy of the new mark.

Launch morphs a circle into a soft square, rounds it back into a seed, then
unfolds it into the exact approved blade paths. Dark intersections are computed
from the current geometry; the play symbol emerges as the ring settles.
The sidebar turns the blade ring while the play symbol stays upright. Sidebar
and Ambient Mode restore the original 650 ms ease-in-out rotation. A small
four-degree overtravel then settles with the official fast spatial spring token.
The launch morph uses the same turn and spring landing while keeping its shape
transitions on the Expressive spatial tokens.

Motion uses `MotionScheme.expressive()` through the brand methods in
`ApertureMotion`: fast/default/slow spatial specs for shape, scale and the spin
settle;
fast/default effects specs for opacity. Compose animator-duration scaling also
governs completion.

References:
- https://m3.material.io/styles/motion/overview/how-it-works
- https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme

Validation: regenerate and visually inspect assets; compile the standard debug
variant and run unit tests via the branding PR workflow. Review the launch and
rapid drawer reopen behaviour on a TV device before merging.
