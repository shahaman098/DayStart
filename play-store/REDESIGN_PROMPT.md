# Prompt: Redesign DayStart UI to feel modern and premium

You are redesigning **DayStart** (`com.loudwake.app`), an Android alarm app built in Jetpack Compose. Goal: make the UI feel modern, premium, and brand-distinctive — without changing core product behavior (scheduling, Wake Gate, Relapse Guard, Voice set, emergency stop).

## Product context
DayStart is for people who dismiss alarms and fall back asleep. Differentiator is accountability: ring → **Wake Gate** challenges → **Relapse Guard** so the morning sticks. Voice set creates alarms by speaking (confirm before create). Privacy stance: no ads, no account, on-device challenges.

## Current state (problems to fix)
- Home / Alarm Editor feel generic Material flat UI (pale blue bg, solid white cards, hairline borders) — could belong to any alarm app.
- Chase / mission screens already have stronger identity (dark red urgency, bold type) — extend that quality system-wide; don't dilute it.
- `GlassCard` in `Components.kt` is **misnamed**: flat opaque card + border, not glass. Either implement real frosted depth or rename.
- Theme already has Outfit type, ember accent (`#FF5C1A`), light/dark schemes, and a "Mission" palette — evolve these; don't throw them out for Inter/purple defaults.
- Store screenshots show light-only; keep dark + light coherent and screenshot-ready.

## Design direction (premium, not trendy-generic)
Pick **one** coherent visual system and apply it everywhere:

**Preferred direction — "Dawn mission control"**
- Atmosphere: soft sunrise-to-ink gradients or layered depth behind content (not flat single-color canvases).
- Surfaces: selective glass / frosted panels (blur + translucency + subtle border) for alarm cards and armed-session status — use sparingly so Wake Gate stays high-contrast and glanceable at 6am.
- Hierarchy: time is hero; mode tags and secondary copy recede.
- Motion: 2–3 intentional motions (e.g. arming pulse, gate progress, card settle) — presence, not noise.
- Avoid: purple-indigo gradients, cream+terracotta "AI default," neon glow stacks, dashboard clutter, emoji decoration, rounded-full pill spam.

Chase / Wake Gate must stay urgent and readable in half-asleep state: larger type, stronger contrast, primary CTA obvious. Fix any low-contrast helper text.

## Scope
1. Update `Theme.kt` (color tokens, shapes, typography scale) for a premium system.
2. Rebuild shared surfaces in `Components.kt` (`GlassCard` → real glass or honest rename; buttons, chips, dividers).
3. Restyle main flows: Home (alarm list + armed/chase banner), Alarm Editor, Chase / Wake Gate entry, Relapse Guard, Emergency stop, Voice set confirm dialogs.
4. Preserve accessibility: contrast AA+, large touch targets, readable times; emergency stop must remain unmistakable.
5. Do **not** add monetization UI, ads, or new features unless needed for visual hierarchy.
6. After UI changes, note which Play Store screenshots should be recaptured.

## Success criteria
- First glance on Home: clearly DayStart, not "generic Material alarm."
- Armed / Chase state feels intentional and premium, not bolted-on.
- Light and dark both look intentional.
- No glassmorphism claims unless blur/translucency is actually implemented.
- Product copy and flows unchanged; only visual/interaction polish.

Start by reading `Theme.kt`, `Components.kt`, and the main Compose screens, then propose a short token plan (colors / radii / elevation-or-blur) before editing. Implement the full visual pass after the plan is clear.
