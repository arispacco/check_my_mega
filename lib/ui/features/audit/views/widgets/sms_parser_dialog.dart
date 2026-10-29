import 'package:flutter/material.dart';

import '../../../../../domain/models/isp_provider.dart';
import '../../../../core/theme/app_theme.dart';
import '../../view_models/audit_view_model.dart';

/// Dialog allowing the user to paste an operator SMS (Camtel, MTN, Orange)
/// or pick a 1-tap preset and parse it with Gemma 4.
class SmsParserDialog extends StatefulWidget {
  const SmsParserDialog({super.key, required this.viewModel});

  final AuditViewModel viewModel;

  @override
  State<SmsParserDialog> createState() => _SmsParserDialogState();
}

class _SmsParserDialogState extends State<SmsParserDialog> {
  late final TextEditingController _smsController;

  @override
  void initState() {
    super.initState();
    _smsController = TextEditingController(
      text: IspProvider.camtel.sampleSms,
    );
  }

  @override
  void dispose() {
    _smsController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      backgroundColor: AppTheme.cardBg,
      title: const Row(
        children: [
          Icon(Icons.auto_awesome, color: AppTheme.emerald),
          SizedBox(width: 10),
          Expanded(
            child: Text(
              'Scanner un SMS / USSD avec Gemma 4',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
          ),
        ],
      ),
      content: SizedBox(
        width: 520,
        child: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text(
                'Choisis un SMS opérateur camerounais type ou colle le SMS reçu après *825#, *123# ou #145# :',
                style: TextStyle(fontSize: 13, color: Colors.white70),
              ),
              const SizedBox(height: 12),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: IspProvider.values.map((isp) {
                  return ActionChip(
                    avatar: CircleAvatar(
                      backgroundColor: isp.brandColor,
                      radius: 6,
                    ),
                    label: Text(
                      '${isp.shortName} (${isp.defaultPriceXaf}F • 1Go)',
                      style: const TextStyle(fontSize: 12),
                    ),
                    onPressed: () {
                      setState(() {
                        _smsController.text = isp.sampleSms;
                      });
                    },
                  );
                }).toList(),
              ),
              const SizedBox(height: 14),
              TextField(
                controller: _smsController,
                maxLines: 4,
                decoration: InputDecoration(
                  labelText: 'Texte du SMS ou Popup USSD',
                  filled: true,
                  fillColor: Colors.black26,
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                ),
              ),
              const SizedBox(height: 12),
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: AppTheme.emerald.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Row(
                  children: [
                    const Icon(
                      Icons.shield_outlined,
                      size: 18,
                      color: AppTheme.emerald,
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        'Moteur actif : ${widget.viewModel.gemmaService.backendMode.label}',
                        style: const TextStyle(
                          fontSize: 12,
                          color: Colors.white70,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Annuler'),
        ),
        FilledButton.icon(
          icon: const Icon(Icons.play_arrow_rounded),
          label: const Text('Extraire avec Gemma 4 & Démarrer l\'Audit'),
          onPressed: () async {
            final text = _smsController.text.trim();
            if (text.isEmpty) return;
            Navigator.of(context).pop();
            await widget.viewModel.parseSmsWithGemma(
              text,
              autoStartSession: true,
            );
          },
        ),
      ],
    );
  }
}
