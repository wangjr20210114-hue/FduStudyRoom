import type { GlobalThemeOverrides } from 'naive-ui'

// CSS variables remain the source of truth for both native and Naive UI controls.
export function createThemeOverrides(): GlobalThemeOverrides {
  const styles = getComputedStyle(document.documentElement)
  const token = (name: string) => styles.getPropertyValue(name).trim()

  return {
    common: {
      primaryColor: token('--color-primary'),
      primaryColorHover: token('--color-primary-hover'),
      primaryColorPressed: token('--color-primary-pressed'),
      primaryColorSuppl: token('--color-primary-hover'),
      successColor: token('--color-success'),
      warningColor: token('--color-warning'),
      errorColor: token('--color-error'),
      infoColor: token('--color-info'),
      bodyColor: token('--color-canvas'),
      cardColor: token('--color-surface'),
      modalColor: token('--color-surface'),
      popoverColor: token('--color-surface'),
      inputColor: token('--color-surface'),
      textColorBase: token('--color-text'),
      textColor1: token('--color-text'),
      textColor2: token('--color-text-secondary'),
      textColor3: token('--color-text-muted'),
      placeholderColor: token('--color-placeholder'),
      borderColor: token('--color-border'),
      dividerColor: token('--color-border'),
      hoverColor: token('--color-surface-muted'),
      fontFamily: token('--font-family'),
      fontSize: token('--font-size-body'),
      fontSizeSmall: token('--font-size-small'),
      borderRadius: token('--radius-control'),
      borderRadiusSmall: token('--radius-control'),
      heightSmall: token('--control-height-small'),
      heightMedium: token('--control-height-medium'),
      heightLarge: token('--control-height-large'),
    },
  }
}
