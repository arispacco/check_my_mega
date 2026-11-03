import 'dart:async';
import 'dart:math' as math;
import 'package:flutter/foundation.dart';

import '../../../../data/repositories/audit_repository.dart';
import '../../../../data/services/gemma_service.dart';
import '../../../../domain/models/data_plan_session.dart';
import '../../../../domain/models/isp_provider.dart';
import '../../../../domain/models/signal_radar_spot.dart';
import '../../../../domain/use_cases/mega_calculator.dart';

/// ViewModel managing the active OS network audit session, real SMS inbox parsing,
/// live 4G/5G radio + magnetic compass indoor radar, and Gemma 4 analysis.
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
  String _detectedSimCode = '';
  IspProvider _detectedSimIsp = IspProvider.camtel;
  bool _isBusyGemma = false;
  bool _isRunningSpeedTest = false;
  double? _lastActiveSpeedMbps;
  int? _lastActivePingMs;
  String? _statusBannerMessage;

  RadioCompassSnapshot? _radioSnapshot;
  final List<IndoorSignalSpot> _indoorSpots = [];

  /// Isolated 60 FPS ValueNotifier for the hardware compass azimuth so the
  /// compass dial repaints on the GPU without rebuilding the entire widget tree.
  final ValueNotifier<double> compassAzimuthNotifier = ValueNotifier<double>(
    0.0,
  );
  int _liveStepCount = 0;
  double _livePosX = 0.0;
  double _livePosY = 0.0;

  Timer? _pollingTimer;
  Timer? _compassTimer;

  DataPlanSession? get activeSession => _activeSession;
  DataPlanSession? get selectedReportSession =>
      _selectedReportSession ?? _activeSession ?? history.firstOrNull;
  List<DataPlanSession> get history => _repository.history;
  List<DataPlanSession> get userSessions => _repository.userSessions;
  List<OperatorSmsMessage> get smsInboxMessages =>
      _repository.smsInboxMessages;
  bool get showReferenceBenchmarks => _repository.showReferenceBenchmarks;
  MegaCalculator get calculator => _repository.calculator;
  GemmaService get gemmaService => _repository.gemmaService;

  bool get hasOsPermission => _hasOsPermission;
  bool get isNativeOs => _isNativeOs;
  String get detectedCarrierName => _detectedCarrierName;
  String get detectedSimCode => _detectedSimCode;
  IspProvider get detectedSimIsp => _detectedSimIsp;
  bool get isBusyGemma => _isBusyGemma;
  bool get isRunningSpeedTest => _isRunningSpeedTest;
  double? get lastActiveSpeedMbps => _lastActiveSpeedMbps;
  int? get lastActivePingMs => _lastActivePingMs;
  String? get statusBannerMessage => _statusBannerMessage;

  RadioCompassSnapshot? get radioSnapshot => _radioSnapshot;
  List<IndoorSignalSpot> get indoorSpots => List.unmodifiable(_indoorSpots);

  /// Returns the recorded spot in the house with the highest signal & throughput score.
  IndoorSignalSpot? get bestIndoorSpot {
    if (_indoorSpots.isEmpty) return null;
    final sorted = [..._indoorSpots]
      ..sort((a, b) => b.compositeSpotScore.compareTo(a.compositeSpotScore));
    return sorted.first;
  }

  /// Computes the magnetic compass bearing (0–360°) pointing from the user's current
  /// position toward [bestIndoorSpot], or the azimuth recorded at [bestIndoorSpot].
  double? get bearingToBestSpotDeg {
    final best = bestIndoorSpot;
    final current = _radioSnapshot;
    if (best == null || current == null) return null;
    final dx = best.posX - current.posX;
    final dy = best.posY - current.posY;
    final distSq = dx * dx + dy * dy;
    if (distSq < 0.25) {
      // User is already standing at the best spot; point toward the best antenna orientation
      return best.azimuthDeg;
    }
    final angleRad = math.atan2(dx, dy);
    return (angleRad * 180.0 / math.pi + 360.0) % 360.0;
  }

  /// Distance in meters from current position to [bestIndoorSpot].
  double get distanceToBestSpotMeters {
    final best = bestIndoorSpot;
    final current = _radioSnapshot;
    if (best == null || current == null) return 0.0;
    final dx = best.posX - current.posX;
    final dy = best.posY - current.posY;
    return math.sqrt(dx * dx + dy * dy);
  }

  Future<void> _initialize() async {
    await _repository.networkService.requestHardwarePermissions();
    _hasOsPermission = await _repository.networkService
        .hasUsageStatsPermission();
    await _repository.gemmaService.checkOllamaConnection();

    // Listen for live incoming SMS messages from CAMTEL / Blue / MTN / Orange
    _repository.networkService.setIncomingSmsListener(_handleLiveIncomingSms);
    // Listen for 60 FPS hardware compass & step frames from Kotlin SensorManager
    _repository.networkService.setCompassSensorFrameListener(
      _handleCompassSensorFrame,
    );

    final now = DateTime.now();
    final snap = await _repository.networkService.getMobileCountersSnapshot(
      startTime: now.subtract(const Duration(hours: 3)),
      endTime: now,
    );
    _isNativeOs = snap.isFromNativeOs;
    _detectedCarrierName = snap.operatorName;
    _detectedSimCode = snap.simOperatorCode;
    _detectedSimIsp = snap.detectedIsp;

    _radioSnapshot = await _repository.networkService
        .getRadioAndCompassSnapshot();
    if (_radioSnapshot != null) {
      compassAzimuthNotifier.value = _radioSnapshot!.azimuthDeg;
      _liveStepCount = _radioSnapshot!.stepCount;
      _livePosX = _radioSnapshot!.posX;
      _livePosY = _radioSnapshot!.posY;
    }

    // 1. Try reconstructing sessions directly from the phone's real SMS inbox!
    final smsSession = await _repository.syncSessionsFromSmsInbox();
    if (smsSession != null) {
      _activeSession = smsSession;
      _selectedReportSession = smsSession;
      final consumedMb = calculator.bytesToDecimalMb(
        smsSession.totalConsumedBytes,
      );
      final scorePct = calculator.computeVeracityScorePct(
        totalConsumedBytes: smsSession.totalConsumedBytes,
        promisedMb: smsSession.promisedMb,
      );
      _statusBannerMessage =
          '📩 Boîte SMS analysée : ${smsSession.planName} (${smsSession.priceXaf} FCFA) → '
          '${consumedMb.toStringAsFixed(1)} Mo reçus / ${smsSession.promisedMb.toStringAsFixed(0)} Mo promis (${scorePct.toStringAsFixed(1)}%).';
      _startPeriodicOsPolling();
      _startCompassPolling();
      notifyListeners();
      return;
    }

    // 2. Fallback if SMS inbox is empty or on desktop preview
    await startAuditSession(
      isp: _detectedSimIsp,
      planName: _detectedSimIsp.defaultSamplePlanName,
      promisedMb: _detectedSimIsp.defaultPromisedMb,
      priceXaf: _detectedSimIsp.defaultPriceXaf,
      validityHours: _detectedSimIsp.defaultValidityHours,
      includePastValidityWindow: false,
    );

    if (!_isNativeOs) {
      await injectCalibratedWorkload(mbAmount: 280);
    }
    _startCompassPolling();
  }

  Future<void> _handleLiveIncomingSms(OperatorSmsMessage msg) async {
    final synced = await _repository.syncSessionsFromSmsInbox();
    if (synced != null) {
      _activeSession = synced;
      _selectedReportSession = synced;
    }
    if (msg.isExhaustion) {
      _statusBannerMessage =
          '🚨 SMS Coupure ${msg.isp.shortName} intercepté en direct : "${msg.body}". Certificat d\'audit mis à jour !';
    } else if (msg.isActivation) {
      _statusBannerMessage =
          '📩 Nouveau SMS ${msg.isp.shortName} intercepté en direct : ${msg.promisedMb.toStringAsFixed(0)} Mo (${msg.priceXaf} FCFA) !';
    }
    notifyListeners();
  }

  /// Re-scans the Android SMS inbox (`content://sms/inbox`) and updates bundle audits.
  Future<void> syncFromSmsInbox() async {
    await _repository.networkService.requestHardwarePermissions();
    final smsSession = await _repository.syncSessionsFromSmsInbox();
    if (smsSession != null) {
      _activeSession = smsSession;
      _selectedReportSession = smsSession;
      final consumedMb = calculator.bytesToDecimalMb(
        smsSession.totalConsumedBytes,
      );
      _statusBannerMessage =
          '📩 Synchronisé avec vos vrais SMS : ${smsSession.planName} → ${consumedMb.toStringAsFixed(1)} Mo / ${smsSession.promisedMb.toStringAsFixed(0)} Mo.';
    } else {
      _statusBannerMessage =
          'Aucun SMS de forfait récent (< 24h) trouvé dans la boîte de réception.';
    }
    notifyListeners();
  }

  void _handleCompassSensorFrame(
    double azimuthDeg,
    int stepCount,
    double posX,
    double posY,
  ) {
    compassAzimuthNotifier.value = azimuthDeg;
    final stepChanged = stepCount != _liveStepCount;
    _liveStepCount = stepCount;
    _livePosX = posX;
    _livePosY = posY;
    final prev = _radioSnapshot;
    if (prev != null) {
      _radioSnapshot = RadioCompassSnapshot(
        operatorName: prev.operatorName,
        simOperatorCode: prev.simOperatorCode,
        networkType: prev.networkType,
        bandLabel: prev.bandLabel,
        earfcn: prev.earfcn,
        pci: prev.pci,
        rsrpDbm: prev.rsrpDbm,
        rsrqDb: prev.rsrqDb,
        sinrDb: prev.sinrDb,
        rssiDbm: prev.rssiDbm,
        cqi: prev.cqi,
        timingAdvance: prev.timingAdvance,
        estimatedDistanceMeters: prev.estimatedDistanceMeters,
        linkDownKbps: prev.linkDownKbps,
        linkUpKbps: prev.linkUpKbps,
        passiveRxMbps: prev.passiveRxMbps,
        passiveTxMbps: prev.passiveTxMbps,
        azimuthDeg: azimuthDeg,
        stepCount: stepCount,
        posX: posX,
        posY: posY,
        timestamp: prev.timestamp,
      );
    }
    if (stepChanged) {
      notifyListeners();
    }
  }

  void _startCompassPolling() {
    _compassTimer?.cancel();
    _compassTimer = Timer.periodic(const Duration(milliseconds: 2200), (
      _,
    ) async {
      final snap = await _repository.networkService
          .getRadioAndCompassSnapshot();
      _radioSnapshot = RadioCompassSnapshot(
        operatorName: snap.operatorName,
        simOperatorCode: snap.simOperatorCode,
        networkType: snap.networkType,
        bandLabel: snap.bandLabel,
        earfcn: snap.earfcn,
        pci: snap.pci,
        rsrpDbm: snap.rsrpDbm,
        rsrqDb: snap.rsrqDb,
        sinrDb: snap.sinrDb,
        rssiDbm: snap.rssiDbm,
        cqi: snap.cqi,
        timingAdvance: snap.timingAdvance,
        estimatedDistanceMeters: snap.estimatedDistanceMeters,
        linkDownKbps: snap.linkDownKbps,
        linkUpKbps: snap.linkUpKbps,
        passiveRxMbps: snap.passiveRxMbps,
        passiveTxMbps: snap.passiveTxMbps,
        azimuthDeg: compassAzimuthNotifier.value,
        stepCount: _liveStepCount > 0 ? _liveStepCount : snap.stepCount,
        posX: _livePosX != 0.0 ? _livePosX : snap.posX,
        posY: _livePosY != 0.0 ? _livePosY : snap.posY,
        timestamp: snap.timestamp,
      );
      if (!_isNativeOs) {
        compassAzimuthNotifier.value = snap.azimuthDeg;
      }
      notifyListeners();
    });
  }

  /// Runs a real active speed & latency test and optionally records an indoor spot.
  Future<void> runSpeedTestAndRecordSpot({String? spotLabel}) async {
    if (_isRunningSpeedTest) return;
    _isRunningSpeedTest = true;
    notifyListeners();

    try {
      final radioBefore = await _repository.networkService
          .getRadioAndCompassSnapshot();
      final result = await _repository.networkService.runActiveSpeedProbe();
      _lastActiveSpeedMbps = result.speedMbps;
      _lastActivePingMs = result.pingMs;

      final label = (spotLabel != null && spotLabel.trim().isNotEmpty)
          ? spotLabel.trim()
          : 'Point #${_indoorSpots.length + 1} (${radioBefore.cardinalDirection})';

      final spot = IndoorSignalSpot(
        id: 'SPOT-${DateTime.now().millisecondsSinceEpoch}',
        roomLabel: label,
        rsrpDbm: radioBefore.rsrpDbm,
        rsrqDb: radioBefore.rsrqDb,
        sinrDb: radioBefore.sinrDb,
        measuredSpeedMbps: result.speedMbps,
        pingMs: result.pingMs,
        azimuthDeg: radioBefore.azimuthDeg,
        posX: radioBefore.posX,
        posY: radioBefore.posY,
        stepMark: radioBefore.stepCount,
        bandLabel: radioBefore.bandLabel,
        recordedAt: DateTime.now(),
      );

      _indoorSpots.insert(0, spot);
      _statusBannerMessage =
          '📍 Point "$label" mesuré : ${result.speedMbps.toStringAsFixed(1)} Mbps • Ping ${result.pingMs} ms • Signal ${radioBefore.rsrpDbm} dBm.';
    } finally {
      _isRunningSpeedTest = false;
      notifyListeners();
    }
  }

  /// Records the current indoor spot immediately using live radio + passive/estimated speed
  /// (0 bytes consumed) while walking through the house.
  Future<void> recordInstantSpot(String roomLabel) async {
    final radio =
        _radioSnapshot ??
        await _repository.networkService.getRadioAndCompassSnapshot();
    final estimatedMbps = radio.passiveRxMbps > 0.2
        ? radio.passiveRxMbps
        : _estimateThroughputFromRadio(radio);

    final spot = IndoorSignalSpot(
      id: 'SPOT-${DateTime.now().millisecondsSinceEpoch}',
      roomLabel: roomLabel,
      rsrpDbm: radio.rsrpDbm,
      rsrqDb: radio.rsrqDb,
      sinrDb: radio.sinrDb,
      measuredSpeedMbps: estimatedMbps,
      pingMs: _lastActivePingMs ?? (radio.sinrDb >= 8 ? 42 : 78),
      azimuthDeg: radio.azimuthDeg,
      posX: radio.posX,
      posY: radio.posY,
      stepMark: radio.stepCount,
      bandLabel: radio.bandLabel,
      recordedAt: DateTime.now(),
    );

    _indoorSpots.insert(0, spot);
    _statusBannerMessage =
        '🧭 Coin "$roomLabel" enregistré (${radio.rsrpDbm} dBm • SINR ${radio.sinrDb} dB • ~${estimatedMbps.toStringAsFixed(1)} Mbps).';
    notifyListeners();
  }

  /// Clears recorded indoor spots and resets the walk origin.
  Future<void> clearIndoorRadarSpots() async {
    _indoorSpots.clear();
    await _repository.networkService.resetWalkRadarOrigin();
    _radioSnapshot = await _repository.networkService
        .getRadioAndCompassSnapshot();
    notifyListeners();
  }

  double _estimateThroughputFromRadio(RadioCompassSnapshot r) {
    // Physical LTE Shannon-inspired estimate from RSRP, SINR, and CQI on 20 MHz channel
    final sinrFactor = ((r.sinrDb + 5.0) / 25.0).clamp(0.08, 1.0);
    final rsrpFactor = ((r.rsrpDbm + 120.0) / 45.0).clamp(0.08, 1.0);
    final cqiFactor = (r.cqi / 15.0).clamp(0.15, 1.0);
    return (45.0 * sinrFactor * 0.5 + 35.0 * rsrpFactor * 0.3 + 30.0 * cqiFactor * 0.2)
        .clamp(0.5, 65.0);
  }

  /// Generates a comprehensive AI & hardware diagnosis of Camtel coverage in the user's
  /// home/area and practical advice to maximize speed.
  String buildCamtelCoverageAndTipsReport() {
    final r = _radioSnapshot;
    if (r == null) {
      return 'Lecture des capteurs radio 4G en cours...';
    }
    final best = bestIndoorSpot;
    final distText = r.estimatedDistanceMeters > 0
        ? 'à environ ${r.estimatedDistanceMeters} mètres de l\'antenne relais (Timing Advance TA=${r.timingAdvance})'
        : 'connecté à la cellule PCI #${r.pci}';

    final coverageVerdict = switch (r.rsrpDbm) {
      >= -90 =>
        '✅ Excellente couverture Camtel dans votre zone ($distText sur ${r.bandLabel}). '
            'Vous pouvez utiliser Camtel comme connexion principale à domicile sans difficulté.',
      >= -105 =>
        '🟡 Couverture Camtel présente mais atténuée par les murs ($distText sur ${r.bandLabel}, RSRP ${r.rsrpDbm} dBm, SINR ${r.sinrDb} dB). '
            'Camtel réduira vos dépenses internet par 4 à 5 fois par rapport à MTN/Orange, à condition de placer le téléphone au bon endroit.',
      _ =>
        '🔴 Signal Camtel intérieur faible (${r.rsrpDbm} dBm). Votre maison subit une forte atténuation sur ${r.bandLabel}. '
            'Utilisez impérativement le point culminant près d\'une fenêtre identifié par la boussole ou gardez MTN/Orange en secours pour les appels urgents.',
    };

    final bestSpotAdvice = best != null
        ? '\n🏆 Meilleur coin détecté chez vous : « ${best.roomLabel} » '
              '(${best.rsrpDbm} dBm, SINR ${best.sinrDb} dB, débit ${best.measuredSpeedMbps.toStringAsFixed(1)} Mbps, cap ${best.azimuthDeg.toStringAsFixed(0)}°). '
              'Posez votre téléphone à cet endroit précis pendant vos gros téléchargements ou en Partage de connexion (Hotspot Wi-Fi) !'
        : '\n🧭 Astuce Boussole : Marchez dans 3 ou 4 coins de votre maison (Salon, Fenêtre, Chambre, Véranda) et appuyez sur « Enregistrer ce coin » pour que la boussole vous guide vers le point le plus rapide.';

    return '$coverageVerdict$bestSpotAdvice';
  }

  /// Checks or refreshes Android `PACKAGE_USAGE_STATS` permission state.
  Future<void> checkAndOpenOsPermissions() async {
    await _repository.networkService.openUsageStatsSettings();
    await refreshPermissionState();
  }

  /// Opens Android App Info settings so users on Android 13+ can enable
  /// "Allow restricted settings" ("Autoriser les paramètres restreints").
  Future<void> openAppDetailsForRestrictedSettings() async {
    await _repository.networkService.openAppDetailsSettings();
  }

  /// Re-checks `PACKAGE_USAGE_STATS` permission and refreshes the active session.
  Future<void> refreshPermissionState() async {
    _hasOsPermission = await _repository.networkService
        .hasUsageStatsPermission();
    await syncFromSmsInbox();
    notifyListeners();
  }

  /// Toggles whether national study reference benchmarks (MTN/Orange/Camtel) are shown
  /// alongside the user's real recorded SIM sessions.
  void toggleReferenceBenchmarks(bool value) {
    _repository.showReferenceBenchmarks = value;
    notifyListeners();
  }

  /// Starts a new real-time OS network audit session for the given ISP plan.
  Future<void> startAuditSession({
    required IspProvider isp,
    required String planName,
    required double promisedMb,
    required int priceXaf,
    required int validityHours,
    bool includePastValidityWindow = true,
  }) async {
    _pollingTimer?.cancel();
    _activeSession = await _repository.startNewSession(
      isp: isp,
      planName: planName,
      promisedMb: promisedMb,
      priceXaf: priceXaf,
      validityHours: validityHours,
      includePastValidityWindow: includePastValidityWindow,
      workloadTag: includePastValidityWindow
          ? 'SIM Active ($_detectedCarrierName • Fenêtre ${validityHours}h)'
          : 'Nouveau Compteur OS (Depuis 0 Mo)',
    );
    _selectedReportSession = _activeSession;
    final consumedMb = calculator.bytesToDecimalMb(
      _activeSession!.totalConsumedBytes,
    );
    _statusBannerMessage = includePastValidityWindow
        ? 'SIM détectée : $_detectedCarrierName. Consommation réelle mesurée sur les $validityHours dernières heures : ${consumedMb.toStringAsFixed(1)} Mo.'
        : 'Nouveau compteur démarré à 0 Mo pour ${isp.displayName} ($planName • $priceXaf FCFA).';
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

  /// Finalizes the active session and generates the Gemma 4 Audit Certificate.
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
          'Forfait clôturé ! Rapport d\'audit Gemma 4 généré (Score de vérité : ${finalized.report?.veracityScorePct.toStringAsFixed(1)}%).';
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
    unawaited(
      gemmaService.checkOllamaConnection().then((_) => notifyListeners()),
    );
  }

  @override
  void dispose() {
    _pollingTimer?.cancel();
    _compassTimer?.cancel();
    compassAzimuthNotifier.dispose();
    super.dispose();
  }
}
