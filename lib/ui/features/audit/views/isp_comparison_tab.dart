import 'package:flutter/material.dart';

import '../../../../domain/models/data_plan_session.dart';
import '../../../core/theme/app_theme.dart';
import '../view_models/audit_view_model.dart';

/// Tab 3: Mobile-First Visual Comparator of Recorded SIM Audits & National ISP Benchmarks.
class IspComparisonTab extends StatelessWidget {
  const IspComparisonTab({
    super.key,
    required this.viewModel,
    required this.onInspectSessionReport,
  });

  final AuditViewModel viewModel;
  final ValueChanged<DataPlanSession> onInspectSessionReport;

  @override
  Widget build(BuildContext context) {
    final sessions = viewModel.history;
    final calc = viewModel.calculator;

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 28),
      children: [
        GlassTelemetryCard(
          accentColor: AppTheme.sky,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const TelemetrySectionHeader(
                icon: Icons.balance_rounded,
                title: 'Historique de Mes Audits & Comparateur',
                subtitle:
                    'Vos vraies sessions mesurées sur carte SIM vs repères nationaux',
                accentColor: AppTheme.sky,
              ),
              const SizedBox(height: 12),
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 6,
                ),
                decoration: BoxDecoration(
                  color: AppTheme.obsidianBg.withValues(alpha: 0.55),
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: AppTheme.borderSubtle),
                ),
                child: Material(
                  type: MaterialType.transparency,
                  child: SwitchListTile.adaptive(
                    contentPadding: EdgeInsets.zero,
                    activeThumbColor: AppTheme.neonEmerald,
                    title: const Text(
                      'Afficher les repères nationaux (MTN / Orange / Camtel)',
                      style: TextStyle(
                        fontSize: 12.5,
                        fontWeight: FontWeight.w700,
                        color: AppTheme.textPrimary,
                      ),
                    ),
                    subtitle: Text(
                      'SIM détectée : ${viewModel.detectedCarrierName}',
                      style: const TextStyle(
                        fontSize: 11,
                        color: AppTheme.textSecondary,
                      ),
                    ),
                    value: viewModel.showReferenceBenchmarks,
                    onChanged: viewModel.toggleReferenceBenchmarks,
                  ),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),

        // Mobile-First Visual Audit Scorecards
        ...sessions.map((s) {
          final measuredMb = calc.bytesToDecimalMb(s.totalConsumedBytes);
          final scorePct = calc.computeVeracityScorePct(
            totalConsumedBytes: s.totalConsumedBytes,
            promisedMb: s.promisedMb,
          );
          final missingMb = calc.computeMissingMb(
            totalConsumedBytes: s.totalConsumedBytes,
            promisedMb: s.promisedMb,
          );
          final realCost = calc.computeRealCostPerGbXaf(
            priceXaf: s.priceXaf,
            totalConsumedBytes: s.totalConsumedBytes,
          );

          final scoreColor = scorePct >= 90
              ? AppTheme.neonEmerald
              : (scorePct >= 75 ? AppTheme.amber : AppTheme.crimson);

          return Padding(
            padding: const EdgeInsets.only(bottom: 14),
            child: GlassTelemetryCard(
              accentColor: s.isp.brandColor,
              padding: const EdgeInsets.all(18),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 10,
                          vertical: 4,
                        ),
                        decoration: BoxDecoration(
                          color: s.isp.brandColor.withValues(alpha: 0.16),
                          borderRadius: BorderRadius.circular(20),
                          border: Border.all(
                            color: s.isp.brandColor.withValues(alpha: 0.6),
                          ),
                        ),
                        child: Text(
                          s.isp.displayName,
                          style: TextStyle(
                            color: s.isp.brandColor,
                            fontWeight: FontWeight.w800,
                            fontSize: 11.5,
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          '${s.planName} (${s.priceXaf} FCFA)',
                          style: const TextStyle(
                            fontWeight: FontWeight.w800,
                            fontSize: 14,
                            color: AppTheme.textPrimary,
                          ),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 9,
                          vertical: 4,
                        ),
                        decoration: BoxDecoration(
                          color: scoreColor.withValues(alpha: 0.16),
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: Text(
                          '${scorePct.toStringAsFixed(1)}%',
                          style: TextStyle(
                            color: scoreColor,
                            fontWeight: FontWeight.w900,
                            fontSize: 13,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 14),

                  // Progress Bar
                  ClipRRect(
                    borderRadius: BorderRadius.circular(8),
                    child: LinearProgressIndicator(
                      value: (measuredMb / s.promisedMb).clamp(0.02, 1.0),
                      minHeight: 10,
                      backgroundColor: Colors.white10,
                      valueColor: AlwaysStoppedAnimation<Color>(
                        s.isp.brandColor,
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),

                  // 3-Column Metric Strip
                  Row(
                    children: [
                      Expanded(
                        child: _MiniCompareStat(
                          label: 'REÇU / PROMIS',
                          value:
                              '${measuredMb.toStringAsFixed(1)} / ${s.promisedMb.toStringAsFixed(0)} Mo',
                          color: AppTheme.textPrimary,
                        ),
                      ),
                      Expanded(
                        child: _MiniCompareStat(
                          label: 'ÉCART NON REÇU',
                          value: '-${missingMb.toStringAsFixed(1)} Mo',
                          color: missingMb > 150
                              ? AppTheme.crimson
                              : AppTheme.textSecondary,
                        ),
                      ),
                      Expanded(
                        child: _MiniCompareStat(
                          label: 'VRAI PRIX / GO',
                          value: '${realCost.toStringAsFixed(0)} FCFA',
                          color: AppTheme.neonEmerald,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),

                  // Footer Action Row
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Flexible(
                        child: Text(
                          s.workloadTag,
                          style: const TextStyle(
                            fontSize: 11,
                            color: AppTheme.textSecondary,
                          ),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      TextButton.icon(
                        style: TextButton.styleFrom(
                          foregroundColor: AppTheme.neonEmerald,
                          visualDensity: VisualDensity.compact,
                        ),
                        icon: const Icon(Icons.verified_outlined, size: 16),
                        label: const Text('Certificat IA'),
                        onPressed: () => onInspectSessionReport(s),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          );
        }),
      ],
    );
  }
}

class _MiniCompareStat extends StatelessWidget {
  const _MiniCompareStat({
    required this.label,
    required this.value,
    required this.color,
  });

  final String label;
  final String value;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: const TextStyle(
            fontSize: 9.5,
            fontWeight: FontWeight.w800,
            letterSpacing: 0.5,
            color: AppTheme.textSecondary,
          ),
        ),
        const SizedBox(height: 2),
        Text(
          value,
          style: TextStyle(
            fontSize: 12.5,
            fontWeight: FontWeight.w800,
            color: color,
          ),
          overflow: TextOverflow.ellipsis,
        ),
      ],
    );
  }
}
