---
version: alpha
name: eggbabe-design-system
description: eggbabe (蛋宝宝) presents itself as a warm, organic emotional-companion brand built around a three-egg color narrative — 青禾绿 growth green ({colors.brand-green}) for rootedness, 晨露黄 dawn yellow ({colors.brand-yellow}) for the spark of awakening, and 桃粉霜 peach frost ({colors.brand-pink}) for the warmth of bonding. The wordmark is a rounded geometric lowercase logotype in deep ink-green ({colors.ink-green}), with small colored accent dots echoing the three-egg palette on the letterforms. The system runs on exactly two type families: all Chinese (display and body) uses PingFang SC, all English uses Google Sans. The system favors soft, elliptical shapes, generous whitespace, and a mythological-but-tactile tone (破壳 hatching, 灵魂底色 soul imprint, 命运 fate) that must read as premium collectible-toy, not generic SaaS. Coverage: marketing site (eggbabe.com) sections and WeChat mini-program UI primitives.

colors:
  # === Brand core (from VI system) ===
  brand-green: "#ACC861"        # 青禾绿 · 扎根·滋养 — primary, the "soil" of the brand
  brand-yellow: "#F1EC9A"       # 晨露黄 · 觉醒·互动 — secondary, the "spark" accent
  brand-pink: "#FFC5BA"         # 桃粉霜 · 羁绊·亲密 — tertiary, the "warmth" accent
  ink-green: "#3F5A47"          # wordmark ink / deep brand text — sourced from eggbabe.com theme-color meta; VERIFY against source logo file
  ink-green-pressed: "#324839"  # shade-20, button pressed state
  ink-green-deep: "#26362B"     # shade-40, high-contrast text-on-light use

  # === Tints (soft surfaces, derived from brand core — mirrors Notion's card-tint pattern) ===
  green-surface: "#F3F7E7"      # tint-15, card background
  green-surface-soft: "#E6EED0" # tint-30, hover/deeper card background
  green-on-tint: "#566430"      # shade-50, text-safe on green surfaces (WCAG AA 5.9:1)
  yellow-surface: "#FDFCF0"
  yellow-surface-soft: "#FBF9E1"
  yellow-on-tint: "#78764D"     # shade-50, text-safe on yellow surfaces (WCAG AA 4.5:1)
  pink-surface: "#FFF6F5"
  pink-surface-soft: "#FFEEEA"
  pink-on-tint: "#80625D"       # shade-50, text-safe on pink surfaces (WCAG AA 5.2:1)
  ink-green-surface: "#E2E6E3"
  ink-green-surface-soft: "#C5CEC8"

  # === Neutrals ===
  canvas: "#FFFFFF"
  canvas-dark: "#0A0A0A"        # reversed lockup background
  ink: "#1A1A1A"                # general body text (not brand green — plain near-black for small-size legibility)
  ink-muted: "#5C5C5C"
  ink-faint: "#8C8C88"
  hairline: "#E5E3DF"
  hairline-strong: "#CFCBC4"
  on-dark: "#FFFFFF"
  on-dark-muted: "#B5B5B0"

  # === Semantic (functional, brand-adjacent but distinct from decorative accents) ===
  # Each has a -base (for fills/icons/borders) and a -text (darkened to pass WCAG AA as text on white)
  semantic-success: "{colors.brand-green}"   # reuses growth green — narratively consistent (成长顺利)
  semantic-success-text: "#566430"           # text-safe green on light
  semantic-warning: "#E8A33D"                # fill/icon only — fails as text on white (2.2:1)
  semantic-warning-text: "#976A28"           # use this for warning TEXT (WCAG AA 4.8:1)
  semantic-error: "#D9463C"                  # fill/icon only — fails as text on white (4.3:1)
  semantic-error-text: "#C33F36"             # use this for error TEXT (WCAG AA 5.1:1)
  semantic-error-surface: "#FDECEA"          # pale error background for input error / toast
  disabled-text: "#A8A6A0"                   # slightly darker than ink-faint for disabled labels

