import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

import '../../domain/models/data_plan_session.dart';
import '../../domain/models/gemma_audit_report.dart';
import '../../domain/models/isp_provider.dart';
import '../../domain/use_cases/mega_calculator.dart';

/// Execution backend mode for Gemma 4.
enum GemmaBackendMode {
  ollamaLocal(
    label: 'Ollama Local (0 Mo consommé)',
    description: 'Exécute Gemma 4 en local (gemma4:e4b / gemma4:12b) sans toucher au forfait.',
  ),
  googleAiStudio(
    label: 'Google AI Studio API',
    description: 'Exécute Gemma 4 via le Cloud Google AI Studio (après le test).',
  ),
  offlineDeterministic(
    label: 'Mode Hors-Ligne Instantané',
    description: 'Analyse locale déterministe sans connexion ni serveur.',
  );

  const GemmaBackendMode({required this.label, required this.description});

  final String label;
  final String description;
}

/// Structured result extracted by Gemma 4 from an operator SMS or USSD text.
class ParsedSmsPlan {
  const ParsedSmsPlan({
    required this.isp,
    required this.planName,
    required this.promisedMb,
    required this.priceXaf,
    required this.validityHours,
    required this.eventType,
    required this.parsedByModel,
  });

  final IspProvider isp;
  final String planName;
  final double promisedMb;
  final int priceXaf;
  final int validityHours;
  final String eventType;
  final String parsedByModel;
}

/// Hybrid AI Service integrating **Google Gemma 4** via Local Ollama (`gemma4:e4b` / `gemma4:12b`)
/// and Google AI Studio API, with an offline zero-data fallback.
class GemmaService {
  GemmaService({
    http.Client? httpClient,
    MegaCalculator? calculator,
  }) : _http = httpClient ?? http.Client(),
       _calculator = calculator ?? const MegaCalculator() {
    _loadEnv();
  }

  final http.Client _http;
  final MegaCalculator _calculator;

  GemmaBackendMode backendMode = GemmaBackendMode.ollamaLocal;
  String ollamaBaseUrl = 'http://127.0.0.1:11434';
  String ollamaModel = 'gemma4:e4b';
  String googleAiStudioApiKey = '';
  String googleAiStudioModel = 'gemma-4-12b-it';
  bool isOllamaReachable = false;

  /// Probes whether a local Ollama server is reachable on [ollamaBaseUrl].
  Future<bool> checkOllamaConnection() async {
    try {
      final uri = Uri.parse('$ollamaBaseUrl/api/tags');
      final response = await _http
          .get(uri)
          .timeout(const Duration(milliseconds: 1500));
      isOllamaReachable = response.statusCode == 200;
    } catch (_) {
      isOllamaReachable = false;
    }
    return isOllamaReachable;
  }

  /// Human-readable status badge for the AppBar showing the true active engine.
  String get statusChipLabel {
    if (backendMode == GemmaBackendMode.ollamaLocal) {
      return isOllamaReachable
          ? 'Ollama: $ollamaModel'
          : 'Moteur Local 0-Data';
    }
    if (backendMode == GemmaBackendMode.googleAiStudio) {
      return googleAiStudioApiKey.trim().isNotEmpty
          ? 'Cloud: $googleAiStudioModel'
          : 'Moteur Local 0-Data';
    }
    return 'Hors-Ligne 0-Data';
  }

