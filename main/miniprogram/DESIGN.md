---
name: Ethereal Companion
colors:
  surface: '#fbf9f8'
  surface-dim: '#dcd9d9'
  surface-bright: '#fbf9f8'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f6f3f2'
  surface-container: '#f0eded'
  surface-container-high: '#eae8e7'
  surface-container-highest: '#e4e2e1'
  on-surface: '#1b1c1c'
  on-surface-variant: '#514345'
  inverse-surface: '#303030'
  inverse-on-surface: '#f3f0f0'
  outline: '#837375'
  outline-variant: '#d6c2c4'
  surface-tint: '#864e5a'
  primary: '#864e5a'
  on-primary: '#ffffff'
  primary-container: '#ffb7c5'
  on-primary-container: '#7b4551'
  inverse-primary: '#fbb3c1'
  secondary: '#5e5f5d'
  on-secondary: '#ffffff'
  secondary-container: '#e0e0dd'
  on-secondary-container: '#626361'
  tertiary: '#655b6c'
  on-tertiary: '#ffffff'
  tertiary-container: '#d3c6da'
  on-tertiary-container: '#5b5163'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#ffd9df'
  primary-fixed-dim: '#fbb3c1'
  on-primary-fixed: '#360c19'
  on-primary-fixed-variant: '#6b3743'
  secondary-fixed: '#e3e2e0'
  secondary-fixed-dim: '#c7c6c4'
  on-secondary-fixed: '#1a1c1a'
  on-secondary-fixed-variant: '#464745'
  tertiary-fixed: '#ecdef3'
  tertiary-fixed-dim: '#cfc2d6'
  on-tertiary-fixed: '#201827'
  on-tertiary-fixed-variant: '#4d4354'
  background: '#fbf9f8'
  on-background: '#1b1c1c'
  surface-variant: '#e4e2e1'
typography:
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 32px
    fontWeight: '700'
    lineHeight: '1.2'
  headline-lg-mobile:
    fontFamily: Plus Jakarta Sans
    fontSize: 28px
    fontWeight: '700'
    lineHeight: '1.2'
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 24px
    fontWeight: '600'
    lineHeight: '1.3'
  body-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 18px
    fontWeight: '400'
    lineHeight: '1.6'
  body-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: '400'
    lineHeight: '1.6'
  label-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '500'
    lineHeight: '1.4'
    letterSpacing: 0.02em
  caption:
    fontFamily: Plus Jakarta Sans
    fontSize: 12px
    fontWeight: '400'
    lineHeight: '1.4'
rounded:
  sm: 0.5rem
  DEFAULT: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
  full: 9999px
spacing:
  base: 8px
  xs: 4px
  sm: 12px
  md: 24px
  lg: 40px
  xl: 64px
  gutter: 16px
  margin-mobile: 20px
  margin-desktop: 120px
---

## Brand & Style

The design system is centered on the concept of "Healing Intimacy." It is crafted to provide a sanctuary for users, evoking feelings of safety, warmth, and emotional resonance. The target audience seeks a digital companion that feels less like a machine and more like a gentle, supportive presence. 

The aesthetic direction blends **Minimalism** with **Glassmorphism**. By prioritizing high-quality whitespace and translucent, layered elements, the interface feels airy and non-intrusive. The visual language avoids sharp edges or aggressive transitions, opting instead for a "Cloud-like" softness that mimics the comforting nature of a personal diary or a serene spring afternoon. Every interaction is designed to feel deliberate, soft, and emotionally responsive.

## Colors

The palette is intentionally restrained to maintain a "healing" atmosphere.
- **Primary (Cherry Blossom Pink):** Used for key actions, emotional highlights, and active states. It represents the heart of the experience.
- **Secondary (Porcelain White):** The foundational surface color. It provides a warm, organic alternative to pure digital white, reducing eye strain and creating a tactile feel.
- **Tertiary (Soft Lavender):** Used sparingly for secondary information or subtle accents to prevent the UI from feeling monochromatic.
- **Neutral (Charcoal Grey):** Reserved exclusively for high-readability text and iconography. It is never pure black, ensuring the contrast remains gentle on the eyes.

