import 'package:flutter/material.dart';

import '../../../../domain/models/data_plan_session.dart';
import '../../../core/theme/app_theme.dart';
import '../view_models/audit_view_model.dart';

/// Tab 2: Side-by-side comparison of Cameroon ISPs (Camtel 100F vs MTN 500F vs Orange 500F)
/// for the exact same workload ("Même Travail").
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
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(18),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Row(
                  children: [
                    Icon(Icons.balance_rounded, color: AppTheme.emerald),
                    SizedBox(width: 10),
                    Expanded(
                      child: Text(
                        'Comparateur Multi-FAI : Protocole « Même Travail » (1 Go)',
                        style: TextStyle(
                          fontSize: 17,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                const Text(
                  'Pourquoi un forfait de 1 Go à 500 FCFA chez MTN ou Orange finit-il avant le 1 Go à 100 FCFA (3h) de Camtel pour exactement le même travail ? Voici la vérité mesurée au niveau de la carte réseau du téléphone :',
                  style: TextStyle(fontSize: 13, color: Colors.white70),
                ),
                const SizedBox(height: 16),
                SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  child: DataTable(
                    headingRowColor: WidgetStateProperty.all(Colors.white10),
                    columns: const [
                      DataColumn(label: Text('Opérateur (FAI)')),
                      DataColumn(label: Text('Prix Payé')),
                      DataColumn(label: Text('Promis')),
                      DataColumn(label: Text('Reçu par l\'OS')),
                      DataColumn(label: Text('Mégas Perdus')),
                      DataColumn(label: Text('Taux Vérité')),
                      DataColumn(label: Text('Vrai Prix / Go')),
                      DataColumn(label: Text('Rapport IA')),
                    ],
                    rows: sessions.map((s) {
                      final measuredMb = calc.bytesToDecimalMb(
                        s.totalConsumedBytes,
                      );
                      final missingMb = calc.computeMissingMb(
                        totalConsumedBytes: s.totalConsumedBytes,
                        promisedMb: s.promisedMb,
                      );
                      final scorePct = calc.computeVeracityScorePct(
                        totalConsumedBytes: s.totalConsumedBytes,
                        promisedMb: s.promisedMb,
                      );
                      final realCost = calc.computeRealCostPerGbXaf(
                        priceXaf: s.priceXaf,
                        totalConsumedBytes: s.totalConsumedBytes,
                      );

                      final scoreColor = scorePct >= 90
                          ? AppTheme.emerald
                          : (scorePct >= 75
                                ? AppTheme.amber
                                : AppTheme.crimson);

                      return DataRow(
                        cells: [
                          DataCell(
                            Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                CircleAvatar(
                                  backgroundColor: s.isp.brandColor,
                                  radius: 6,
                                ),
                                const SizedBox(width: 8),
                                Text(
                                  s.isp.displayName,
                                  style: const TextStyle(
                                    fontWeight: FontWeight.bold,
                                  ),
                                ),
                              ],
                            ),
                          ),
                          DataCell(Text('${s.priceXaf} FCFA')),
                          DataCell(Text('${s.promisedMb.toStringAsFixed(0)} Mo')),
                          DataCell(
                            Text(
                              '${measuredMb.toStringAsFixed(1)} Mo',
                              style: const TextStyle(
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                          DataCell(
                            Text(
                              '-${missingMb.toStringAsFixed(1)} Mo',
                              style: TextStyle(
                                color: missingMb > 150
                                    ? AppTheme.crimson
                                    : Colors.white70,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ),
                          DataCell(
                            Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 8,
                                vertical: 4,
                              ),
                              decoration: BoxDecoration(
                                color: scoreColor.withValues(alpha: 0.18),
                                borderRadius: BorderRadius.circular(6),
                              ),
                              child: Text(
                                '${scorePct.toStringAsFixed(1)}%',
                                style: TextStyle(
                                  color: scoreColor,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            ),
                          ),
                          DataCell(
                            Text(
                              '${realCost.toStringAsFixed(0)} FCFA/Go',
                              style: const TextStyle(
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                          DataCell(
                            TextButton.icon(
                              icon: const Icon(
                                Icons.description_outlined,
                                size: 16,
                              ),
                              label: const Text('Voir Audit'),
                              onPressed: () => onInspectSessionReport(s),
                            ),
                          ),
                        ],
                      );
                    }).toList(),
                  ),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),

        // Visual Bar Comparison Cards
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

          return Card(
            margin: const EdgeInsets.only(bottom: 12),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Container(
                        width: 12,
                        height: 12,
                        decoration: BoxDecoration(
                          color: s.isp.brandColor,
                          shape: BoxShape.circle,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          '${s.isp.displayName} — ${s.planName} (${s.priceXaf} FCFA)',
                          style: const TextStyle(
                            fontWeight: FontWeight.bold,
                            fontSize: 15,
                          ),
                        ),
                      ),
                      Text(
                        'Coût réel : ${realCost.toStringAsFixed(0)} FCFA / Go livré',
                        style: const TextStyle(
                          fontSize: 12,
                          color: AppTheme.emerald,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(8),
                    child: LinearProgressIndicator(
                      value: (measuredMb / s.promisedMb).clamp(0.0, 1.0),
                      minHeight: 12,
                      backgroundColor: Colors.white10,
                      valueColor: AlwaysStoppedAnimation<Color>(
                        s.isp.brandColor,
                      ),
                    ),
                  ),
                  const SizedBox(height: 8),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        'Reçu OS : ${measuredMb.toStringAsFixed(1)} Mo / ${s.promisedMb.toStringAsFixed(0)} Mo (${scorePct.toStringAsFixed(1)}%)',
                        style: const TextStyle(
                          fontSize: 12,
                          color: Colors.white70,
                        ),
                      ),
                      Text(
                        'Écart : -${missingMb.toStringAsFixed(1)} Mo non reçus',
                        style: TextStyle(
                          fontSize: 12,
                          color: missingMb > 150
                              ? AppTheme.crimson
                              : Colors.white54,
                          fontWeight: FontWeight.w600,
                        ),
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
