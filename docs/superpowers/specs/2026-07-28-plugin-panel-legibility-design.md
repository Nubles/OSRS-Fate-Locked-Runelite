# RuneLite plugin panel legibility design

**Date:** 2026-07-28
**Status:** Approved
**Repository:** `Nubles/OSRS-Fate-Locked-Runelite`

## Goal

Make the unified Fate Locked sidebar readable at RuneLite's normal narrow panel
width without changing the plugin's behavior, connection model, setting count,
or section structure.

## Scope

This correction covers three reported layout defects:

1. Collapsible section arrows and titles are vertically clipped.
2. Rendering color previews show stale text from the preceding tooltip control.
3. The Run section compresses three key balances into an unclear abbreviation.

No connection, rule, warning, guardian, rendering, or persistence behavior
changes are included.

## Approved layout

### Collapsible section headers

Each header calculates its constrained height only after its arrow and title
have been assigned. Expanding and collapsing a section must preserve a maximum
height at least as large as the header's current preferred height.

The existing seven sections, order, expansion defaults, and independent toggle
behavior remain unchanged.

### Rendering color controls

The four saved rendering colors may contain alpha because the overlays use
transparency. A Swing button must not paint with that translucent color:
translucent component backgrounds can retain pixels from previously painted
rows while the narrow panel scrolls.

Each color button therefore displays an opaque RGB preview derived from the
saved color while the original RGBA value remains the value supplied to the
chooser and stored in RuneLite configuration. The labels remain:

- Unlocked color
- Frontier color (Chunked)
- Locked color
- Unauthored color

### Run key balances

The single abbreviated `Keys` value is replaced by three separate statistic
rows:

- `Keys` shows `state.keys`.
- `Omni Keys` shows `state.specialKeys`.
- `Chaos Keys` shows `state.chaosKeys`.

All three values use the existing amber emphasis. Missing run state shows an
em dash for each row.

## Verification

Automated regressions will prove:

- collapsed and expanded section headers retain sufficient height;
- translucent saved colors produce opaque previews without losing their RGB;
- the color chooser still receives and saves the original RGBA value;
- Run renders the three approved labels and values independently;
- all 30 retained settings still exist in their current owning sections.

After the full Gradle suite and packaged-JAR boundary gate pass, the candidate
will be relaunched at the normal RuneLite sidebar width. The seven headers,
Rendering controls, and Run balances will be visually checked against the
reported screenshots.
