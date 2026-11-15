# Native motion parity

The supplied frontend has motion in `src/index.css`, `AmbientBackdrop.tsx`, `TodayScreen.tsx`, `OnboardingScreens.tsx`, `SkiesScreen.tsx`, `CheckInScreen.tsx` and `MonthlyRecapScreen.tsx`. The first native port kept some artwork motion but simplified other effects.

| Reference behavior | Native behavior |
| --- | --- |
| 7-second float, 9-second drift, 5.5-second breathing glow | Moving ambient gradients; floating interest bubbles and home/recap auras |
| Handwritten hello and delayed subtitle | Existing 2.2-second native pen tracing retained |
| Trunk rise, branches, twig buds and leaf bloom | Staggered path drawing, bud/leaf overshoot and new flower growth |
| Existing tree remains when returning; Replay restarts it | Session-only growth memory, separate for personal/sample mode and each month |
| Butterfly float and 0.65-second wing beat | Original SVG paths drawn natively; received butterflies hover beside the tree and open their real message |
| 200–300 ms selection/press feedback and mood-orb changes | Soft control scale/colour transitions, animated mood ring and 1–5 orb size/tint |
| Soft onboarding and recap changes | Short screen/recap entry fades with an 8 dp rise; recap progress colours ease |

Browser-only mouse hover and the desktop workbench/phone frame are not mobile interactions. Native focus, ripple and touch semantics remain intact. Diary speech feedback must reflect actual recognition; do not reproduce the source's simulated recording behavior.

## Implementation

`SolaMotion.kt` centralizes frame-clock easing and the reference cycles. `sceneClock` pauses below the RESUMED lifecycle state; it resumes without skipping through a growth sequence. Short transitions finish rather than retaining an infinite animation. Drawing/layer transforms preserve the compact check-in layout and normal touch targets.

Artwork keeps animating when Android's animator duration scale is zero, as already required for the cat and welcome. Never change system animation settings. `LocalSceneMotion=false` produces complete static artwork for reduced-motion hosts and deterministic previews.

`LocalGardenGrowth` is ephemeral visual state owned by `SolaTheme`, not journal data. It records completed stage counts only. It must not save an entry, mark a nudge complete, copy sample data into a personal journal or send a message.

## Verify

Run the README build/unit/UI/lint checks. The motion tests render real native frames, compare growing/complete trees, check the tree stays grown after Settings/back, exercise an incoming butterfly, test lifecycle pause/resume, and save a rapid final mood selection during animation. The zero animator-scale setting is isolated Robolectric test state, never a device command.

Native frame previews: `app/build/sola-preview/restored-motion/` and `app/build/sola-preview/butterfly-motion/`.

Android control interactions use [InteractionSource and graphics layers](https://developer.android.com/develop/ui/compose/touch-input/user-interactions/handling-interactions). The cat module and host scheduling are independent and unchanged.