typography:
  # English — Google Sans across all weights (SIL OFL, released 2025-12-10, free for commercial use)
  hero-display-en:
    fontFamily: Google Sans
    fontSize: 88px
    fontWeight: 700
    lineHeight: 1.05
    letterSpacing: -1.5px
  display-lg-en:
    fontFamily: Google Sans
    fontSize: 56px
    fontWeight: 700
    lineHeight: 1.10
  heading-1-en:
    fontFamily: Google Sans
    fontSize: 44px
    fontWeight: 600
    lineHeight: 1.15
  heading-2-en:
    fontFamily: Google Sans
    fontSize: 34px
    fontWeight: 600
    lineHeight: 1.20
  heading-3-en:
    fontFamily: Google Sans
    fontSize: 26px
    fontWeight: 600
    lineHeight: 1.25
  subtitle-en:
    fontFamily: Google Sans
    fontSize: 18px
    fontWeight: 400
    lineHeight: 1.50
  body-md-en:
    fontFamily: Google Sans
    fontSize: 16px
    fontWeight: 400
    lineHeight: 1.55
  body-sm-en:
    fontFamily: Google Sans
    fontSize: 14px
    fontWeight: 400
    lineHeight: 1.50
  caption-en:
    fontFamily: Google Sans
    fontSize: 13px
    fontWeight: 500
    lineHeight: 1.40
  button-en:
    fontFamily: Google Sans
    fontSize: 15px
    fontWeight: 600
    lineHeight: 1.30

  # Chinese — PingFang SC across all weights, display down to caption (system font, needs fallback stack — see Known Gaps)
  hero-display-cn:
    fontFamily: "PingFang SC"
    fontSize: 72px
    fontWeight: 600   # PingFang SC tops out at Semibold — there is no true Bold/Black cut, see Known Gaps
    lineHeight: 1.20
  display-lg-cn:
    fontFamily: "PingFang SC"
    fontSize: 48px
    fontWeight: 600
    lineHeight: 1.25
  heading-1-cn:
    fontFamily: "PingFang SC"
    fontSize: 36px
    fontWeight: 600
    lineHeight: 1.30
  heading-2-cn:
    fontFamily: "PingFang SC"
    fontSize: 28px
    fontWeight: 600
    lineHeight: 1.35
  heading-3-cn:
    fontFamily: "PingFang SC"
    fontSize: 22px
    fontWeight: 600
    lineHeight: 1.40
  subtitle-cn:
    fontFamily: "PingFang SC"
    fontSize: 17px
    fontWeight: 400
    lineHeight: 1.55
  body-md-cn:
    fontFamily: "PingFang SC"
    fontSize: 15px
    fontWeight: 400
    lineHeight: 1.60
  body-sm-cn:
    fontFamily: "PingFang SC"
    fontSize: 13px
    fontWeight: 400
    lineHeight: 1.55
  caption-cn:
    fontFamily: "PingFang SC"
    fontSize: 12px
    fontWeight: 500
    lineHeight: 1.45
  button-cn:
    fontFamily: "PingFang SC"
    fontSize: 15px
    fontWeight: 600
    lineHeight: 1.30

rounded:
  xs: 4px
  sm: 8px
  md: 12px
  lg: 16px
  xl: 24px
  xxl: 32px
  full: 9999px
  egg: "50% 50% 50% 50% / 58% 58% 42% 42%"   # signature asymmetric egg silhouette — use for avatar frames, hero containers, hatching-moment visuals only

spacing:
  xxs: 4px
  xs: 8px
  sm: 12px
  md: 16px
  lg: 24px
  xl: 32px
  xxl: 48px
  section-sm: 64px
  section: 96px
  section-lg: 128px
  hero: 160px

elevation:
  # Soft, warm-tinted shadows (green-black, not neutral gray) to match the organic brand feel
  flat: "none"
  raised: "0px 1px 2px rgba(38, 54, 43, 0.06), 0px 2px 4px rgba(38, 54, 43, 0.04)"   # cards at rest
  floating: "0px 4px 12px rgba(38, 54, 43, 0.10)"                                      # hovered cards, avatars, dropdowns
  overlay: "0px 12px 32px rgba(38, 54, 43, 0.16)"                                      # modals, bottom sheets, popovers
  focus-ring: "0px 0px 0px 3px rgba(63, 90, 71, 0.30)"                                 # keyboard focus / active input glow

