# Minimal Vhaya Login Screen Redesign

Strip away decorative marketing cards, status badges, trust pills, and duplicate copy from the authentication screen so riders and drivers see only the compact Vhaya brand mark, clean open-canvas inputs, a primary action button, and a bottom mode-switch link.

## User Review & Critical Decisions

> [!IMPORTANT]
> All layout and structural choices below reflect your confirmed preferences from discovery and research on minimal ride-hailing authentication UX.

- **Confirmed Top Branding**: **Compact logo mark and wordmark only** — removes the tall dark-navy hero banner, region/service descriptor pills, and marketing taglines.
- **Confirmed Surface & Layout**: **Open canvas with clean input fields** — places Material 3 outlined fields directly on the cool slate-light (or slate-navy dark) canvas with generous whitespace instead of a heavy boxed card or decorative gradient orbs.
- **Confirmed Mode Switching**: **Bottom link to switch between sign in and sign up** — removes the top segmented toggle bar and replaces it with a clean text prompt below the primary CTA (`New to Vhaya? Create account` / `Already have an account? Sign in`).

---

## 1. Overview & Core Concept

- **What It Does**: Replaces the current multi-card, marketing-heavy authentication layout with a focused, single-column mobile login experience modeled after high-speed mobility apps (Uber/Bolt-style minimal cognitive load paired with Material 3 precision).
- **Target Audience / Persona**: Riders and drivers signing in outdoors or on the move who need immediate focus on email, password, and a large thumb-zone CTA without scrolling past banners.
- **Key Value**: Cuts visual noise by more than half, eliminates redundant headings, fits comfortably on a single viewport even with the keyboard open, and aligns with the Cobalt Blue + Signal Crimson brand palette.

---

## 2. User Experience & Visual Design

### What Is Being Removed (Unnecessary Clutter)
- **Tall Hero Editorial Card**: Removes the `196–290dp` dark-navy hero box, the `"SOUTH AFRICA · EVERYDAY RIDES"` pill, the `"RIDE · DRIVE · DELIVER"` sub-label, and the animated `"Good to have you back"` marketing paragraph.
- **Decorative Background Orbs & Rings**: Removes radial glow circles and contour strokes behind the canvas for a clean, distraction-free surface.
- **Outer Form Card Container & Duplicate Headings**: Removes the elevated white card border/shadow wrapper, `"Welcome back"` / `"Sign in with your Vhaya email and password"` filler copy, and the top segmented mode switcher.
- **Bottom Trust Pill Row**: Removes the `"6-digit PIN"`, `"Card · Wallet"`, and `"Vetted drivers"` marketing chips below the form.
- **Verbose Password Helper Copy**: Removes extra instructional paragraphs in create-account mode (keeping only concise inline placeholder guidance).

### Key User Flows
1. **Default Sign-In**: User lands on a calm open canvas showing the compact Vhaya emblem + `"VHAYA"` wordmark, a crisp `"Sign in"` title, Email field, Password field (with visibility toggle), right-aligned `"Forgot password?"` action, full-width `"Sign in"` button, and `"New to Vhaya? Create account"` link at the bottom.
2. **Create Account**: Tapping `"Create account"` smoothly transitions to add the `"Full name"` field above Email and Password, updates the title and CTA to `"Create account"`, and flips the bottom prompt to `"Already have an account? Sign in"`.
3. **Password Reset**: Tapping `"Forgot password?"` hides the password field, updates the title to `"Reset password"`, shows only Email + `"Send reset link"`, and provides a `"Back to sign in"` link.
4. **Email Verification Pending**: Preserves the verification check screen with matching minimal open-canvas styling (clean icon, email prompt, primary `"I've verified my email"` button, `"Resend"`, and `"Sign out"`).

### Visual Identity & Theme
- **Aesthetic Direction**: High-precision utilitarian minimalism — generous breathing room (`24dp` horizontal padding, `16dp` vertical field rhythm) so the Cobalt Blue primary CTA and Vhaya logo command immediate attention.
- **Color Palette & Mood**:
  - Open canvas uses `MaterialTheme.colorScheme.background` (`#F4F7FC` light / `#0B1220` dark).
  - Compact logo emblem sits in a clean Cobalt/Navy rounded badge (`44dp`) with crisp white chevron and Signal Crimson spark (`crimsonAccent`).
  - Inputs use clean `OutlinedTextField` styling with `14dp` rounded corners, subtle `outline` borders when unfocused, `primary` Cobalt borders when focused, and clean surface fill (`colorScheme.surface`) for effortless legibility.
  - Primary CTA uses solid Cobalt Blue (`primary` / `onPrimary`) at `52dp` height with `16dp` corners.