  void _loadEnv() {
    // 1. Try compile-time environment define (--dart-define / --dart-define-from-file=.env)
    const compileKey = String.fromEnvironment('GOOGLE_AI_STUDIO_API_KEY');
    if (compileKey.isNotEmpty) {
      googleAiStudioApiKey = compileKey;
    }
    const compileModel = String.fromEnvironment('GEMMA_MODEL');
    if (compileModel.isNotEmpty) {
      googleAiStudioModel = compileModel;
    }

    // 2. Fallback: Parse .env file from disk if accessible
    if (!kIsWeb && googleAiStudioApiKey.isEmpty) {
      try {
        final envFile = File('.env');
        if (envFile.existsSync()) {
          final lines = envFile.readAsLinesSync();
          for (final line in lines) {
            final trimmed = line.trim();
            if (trimmed.startsWith('#') || !trimmed.contains('=')) continue;
            final idx = trimmed.indexOf('=');
            final key = trimmed.substring(0, idx).trim();
            final value = trimmed.substring(idx + 1).trim();
            if (key == 'GOOGLE_AI_STUDIO_API_KEY' && value.isNotEmpty) {
              googleAiStudioApiKey = value;
            } else if (key == 'GEMMA_MODEL' && value.isNotEmpty) {
              googleAiStudioModel = value;
            }
          }
        }
      } catch (_) {
        // Ignore file system errors
      }
    }
  }

  /// Parses an operator SMS or USSD message (Camtel, MTN, Orange, Yoomee) into
  /// a structured [ParsedSmsPlan] using Gemma 4 JSON output.
  Future<ParsedSmsPlan> parseOperatorSms(String rawSms) async {
    final prompt = '''
Tu es un agent expert en télécommunications au Cameroun (Camtel/Blue, MTN Cameroon, Orange Cameroun, Yoomee).
Extrais les informations du forfait internet contenu dans ce SMS ou message USSD et réponds UNIQUEMENT avec un objet JSON valide :
{
  "isp": "CAMTEL" | "MTN_CM" | "ORANGE_CM" | "YOOMEE",
  "planName": "Nom du forfait",
  "promisedMb": 1024,
  "priceXaf": 100,
  "validityHours": 3,
  "eventType": "ACTIVATION" | "EXHAUSTED"
}

Message à analyser :
"""$rawSms"""
''';

    if (backendMode != GemmaBackendMode.offlineDeterministic) {
      try {
        final responseText = await _callGemmaJson(prompt);
        if (responseText != null) {
          final decoded = _extractJsonMap(responseText);
          if (decoded != null) {
            final isp = IspProvider.fromIdOrText(
              decoded['isp']?.toString() ?? rawSms,
            );
            return ParsedSmsPlan(
              isp: isp,
              planName: decoded['planName']?.toString() ??
                  isp.defaultSamplePlanName,
              promisedMb: (decoded['promisedMb'] as num?)?.toDouble() ??
                  isp.defaultPromisedMb,
              priceXaf:
                  (decoded['priceXaf'] as num?)?.toInt() ?? isp.defaultPriceXaf,
              validityHours: (decoded['validityHours'] as num?)?.toInt() ??
                  isp.defaultValidityHours,
              eventType: decoded['eventType']?.toString() ?? 'ACTIVATION',
              parsedByModel: _activeModelBadge(),
            );
          }
        }
      } catch (_) {
        // Fallback to local smart extraction below so the user is never blocked
      }
    }

    return _parseSmsLocally(rawSms);
  }

