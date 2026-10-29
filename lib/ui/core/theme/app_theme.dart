import 'package:flutter/material.dart';

/// Application-wide Material 3 theme designed for high readability during live streams
/// and mobile field audits in Cameroon.
class AppTheme {
  static const Color emerald = Color(0xFF10B981);
  static const Color amber = Color(0xFFF59E0B);
  static const Color crimson = Color(0xFFEF4444);
  static const Color slateBg = Color(0xFF0B1120);
  static const Color cardBg = Color(0xFF151F32);
  static const Color borderSubtle = Color(0xFF263554);

  static ThemeData get darkTheme {
    final base = ThemeData.dark(useMaterial3: true);
    return base.copyWith(
      scaffoldBackgroundColor: slateBg,
      colorScheme: ColorScheme.fromSeed(
        seedColor: emerald,
        brightness: Brightness.dark,
        primary: emerald,
        secondary: amber,
        error: crimson,
        surface: cardBg,
      ),
      appBarTheme: const AppBarTheme(
        backgroundColor: slateBg,
        foregroundColor: Colors.white,
        elevation: 0,
        centerTitle: false,
      ),
      cardTheme: CardThemeData(
        color: cardBg,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: const BorderSide(color: borderSubtle, width: 1),
        ),
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: const Color(0xFF0F172A),
        indicatorColor: emerald.withValues(alpha: 0.2),
        labelTextStyle: WidgetStateProperty.all(
          const TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
        ),
      ),
    );
  }
}
