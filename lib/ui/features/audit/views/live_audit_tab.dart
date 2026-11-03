import 'package:flutter/material.dart';

import '../../../../domain/models/isp_provider.dart';
import '../../../core/theme/app_theme.dart';
import '../view_models/audit_view_model.dart';
import 'widgets/mega_gauge_card.dart';
import 'widgets/sms_parser_dialog.dart';

/// Tab 1: Real-time OS Network Metering, Live SMS Inbox Timeline, and Per-App Breakdown.
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
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 28),
      children: [
        // Android 13+ Restricted Settings Helper Banner (only shown if permission missing)
        if (!viewModel.hasOsPermission)
          Padding(
            padding: const EdgeInsets.only(bottom: 14),
            child: GlassTelemetryCard(
              accentColor: AppTheme.amber,
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const TelemetrySectionHeader(
                    icon: Icons.security_update_good_rounded,
                    title: 'Autorisation Système Android 13+',
                    subtitle:
                        'Débloquez l\'accès aux compteurs matériels NetworkStatsManager en 2 étapes',
                    accentColor: AppTheme.amber,
                  ),
                  const SizedBox(height: 12),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      OutlinedButton.icon(
                        icon: const Icon(Icons.settings_applications, size: 16),
                        label: const Text('1. Débloquer (Menu ⋮)'),
                        onPressed:
                            viewModel.openAppDetailsForRestrictedSettings,
                      ),
                      FilledButton.icon(
                        icon: const Icon(Icons.lock_open_rounded, size: 16),
                        label: const Text('2. Activer Accès Usage'),
                        onPressed: viewModel.checkAndOpenOsPermissions,
                      ),
                      TextButton.icon(
                        icon: const Icon(Icons.refresh_rounded, size: 16),
                        label: const Text('Vérifier'),
                        onPressed: viewModel.refreshPermissionState,
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),

        // Live Status Notification Pill
        if (viewModel.statusBannerMessage != null)
          Container(
            margin: const EdgeInsets.only(bottom: 14),
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 11),
            decoration: BoxDecoration(
              color: AppTheme.emerald.withValues(alpha: 0.11),
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                color: AppTheme.emerald.withValues(alpha: 0.4),
              ),
            ),
            child: Row(
              children: [
                const Icon(
                  Icons.bolt_rounded,
                  size: 18,
                  color: AppTheme.neonEmerald,
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    viewModel.statusBannerMessage!,
                    style: const TextStyle(
                      fontSize: 12.5,
                      fontWeight: FontWeight.w600,
                      color: AppTheme.textPrimary,
                    ),
                  ),
                ),
              ],
            ),
          ),

        // 1. HERO RADIAL GAUGE CARD (Top Priority Visual)
        MegaGaugeCard(session: session, calculator: viewModel.calculator),
        const SizedBox(height: 16),

        // 2. Active Plan & SMS Synchronization Controls
        GlassTelemetryCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              TelemetrySectionHeader(
                icon: Icons.sim_card_download_rounded,
                title: 'Forfait & Synchronisation SMS',
                subtitle:
                    'SIM active : ${viewModel.detectedCarrierName} • Détection automatique des forfaits cumulés',
                accentColor: AppTheme.sky,
                trailing: IconButton.outlined(
                  tooltip: 'Analyser un SMS manuellement avec Gemma 4',
                  icon: const Icon(Icons.qr_code_scanner_rounded, size: 18),
                  onPressed: () {
                    showDialog<void>(
                      context: context,
                      builder: (_) => SmsParserDialog(viewModel: viewModel),
                    );
                  },
                ),
              ),
              const SizedBox(height: 14),

              // Horizontal Plan Presets Carousel
              SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: Row(
                  children: [
                    _QuickPlanChip(
                      isp: IspProvider.camtel,
                      label: 'Blue 1Go • 100F (3h)',
                      isSelected:
                          session.isp == IspProvider.camtel &&
                          session.priceXaf == 100,
                      onTap: () => viewModel.startAuditSession(
                        isp: IspProvider.camtel,
                        planName: 'Blue mo S 1Go (3h)',
                        promisedMb: 1024,
                        priceXaf: 100,
                        validityHours: 3,
                      ),
                    ),
                    const SizedBox(width: 8),
                    _QuickPlanChip(
                      isp: IspProvider.camtel,
                      label: 'Blue 2Go • 300F (24h)',
                      isSelected:
                          session.isp == IspProvider.camtel &&
                          session.priceXaf == 300,
                      onTap: () => viewModel.startAuditSession(
                        isp: IspProvider.camtel,
                        planName: 'Blue Jour 2Go (24h)',
                        promisedMb: 2048,
                        priceXaf: 300,
                        validityHours: 24,
                      ),
                    ),
                    const SizedBox(width: 8),
                    _QuickPlanChip(
                      isp: IspProvider.camtel,
                      label: 'Blue 3Go • 250F (Nuit)',
                      isSelected:
                          session.isp == IspProvider.camtel &&
                          session.priceXaf == 250,
                      onTap: () => viewModel.startAuditSession(
                        isp: IspProvider.camtel,
                        planName: 'Blue Night 3Go',
                        promisedMb: 3072,
                        priceXaf: 250,
                        validityHours: 6,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 14),

              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  FilledButton.icon(
                    icon: const Icon(Icons.sync_rounded, size: 17),
                    label: const Text('Synchroniser Boîte SMS'),
                    onPressed: viewModel.syncFromSmsInbox,
                  ),
                  OutlinedButton.icon(
                    icon: const Icon(Icons.history_rounded, size: 17),
                    label: Text('Fenêtre ${session.validityHours}h'),
                    onPressed: () => viewModel.startAuditSession(
                      isp: session.isp,
                      planName: session.planName,
                      promisedMb: session.promisedMb,
                      priceXaf: session.priceXaf,
                      validityHours: session.validityHours,
                      includePastValidityWindow: true,
                    ),
                  ),
                  OutlinedButton.icon(
                    icon: const Icon(Icons.restart_alt_rounded, size: 17),
                    label: const Text('Partir de 0 Mo'),
                    onPressed: () => viewModel.startAuditSession(
                      isp: session.isp,
                      planName: session.planName,
                      promisedMb: session.promisedMb,
                      priceXaf: session.priceXaf,
                      validityHours: session.validityHours,
                      includePastValidityWindow: false,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
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
                      : const Icon(Icons.verified_user_rounded, size: 18),
                  label: const Text(
                    'Forfait Coupé par le FAI → Générer Certificat IA',
                  ),
                  onPressed: viewModel.isBusyGemma
                      ? null
                      : () async {
                          await viewModel.triggerIspExhaustionAndAudit();
                          onNavigateToReport();
                        },
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),

        // 3. Real Operator SMS Inbox & Live Interception Timeline
        if (viewModel.smsInboxMessages.isNotEmpty) ...[
          GlassTelemetryCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                TelemetrySectionHeader(
                  icon: Icons.mark_chat_read_rounded,
                  title: 'Chronologie SMS Opérateur',
                  subtitle:
                      'Lus depuis content://sms/inbox & interceptés en temps réel',
                  accentColor: AppTheme.emerald,
                  trailing: Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 9,
                      vertical: 4,
                    ),
                    decoration: BoxDecoration(
                      color: AppTheme.emerald.withValues(alpha: 0.16),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Text(
                      '${viewModel.smsInboxMessages.length} SMS',
                      style: const TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w800,
                        color: AppTheme.neonEmerald,
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 14),
                ...viewModel.smsInboxMessages.take(6).map((sms) {
                  final dt = DateTime.fromMillisecondsSinceEpoch(sms.dateMs);
                  final timeStr =
                      '${dt.day.toString().padLeft(2, '0')}/${dt.month.toString().padLeft(2, '0')} à '
                      '${dt.hour.toString().padLeft(2, '0')}h${dt.minute.toString().padLeft(2, '0')}';
                  final badgeColor = sms.isExhaustion
                      ? AppTheme.crimson
                      : (sms.isActivation ? AppTheme.neonEmerald : AppTheme.amber);
                  final badgeLabel = sms.isExhaustion
                      ? 'COUPURE 0 Mo'
                      : (sms.isActivation
                            ? '+${sms.promisedMb.toStringAsFixed(0)} Mo'
                            : (sms.priceXaf > 0
                                  ? '${sms.priceXaf} FCFA'
                                  : 'INFO'));
                  return Container(
                    margin: const EdgeInsets.only(bottom: 9),
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: AppTheme.obsidianBg.withValues(alpha: 0.55),
                      borderRadius: BorderRadius.circular(14),
                      border: Border.all(color: AppTheme.borderSubtle),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Row(
                              children: [
                                Icon(
                                  sms.isExhaustion
                                      ? Icons.portable_wifi_off_rounded
                                      : Icons.sms_rounded,
                                  size: 14,
                                  color: badgeColor,
                                ),
                                const SizedBox(width: 6),
                                Text(
                                  '${sms.address} • $timeStr',
                                  style: const TextStyle(
                                    fontWeight: FontWeight.w800,
                                    fontSize: 12,
                                    color: AppTheme.textPrimary,
                                  ),
                                ),
                              ],
                            ),
                            Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 8,
                                vertical: 3,
                              ),
                              decoration: BoxDecoration(
                                color: badgeColor.withValues(alpha: 0.16),
                                borderRadius: BorderRadius.circular(8),
                                border: Border.all(
                                  color: badgeColor.withValues(alpha: 0.45),
                                ),
                              ),
                              child: Text(
                                badgeLabel,
                                style: TextStyle(
                                  fontSize: 10.5,
                                  fontWeight: FontWeight.w900,
                                  color: badgeColor,
                                ),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 6),
                        Text(
                          sms.body,
                          style: const TextStyle(
                            fontSize: 12,
                            color: AppTheme.textSecondary,
                            height: 1.35,
                          ),
                        ),
                      ],
                    ),
                  );
                }),
              ],
            ),
          ),
          const SizedBox(height: 16),
        ],

        // 4. Per-Application Breakdown (NetworkStatsManager UID)
        GlassTelemetryCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              TelemetrySectionHeader(
                icon: Icons.layers_rounded,
                title: 'Ventilation par Application (UID OS)',
                subtitle:
                    'Vérifie qu\'aucune tâche cachée n\'a drainé votre forfait en arrière-plan',
                accentColor: AppTheme.violet,
                trailing: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 9,
                    vertical: 4,
                  ),
                  decoration: BoxDecoration(
                    color: AppTheme.violet.withValues(alpha: 0.16),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Text(
                    '${viewModel.calculator.computeAppAccountingCoveragePct(session).toStringAsFixed(0)}% tracé',
                    style: const TextStyle(
                      fontSize: 11,
                      color: AppTheme.violet,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 14),
              if (session.appUsages.isEmpty)
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 10),
                  child: Text(
                    'Aucune consommation par application détectée sur cette tranche.',
                    style: TextStyle(
                      fontSize: 12.5,
                      color: AppTheme.textSecondary,
                    ),
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
                  return Container(
                    margin: const EdgeInsets.only(bottom: 10),
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: AppTheme.obsidianBg.withValues(alpha: 0.5),
                      borderRadius: BorderRadius.circular(14),
                      border: Border.all(color: AppTheme.borderSubtle),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Expanded(
                              child: Text(
                                app.appName,
                                style: const TextStyle(
                                  fontWeight: FontWeight.w700,
                                  fontSize: 13,
                                  color: AppTheme.textPrimary,
                                ),
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                            const SizedBox(width: 8),
                            Text(
                              '${app.totalMbDecimal.toStringAsFixed(1)} Mo (${(share * 100).toStringAsFixed(0)}%)',
                              style: TextStyle(
                                fontWeight: FontWeight.w800,
                                fontSize: 12.5,
                                color: session.isp.brandColor,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 6),
                        ClipRRect(
                          borderRadius: BorderRadius.circular(5),
                          child: LinearProgressIndicator(
                            value: share,
                            minHeight: 6,
                            backgroundColor: Colors.white10,
                            valueColor: AlwaysStoppedAnimation<Color>(
                              session.isp.brandColor,
                            ),
                          ),
                        ),
                      ],
                    ),
                  );
                }),
            ],
          ),
        ),
      ],
    );
  }
}

class _QuickPlanChip extends StatelessWidget {
  const _QuickPlanChip({
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
      showCheckmark: false,
      selectedColor: isp.brandColor.withValues(alpha: 0.24),
      avatar: Container(
        width: 8,
        height: 8,
        decoration: BoxDecoration(
          color: isp.brandColor,
          shape: BoxShape.circle,
        ),
      ),
      label: Text(
        label,
        style: TextStyle(
          fontSize: 12.5,
          fontWeight: isSelected ? FontWeight.w800 : FontWeight.w500,
          color: isSelected ? AppTheme.textPrimary : AppTheme.textSecondary,
        ),
      ),
      onSelected: (_) => onTap(),
    );
  }
}