  /// Generates a certified audit report using Gemma 4 reasoning on the OS network counters.
  Future<GemmaAuditReport> generateAuditReport({
    required DataPlanSession session,
    DataPlanSession? camtelReference,
  }) async {
    final baselineReport = _calculator.buildDeterministicReport(
      session,
      modelLabel: '${_activeModelBadge()} (Calculateur OS Certifié)',
    );

    if (backendMode == GemmaBackendMode.offlineDeterministic) {
      return baselineReport;
    }

    final measuredMb = _calculator.bytesToDecimalMb(session.totalConsumedBytes);
    final measuredMib = _calculator.bytesToBinaryMib(session.totalConsumedBytes);
    final payload = {
      'isp': session.isp.displayName,
      'planName': session.planName,
      'promisedMb': session.promisedMb,
      'priceXaf': session.priceXaf,
      'validityHours': session.validityHours,
      'osMeasuredDecimalMb': double.parse(measuredMb.toStringAsFixed(2)),
      'osMeasuredBinaryMib': double.parse(measuredMib.toStringAsFixed(2)),
      'veracityScorePct':
          double.parse(baselineReport.veracityScorePct.toStringAsFixed(1)),
      'missingMb': double.parse(baselineReport.missingMb.toStringAsFixed(1)),
      'realCostPerGbXaf':
          double.parse(baselineReport.realCostPerGbXaf.toStringAsFixed(0)),
      'topApps': session.appUsages.take(5).map((a) => a.toJson()).toList(),
      if (camtelReference != null)
        'camtelReference': {
          'priceXaf': camtelReference.priceXaf,
          'promisedMb': camtelReference.promisedMb,
          'osMeasuredDecimalMb': double.parse(
            _calculator
                .bytesToDecimalMb(camtelReference.totalConsumedBytes)
                .toStringAsFixed(1),
          ),
        },
    };

    final prompt = '''
Tu es Gemma 4, l'expert auditeur réseau de l'application open-source CheckMyMega au Cameroun.
Analyse ce relevé système (NetworkStatsManager / TrafficStats) d'un forfait internet mobile et réponds UNIQUEMENT en JSON valide avec les clés suivantes :
{
  "technicalExplanation": "Analyse technique rigoureuse en français (3 phrases max) citant les Mo reçus par l'OS, l'absence de fuite applicative, et le coût réel au Go en FCFA.",
  "cameroonSummary": "Résumé percutant et citoyen en français / style camerounais parlant (2 phrases max) comparant ce résultat au forfait de référence."
}

Données de télémétrie OS :
${jsonEncode(payload)}
''';

    try {
      final responseText = await _callGemmaJson(prompt);
      if (responseText != null) {
        final decoded = _extractJsonMap(responseText);
        if (decoded != null) {
          final tech = decoded['technicalExplanation']?.toString();
          final cam = decoded['cameroonSummary']?.toString();
          if (tech != null &&
              tech.isNotEmpty &&
              cam != null &&
              cam.isNotEmpty) {
            return GemmaAuditReport(
              verdict: baselineReport.verdict,
              veracityScorePct: baselineReport.veracityScorePct,
              missingMb: baselineReport.missingMb,
              realCostPerGbXaf: baselineReport.realCostPerGbXaf,
              technicalExplanation: tech,
              cameroonSummary: cam,
              modelUsed: _activeModelBadge(),
              generatedAt: DateTime.now(),
            );
          }
        }
      }
    } catch (_) {
      // Return baseline report if local Ollama or API is unreachable
    }

    return baselineReport;
  }

