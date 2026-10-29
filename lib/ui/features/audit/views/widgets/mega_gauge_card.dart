import 'package:flutter/material.dart';

import '../../../../../domain/models/data_plan_session.dart';
import '../../../../../domain/use_cases/mega_calculator.dart';
import '../../../../core/theme/app_theme.dart';

/// High-contrast telemetry card comparing Promised ISP Megabytes vs Real OS Measured Bytes.
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
              ? AppTheme.emerald
              : (scorePct >= 75 ? AppTheme.amber : AppTheme.crimson))
        : session.isp.brandColor;

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 12,
                    vertical: 6,
                  ),
                  decoration: BoxDecoration(
                    color: session.isp.brandColor.withValues(alpha: 0.18),
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(color: session.isp.brandColor),
                  ),
                  child: Text(
                    session.isp.displayName,
                    style: TextStyle(
                      color: session.isp.brandColor,
                      fontWeight: FontWeight.bold,
                      fontSize: 13,
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    '${session.planName} • ${session.priceXaf} FCFA (${session.validityHours}h)',
                    style: const TextStyle(
                      fontWeight: FontWeight.w600,
                      fontSize: 14,
                    ),
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 10,
                    vertical: 4,
                  ),
                  decoration: BoxDecoration(
                    color: isExhausted
                        ? AppTheme.crimson.withValues(alpha: 0.2)
                        : AppTheme.emerald.withValues(alpha: 0.2),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(
                    isExhausted ? 'FORFAIT ÉPUISÉ' : 'AUDIT OS ACTIF',
                    style: TextStyle(
                      color: isExhausted ? AppTheme.crimson : AppTheme.emerald,
                      fontSize: 11,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 20),
            Row(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'VOLUME RÉEL MESURÉ PAR LE SYSTÈME (OS)',
                        style: TextStyle(
                          fontSize: 11,
                          letterSpacing: 0.8,
                          color: Colors.white60,
                        ),
                      ),
                      const SizedBox(height: 4),
                      RichText(
                        text: TextSpan(
                          children: [
                            TextSpan(
                              text: measuredDecimalMb.toStringAsFixed(1),
                              style: TextStyle(
                                fontSize: 36,
                                fontWeight: FontWeight.w900,
                                color: gaugeColor,
                              ),
                            ),
                            TextSpan(
                              text:
                                  ' Mo / ${session.promisedMb.toStringAsFixed(0)} Mo promis',
                              style: const TextStyle(
                                fontSize: 16,
                                color: Colors.white70,
                                fontWeight: FontWeight.w500,
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        'Base binaire (Mio) : ${measuredBinaryMib.toStringAsFixed(1)} Mio • '
                        'Octets bruts : ${session.totalConsumedBytes}',
                        style: const TextStyle(
                          fontSize: 12,
                          color: Colors.white54,
                        ),
                      ),
                    ],
                  ),
                ),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    const Text(
                      'TAUX DE VÉRITÉ',
                      style: TextStyle(fontSize: 11, color: Colors.white60),
                    ),
                    Text(
                      '${scorePct.toStringAsFixed(1)}%',
                      style: TextStyle(
                        fontSize: 28,
                        fontWeight: FontWeight.bold,
                        color: gaugeColor,
                      ),
                    ),
                  ],
                ),
              ],
            ),
            const SizedBox(height: 16),
            ClipRRect(
              borderRadius: BorderRadius.circular(10),
              child: LinearProgressIndicator(
                value: progress,
                minHeight: 14,
                backgroundColor: Colors.white12,
                valueColor: AlwaysStoppedAnimation<Color>(gaugeColor),
              ),
            ),
            const SizedBox(height: 16),
            Wrap(
              spacing: 12,
              runSpacing: 10,
              children: [
                _MetricPill(
                  icon: Icons.download_rounded,
                  label: 'Download (Rx)',
                  value: '${rxMb.toStringAsFixed(1)} Mo',
                  color: Colors.cyanAccent,
                ),
                _MetricPill(
                  icon: Icons.upload_rounded,
                  label: 'Upload (Tx)',
                  value: '${txMb.toStringAsFixed(1)} Mo',
                  color: Colors.purpleAccent,
                ),
                _MetricPill(
                  icon: Icons.warning_amber_rounded,
                  label: isExhausted ? 'Mégas Fantômes' : 'Reste théorique',
                  value: '${missingMb.toStringAsFixed(1)} Mo',
                  color: isExhausted && missingMb > 100
                      ? AppTheme.crimson
                      : AppTheme.amber,
                ),
                _MetricPill(
                  icon: Icons.payments_outlined,
                  label: 'Coût Réel / Go reçu',
                  value: '${realCostPerGb.toStringAsFixed(0)} FCFA/Go',
                  color: AppTheme.emerald,
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _MetricPill extends StatelessWidget {
  const _MetricPill({
    required this.icon,
    required this.label,
    required this.value,
    required this.color,
  });

  final IconData icon;
  final String label;
  final String value;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.04),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: Colors.white12),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 16, color: color),
          const SizedBox(width: 8),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                label,
                style: const TextStyle(fontSize: 10, color: Colors.white54),
              ),
              Text(
                value,
                style: const TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.bold,
                  color: Colors.white,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
