import 'dart:async';
import 'package:flutter/foundation.dart';

import '../../../../data/repositories/audit_repository.dart';
import '../../../../data/services/gemma_service.dart';
import '../../../../domain/models/data_plan_session.dart';
import '../../../../domain/models/isp_provider.dart';
import '../../../../domain/use_cases/mega_calculator.dart';

/// ViewModel managing the active OS network audit session, Gemma 4 interactions,
/// and multi-ISP comparisons.
class AuditViewModel extends ChangeNotifier {
  AuditViewModel({required AuditRepository repository})
    : _repository = repository {
    _initialize();
  }

  final AuditRepository _repository;

  DataPlanSession? _activeSession;
  DataPlanSession? _selectedReportSession;
  bool _hasOsPermission = true;
  bool _isNativeOs = false;
  String _detectedCarrierName = 'Sonde OS Prête';
  bool _isBusyGemma = false;
  String? _statusBannerMessage;
  Timer? _pollingTimer;

  DataPlanSession? get activeSession => _activeSession;
  DataPlanSession? get selectedReportSession =>
      _selectedReportSession ?? _activeSession ?? history.firstOrNull;
  List<DataPlanSession> get history => _repository.history;
  MegaCalculator get calculator => _repository.calculator;
  GemmaService get gemmaService => _repository.gemmaService;

  bool get hasOsPermission => _hasOsPermission;
  bool get isNativeOs => _isNativeOs;
  String get detectedCarrierName => _detectedCarrierName;
  bool get isBusyGemma => _isBusyGemma;
  String? get statusBannerMessage => _statusBannerMessage;

  Future<void> _initialize() async {
    _hasOsPermission = await _repository.networkService
        .hasUsageStatsPermission();
    final snap = await _repository.networkService.getMobileCountersSnapshot(
      startTime: DateTime.now(),
    );
    _isNativeOs = snap.isFromNativeOs;
    _detectedCarrierName = snap.operatorName;

    // Start a default active session on Camtel 1Go (100 FCFA / 3h) so the dashboard
    // is immediately interactive on launch.
    await startAuditSession(
      isp: IspProvider.camtel,
      planName: IspProvider.camtel.defaultSamplePlanName,
      promisedMb: 1024,
      priceXaf: 100,
      validityHours: 3,
    );
    // Inject an initial 280 MB of real/simulated work so the gauge is non-zero out of the box
    await injectCalibratedWorkload(mbAmount: 280);
  }

  /// Checks or refreshes Android `PACKAGE_USAGE_STATS` permission state.
  Future<void> checkAndOpenOsPermissions() async {
    await _repository.networkService.openUsageStatsSettings();
    _hasOsPermission = await _repository.networkService
        .hasUsageStatsPermission();
    notifyListeners();
  }

  /// Starts a new real-time OS network audit session for the given ISP plan.
  Future<void> startAuditSession({
    required IspProvider isp,
    required String planName,
    required double promisedMb,
    required int priceXaf,
    required int validityHours,
  }) async {
    _pollingTimer?.cancel();
    _activeSession = await _repository.startNewSession(
      isp: isp,
      planName: planName,
      promisedMb: promisedMb,
      priceXaf: priceXaf,
      validityHours: validityHours,
    );
    _selectedReportSession = _activeSession;
    _statusBannerMessage =
        'Audit démarré pour ${isp.displayName} ($planName • $priceXaf FCFA). Compteur OS initialisé à 0 Mo.';
    _startPeriodicOsPolling();
    notifyListeners();
  }

  void _startPeriodicOsPolling() {
    _pollingTimer?.cancel();
    _pollingTimer = Timer.periodic(const Duration(seconds: 3), (_) async {
      final current = _activeSession;
      if (current == null || current.status != AuditSessionStatus.active) {
        return;
      }
      _activeSession = await _repository.refreshSessionFromOs(current);
      notifyListeners();
    });
  }

  /// Parses an operator SMS or USSD confirmation with Gemma 4 and optionally starts the session.
  Future<ParsedSmsPlan> parseSmsWithGemma(
    String rawSms, {
    bool autoStartSession = true,
  }) async {
    _isBusyGemma = true;
    notifyListeners();
    try {
      final parsed = await _repository.gemmaService.parseOperatorSms(rawSms);
      if (autoStartSession) {
        await startAuditSession(
          isp: parsed.isp,
          planName: parsed.planName,
          promisedMb: parsed.promisedMb,
          priceXaf: parsed.priceXaf,
          validityHours: parsed.validityHours,
        );
        _statusBannerMessage =
            'SMS analysé par ${parsed.parsedByModel} : ${parsed.isp.shortName} ${parsed.promisedMb.toStringAsFixed(0)} Mo (${parsed.priceXaf} FCFA).';
      }
      return parsed;
    } finally {
      _isBusyGemma = false;
      notifyListeners();
    }
  }

  /// Simulates or records a calibrated workload block ("Même Travail") on the OS counter.
  Future<void> injectCalibratedWorkload({required double mbAmount}) async {
    final current = _activeSession;
    if (current == null) return;

    final totalBytesDelta = (mbAmount * MegaCalculator.bytesPerDecimalMb)
        .round();
    final rxDelta = (totalBytesDelta * 0.90).round();
    final txDelta = totalBytesDelta - rxDelta;

    _repository.networkService.injectWorkloadBytes(
      rxBytesDelta: rxDelta,
      txBytesDelta: txDelta,
    );
    _activeSession = await _repository.refreshSessionFromOs(current);
    _selectedReportSession = _activeSession;
    notifyListeners();
  }

  /// Simulates the exact scenario described by the user:
  /// Running the "Même Travail" workload until the ISP cuts off the 1 GB bundle,
  /// then generating the Gemma 4 Audit Certificate.
  Future<void> triggerIspExhaustionAndAudit() async {
    final current = _activeSession;
    if (current == null) return;

    _isBusyGemma = true;
    notifyListeners();

    try {
      _pollingTimer?.cancel();
      final finalized = await _repository.finalizeSessionWithGemma(
        current,
        isExhaustedByIsp: true,
      );
      _activeSession = finalized;
      _selectedReportSession = finalized;
      _statusBannerMessage =
          'Forfait déclaré épuisé ! Rapport d\'audit Gemma 4 généré (Score de vérité : ${finalized.report?.veracityScorePct.toStringAsFixed(1)}%).';
    } finally {
      _isBusyGemma = false;
      notifyListeners();
    }
  }

  /// Selects a historical or benchmark session to inspect its Gemma 4 audit certificate.
  void selectSessionForReport(DataPlanSession session) {
    _selectedReportSession = session;
    notifyListeners();
  }

  /// Updates Gemma 4 backend configuration (Ollama Local vs Google AI Studio vs Offline).
  void updateGemmaConfig({
    GemmaBackendMode? mode,
    String? ollamaUrl,
    String? ollamaModelName,
    String? apiKey,
  }) {
    if (mode != null) gemmaService.backendMode = mode;
    if (ollamaUrl != null && ollamaUrl.trim().isNotEmpty) {
      gemmaService.ollamaBaseUrl = ollamaUrl.trim();
    }
    if (ollamaModelName != null && ollamaModelName.trim().isNotEmpty) {
      gemmaService.ollamaModel = ollamaModelName.trim();
    }
    if (apiKey != null) {
      gemmaService.googleAiStudioApiKey = apiKey.trim();
    }
    _statusBannerMessage =
        'Configuration IA mise à jour : ${gemmaService.backendMode.label}';
    notifyListeners();
  }

  @override
  void dispose() {
    _pollingTimer?.cancel();
    super.dispose();
  }
}