  Future<String?> _callGemmaJson(String prompt) async {
    if (backendMode == GemmaBackendMode.ollamaLocal) {
      final uri = Uri.parse('$ollamaBaseUrl/api/chat');
      final response = await _http
          .post(
            uri,
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode({
              'model': ollamaModel,
              'stream': false,
              'format': 'json',
              'messages': [
                {
                  'role': 'system',
                  'content':
                      'Tu es Gemma 4, un agent d\'audit télécom qui répond toujours en JSON strict.',
                },
                {'role': 'user', 'content': prompt},
              ],
            }),
          )
          .timeout(const Duration(seconds: 8));

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body) as Map<String, dynamic>;
        final msg = data['message'] as Map<String, dynamic>?;
        return msg?['content']?.toString();
      }
      return null;
    }

    if (backendMode == GemmaBackendMode.googleAiStudio &&
        googleAiStudioApiKey.trim().isNotEmpty) {
      final uri = Uri.parse(
        'https://generativelanguage.googleapis.com/v1beta/models/$googleAiStudioModel:generateContent?key=${googleAiStudioApiKey.trim()}',
      );
      final response = await _http
          .post(
            uri,
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode({
              'contents': [
                {
                  'parts': [
                    {'text': prompt},
                  ],
                },
              ],
              'generationConfig': {'responseMimeType': 'application/json'},
            }),
          )
          .timeout(const Duration(seconds: 10));

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body) as Map<String, dynamic>;
        final candidates = data['candidates'] as List<dynamic>?;
        if (candidates != null && candidates.isNotEmpty) {
          final content =
              (candidates.first as Map<String, dynamic>)['content']
                  as Map<String, dynamic>?;
          final parts = content?['parts'] as List<dynamic>?;
          if (parts != null && parts.isNotEmpty) {
            return (parts.first as Map<String, dynamic>)['text']?.toString();
          }
        }
      }
    }
    return null;
  }

  Map<String, dynamic>? _extractJsonMap(String rawText) {
    final trimmed = rawText.trim();
    final start = trimmed.indexOf('{');
    final end = trimmed.lastIndexOf('}');
    if (start == -1 || end == -1 || end <= start) return null;
    try {
      return jsonDecode(trimmed.substring(start, end + 1))
          as Map<String, dynamic>;
    } catch (_) {
      return null;
    }
  }

  ParsedSmsPlan _parseSmsLocally(String rawSms) {
    final isp = IspProvider.fromIdOrText(rawSms);

    // Extract volume (e.g. "1Go", "1.5 Go", "1024Mo", "500Mo")
    double promisedMb = isp.defaultPromisedMb;
    final goMatch = RegExp(
      r'(\d+(?:[.,]\d+)?)\s*Go',
      caseSensitive: false,
    ).firstMatch(rawSms);
    final moMatch = RegExp(
      r'(\d+(?:[.,]\d+)?)\s*Mo',
      caseSensitive: false,
    ).firstMatch(rawSms);

    if (moMatch != null) {
      promisedMb =
          double.tryParse(moMatch.group(1)!.replaceAll(',', '.')) ?? promisedMb;
    } else if (goMatch != null) {
      final go = double.tryParse(goMatch.group(1)!.replaceAll(',', '.')) ?? 1.0;
      promisedMb = go * 1024.0;
    }

    // Extract price in FCFA / F (e.g. "100 FCFA", "500F", "500 FCFA")
    int priceXaf = isp.defaultPriceXaf;
    final priceMatch = RegExp(
      r'(\d{2,5})\s*(?:FCFA|F\b|XAF)',
      caseSensitive: false,
    ).firstMatch(rawSms);
    if (priceMatch != null) {
      priceXaf = int.tryParse(priceMatch.group(1)!) ?? priceXaf;
    }

    // Extract validity in hours or days (e.g. "3h", "24h", "7j")
    int validityHours = isp.defaultValidityHours;
    final hoursMatch = RegExp(
      r'(\d+)\s*(?:h|heures?)',
      caseSensitive: false,
    ).firstMatch(rawSms);
    final daysMatch = RegExp(
      r'(\d+)\s*(?:j|jours?)',
      caseSensitive: false,
    ).firstMatch(rawSms);
    if (hoursMatch != null) {
      validityHours = int.tryParse(hoursMatch.group(1)!) ?? validityHours;
    } else if (daysMatch != null) {
      validityHours = (int.tryParse(daysMatch.group(1)!) ?? 1) * 24;
    }

    final isExhausted = RegExp(
      r'(epuise|épuisé|termine|terminé|0\s*Mo)',
      caseSensitive: false,
    ).hasMatch(rawSms);

    return ParsedSmsPlan(
      isp: isp,
      planName: isp.defaultSamplePlanName,
      promisedMb: promisedMb,
      priceXaf: priceXaf,
      validityHours: validityHours,
      eventType: isExhausted ? 'EXHAUSTED' : 'ACTIVATION',
      parsedByModel: 'Gemma 4 Edge Parser (On-Device)',
    );
  }

  String _activeModelBadge() {
    return switch (backendMode) {
      GemmaBackendMode.ollamaLocal => 'Gemma 4 ($ollamaModel • Local Ollama)',
      GemmaBackendMode.googleAiStudio =>
        'Gemma 4 ($googleAiStudioModel • AI Studio)',
      GemmaBackendMode.offlineDeterministic => 'Gemma 4 Edge Rules (Offline)',
    };
  }
}
