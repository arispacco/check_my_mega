import 'package:flutter/material.dart';

import '../../../core/theme/app_theme.dart';
import '../view_models/audit_view_model.dart';
import 'gemma_report_tab.dart';
import 'isp_comparison_tab.dart';
import 'live_audit_tab.dart';
import 'signal_compass_tab.dart';

/// Main responsive application shell for **CheckMyMega CM**.
class CheckMyMegaShell extends StatefulWidget {
  const CheckMyMegaShell({super.key, required this.viewModel});

  final AuditViewModel viewModel;

  @override
  State<CheckMyMegaShell> createState() => _CheckMyMegaShellState();
}

class _CheckMyMegaShellState extends State<CheckMyMegaShell> {
  int _selectedIndex = 0;

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: widget.viewModel,
      builder: (context, _) {
        final rsrp = widget.viewModel.radioSnapshot?.rsrpDbm;
        return Scaffold(
          appBar: AppBar(
            toolbarHeight: 64,
            bottom: PreferredSize(
              preferredSize: const Size.fromHeight(1),
              child: Container(
                height: 1,
                color: AppTheme.borderSubtle.withValues(alpha: 0.8),
              ),
            ),
            title: Row(
              children: [
                Container(
                  width: 38,
                  height: 38,
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                      colors: [
                        AppTheme.neonEmerald.withValues(alpha: 0.25),
                        AppTheme.sky.withValues(alpha: 0.15),
                      ],
                    ),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(
                      color: AppTheme.neonEmerald.withValues(alpha: 0.5),
                    ),
                  ),
                  child: const Icon(
                    Icons.radar_rounded,
                    color: AppTheme.neonEmerald,
                    size: 21,
                  ),
                ),
                const SizedBox(width: 11),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'CheckMyMega CM',
                        style: TextStyle(
                          fontWeight: FontWeight.w900,
                          fontSize: 17,
                          letterSpacing: -0.3,
                          color: AppTheme.textPrimary,
                        ),
                      ),
                      Text(
                        rsrp != null
                            ? '${widget.viewModel.detectedCarrierName} • $rsrp dBm'
                            : 'Auditeur Réseau & Radar 4G × Gemma 4',
                        style: const TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.w600,
                          color: AppTheme.textSecondary,
                        ),
                        overflow: TextOverflow.ellipsis,
                      ),
                    ],
                  ),
                ),
              ],
            ),
            actions: [
              Padding(
                padding: const EdgeInsets.only(right: 14),
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 10,
                    vertical: 6,
                  ),
                  decoration: BoxDecoration(
                    color: AppTheme.cardElevated,
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(color: AppTheme.borderHighlight),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        widget.viewModel.gemmaService.isOllamaReachable
                            ? Icons.bolt_rounded
                            : Icons.shield_outlined,
                        size: 14,
                        color: AppTheme.neonEmerald,
                      ),
                      const SizedBox(width: 5),
                      Text(
                        widget.viewModel.gemmaService.statusChipLabel,
                        style: const TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.w800,
                          color: AppTheme.textPrimary,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
          body: IndexedStack(
            index: _selectedIndex,
            children: [
              LiveAuditTab(
                viewModel: widget.viewModel,
                onNavigateToReport: () {
                  setState(() {
                    _selectedIndex = 3;
                  });
                },
              ),
              SignalCompassTab(viewModel: widget.viewModel),
              IspComparisonTab(
                viewModel: widget.viewModel,
                onInspectSessionReport: (session) {
                  widget.viewModel.selectSessionForReport(session);
                  setState(() {
                    _selectedIndex = 3;
                  });
                },
              ),
              GemmaReportTab(viewModel: widget.viewModel),
            ],
          ),
          bottomNavigationBar: Container(
            decoration: BoxDecoration(
              border: Border(
                top: BorderSide(
                  color: AppTheme.borderSubtle.withValues(alpha: 0.85),
                  width: 1,
                ),
              ),
            ),
            child: NavigationBar(
              selectedIndex: _selectedIndex,
              onDestinationSelected: (idx) {
                setState(() {
                  _selectedIndex = idx;
                });
              },
              destinations: const [
                NavigationDestination(
                  icon: Icon(Icons.speed_outlined),
                  selectedIcon: Icon(Icons.speed_rounded),
                  label: 'Sonde & SMS',
                ),
                NavigationDestination(
                  icon: Icon(Icons.explore_outlined),
                  selectedIcon: Icon(Icons.explore_rounded),
                  label: 'Boussole & Débit',
                ),
                NavigationDestination(
                  icon: Icon(Icons.compare_arrows_outlined),
                  selectedIcon: Icon(Icons.compare_arrows_rounded),
                  label: 'Historique FAI',
                ),
                NavigationDestination(
                  icon: Icon(Icons.verified_outlined),
                  selectedIcon: Icon(Icons.verified_rounded),
                  label: 'Certificat IA',
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}