components:
  # === Logo lockups ===
  logo-primary:
    description: "Default lockup — ink-green wordmark on light/white surfaces. Use for nav bars, footers, everyday UI."
    textColor: "{colors.ink-green}"
    background: "{colors.canvas}"
  logo-reversed:
    description: "White wordmark on dark/black surfaces."
    textColor: "{colors.on-dark}"
    background: "{colors.canvas-dark}"
  logo-egg-hero:
    description: "Wordmark sitting over three DISCRETE overlapping egg-shaped ovals (green, yellow, pink, left-to-right), each an ellipse — NOT a horizontal gradient bar. The eggs overlap slightly and the wordmark overlaps the eggs. HERO/KV MOMENTS ONLY — never shrink into nav bars, favicons, or list items. Reserved for landing page hero, packaging, KOL key visuals."
    eggShapes: "three ellipses using {rounded.egg}, fills {colors.brand-green} / {colors.brand-yellow} / {colors.brand-pink}"
    wordmarkColor: "{colors.ink-green} on light; {colors.on-dark} on dark"

  # === Buttons ===
  button-primary:
    backgroundColor: "{colors.ink-green}"
    textColor: "{colors.on-dark}"
    typography: "{typography.button-cn}"
    rounded: "{rounded.full}"
    padding: "12px 28px"
  button-primary-hover:
    backgroundColor: "{colors.ink-green-pressed}"    # slight darken on hover (web/marketing site)
    shadow: "{elevation.floating}"
  button-primary-pressed:
    backgroundColor: "{colors.ink-green-pressed}"
    textColor: "{colors.on-dark}"
    transform: "scale(0.98)"
  button-primary-disabled:
    backgroundColor: "{colors.hairline}"
    textColor: "{colors.disabled-text}"              # was ink-faint (2.6:1, near-invisible) — do not use ink-faint here
  button-secondary:
    backgroundColor: "transparent"
    textColor: "{colors.ink-green}"
    border: "1.5px solid {colors.ink-green}"
    rounded: "{rounded.full}"
    padding: "12px 28px"
  button-secondary-hover:
    backgroundColor: "{colors.ink-green-surface}"    # faint green fill on hover
  button-ghost:
    backgroundColor: "transparent"
    textColor: "{colors.ink}"
    rounded: "{rounded.full}"
    padding: "10px 20px"
  button-ghost-hover:
    backgroundColor: "{colors.hairline}"

  # === Cards (tinted surfaces echo the three-egg narrative) ===
  card-base:
    backgroundColor: "{colors.canvas}"
    border: "1px solid {colors.hairline}"
    rounded: "{rounded.lg}"
    padding: "{spacing.xl}"
    shadow: "{elevation.raised}"
  card-hover:
    shadow: "{elevation.floating}"
    transform: "translateY(-2px)"          # gentle lift on hover (marketing site cards)
  card-tint-green:
    description: "Use for 灵魂底色/foundation-related content (stability, roots). Body text on this surface must use {colors.green-on-tint}."
    backgroundColor: "{colors.green-surface}"
    textColor: "{colors.green-on-tint}"
    rounded: "{rounded.lg}"
    padding: "{spacing.xl}"
  card-tint-yellow:
    description: "Use for 今日心境/awakening moments (hatching, daily refresh, highlights). Body text must use {colors.yellow-on-tint}."
    backgroundColor: "{colors.yellow-surface}"
    textColor: "{colors.yellow-on-tint}"
    rounded: "{rounded.lg}"
    padding: "{spacing.xl}"
  card-tint-pink:
    description: "Use for 性格轨迹/bond moments (milestones, memory callouts, intimacy). Body text must use {colors.pink-on-tint}."
    backgroundColor: "{colors.pink-surface}"
    textColor: "{colors.pink-on-tint}"
    rounded: "{rounded.lg}"
    padding: "{spacing.xl}"

  # === Badges & tags ===
  badge-growth:
    backgroundColor: "{colors.brand-green}"
    textColor: "{colors.ink-green-deep}"
    typography: "{typography.caption-cn}"
    rounded: "{rounded.full}"
    padding: "4px 12px"
  badge-awakening:
    backgroundColor: "{colors.brand-yellow}"
    textColor: "{colors.ink-green-deep}"
    typography: "{typography.caption-cn}"
    rounded: "{rounded.full}"
    padding: "4px 12px"
  badge-bond:
    backgroundColor: "{colors.brand-pink}"
    textColor: "{colors.ink-green-deep}"
    typography: "{typography.caption-cn}"
    rounded: "{rounded.full}"
    padding: "4px 12px"

  # === Forms ===
  text-input:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    placeholderColor: "{colors.ink-faint}"
    border: "1px solid {colors.hairline-strong}"
    rounded: "{rounded.md}"
    padding: "{spacing.sm} {spacing.md}"
    height: "48px"
  text-input-focused:
    border: "2px solid {colors.ink-green}"
    shadow: "{elevation.focus-ring}"
  text-input-error:
    border: "2px solid {colors.semantic-error}"
    backgroundColor: "{colors.semantic-error-surface}"
  input-error-message:
    textColor: "{colors.semantic-error-text}"
    typography: "{typography.caption-cn}"
  input-label:
    textColor: "{colors.ink-muted}"
    typography: "{typography.caption-cn}"

  # === Links ===
  link:
    textColor: "{colors.ink-green}"
    textDecoration: "none"
  link-hover:
    textColor: "{colors.ink-green-pressed}"
    textDecoration: "underline"

  # === Navigation ===
  pill-tab:
    textColor: "{colors.ink-muted}"
    border: "1px solid {colors.hairline}"
    rounded: "{rounded.full}"
    padding: "{spacing.xs} {spacing.md}"
  pill-tab-active:
    backgroundColor: "{colors.ink-green}"
    textColor: "{colors.on-dark}"
    rounded: "{rounded.full}"

  # === Companion / hardware-adjacent (mini-program specific) ===
  egg-avatar:
    description: "Circular/egg-shaped frame for companion avatar (玉兔/锦鲤 profile pictures)."
    rounded: "{rounded.egg}"
    border: "2px solid {colors.canvas}"
    shadow: "{elevation.floating}"
  soul-seal-card:
    description: "灵魂底色 SEAL 证书卡片 — pairs card-tint-green background with PingFang SC Semibold display numerals for the SEAL ID."
    backgroundColor: "{colors.green-surface}"
    border: "1px dashed {colors.ink-green}"
    rounded: "{rounded.lg}"
    padding: "{spacing.xl}"

  # === Mini-program structural (我的 module backbone) ===
  tab-bar:
    description: "Bottom tab bar — MVP has exactly two tabs: 蛋宝宝 (companion home) and 我的 (profile). Icon above label."
    backgroundColor: "{colors.canvas}"
    borderTop: "1px solid {colors.hairline}"
    height: "56px + safe-area-inset-bottom"
    itemColor: "{colors.ink-faint}"
    itemColorActive: "{colors.ink-green}"
    labelTypography: "{typography.caption-cn}"
  switch:
    description: "Toggle for settings & subscription authorization rows."
    trackOff: "{colors.hairline-strong}"
    trackOn: "{colors.brand-green}"
    thumb: "{colors.canvas}"
    width: "48px"
    height: "28px"
    thumbSize: "24px"
  progress-bar:
    description: "羁绊五阶 bond progression / hatching progress. Rounded, soft."
    track: "{colors.hairline}"
    fill: "{colors.brand-green}"
    height: "8px"
    rounded: "{rounded.full}"
  skeleton:
    description: "Loading placeholder blocks while companion data / soul profile loads."
    color: "{colors.hairline}"
    shimmer: "{colors.canvas}"
    rounded: "{rounded.sm}"

  list-row:
    description: "Settings/menu row (系统设置, 账号, 隐私协议). Leading icon + label, optional trailing value + chevron."
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body-md-cn}"
    minHeight: "52px"
    padding: "{spacing.md} {spacing.lg}"
    trailingColor: "{colors.ink-faint}"     # chevron / trailing value
  list-row-pressed:
    backgroundColor: "{colors.hairline}"
  divider:
    color: "{colors.hairline}"
    thickness: "1px"
    inset: "{spacing.lg}"                    # left-inset to align with row text, not icon
  section-header:
    description: "Grouped-list section label (e.g. 账号 / 通用 / 关于)."
    textColor: "{colors.ink-muted}"
    typography: "{typography.caption-cn}"
    padding: "{spacing.lg} {spacing.lg} {spacing.xs}"

  # === Overlays ===
  bottom-sheet:
    description: "WeChat-style bottom sheet for subscription authorization prompt, confirmations, pickers."
    backgroundColor: "{colors.canvas}"
    rounded: "{rounded.xl} {rounded.xl} 0 0"   # top corners only
    shadow: "{elevation.overlay}"
    padding: "{spacing.xl}"
    scrim: "rgba(38, 54, 43, 0.40)"            # dimmed backdrop
  modal:
    backgroundColor: "{colors.canvas}"
    rounded: "{rounded.xl}"
    shadow: "{elevation.overlay}"
    padding: "{spacing.xl}"
    maxWidth: "320px"
    scrim: "rgba(38, 54, 43, 0.40)"
  toast:
    description: "Transient feedback (保存成功 / 网络异常). Auto-dismiss."
    backgroundColor: "{colors.ink-green-deep}"
    textColor: "{colors.on-dark}"
    typography: "{typography.body-sm-cn}"
    rounded: "{rounded.md}"
    padding: "{spacing.sm} {spacing.md}"
    shadow: "{elevation.floating}"
  toast-error:
    backgroundColor: "{colors.semantic-error-text}"   # darker red — white 13px text on #D9463C is only 4.3:1; #C33F36 passes
    textColor: "{colors.on-dark}"

