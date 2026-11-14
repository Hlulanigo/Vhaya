# Vhaya Visual Design Plan

**Prepared:** 14 November 2026  
**Status:** Design brief for the next visual-design pass; no UI code changed. The app was not run.  
**Scope:** Rider and driver mobile experience first; wallet, support, and admin follow the same system. South African licensing and regulatory work is out of scope and remains deferred.

## Design goal

Make Vhaya feel like the clearest, most dependable way to get a ride in Polokwane—not a copy of a global ride app and not a collection of feature cards.

“Surpass the ones online” should mean a better experience users can notice and test:

- They can find the next action immediately.
- Fare, pickup, vehicle, driver, and safety details are easy to verify.
- The interface remains readable outdoors, on small Android screens, and with larger text.
- Every important state looks intentional: waiting, no drivers, poor connection, payment issue, driver arriving, and trip complete.
- Rider and driver screens feel like one Vhaya product instead of separate templates.

Do not claim Vhaya is better until local users have compared and tested the designs.

## What the public references show

These are current Google Play listing screenshots and marketing materials, not direct inspection of the competitors’ live apps. Treat them as visual reference, not proof of every in-app interaction.

| Reference | Visible strength to learn from | What Vhaya should do differently |
|---|---|---|
| **Uber** | Restrained black-and-white brand, map-led ride imagery, and a clear ride/fare card. | Keep the booking job focused. Avoid bringing unrelated services or extra panels into the rider’s first screen. |
| **Bolt** | Strong green brand recognition, bold booking imagery, upfront vehicle choices, and a very visible scheduled-ride message. | Make Vhaya’s own blue identity more distinctive; avoid making every screen feel like a promotional banner. |
| **inDrive** | Memorable lime accent, visible fare-offer flow, and safety information shown in its store imagery. | Preserve Vhaya’s fare-offer feature, but use calmer, more consistent color and hierarchy rather than competing bright accents. |

### Vhaya’s starting point

The current app already has a navy/blue palette, Material 3 surfaces, Inter typography, JetBrains Mono for numeric details, a map-led rider home, rounded cards, fare offers, trip PINs, and rider/driver/admin experiences. Keep the useful foundations. The design pass should reduce competing surfaces and clarify what matters in each state rather than replace the brand without evidence.

## Recommended visual direction

### “Local clarity”

Use a calm, confident mobility identity: precise maps, strong fare and ETA typography, warm neutral surfaces, and a controlled Vhaya-blue action color. Add local context through useful places and clear pickup descriptions—not flag colors or unvalidated cultural decoration.

The map is the working area, not background wallpaper. Bottom sheets should carry the decision being made, with only one dominant surface and one primary action at a time.

### Draft visual tokens

These are design targets to prototype and tune—not final production color values.

| Token | Direction | Current starting point |
|---|---|---|
| Brand action | Vhaya blue; use consistently for the primary action and selected states. | Existing primary `#1652E6`. |
| Main ink | Deep navy for strong readable text and dark surfaces. | Existing dark background `#090F1C`. |
| Day surface | Cool, very light neutral that keeps the map and fare cards readable in sunlight. | Existing light background `#F4F7FC`. |
| Map route | A bright blue route with distinct pickup, destination, and driver markers. | Existing route-highlight colors. |
| Success | Restrained teal/green for confirmed or completed states only. | Existing positive color. |
| Safety/emergency | Red/coral reserved for emergency and destructive actions. Never use it as decoration. | Existing crimson/error colors. |
| Fare figures | Use the existing mono face only for prices, trip PINs, and compact identifiers. | Inter UI + JetBrains Mono. |

Do not use Bolt’s green or inDrive’s fluorescent lime as Vhaya’s dominant brand color. Keep both light and dark themes, then test the default with local riders and drivers in bright outdoor conditions.

### Layout and component rules

- Use a consistent 4 dp spacing rhythm. Prefer 16–20 dp screen gutters and 12–20 dp card padding.
- Use a small radius set (12, 16, and 24 dp). Reserve the largest radius for sheets and major hero surfaces.
- Reduce borders and shadows. Use elevation only when it explains which layer is interactive.
- Avoid stacking multiple floating pills over the map. Keep map controls grouped and leave the route visible.
- Use one filled primary button per decision area. Secondary actions should be quieter but still easy to find.
- Keep labels short and specific. Put fare, ETA, pickup, and vehicle information before decorative copy.
- Show state with text and an icon as well as color.
- Reuse the same button, card, status, driver identity, fare, and empty-state components across rider and driver flows.

## Screen-by-screen plan

### Rider home and booking

