import 'package:flutter/material.dart';

import '../../../../domain/models/data_plan_session.dart';
import '../../../../domain/models/isp_provider.dart';
import '../../../core/theme/app_theme.dart';
import '../view_models/audit_view_model.dart';
import 'widgets/mega_gauge_card.dart';
import 'widgets/sms_parser_dialog.dart';

/// Tab 1: Real-time OS Network Metering, Workload Calibration, and Per-App Breakdown.
class LiveAuditTab extends StatelessWidget {
  const LiveAuditTab({
    super.key,
    required this.viewModel,
    required this.onNavigateToReport,
  });

  final AuditViewModel viewModel;
  final VoidCallback onNavigateToReport;

  @override
  Widget build(BuildContext context) {
    final session = viewModel.activeSession;
    if (session == null) {
      return const Center(child: CircularProgressIndicator());
    }

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        if (!viewModel.hasOsPermission)
          Container(
            margin: const EdgeInsets.only(bottom: 14),
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: AppTheme.amber.withValues(alpha: 0.15),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: AppTheme.amber),
            ),
            child: Row(
              children: [
                const Icon(Icons.security_update_good, color: AppTheme.amber),
                const SizedBox(width: 12),
                const Expanded(
                  child: Text(
                    'Permission Android requise : Autorisez "Accès aux données d\'utilisation" pour lire NetworkStatsManager par application.',
                    style: TextStyle(fontSize: 13),
                  ),
                ),
                FilledButton.tonal(
                  onPressed: viewModel.checkAndOpenOsPermissions,
                  child: const Text('Autoriser OS'),
                ),
              ],
            ),
          ),
        if (viewModel.statusBannerMessage != null)
          Container(
            margin: const EdgeInsets.only(bottom: 14),
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
            decoration: BoxDecoration(
              color: AppTheme.emerald.withValues(alpha: 0.12),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                color: AppTheme.emerald.withValues(alpha: 0.4),
              ),
            ),
            child: Row(
              children: [
                const Icon(
                  Icons.info_outline_rounded,
                  size: 18,
                  color: AppTheme.emerald,
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    viewModel.statusBannerMessage!,
                    style: const TextStyle(fontSize: 13),
                  ),
                ),
              ],
            ),
          ),

        // Quick FAI Switcher & Gemma 4 SMS Scanner
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    const Expanded(
                      child: Text(
                        '1. Sélectionner ou Scanner un Forfait à Auditer',
                        style: TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 15,
                        ),
                      ),
                    ),
                    FilledButton.icon(
                      icon: const Icon(Icons.sms_outlined, size: 18),
                      label: const Text('Scanner SMS avec Gemma 4'),
                      onPressed: () {
                        showDialog<void>(
                          context: context,
                          builder: (_) => SmsParserDialog(viewModel: viewModel),
                        );
                      },
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                Wrap(
                  spacing: 10,
                  runSpacing: 8,
                  children: [
                    _QuickPlanButton(
                      isp: IspProvider.camtel,
                      label: 'Camtel 1Go • 100 FCFA (3h)',
                      isSelected: session.isp == IspProvider.camtel,
                      onTap: () => viewModel.startAuditSession(
                        isp: IspProvider.camtel,
                        planName: 'Blue Horaire 1Go (3h)',
                        promisedMb: 1024,
                        priceXaf: 100,
                        validityHours: 3,
                      ),
                    ),
                    _QuickPlanButton(
                      isp: IspProvider.mtnCm,
                      label: 'MTN 1Go • 500 FCFA (24h)',
                      isSelected: session.isp == IspProvider.mtnCm,
                      onTap: () => viewModel.startAuditSession(
                        isp: IspProvider.mtnCm,
                        planName: 'Mobile Surf / Wanda 1Go',
                        promisedMb: 1024,
                        priceXaf: 500,
                        validityHours: 24,
                      ),
                    ),
                    _QuickPlanButton(
                      isp: IspProvider.orangeCm,
                      label: 'Orange 1Go • 500 FCFA (24h)',
                      isSelected: session.isp == IspProvider.orangeCm,
                      onTap: () => viewModel.startAuditSession(
                        isp: IspProvider.orangeCm,
                        planName: 'Forfait Internet 1Go Jour',
                        promisedMb: 1024,
                        priceXaf: 500,
                        validityHours: 24,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 14),

        // Main OS Gauge Card
        MegaGaugeCard(session: session, calculator: viewModel.calculator),
        const SizedBox(height: 14),

        // Calibrated Workload ("Même Travail") & Exhaustion Trigger
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  '2. Protocole « Même Travail » & Clôture du Forfait',
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
                ),
                const SizedBox(height: 6),
                const Text(
                  'Enregistre la consommation système en direct ou injecte une charge de travail reproductible avant de déclarer la coupure du forfait par le FAI :',
                  style: TextStyle(fontSize: 12, color: Colors.white60),
                ),
                const SizedBox(height: 12),
                Wrap(
                  spacing: 10,
                  runSpacing: 10,
                  children: [
                    OutlinedButton.icon(
                      icon: const Icon(Icons.add_chart_rounded, size: 18),
                      label: const Text('+50 Mo (Travail Standard)'),
                      onPressed: session.status == AuditSessionStatus.active
                          ? () => viewModel.injectCalibratedWorkload(
                              mbAmount: 50,
                            )
                          : null,
                    ),
                    OutlinedButton.icon(
                      icon: const Icon(Icons.speed_rounded, size: 18),
                      label: const Text('+200 Mo (Streaming / Dev)'),
                      onPressed: session.status == AuditSessionStatus.active
                          ? () => viewModel.injectCalibratedWorkload(
                              mbAmount: 200,
                            )
                          : null,
                    ),
                    FilledButton.icon(
                      style: FilledButton.styleFrom(
                        backgroundColor: AppTheme.crimson,
                        foregroundColor: Colors.white,
                      ),
                      icon: viewModel.isBusyGemma
                          ? const SizedBox(
                              width: 16,
                              height: 16,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: Colors.white,
                              ),
                            )
                          : const Icon(Icons.gavel_rounded, size: 18),
                      label: const Text(
                        'Forfait Coupé par le FAI → Générer Audit Gemma 4',
                      ),
                      onPressed: viewModel.isBusyGemma
                          ? null
                          : () async {
                              await viewModel.triggerIspExhaustionAndAudit();
                              onNavigateToReport();
                            },
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 14),

        // Per-Application Breakdown (Proof of No Background Leak)
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    const Icon(
                      Icons.apps_rounded,
                      size: 20,
                      color: AppTheme.emerald,
                    ),
                    const SizedBox(width: 8),
                    const Expanded(
                      child: Text(
                        '3. Ventilation Système par Application (NetworkStatsManager UID)',
                        style: TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 15,
                        ),
                      ),
                    ),
                    Text(
                      'Couverture OS : ${viewModel.calculator.computeAppAccountingCoveragePct(session).toStringAsFixed(0)}%',
                      style: const TextStyle(
                        fontSize: 12,
                        color: AppTheme.emerald,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 6),
                const Text(
                  'Prouve qu\'aucune application cachée n\'a volé les mégas en arrière-plan pendant le test :',
                  style: TextStyle(fontSize: 12, color: Colors.white60),
                ),
                const SizedBox(height: 12),
                if (session.appUsages.isEmpty)
                  const Padding(
                    padding: EdgeInsets.symmetric(vertical: 12),
                    child: Text(
                      'Aucune donnée consommée sur cette session pour l\'instant.',
                    ),
                  )
                else
                  ...session.appUsages.map((app) {
                    final share = session.totalConsumedBytes > 0
                        ? (app.totalBytes / session.totalConsumedBytes).clamp(
                            0.0,
                            1.0,
                          )
                        : 0.0;
                    return Padding(
                      padding: const EdgeInsets.only(bottom: 10),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              Expanded(
                                child: Text(
                                  '${app.appName} (${app.packageName})',
                                  style: const TextStyle(
                                    fontWeight: FontWeight.w600,
                                    fontSize: 13,
                                  ),
                                ),
                              ),
                              Text(
                                '${app.totalMbDecimal.toStringAsFixed(1)} Mo (${(share * 100).toStringAsFixed(0)}%)',
                                style: const TextStyle(
                                  fontWeight: FontWeight.bold,
                                  fontSize: 13,
                                  color: Colors.white,
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 4),
                          LinearProgressIndicator(
                            value: share,
                            minHeight: 6,
                            borderRadius: BorderRadius.circular(4),
                            backgroundColor: Colors.white10,
                            valueColor: AlwaysStoppedAnimation<Color>(
                              session.isp.brandColor,
                            ),
                          ),
                        ],
                      ),
                    );
                  }),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class _QuickPlanButton extends StatelessWidget {
  const _QuickPlanButton({
    required this.isp,
    required this.label,
    required this.isSelected,
    required this.onTap,
  });

  final IspProvider isp;
  final String label;
  final bool isSelected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return ChoiceChip(
      selected: isSelected,
      selectedColor: isp.brandColor.withValues(alpha: 0.25),
      label: Text(
        label,
        style: TextStyle(
          fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
          color: isSelected ? Colors.white : Colors.white70,
        ),
      ),
      onSelected: (_) => onTap(),
    );
  }
}