## Do's and Don'ts

### Do
- Use `{colors.ink-green}` as the dominant CTA / interactive color — it is the brand's grounding signal, equivalent to Notion's purple
- Reserve `logo-egg-hero` (full three-color lockup) for hero/KV/packaging moments only — never shrink it into nav bars or list icons
- Use the three tinted card variants (`card-tint-green/yellow/pink`) to visually separate content by which soul layer it belongs to (灵魂底色 / 今日心境 / 性格轨迹)
- Keep `rounded.egg` reserved for avatar frames and hatching-moment visuals — it is a signature shape, not a general-purpose radius
- At hero/display sizes, lean on scale and letter-spacing (not just weight) to create punch — PingFang SC has no true Bold, so `hero-display-cn` needs to earn its presence through size (72px) rather than heavier strokes
- Use the `-base` semantic colors for fills/icons/borders, but the `-text` variants (`semantic-error-text`, `semantic-warning-text`) whenever the color is applied to text — the base versions fail contrast as text
- Match shadow to purpose using the `elevation` scale: `raised` for resting cards, `floating` for hover/dropdowns, `overlay` for modals/sheets — don't invent one-off shadows
- Pick button typography by label language: `{typography.button-cn}` for Chinese labels, `{typography.button-en}` for English labels — never mix families inside one label
- Give every interactive element a visible keyboard-focus state using `{elevation.focus-ring}` — not just text inputs