1. **Home:** Keep the map visible. Give “Where to?” the strongest non-map emphasis. Show the current pickup clearly, with account access and map controls grouped consistently.
2. **Search:** Make pickup and destination unmistakable, easy to swap, and easy to correct on the map. Show useful local landmarks as well as street addresses.
3. **Route and fare:** Show route distance/time, fare basis, and payment choice before the request is committed. Make a custom fare offer look like an intentional choice, not an error or discount field.
4. **Ride options:** Put price, arrival estimate, and passenger capacity in a stable scan order. Keep the final request action pinned and visible without covering important map or fare information.
5. **No-driver state:** Say clearly that no driver has accepted yet, show what the rider can do next, and keep cancellation visible without presenting a false live ETA.

### Driver request and trip

- Make online/offline status unmistakable and prevent the control from looking active when the driver is not approved.
- Each request card should prioritize pickup distance, destination, estimated earnings/fare, and remaining response time.
- Keep Accept and Counter-offer visually distinct; avoid a countdown animation that distracts from the offer.
- In an active trip, show the next required step, navigation/map context, and the pickup PIN or trip status without forcing the driver to scan several cards.
- Make location, connection, and request freshness visible when they affect dispatch.
- Design for glanceability and avoid interactions that require detailed reading while a vehicle is moving.

### Wallet, history, support, and admin

- Wallet: make the real balance, recent transactions, and payment state clear. Never make a decorative card look like a bank card or imply a payment succeeded before verification.
- Trip history: prioritize route, date, final fare, and receipt/support actions.
- Support: separate routine questions from urgent safety concerns and show the status of a submitted case.
- Admin: use a compact operational dashboard with readable status, clear review actions, and destructive-action confirmation. Keep admin styling consistent but visually distinct from rider booking.

## Motion, feedback, and state design

- Use brief transitions (about 150–250 ms) for sheet changes and state updates. Avoid motion that competes with the map.
- Reserve haptics for meaningful actions such as accepting a request or confirming a ride.
- Design explicit loading, empty, offline, stale-data, permission-denied, payment-pending, payment-failed, no-driver, and cancelled states.
- Never leave a blank panel or spinner without an explanation and a recovery action.
- Make the active ride’s current state and next step understandable without relying on color or animation.

## Accessibility and local usability bar

- Maintain at least 4.5:1 contrast for normal text and 3:1 for large text and meaningful controls.
- Use at least 48 dp touch targets for primary actions and safety controls.
- Support system font scaling and test at 1.3× text size and a narrow 320 dp screen.
- Provide TalkBack labels for map controls, fare choices, trip state, and safety actions.
- Test route/marker visibility in both themes and with color-vision differences.
- Keep ZAR formatting, South African phone-number expectations, and local place naming consistent.
- Do not add language translations until the wording is reviewed by fluent local speakers.

## Quality bar and review checklist

Before integrating a visual redesign, check that:

- A first-time rider can locate destination search and understand the next step without coaching.
- Fare, ETA, payment method, driver identity, vehicle, and registration are easy to distinguish.
- On every screen, the main action is visually clear and does not compete with another filled action.
- No text, prices, labels, or critical controls are clipped at narrow width or larger text size.
- Rider and driver screens use the same spacing, typography, status, and action rules.
- Maps remain legible beneath sheets and controls in light and dark themes.
- Loading, offline, no-driver, failed-payment, and emergency states are designed—not left to default dialogs.
- At least five local riders and five local drivers complete the key flows on their own devices before the design is called ready.

Record task completion time, mis-taps, fare comprehension, and whether users can identify the correct car before boarding. Visual polish alone is not a pass.

## Design work sequence

1. Inventory the current rider and driver screens and identify clutter, duplicated patterns, and missing states.
2. Prototype the design tokens and shared controls in both light and dark themes.
3. Redesign the rider home → booking → matching → active trip path first.
4. Apply the same visual system to driver requests and active-trip mode.
5. Bring wallet, history, support, and admin into the system.
6. Compare the prototype at the same phone size as the public references; test with local riders and drivers before changing production UI.

## Reference links

- [Uber on Google Play](https://play.google.com/store/apps/details?hl=en_ZA&id=com.ubercab)
- [Bolt on Google Play](https://play.google.com/store/apps/details?hl=en_ZA&id=ee.mtakso.client)
- [inDrive on Google Play](https://play.google.com/store/apps/details?hl=en-ZA&id=sinet.startup.inDriver)
- [Bolt rider safety](https://bolt.eu/en-za/rides/safety)
- [inDrive South Africa rides](https://indrive.com/en-za/city-rides)