- **Typography & Hierarchy**:
  - Bold `"VHAYA"` wordmark (`titleLarge`, tight tracking) paired with the compact icon.
  - Crisp `"Sign in"` / `"Create account"` / `"Reset password"` section title (`headlineMedium`, `FontWeight.Bold`).
  - Clean `Inter` labels and `48dp` minimum touch targets on all buttons and text links.

### Interactive Feedback & Motion
- Smooth `AnimatedContent` transition when toggling between Sign In, Create Account, and Reset Password so the Full Name and Password fields appear/disappear without layout jumps.
- Inline error and notice banners appear cleanly above the CTA only when triggered, keeping the resting state spotless.
- Existing tactile press scale and inline `CircularProgressIndicator` on the submit button during network requests.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Open Canvas Over Contained Card**
  - *Chosen Approach*: Render the logo header, title, inputs, and CTA directly on `colorScheme.background` constrained to a `440dp` max width (centered cleanly on tablets/foldables as well as phones).
  - *Why*: Removing the nested card box eliminates double borders, reduces vertical padding overhead, and keeps the form well above the keyboard on compact phones.
  - *Alternatives Considered*: Keeping a single white card container — passed over because open-canvas auth feels noticeably lighter and matches your preference.

- **Decision 2: Preserve Existing Firebase Email/Password Auth Flow**
  - *Chosen Approach*: Keep all existing ViewModel actions (email/password sign-in, sign-up with email verification, and password reset) and accessibility/test tags intact while simplifying only the presentation layer.
  - *Why*: Ensures zero regression across backend API authentication, role routing, and existing test automation while delivering the full visual cleanup requested.
  - *Alternatives Considered*: Switching to phone-number + OTP auth — passed over because the current backend API and Firebase Identity Toolkit flow are built around email/password verification.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

```
┌────────────────────────────────────────────────────────────────────┐
│                       Auth Screen Root                             │
│  Open Canvas (colorScheme.background, max 440dp centered column)   │
└───────────────┬───────────────────────────────────┬────────────────┘
                │                                   │
                ▼                                   ▼
┌───────────────────────────────┐   ┌────────────────────────────────┐
│   Unauthenticated Flow        │   │  EmailVerificationPending      │
│                               │   │                                │
│ • Compact Brand Lockup        │   │ • Minimal Verification Prompt  │
│   (VhayaLogoMark + "VHAYA")   │   │ • "I've verified my email" CTA │
│ • Concise Mode Title          │   │ • Resend / Sign out actions    │
│ • Clean Outlined Inputs       │   └────────────────────────────────┘
│   (Name*, Email, Password*)   │
│ • Inline Forgot Password Link │
│ • Full-Width Cobalt CTA       │
│ • Bottom Mode-Switch Prompt   │
└───────────────┬───────────────┘
                │
                ▼
┌────────────────────────────────────────────────────────────────────┐
│                 ViewModel Auth State & Actions                     │
│  signIn() · signUp() · sendPasswordReset() · checkEmailVerified()  │
└────────────────────────────────────────────────────────────────────┘
```

- **State & Persistence Impact**: No changes to repository, Room database, API client, or ViewModel state contracts; mode switching (`SIGN_IN`, `CREATE_ACCOUNT`, `RESET_PASSWORD`) remains local UI state driven cleanly by the bottom text link and forgot-password action.
- **Interactive Component Mapping**:
  - Bottom mode-switch text button toggles `SIGN_IN` <-> `CREATE_ACCOUNT` and clears transient password visibility state.
  - `"Forgot password?"` switches to `RESET_PASSWORD`; `"Back to sign in"` returns to `SIGN_IN`.
  - Keyboard `ImeAction.Done` and the primary CTA invoke the active ViewModel auth call directly with focus dismissal.
