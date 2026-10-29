import 'package:flutter/material.dart';

import '../../../core/theme/app_theme.dart';
import '../view_models/audit_view_model.dart';
import 'gemma_report_tab.dart';
import 'isp_comparison_tab.dart';
import 'live_audit_tab.dart';

/// Main responsive application shell for **CheckMyMega**.
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
        return Scaffold(
          appBar: AppBar(
            title: Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: AppTheme.emerald.withValues(alpha: 0.18),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: const Icon(
                    Icons.network_check_rounded,
                    color: AppTheme.emerald,
                    size: 22,
                  ),
                ),
                const SizedBox(width: 12),
                const Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'CheckMyMega CM',
                        style: TextStyle(
                          fontWeight: FontWeight.w800,
                          fontSize: 18,
                        ),
                      ),
                      Text(
                        'Auditeur Citoyen de Forfaits Internet × Google Gemma 4',
                        style: TextStyle(fontSize: 11, color: Colors.white60),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            actions: [
              Padding(
                padding: const EdgeInsets.only(right: 12),
                child: Chip(
                  avatar: const Icon(
                    Icons.bolt_rounded,
                    size: 16,
                    color: AppTheme.emerald,
                  ),
                  label: Text(
                    widget.viewModel.gemmaService.ollamaModel,
                    style: const TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.bold,
                    ),
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
                    _selectedIndex = 2;
                  });
                },
              ),
              IspComparisonTab(
                viewModel: widget.viewModel,
                onInspectSessionReport: (session) {
                  widget.viewModel.selectSessionForReport(session);
                  setState(() {
                    _selectedIndex = 2;
                  });
                },
              ),
              GemmaReportTab(viewModel: widget.viewModel),
            ],
          ),
          bottomNavigationBar: NavigationBar(
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
                label: 'Sonde OS Live',
              ),
              NavigationDestination(
                icon: Icon(Icons.compare_arrows_outlined),
                selectedIcon: Icon(Icons.compare_arrows_rounded),
                label: 'Camtel vs MTN vs Orange',
              ),
              NavigationDestination(
                icon: Icon(Icons.verified_outlined),
                selectedIcon: Icon(Icons.verified_rounded),
                label: 'Certificat Gemma 4',
              ),
            ],
          ),
        );
      },
    );
  }
}
