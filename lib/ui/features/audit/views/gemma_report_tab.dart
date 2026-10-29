import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../../data/services/gemma_service.dart';
import '../../../../domain/models/gemma_audit_report.dart';
import '../../../core/theme/app_theme.dart';
import '../view_models/audit_view_model.dart';

/// Tab 3: Certified Gemma 4 Audit Report & AI Configuration (Ollama Local vs Google AI Studio).
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
      AuditVerdict.conforme => AppTheme.emerald,
      AuditVerdict.suspect => AppTheme.amber,
      AuditVerdict.anomalieCritique => AppTheme.crimson,
    };

    return ListView(
      padding: const EdgeInsets.all(16),
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
                  label: Text(
                    '${item.isp.shortName} (${item.priceXaf}F • ${widget.viewModel.calculator.bytesToDecimalMb(item.totalConsumedBytes).toStringAsFixed(0)} Mo reçus)',
                  ),
                  onSelected: (_) =>
                      widget.viewModel.selectSessionForReport(item),
                ),
              );
            }).toList(),
          ),
        ),
        const SizedBox(height: 14),

        // Certified Gemma 4 Report Card
        Card(
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
                        color: verdictColor.withValues(alpha: 0.2),
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(color: verdictColor),
                      ),
                      child: Text(
                        'VERDICT : ${report.verdict.code}',
                        style: TextStyle(
                          color: verdictColor,
                          fontWeight: FontWeight.bold,
                          fontSize: 13,
                        ),
                      ),
                    ),
                    const Spacer(),
                    Icon(
                      Icons.verified_user_rounded,
                      size: 18,
                      color: verdictColor,
                    ),
                    const SizedBox(width: 6),
                    Text(
                      report.modelUsed,
                      style: const TextStyle(
                        fontSize: 12,
                        color: Colors.white60,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 14),
                Text(
                  'Certificat d\'Audit Réseau — ${session.isp.displayName}',
                  style: const TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                Text(
                  '${session.planName} (${session.priceXaf} FCFA) • Protocole : ${session.workloadTag}',
                  style: const TextStyle(fontSize: 13, color: Colors.white60),
                ),
                const Divider(height: 28, color: Colors.white12),

                // Cameroon / Citizen Summary Highlight
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: verdictColor.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(
                      color: verdictColor.withValues(alpha: 0.4),
                    ),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Row(
                        children: [
                          Icon(
                            Icons.record_voice_over_rounded,
                            size: 18,
                            color: Colors.white,
                          ),
                          SizedBox(width: 8),
                          Text(
                            'Synthèse Citoyenne (Gemma 4)',
                            style: TextStyle(
                              fontWeight: FontWeight.bold,
                              fontSize: 14,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 8),
                      Text(
                        report.cameroonSummary,
                        style: const TextStyle(
                          fontSize: 14,
                          height: 1.4,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),

                const Text(
                  'Analyse Technique Système (NetworkStatsManager & TrafficStats)',
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                ),
                const SizedBox(height: 6),
                Text(
                  report.technicalExplanation,
                  style: const TextStyle(
                    fontSize: 13,
                    height: 1.5,
                    color: Colors.white70,
                  ),
                ),
                const SizedBox(height: 18),

                Row(
                  children: [
                    FilledButton.icon(
                      icon: const Icon(Icons.copy_rounded, size: 18),
                      label: const Text('Copier le Certificat (WhatsApp / X)'),
                      onPressed: () {
                        final shareText =
                            '📊 [CheckMyMega × Gemma 4] Audit Forfait ${session.isp.displayName}\n'
                            '• Forfait : ${session.planName} (${session.priceXaf} FCFA)\n'
                            '• Promis : ${session.promisedMb.toStringAsFixed(0)} Mo\n'
                            '• Reçu par l\'OS : ${widget.viewModel.calculator.bytesToDecimalMb(session.totalConsumedBytes).toStringAsFixed(1)} Mo\n'
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
                  ],
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),

        // Gemma 4 Engine Configuration Card (Ollama Local & Google AI Studio)
        Card(
          child: Padding(
            padding: const EdgeInsets.all(18),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Row(
                  children: [
                    Icon(Icons.memory_rounded, color: AppTheme.emerald),
                    SizedBox(width: 10),
                    Text(
                      'Configuration du Moteur IA Google Gemma 4',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 6),
                const Text(
                  'Choisis comment exécuter Gemma 4 (en local via Ollama pour consommer 0 Mo pendant l\'audit, ou via Google AI Studio Cloud) :',
                  style: TextStyle(fontSize: 12, color: Colors.white60),
                ),
                const SizedBox(height: 12),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: GemmaBackendMode.values.map((mode) {
                    final selected =
                        widget.viewModel.gemmaService.backendMode == mode;
                    return ChoiceChip(
                      selected: selected,
                      label: Text(mode.label),
                      onSelected: (_) {
                        setState(() {
                          widget.viewModel.updateGemmaConfig(mode: mode);
                        });
                      },
                    );
                  }).toList(),
                ),
                const SizedBox(height: 14),
                Row(
                  children: [
                    Expanded(
                      flex: 2,
                      child: TextField(
                        controller: _ollamaUrlCtrl,
                        decoration: const InputDecoration(
                          labelText:
                              'URL Ollama Local (127.0.0.1 ou 10.0.2.2:11434)',
                          isDense: true,
                          border: OutlineInputBorder(),
                        ),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: TextField(
                        controller: _ollamaModelCtrl,
                        decoration: const InputDecoration(
                          labelText: 'Modèle Ollama (ex: gemma4:e4b)',
                          isDense: true,
                          border: OutlineInputBorder(),
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _apiKeyCtrl,
                  obscureText: true,
                  decoration: const InputDecoration(
                    labelText: 'Clé API Google AI Studio (Optionnel — Cloud)',
                    isDense: true,
                    border: OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 12),
                Align(
                  alignment: Alignment.centerRight,
                  child: FilledButton.tonalIcon(
                    icon: const Icon(Icons.save_outlined, size: 18),
                    label: const Text('Enregistrer Paramètres Gemma 4'),
                    onPressed: () {
                      widget.viewModel.updateGemmaConfig(
                        ollamaUrl: _ollamaUrlCtrl.text,
                        ollamaModelName: _ollamaModelCtrl.text,
                        apiKey: _apiKeyCtrl.text,
                      );
                    },
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
