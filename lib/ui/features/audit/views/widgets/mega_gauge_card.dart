import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../../../../domain/models/data_plan_session.dart';
import '../../../../../domain/use_cases/mega_calculator.dart';
import '../../../../core/theme/app_theme.dart';

/// Flagship radial arc telemetry card comparing Promised ISP Megabytes vs Real OS Measured Bytes.
class MegaGaugeCard extends StatelessWidget {
  const MegaGaugeCard({
    super.key,
    required this.session,
    required this.calculator,
  });

  final DataPlanSession session;
  final MegaCalculator calculator;

  @override
  Widget build(BuildContext context) {
    final measuredDecimalMb = calculator.bytesToDecimalMb(
      session.totalConsumedBytes,
    );
    final measuredBinaryMib = calculator.bytesToBinaryMib(
      session.totalConsumedBytes,
    );
    final rxMb = calculator.bytesToDecimalMb(session.consumedRxBytes);
    final txMb = calculator.bytesToDecimalMb(session.consumedTxBytes);

    final scorePct = calculator.computeVeracityScorePct(
      totalConsumedBytes: session.totalConsumedBytes,
      promisedMb: session.promisedMb,
    );
    final missingMb = calculator.computeMissingMb(
      totalConsumedBytes: session.totalConsumedBytes,
      promisedMb: session.promisedMb,
    );
    final realCostPerGb = calculator.computeRealCostPerGbXaf(
      priceXaf: session.priceXaf,
      totalConsumedBytes: session.totalConsumedBytes,
    );

    final progress = (measuredDecimalMb / session.promisedMb).clamp(0.0, 1.0);
    final isExhausted = session.status == AuditSessionStatus.exhausted;

    final Color gaugeColor = isExhausted
        ? (scorePct >= 90
              ? AppTheme.neonEmerald
              : (scorePct >= 75 ? AppTheme.amber : AppTheme.crimson))
        : session.isp.brandColor;

    return GlassTelemetryCard(
      accentColor: gaugeColor,
      padding: const EdgeInsets.all(20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Top Status Header
          Row(
            children: [
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 11,
                  vertical: 5,
                ),
                decoration: BoxDecoration(
                  color: session.isp.brandColor.withValues(alpha: 0.16),
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(
                    color: session.isp.brandColor.withValues(alpha: 0.65),
                  ),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Container(
                      width: 7,
                      height: 7,
                      decoration: BoxDecoration(
                        color: session.isp.brandColor,
                        shape: BoxShape.circle,
                      ),
                    ),
                    const SizedBox(width: 6),
                    Text(
                      session.isp.displayName,
                      style: TextStyle(
                        color: session.isp.brandColor,
                        fontWeight: FontWeight.w800,
                        fontSize: 12,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  '${session.planName} • ${session.priceXaf} FCFA',
                  style: const TextStyle(
                    fontWeight: FontWeight.w700,
                    fontSize: 13.5,
                    color: AppTheme.textPrimary,
                  ),
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
                decoration: BoxDecoration(
                  color: isExhausted
                      ? AppTheme.crimson.withValues(alpha: 0.18)
                      : AppTheme.emerald.withValues(alpha: 0.18),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(
                    color: isExhausted
                        ? AppTheme.crimson.withValues(alpha: 0.5)
                        : AppTheme.emerald.withValues(alpha: 0.5),
                  ),
                ),
                child: Text(
                  isExhausted ? 'ÉPUISÉ' : 'SONDE ACTIVE',
                  style: TextStyle(
                    color: isExhausted
                        ? AppTheme.crimson
                        : AppTheme.neonEmerald,
                    fontSize: 10.5,
                    fontWeight: FontWeight.w900,
                    letterSpacing: 0.4,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 20),

          // Hero Radial Dial + OS Volume Readout
          Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              SizedBox(
                width: 126,
                height: 126,
                child: TweenAnimationBuilder<double>(
                  tween: Tween<double>(begin: 0, end: progress),
                  duration: const Duration(milliseconds: 550),
                  curve: Curves.easeOutCubic,
                  builder: (context, animatedProgress, _) {
                    return CustomPaint(
                      painter: _RadialVeracityGaugePainter(
                        progress: animatedProgress,
                        color: gaugeColor,
                      ),
                      child: Center(
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            const Text(
                              'VÉRITÉ',
                              style: TextStyle(
                                fontSize: 9.5,
                                fontWeight: FontWeight.w800,
                                letterSpacing: 1.1,
                                color: AppTheme.textSecondary,
                              ),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              '${scorePct.toStringAsFixed(1)}%',
                              style: TextStyle(
                                fontSize: 22,
                                fontWeight: FontWeight.w900,
                                color: gaugeColor,
                                letterSpacing: -0.5,
                              ),
                            ),
                            Text(
                              'sur ${session.promisedMb.toStringAsFixed(0)} Mo',
                              style: const TextStyle(
                                fontSize: 9.5,
                                color: AppTheme.textSecondary,
                              ),
                            ),
                          ],
                        ),
                      ),
                    );
                  },
                ),
              ),
              const SizedBox(width: 18),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'VOLUME RÉEL REÇU PAR L\'OS',
                      style: TextStyle(
                        fontSize: 10.5,
                        fontWeight: FontWeight.w800,
                        letterSpacing: 0.8,
                        color: AppTheme.textSecondary,
                      ),
                    ),
                    const SizedBox(height: 4),
                    RichText(
                      text: TextSpan(
                        children: [
                          TextSpan(
                            text: measuredDecimalMb.toStringAsFixed(1),
                            style: TextStyle(
                              fontSize: 32,
                              fontWeight: FontWeight.w900,
                              color: gaugeColor,
                              letterSpacing: -0.8,
                            ),
                          ),
                          const TextSpan(
                            text: ' Mo',
                            style: TextStyle(
                              fontSize: 16,
                              fontWeight: FontWeight.w800,
                              color: AppTheme.textPrimary,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'Promis par le FAI : ${session.promisedMb.toStringAsFixed(0)} Mo (${session.validityHours}h)',
                      style: const TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w600,
                        color: AppTheme.textPrimary,
                      ),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      'Base binaire : ${measuredBinaryMib.toStringAsFixed(1)} Mio • '
                      '${_formatBytesCount(session.totalConsumedBytes)} octets',
                      style: const TextStyle(
                        fontSize: 11,
                        color: AppTheme.textSecondary,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 18),

          // 2x2 Bento Telemetry Grid
          LayoutBuilder(
            builder: (context, constraints) {
              final pillW = (constraints.maxWidth - 10) / 2;
              return Wrap(
                spacing: 10,
                runSpacing: 10,
                children: [
                  _MetricPill(
                    width: pillW,
                    icon: Icons.south_west_rounded,
                    label: 'Download (Rx)',
                    value: '${rxMb.toStringAsFixed(1)} Mo',
                    color: AppTheme.sky,
                  ),
                  _MetricPill(
                    width: pillW,
                    icon: Icons.north_east_rounded,
                    label: 'Upload (Tx)',
                    value: '${txMb.toStringAsFixed(1)} Mo',
                    color: AppTheme.violet,
                  ),
                  _MetricPill(
                    width: pillW,
                    icon: isExhausted
                        ? Icons.warning_amber_rounded
                        : Icons.hourglass_bottom_rounded,
                    label: isExhausted ? 'Mégas Non Reçus' : 'Solde Théorique',
                    value: '${missingMb.toStringAsFixed(1)} Mo',
                    color: isExhausted && missingMb > 100
                        ? AppTheme.crimson
                        : AppTheme.amber,
                  ),
                  _MetricPill(
                    width: pillW,
                    icon: Icons.payments_outlined,
                    label: 'Coût Réel / Go Livré',
                    value: '${realCostPerGb.toStringAsFixed(0)} FCFA/Go',
                    color: AppTheme.neonEmerald,
                  ),
                ],
              );
            },
          ),
        ],
      ),
    );
  }

  static String _formatBytesCount(int bytes) {
    final s = bytes.toString();
    final buf = StringBuffer();
    for (int i = 0; i < s.length; i++) {
      if (i > 0 && (s.length - i) % 3 == 0) {
        buf.write(' ');
      }
      buf.write(s[i]);
    }
    return buf.toString();
  }
}

class _MetricPill extends StatelessWidget {
  const _MetricPill({
    required this.width,
    required this.icon,
    required this.label,
    required this.value,
    required this.color,
  });

  final double width;
  final IconData icon;
  final String label;
  final String value;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: width,
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: AppTheme.obsidianBg.withValues(alpha: 0.55),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppTheme.borderSubtle),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(6),
            decoration: BoxDecoration(
              color: color.withValues(alpha: 0.14),
              borderRadius: BorderRadius.circular(9),
            ),
            child: Icon(icon, size: 15, color: color),
          ),
          const SizedBox(width: 9),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  label,
                  style: const TextStyle(
                    fontSize: 10.5,
                    color: AppTheme.textSecondary,
                  ),
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 1),
                Text(
                  value,
                  style: const TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w800,
                    color: AppTheme.textPrimary,
                  ),
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _RadialVeracityGaugePainter extends CustomPainter {
  _RadialVeracityGaugePainter({
    required this.progress,
    required this.color,
  });

  final double progress;
  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = math.min(size.width, size.height) / 2 - 8;
    const startAngle = math.pi * 0.75; // 135 deg
    const sweepTotal = math.pi * 1.5; // 270 deg

    final rect = Rect.fromCircle(center: center, radius: radius);

    // Background track
    canvas.drawArc(
      rect,
      startAngle,
      sweepTotal,
      false,
      Paint()
        ..color = Colors.white.withValues(alpha: 0.09)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 10
        ..strokeCap = StrokeCap.round,
    );

    // Subtle tick marks along the outer perimeter
    for (int i = 0; i <= 18; i++) {
      final a = startAngle + (sweepTotal * i / 18.0);
      final p1 = Offset(
        center.dx + (radius + 6) * math.cos(a),
        center.dy + (radius + 6) * math.sin(a),
      );
      final p2 = Offset(
        center.dx + (radius + 9) * math.cos(a),
        center.dy + (radius + 9) * math.sin(a),
      );
      canvas.drawLine(
        p1,
        p2,
        Paint()
          ..color = i <= (progress * 18).round()
              ? color.withValues(alpha: 0.7)
              : Colors.white.withValues(alpha: 0.16)
          ..strokeWidth = 1.4
          ..strokeCap = StrokeCap.round,
      );
    }

    // Active progress arc with glow
    final activeSweep = (sweepTotal * progress).clamp(0.02, sweepTotal);
    canvas.drawArc(
      rect,
      startAngle,
      activeSweep,
      false,
      Paint()
        ..color = color.withValues(alpha: 0.28)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 15
        ..strokeCap = StrokeCap.round,
    );

    canvas.drawArc(
      rect,
      startAngle,
      activeSweep,
      false,
      Paint()
        ..color = color
        ..style = PaintingStyle.stroke
        ..strokeWidth = 9.5
        ..strokeCap = StrokeCap.round,
    );
  }

  @override
  bool shouldRepaint(covariant _RadialVeracityGaugePainter oldDelegate) {
    return oldDelegate.progress != progress || oldDelegate.color != color;
  }
}