### Don't
- Don't use `{colors.brand-yellow}` or `{colors.brand-pink}` as large background fills for body text — they're accents, not surfaces for dense reading
- Don't set `fontWeight: 700` (or "Bold") on PingFang SC anywhere — the system font maxes out at Semibold (600); requesting 700 either does nothing or triggers synthetic/faux bold, which looks broken on macOS/iOS
- Don't put `{colors.ink-faint}` on `{colors.hairline}` (disabled buttons) — it's near-invisible; use `{colors.disabled-text}`
- Don't render `logo-egg-hero` as a gradient bar — it is three discrete overlapping egg-shaped ovals
- Don't rely on PingFang SC without a fallback stack — Android WeChat clients will not render it (see Known Gaps)
- Don't reintroduce the 2026-04 KV system's yellow/blue/black palette (`#F5C400` / `#009FE3` / `#1A1A1A`) — that direction is retired

## Layout
- Marketing site content container: max-width **1120px**, centered, side padding `{spacing.lg}` (24px) on mobile → `{spacing.xxl}` (48px) on desktop
- Marketing sections separated by `{spacing.section}` (96px); hero gets `{spacing.hero}` (160px) top padding
- Mini-program pages: full-bleed grouped lists (list-rows edge-to-edge, dividers inset); floating cards get `{spacing.md}` (16px) side margin
- Prefer one clear focal point per screen — the brand voice is calm and uncluttered; resist dense multi-column dashboards

