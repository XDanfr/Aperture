# Expressive Quick Menu

## Goal

Replace the current all-controls Quick Menu with a focused, category-based TV interface that scales to deeper configuration without becoming a navigation maze.

## Interaction model

- The thin OSD exposes a small tail-less chevron to indicate that the user can press Down.
- The classic OSD keeps its three-dot Quick Menu affordance.
- Opening the Quick Menu pauses playback and hides the player OSD.
- Focus moves into the Quick Menu automatically.
- Categories are opened with Select/Enter.
- Inside a page, D-pad Up/Down handles normal focus navigation.
- D-pad Left/Right does not switch Quick Menu pages.
- Back returns from a page to the category list, then closes the menu from the category list.
- Pages that eventually contain deeper settings may expose an explicit Done/Back action.

## Categories

- Audio
- Subtitles
- Playback
- Video
- Other

## Visual direction

- Treat the Quick Menu as a proper M3 Expressive surface rather than a dense control dump.
- Use spring-based entry and page transitions.
- Keep page changes spatially coherent without relying on horizontal D-pad navigation.
- Give focused TV controls clear expressive focus treatment.
- Keep the menu large enough to read and operate comfortably from a TV viewing distance.

## Implementation phases

1. Extract Quick Menu navigation state and split the current control dump into category/page components.
2. Build the category landing page with deterministic TV focus order.
3. Move existing Audio, Subtitle, and picture controls into their respective pages without changing behaviour.
4. Add Playback and Other pages around existing/obvious player actions.
5. Replace the current popup animation with M3 Expressive spring transitions.
6. Add the thin-OSD Down-chevron affordance and preserve the classic three-dot entry point.
7. Validate Back handling, focus restoration, nested pages, and playback pause/resume behaviour on TV hardware.

## Constraints

Avoid making Left/Right a global page-navigation mechanism. The navigation model should remain suitable for nested configuration such as subtitle customisation.
