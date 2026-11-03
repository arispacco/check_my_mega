import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../../data/services/gemma_service.dart';
import '../../../../domain/models/gemma_audit_report.dart';
import '../../../core/theme/app_theme.dart';
import '../view_models/audit_view_model.dart';

/// Tab 4: Digital Citizen Audit Certificate & Collapsible Gemma 4 Engine Configuration.
class GemmaReportTab extends StatefulWidget {
  const GemmaReportTab({super.key, required this.viewModel});

  final AuditViewModel viewModel;

  @override
  State<GemmaReportTab> createState() => _GemmaReportTabState();
}

class _GemmaReportTabState extends State<GemmaReportTab> {
  late final TextEditingController _ollamaUrlCtrl;
  late final TextEditingController _ollamaModelCtrl;
  late final TextEditingController _apiKeyCtrl;

  @override
  void initState() {
    super.initState();
    final gemma = widget.viewModel.gemmaService;
    _ollamaUrlCtrl = TextEditingController(text: gemma.ollamaBaseUrl);
    _ollamaModelCtrl = TextEditingController(text: gemma.ollamaModel);
    _apiKeyCtrl = TextEditingController(text: gemma.googleAiStudioApiKey);
  }

  @override
  void dispose() {
    _ollamaUrlCtrl.dispose();
    _ollamaModelCtrl.dispose();
    _apiKeyCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final session = widget.viewModel.selectedReportSession;
    if (session == null) {
      return const Center(child: Text('Aucune session sélectionnée.'));
    }

    final report =
        session.report ??
        widget.viewModel.calculator.buildDeterministicReport(session);

    final verdictColor = switch (report.verdict) {
      AuditVerdict.conforme => AppTheme.neonEmerald,
      AuditVerdict.suspect => AppTheme.amber,
      AuditVerdict.anomalieCritique => AppTheme.crimson,
    };

    final measuredMb = widget.viewModel.calculator.bytesToDecimalMb(
      session.totalConsumedBytes,
    );

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 28),
      children: [
        // Session Selector Pills
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(
            children: widget.viewModel.history.map((item) {
              final selected = item.id == session.id;
              return Padding(
                padding: const EdgeInsets.only(right: 8),
                child: ChoiceChip(
                  selected: selected,
                  showCheckmark: false,
                  label: Text(
                    '${item.isp.shortName} • ${item.priceXaf}F (${widget.viewModel.calculator.bytesToDecimalMb(item.totalConsumedBytes).toStringAsFixed(0)} Mo)',
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: selected ? FontWeight.w800 : FontWeight.w500,
                    ),
                  ),
                  onSelected: (_) =>
                      widget.viewModel.selectSessionForReport(item),
                ),
              );
            }).toList(),
          ),
        ),
        const SizedBox(height: 14),

        // Certified Digital Audit Report Card
        GlassTelemetryCard(
          accentColor: verdictColor,
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 11,
                      vertical: 5,
                    ),
                    decoration: BoxDecoration(
                      color: verdictColor.withValues(alpha: 0.16),
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(
                        color: verdictColor.withValues(alpha: 0.65),
                      ),
                    ),
                    child: Text(
                      'VERDICT : ${report.verdict.code}',
                      style: TextStyle(
                        color: verdictColor,
                        fontWeight: FontWeight.w900,
                        fontSize: 11.5,
                        letterSpacing: 0.4,
                      ),
                    ),
                  ),
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        Icons.verified_rounded,
                        size: 16,
                        color: verdictColor,
                      ),
                      const SizedBox(width: 5),
                      Text(
                        report.modelUsed,
                        style: const TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.w700,
                          color: AppTheme.textSecondary,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
              const SizedBox(height: 16),
              Text(
                'Certificat d\'Audit — ${session.isp.displayName}',
                style: const TextStyle(
                  fontSize: 20,
                  fontWeight: FontWeight.w900,
                  color: AppTheme.textPrimary,
                  letterSpacing: -0.4,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                '${session.planName} (${session.priceXaf} FCFA) • ${session.workloadTag}',
                style: const TextStyle(
                  fontSize: 12.5,
                  color: AppTheme.textSecondary,
                ),
              ),
              const SizedBox(height: 16),

              // 3 Key Figures Summary Strip
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: AppTheme.obsidianBg.withValues(alpha: 0.6),
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: AppTheme.borderSubtle),
                ),
                child: Row(
                  children: [
                    Expanded(
                      child: _CertMetric(
                        label: 'TAUX DE VÉRITÉ',
                        value: '${report.veracityScorePct.toStringAsFixed(1)}%',
                        color: verdictColor,
                      ),
                    ),
                    Expanded(
                      child: _CertMetric(
                        label: 'REÇU / PROMIS',
                        value:
                            '${measuredMb.toStringAsFixed(0)}/${session.promisedMb.toStringAsFixed(0)} Mo',
                        color: AppTheme.textPrimary,
                      ),
                    ),
                    Expanded(
                      child: _CertMetric(
                        label: 'COÛT RÉEL / GO',
                        value:
                            '${report.realCostPerGbXaf.toStringAsFixed(0)} FCFA',
                        color: AppTheme.neonEmerald,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),

              // Citizen Summary Highlight
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: verdictColor.withValues(alpha: 0.09),
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(
                    color: verdictColor.withValues(alpha: 0.35),
                  ),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Row(
                      children: [
                        Icon(
                          Icons.auto_awesome_rounded,
                          size: 17,
                          color: AppTheme.neonEmerald,
                        ),
                        SizedBox(width: 8),
                        Text(
                          'Synthèse Citoyenne (Gemma 4)',
                          style: TextStyle(
                            fontWeight: FontWeight.w800,
                            fontSize: 13.5,
                            color: AppTheme.textPrimary,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    Text(
                      report.cameroonSummary,
                      style: const TextStyle(
                        fontSize: 13.5,
                        height: 1.45,
                        color: AppTheme.textPrimary,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),

              const Text(
                'PREUVE MATÉRIELLE (NETWORKSTATSMANAGER & TRAFFICSTATS)',
                style: TextStyle(
                  fontWeight: FontWeight.w800,
                  fontSize: 10.5,
                  letterSpacing: 0.7,
                  color: AppTheme.textSecondary,
                ),
              ),
              const SizedBox(height: 6),
              Text(
                report.technicalExplanation,
                style: const TextStyle(
                  fontSize: 12.5,
                  height: 1.48,
                  color: AppTheme.textSecondary,
                ),
              ),
              const SizedBox(height: 18),

              SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  icon: const Icon(Icons.share_rounded, size: 18),
                  label: const Text('Copier le Certificat (WhatsApp / X)'),
                  onPressed: () {
                    final shareText =
                        '📊 [CheckMyMega × Gemma 4] Audit Forfait ${session.isp.displayName}\n'
                        '• Forfait : ${session.planName} (${session.priceXaf} FCFA)\n'
                        '• Promis : ${session.promisedMb.toStringAsFixed(0)} Mo\n'
                        '• Reçu par l\'OS : ${measuredMb.toStringAsFixed(1)} Mo\n'
                        '• Taux de Vérité : ${report.veracityScorePct.toStringAsFixed(1)}% (-${report.missingMb.toStringAsFixed(0)} Mo perdus)\n'
                        '• Vrai Coût : ${report.realCostPerGbXaf.toStringAsFixed(0)} FCFA / Go réel\n'
                        '💬 Verdict Gemma 4 : ${report.cameroonSummary}';
                    Clipboard.setData(ClipboardData(text: shareText));
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text(
                          'Certificat d\'audit copié dans le presse-papiers !',
                        ),
                      ),
                    );
                  },
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),

        // Collapsible AI Engine Settings so technical inputs stay tidy
        GlassTelemetryCard(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          child: Theme(
            data: Theme.of(context).copyWith(dividerColor: Colors.transparent),
            child: ExpansionTile(
              tilePadding: EdgeInsets.zero,
              leading: const Icon(
                Icons.memory_rounded,
                color: AppTheme.emerald,
              ),
              title: const Text(
                'Paramètres du Moteur IA (Gemma 4)',
                style: TextStyle(
                  fontSize: 14.5,
                  fontWeight: FontWeight.w800,
                  color: AppTheme.textPrimary,
                ),
              ),
              subtitle: Text(
                'Mode actif : ${widget.viewModel.gemmaService.backendMode.label}',
                style: const TextStyle(
                  fontSize: 11.5,
                  color: AppTheme.textSecondary,
                ),
              ),
              children: [
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: GemmaBackendMode.values.map((mode) {
                    final selected =
                        widget.viewModel.gemmaService.backendMode == mode;
                    return ChoiceChip(
                      selected: selected,
                      showCheckmark: false,
                      label: Text(
                        mode.label,
                        style: const TextStyle(fontSize: 12),
                      ),
                      onSelected: (_) {
                        setState(() {
                          widget.viewModel.updateGemmaConfig(mode: mode);
                        });
                      },
                    );
                  }).toList(),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _ollamaUrlCtrl,
                  decoration: const InputDecoration(
                    labelText: 'URL Ollama Local (ex: http://127.0.0.1:11434)',
                    isDense: true,
                    border: OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 10),
                TextField(
                  controller: _ollamaModelCtrl,
                  decoration: const InputDecoration(
                    labelText: 'Modèle Ollama (ex: gemma4:e4b)',
                    isDense: true,
                    border: OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 10),
                TextField(
                  controller: _apiKeyCtrl,
                  obscureText: true,
                  decoration: const InputDecoration(
                    labelText: 'Clé API Google AI Studio (Optionnel)',
                    isDense: true,
                    border: OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 12),
                Align(
                  alignment: Alignment.centerRight,
                  child: FilledButton.icon(
                    icon: const Icon(Icons.save_outlined, size: 17),
                    label: const Text('Enregistrer'),
                    onPressed: () {
                      widget.viewModel.updateGemmaConfig(
                        ollamaUrl: _ollamaUrlCtrl.text,
                        ollamaModelName: _ollamaModelCtrl.text,
                        apiKey: _apiKeyCtrl.text,
                      );
                    },
                  ),
                ),
                const SizedBox(height: 8),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class _CertMetric extends StatelessWidget {
  const _CertMetric({
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
        const SizedBox(height: 3),
        Text(
          value,
          style: TextStyle(
            fontSize: 15,
            fontWeight: FontWeight.w900,
            color: color,
          ),
          overflow: TextOverflow.ellipsis,
        ),
      ],
    );
  }
}