## Responsive Behavior

### Breakpoints
| Name | Width | Key Changes |
|---|---|---|
| Mobile (mini-program default) | < 480px | Single column. Hero 36px. Cards full-width. |
| Mobile (large) | 480–767px | 2-up card grid. Hero 48px. |
| Tablet | 768–1023px | 2-column layout. Hero 56px. |
| Desktop (marketing site) | ≥ 1024px | Full hero-display scale (88px). Multi-column sections. |

### Touch Targets
- Buttons render at 44–48px effective height (mini-program touch minimum)
- Form inputs at 48px height
- List rows at 52px minimum height
- Pill tabs 36px → 44px on mobile

## Motion
- Default transition: `180ms ease-out` for hover/press/color changes — keep it quick and soft, never bouncy for everyday UI
- Reserve expressive motion (spring, scale, reveal) for the emotional beats: 破壳 hatching, SEAL reveal, milestone unlocks — these moments can be slower (400–600ms) and more theatrical
- Card hover lift is `translateY(-2px)` + shadow `raised → floating`; button press is `scale(0.98)`
- Respect `prefers-reduced-motion`: disable the hatching/scale animations and fall back to a simple fade

## Iteration Guide
1. Confirm exact `{colors.ink-green}` value against the source logo file (currently inherited from eggbabe.com's theme-color meta tag, not color-picked from the AI file)
2. Add a PingFang SC fallback stack before any Android build
3. Validate the proposed type scale (currently extrapolated from the 90/50/32pt brand-deck reference) against eggbabe.com's live CSS once browser access is available
4. Choose an icon library (rounded line-icon set recommended to match the soft geometry) and record it here
5. Design dark-mode surface/text tokens when a dark theme is actually scheduled — don't let generation tools improvise them

## Known Gaps
- **Google Sans has no CJK glyphs** — it covers Latin script only. It must never appear as the `fontFamily` on any Chinese-language token; the two type stacks (`Google Sans` for EN, `PingFang SC` for CN) are intentionally kept separate and should not be merged or used as fallbacks for each other
- **PingFang SC has no true Bold weight** — Apple ships it in six weights (Ultralight/Thin/Light/Regular/Medium/Semibold), topping out at Semibold (600). Design and engineering should treat 600 as the heaviest usable weight; anything requesting "Bold" (700) risks synthetic bold rendering, which looks off-brand
- **PingFang SC has no built-in fallback** — it ships only on Apple platforms. Recommended stack for the mini-program: `PingFang SC, "Helvetica Neue", "Microsoft YaHei", sans-serif`
- **`{colors.ink-green}` (#3F5A47) is inherited from eggbabe.com's live theme-color meta tag**, not verified against the actual logo source file provided — recommend a quick color-pick check against the original vector/PNG
- **Exact type-scale pixel values are proposed, not extracted** — Chrome browser tool was unavailable during this session, so eggbabe.com's live CSS wasn't inspected; validate against production before locking
- **Logo minimum size and clear-space rules** are not defined in the provided VI assets — confirm with the designer before use in dense UI (mini-program nav, favicons)
- **No iconography spec** — icon style (line vs filled), stroke width, and corner radius aren't defined. Claude Design will pick a set; if you want consistency, decide on one library (e.g. a rounded line-icon set to match the soft geometry) and note it here
- **Dark-mode surface tokens are incomplete** — only the reversed logo lockup and dark overlays exist. Full dark-mode card/surface/text tokens are not designed; if you ask Claude Design for dark screens it will improvise them
- **Illustration / mascot rendering style** (how 玉兔/锦鲤 are drawn in-app vs the physical toy) is not specified here — keep it out of scope for this DESIGN.md or add a dedicated section later