Backgrounds should utilize soft gradients moving from Porcelain White to very faint washes of Cherry Blossom Pink to create depth without clutter.

## Typography

This design system utilizes **Plus Jakarta Sans** for Latin characters due to its modern, soft, and optimistic geometry. For the Chinese market, this should be paired with **PingFang SC** (or a similar high-quality Sans-Serif like Noto Sans SC) to maintain a clean, contemporary look.

The typographic hierarchy emphasizes generous line heights (1.6 for body text) to ensure that long conversations with the AI feel effortless to read. Headlines use a heavier weight to provide clear structural anchors, while labels and captions use slightly increased letter spacing to maintain clarity against soft, glassmorphic backgrounds.

## Layout & Spacing

The layout philosophy follows a **Fluid Grid** model with an emphasis on "Negative Space as a Feature." 

- **Mobile:** A 4-column grid with 20px side margins. Content cards should typically span all 4 columns to maximize the "intimate" feel of the conversation.
- **Desktop/Tablet:** A 12-column centered grid. Content is constrained to a max-width to prevent the AI interaction from feeling distant or impersonal.

Spacing units follow an 8px scale. Large `lg` and `xl` spacing increments are used between major sections to allow the UI to "breathe," reinforcing the feeling of a calm, uncluttered environment.

## Elevation & Depth

Hierarchy is established through **Glassmorphism** and **Ambient Shadows** rather than harsh lines or heavy fills.

1.  **The Base:** Porcelain White (#FAF9F6) serves as the ground.
2.  **The Surface:** Interactive elements and cards use a semi-transparent white (60-80% opacity) with a `20px` backdrop blur. This creates the "Frosted Glass" effect.
3.  **Shadows:** Use extremely diffused, low-opacity shadows. Shadows should be tinted with a hint of the Primary color (e.g., `rgba(255, 183, 197, 0.15)`) to maintain a warm, glowing appearance instead of a "dirty" grey shadow.
4.  **Glows:** Primary buttons utilize an outer glow effect (box-shadow with a high spread and low opacity) to simulate a soft light source emanating from the interaction point.

## Shapes

The shape language is defined by extreme **Roundedness (Level 3)**. 

Every interactive element—from chat bubbles to input fields—features pill-shaped or heavily rounded corners. Sharp 90-degree angles are strictly avoided to prevent the UI from feeling clinical or aggressive. This hyper-rounded approach mimics organic forms, contributing to the "soft" and "approachable" brand personality. 

- **Standard Elements:** 1rem (16px) radius.
- **Large Cards/Containers:** 2rem to 3rem (32px-48px) radius.
- **Buttons:** Full pill-shape.

## Components

### Buttons
Primary buttons are pill-shaped, using a Cherry Blossom Pink gradient. They feature a soft pink outer glow. Text inside buttons should be charcoal or white, depending on the gradient depth. Secondary buttons use a ghost style with a 1px soft pink border.

### Chat Bubbles
AI messages use the Glassmorphic style (frosted white). User messages use a soft pink tint. All bubbles have a minimum radius of `20px` to maintain the soft aesthetic.

### Input Fields
Inputs are large, pill-shaped containers with a subtle Porcelain White inner shadow to give a slight "inset" tactile feel. The cursor and focus state should use a soft pink pulse.

### Cards
Cards for AI "Memories" or "Status" should use a subtle backdrop blur and a thin, 1px semi-transparent white border to define the edge against the background.

### Delicate Icons
Icons should use a "Thin" or "Light" weight with rounded terminals. Avoid filled-in icons unless they represent an active state; use outlined paths to maintain the airy, light feel of the system.

### Progress Indicators
Status bars (like "Affection Level") should be soft, rounded tracks with a glowing primary color fill.