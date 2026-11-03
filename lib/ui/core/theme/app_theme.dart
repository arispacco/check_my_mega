import 'package:flutter/material.dart';

/// Precision Dark Telemetry Design System for **CheckMyMega CM**.
///
/// Inspired by Ubiquiti WiFiman, Apple Watch Ultra Wayfinder, and Linear Dark UI.
class AppTheme {
  // Core Surface Palette (Deep Obsidian & Midnight Glass)
  static const Color obsidianBg = Color(0xFF060A12);
  static const Color slateBg = Color(0xFF0A101D);
  static const Color cardBg = Color(0xFF10192B);
  static const Color cardElevated = Color(0xFF162238);
  static const Color borderSubtle = Color(0xFF1F304D);
  static const Color borderHighlight = Color(0xFF2E4770);

  // Semantic & Telemetry Accents
  static const Color emerald = Color(0xFF10B981);
  static const Color neonEmerald = Color(0xFF00F5A0);
  static const Color cyan = Color(0xFF06B6D4);
  static const Color sky = Color(0xFF38BDF8);
  static const Color amber = Color(0xFFF59E0B);
  static const Color crimson = Color(0xFFF43F5E);
  static const Color violet = Color(0xFF8B5CF6);

  // Text Hierarchy
  static const Color textPrimary = Color(0xFFF8FAFC);
  static const Color textSecondary = Color(0xFF94A3B8);
  static const Color textMuted = Color(0xFF64748B);

  static ThemeData get darkTheme {
    final base = ThemeData.dark(useMaterial3: true);
    return base.copyWith(
      scaffoldBackgroundColor: obsidianBg,
      colorScheme: ColorScheme.fromSeed(
        seedColor: emerald,
        brightness: Brightness.dark,
        primary: emerald,
        secondary: cyan,
        tertiary: amber,
        error: crimson,
        surface: cardBg,
      ),
      appBarTheme: const AppBarTheme(
        backgroundColor: obsidianBg,
        surfaceTintColor: Colors.transparent,
        foregroundColor: textPrimary,
        elevation: 0,
        centerTitle: false,
      ),
      cardTheme: CardThemeData(
        color: cardBg,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(20),
          side: const BorderSide(color: borderSubtle, width: 1),
        ),
      ),
      chipTheme: base.chipTheme.copyWith(
        backgroundColor: cardElevated,
        selectedColor: emerald.withValues(alpha: 0.22),
        side: const BorderSide(color: borderSubtle),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: emerald,
          foregroundColor: const Color(0xFF04130D),
          textStyle: const TextStyle(
            fontWeight: FontWeight.w700,
            fontSize: 13.5,
            letterSpacing: 0.2,
          ),
          padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 13),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(14),
          ),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: textPrimary,
          side: const BorderSide(color: borderHighlight, width: 1),
          textStyle: const TextStyle(
            fontWeight: FontWeight.w600,
            fontSize: 13,
          ),
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(14),
          ),
        ),
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: const Color(0xFF080E1A),
        surfaceTintColor: Colors.transparent,
        height: 70,
        indicatorColor: emerald.withValues(alpha: 0.18),
        iconTheme: WidgetStateProperty.resolveWith((states) {
          if (states.contains(WidgetState.selected)) {
            return const IconThemeData(color: neonEmerald, size: 23);
          }
          return const IconThemeData(color: textSecondary, size: 21);
        }),
        labelTextStyle: WidgetStateProperty.resolveWith((states) {
          if (states.contains(WidgetState.selected)) {
            return const TextStyle(
              fontSize: 11.5,
              fontWeight: FontWeight.w700,
              color: textPrimary,
              letterSpacing: 0.1,
            );
          }
          return const TextStyle(
            fontSize: 11,
            fontWeight: FontWeight.w500,
            color: textSecondary,
          );
        }),
      ),
    );
  }
}

/// Reusable glassmorphic telemetry container with top-left specular gradient
/// and optional accent border glow.
class GlassTelemetryCard extends StatelessWidget {
  const GlassTelemetryCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(18),
    this.accentColor,
    this.borderRadius = 22,
  });

  final Widget child;
  final EdgeInsetsGeometry padding;
  final Color? accentColor;
  final double borderRadius;

  @override
  Widget build(BuildContext context) {
    final effectiveBorder = accentColor != null
        ? accentColor!.withValues(alpha: 0.45)
        : AppTheme.borderSubtle;

    return Container(
      padding: padding,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(borderRadius),
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            accentColor != null
                ? Color.alphaBlend(
                    accentColor!.withValues(alpha: 0.08),
                    AppTheme.cardElevated,
                  )
                : AppTheme.cardElevated,
            AppTheme.cardBg,
          ],
        ),
        border: Border.all(color: effectiveBorder, width: 1.1),
        boxShadow: [
          BoxShadow(
            color: (accentColor ?? Colors.black).withValues(
              alpha: accentColor != null ? 0.12 : 0.35,
            ),
            blurRadius: 20,
            offset: const Offset(0, 8),
          ),
        ],
      ),
      child: Material(type: MaterialType.transparency, child: child),
    );
  }
}

/// Standardized section header used across all tabs for visual consistency.
class TelemetrySectionHeader extends StatelessWidget {
  const TelemetrySectionHeader({
    super.key,
    required this.icon,
    required this.title,
    this.subtitle,
    this.accentColor = AppTheme.emerald,
    this.trailing,
  });

  final IconData icon;
  final String title;
  final String? subtitle;
  final Color accentColor;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        Container(
          width: 38,
          height: 38,
          decoration: BoxDecoration(
            color: accentColor.withValues(alpha: 0.14),
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: accentColor.withValues(alpha: 0.35)),
          ),
          child: Icon(icon, color: accentColor, size: 20),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: const TextStyle(
                  fontSize: 15.5,
                  fontWeight: FontWeight.w800,
                  color: AppTheme.textPrimary,
                  letterSpacing: -0.2,
                ),
              ),
              if (subtitle != null) ...[
                const SizedBox(height: 2),
                Text(
                  subtitle!,
                  style: const TextStyle(
                    fontSize: 12,
                    color: AppTheme.textSecondary,
                    height: 1.3,
                  ),
                ),
              ],
            ],
          ),
        ),
        if (trailing != null) ...[
          const SizedBox(width: 8),
          trailing!,
        ],
      ],
    );
  }
}
