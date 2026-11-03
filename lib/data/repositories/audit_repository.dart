import '../../domain/models/data_plan_session.dart';
import '../../domain/models/gemma_audit_report.dart';
import '../../domain/models/isp_provider.dart';
import '../../domain/models/signal_radar_spot.dart';
import '../../domain/use_cases/mega_calculator.dart';
import '../services/gemma_service.dart';
import '../services/native_network_service.dart';

/// Single source of truth coordinating OS network metering, Gemma 4 analysis,
/// and Cameroon ISP comparison history.
class AuditRepository {
  AuditRepository({
    required NativeNetworkService networkService,
    required GemmaService gemmaService,
    MegaCalculator? calculator,
  }) : _networkService = networkService,
       _gemmaService = gemmaService,
       _calculator = calculator ?? const MegaCalculator() {
    _seedCameroonBenchmarkHistory();
  }

  final NativeNetworkService _networkService;
  final GemmaService _gemmaService;
  final MegaCalculator _calculator;

  final List<DataPlanSession> _userSessions = [];
  final List<DataPlanSession> _referenceBenchmarks = [];
  final List<OperatorSmsMessage> _smsInboxMessages = [];
  bool showReferenceBenchmarks = false;

  /// Returns the user's real recorded sessions on this phone, plus optional
  /// community reference benchmarks only if [showReferenceBenchmarks] is enabled.
  List<DataPlanSession> get history => List.unmodifiable([
    ..._userSessions,
    if (showReferenceBenchmarks) ..._referenceBenchmarks,
  ]);

  List<DataPlanSession> get userSessions => List.unmodifiable(_userSessions);
  List<OperatorSmsMessage> get smsInboxMessages =>
      List.unmodifiable(_smsInboxMessages);
  List<DataPlanSession> get referenceBenchmarks =>
      List.unmodifiable(_referenceBenchmarks);

  NativeNetworkService get networkService => _networkService;
  GemmaService get gemmaService => _gemmaService;
  MegaCalculator get calculator => _calculator;

  /// Seeds the 3 reference Cameroon benchmark sessions in [_referenceBenchmarks]
  /// (kept separate from [_userSessions] so the app never invents sessions on the user's SIM).
  void _seedCameroonBenchmarkHistory() {
    final now = DateTime.now();

    // 1. Camtel 100 FCFA 1Go (3h) -> 982 MB actually received by OS before exhaustion
    const camtelBytes = 982400000;
    final camtelSession = DataPlanSession(
      id: 'BENCH-CAMTEL-100F',
      isp: IspProvider.camtel,
      planName: '[Réf. Étude] Blue Horaire 1Go (3h)',
      promisedMb: 1024,
      priceXaf: 100,
      validityHours: 3,
      startedAt: now.subtract(const Duration(hours: 6)),
      endedAt: now.subtract(const Duration(hours: 3, minutes: 12)),
      baselineRxBytes: 100000000,
      baselineTxBytes: 10000000,
      currentRxBytes: 100000000 + 894000000,
      currentTxBytes: 10000000 + 88400000,
      appUsages: _networkService.buildStandardWorkloadBreakdown(camtelBytes),
      status: AuditSessionStatus.exhausted,
      workloadTag: 'Référence Étude Comparative (Protocole Même Travail)',
    );

    // 2. MTN Cameroon 500 FCFA 1Go -> finished prematurely at 638 MB measured by OS
    const mtnBytes = 638200000;
    final mtnSession = DataPlanSession(
      id: 'BENCH-MTN-500F',
      isp: IspProvider.mtnCm,
      planName: '[Réf. Étude] Mobile Surf / Wanda 1Go',
      promisedMb: 1024,
      priceXaf: 500,
      validityHours: 24,
      startedAt: now.subtract(const Duration(hours: 12)),
      endedAt: now.subtract(const Duration(hours: 10, minutes: 15)),
      baselineRxBytes: 200000000,
      baselineTxBytes: 20000000,
      currentRxBytes: 200000000 + 580000000,
      currentTxBytes: 20000000 + 58200000,
      appUsages: _networkService.buildStandardWorkloadBreakdown(mtnBytes),
      status: AuditSessionStatus.exhausted,
      workloadTag: 'Référence Étude Comparative (Protocole Même Travail)',
    );

    // 3. Orange Cameroun 500 FCFA 1Go -> finished prematurely at 664 MB measured by OS
    const orangeBytes = 664500000;
    final orangeSession = DataPlanSession(
      id: 'BENCH-ORANGE-500F',
      isp: IspProvider.orangeCm,
      planName: '[Réf. Étude] Forfait Internet 1Go Jour',
      promisedMb: 1024,
      priceXaf: 500,
      validityHours: 24,
      startedAt: now.subtract(const Duration(hours: 20)),
      endedAt: now.subtract(const Duration(hours: 18, minutes: 5)),
      baselineRxBytes: 300000000,
      baselineTxBytes: 30000000,
      currentRxBytes: 300000000 + 604000000,
      currentTxBytes: 30000000 + 60500000,
      appUsages: _networkService.buildStandardWorkloadBreakdown(orangeBytes),
      status: AuditSessionStatus.exhausted,
      workloadTag: 'Référence Étude Comparative (Protocole Même Travail)',
    );

    _referenceBenchmarks.addAll([
      camtelSession.copyWith(
        report: _calculator.buildDeterministicReport(
          camtelSession,
          modelLabel: 'Réf. Benchmark Communautaire',
        ),
      ),
      mtnSession.copyWith(
        report: _calculator.buildDeterministicReport(
          mtnSession,
          modelLabel: 'Réf. Benchmark Communautaire',
        ),
      ),
      orangeSession.copyWith(
        report: _calculator.buildDeterministicReport(
          orangeSession,
          modelLabel: 'Réf. Benchmark Communautaire',
        ),
      ),
    ]);
  }

  /// Starts a new real-time OS network audit session.
  /// If [includePastValidityWindow] is true, queries Android `NetworkStatsManager`
  /// over the past [validityHours] so an already-active plan on the SIM shows its
  /// real OS consumption immediately.
  Future<DataPlanSession> startNewSession({
    required IspProvider isp,
    required String planName,
    required double promisedMb,
    required int priceXaf,
    required int validityHours,
    bool includePastValidityWindow = true,
    String workloadTag = 'Mesure OS Réelle (SIM Active)',
  }) async {
    _networkService.resetSimulatedBytes();
    final now = DateTime.now();
    final windowStart = includePastValidityWindow
        ? now.subtract(Duration(hours: validityHours))
        : now;

    final snap = await _networkService.getMobileCountersSnapshot(
      startTime: windowStart,
      endTime: now,
    );

    final int currentRx;
    final int currentTx;
    final int baselineRx = snap.rxBytes;
    final int baselineTx = snap.txBytes;

    if (includePastValidityWindow &&
        snap.isFromNativeOs &&
        snap.totalBucketBytes > 0) {
      currentRx = baselineRx + snap.bucketRxBytes;
      currentTx = baselineTx + snap.bucketTxBytes;
    } else {
      currentRx = baselineRx;
      currentTx = baselineTx;
    }

    final initialConsumed =
        (currentRx - baselineRx) + (currentTx - baselineTx);
    final apps = await _networkService.getPerAppMobileUsage(
      startTime: windowStart,
      endTime: now,
      totalSessionConsumedBytes: initialConsumed,
    );

    // Replace any existing active (unfinalized) session so we never duplicate cards
    _userSessions.removeWhere((s) => s.status == AuditSessionStatus.active);

    final session = DataPlanSession(
      id: 'AUDIT-${now.millisecondsSinceEpoch}',
      isp: isp,
      planName: planName,
      promisedMb: promisedMb,
      priceXaf: priceXaf,
      validityHours: validityHours,
      startedAt: windowStart,
      baselineRxBytes: baselineRx,
      baselineTxBytes: baselineTx,
      currentRxBytes: currentRx,
      currentTxBytes: currentTx,
      appUsages: apps,
      status: AuditSessionStatus.active,
      workloadTag: workloadTag,
    );

    _upsertUserSession(session);
    return session;
  }

  /// Reads the real SMS inbox (`content://sms/inbox`) on Android, detects all
  /// bundle activations ("Vous avez droit a 1024.00 MB...") and exhaustions
  /// ("Your DATA_4G balance is 0 now..."), and reconstructs the true cumulative
  /// and per-bundle audit sessions.
  Future<DataPlanSession?> syncSessionsFromSmsInbox() async {
    final smsList = await _networkService.getOperatorSmsInbox(limit: 80);
    _smsInboxMessages
      ..clear()
      ..addAll(smsList);
    if (smsList.isEmpty) return null;

    final now = DateTime.now();
    final cutoff24h = now.subtract(const Duration(hours: 24));

    // Find all data volume activations in the last 24h (specifically messages granting MB/Go)
    final activationsToday = smsList
        .where(
          (m) =>
              m.isActivation &&
              m.timestamp.isAfter(cutoff24h) &&
              (m.body.toUpperCase().contains('MB') ||
                  m.body.toUpperCase().contains('MO') ||
                  m.body.toUpperCase().contains('GO')),
        )
        .toList()
      ..sort((a, b) => a.dateMs.compareTo(b.dateMs));

    if (activationsToday.isEmpty) return null;

    final firstActivation = activationsToday.first;
    final latestActivation = activationsToday.last;
    final bundleCount = activationsToday.length;
    final cumulativePromisedMb = activationsToday.fold<double>(
      0.0,
      (sum, item) => sum + item.promisedMb,
    );
    final cumulativePriceXaf = activationsToday.fold<int>(
      0,
      (sum, item) => sum + item.priceXaf,
    );

    // Check if an exhaustion SMS arrived AFTER the latest activation
    final latestExhaustionAfterLast = smsList
        .where((m) => m.isExhaustion && m.dateMs > latestActivation.dateMs)
        .firstOrNull;

    final snap = await _networkService.getMobileCountersSnapshot(
      startTime: firstActivation.timestamp,
      endTime: now,
    );

    final baselineRx = snap.rxBytes;
    final baselineTx = snap.txBytes;
    final measuredRx = snap.bucketRxBytes > 0 ? snap.bucketRxBytes : 0;
    final measuredTx = snap.bucketTxBytes > 0 ? snap.bucketTxBytes : 0;
    final totalDayBytes = measuredRx + measuredTx;

    final apps = await _networkService.getPerAppMobileUsage(
      startTime: firstActivation.timestamp,
      endTime: now,
      totalSessionConsumedBytes: totalDayBytes,
    );

    _userSessions.removeWhere((s) => s.id.startsWith('SMS-'));

    // 1. Build individual completed bundle sessions for each activation today
    for (var i = 0; i < activationsToday.length; i++) {
      final act = activationsToday[i];
      final nextActDateMs = i + 1 < activationsToday.length
          ? activationsToday[i + 1].dateMs
          : now.millisecondsSinceEpoch;
      final matchingExhaustion = smsList
          .where(
            (m) =>
                m.isExhaustion &&
                m.dateMs > act.dateMs &&
                m.dateMs <= nextActDateMs,
          )
          .lastOrNull;

      // Proportion the day's hardware bytes across the N bundles activated today
      // so Android's 2-hour bucket aggregation never exceeds a single bundle's cap
      final perBundleRx = (measuredRx / bundleCount).round();
      final perBundleTx = (measuredTx / bundleCount).round();
      final perBundleTotal = perBundleRx + perBundleTx;

      final bundleSession = DataPlanSession(
        id: 'SMS-BUNDLE-${act.dateMs}',
        isp: act.isp,
        planName:
            '${act.planName} #${i + 1}/$bundleCount (${_formatHm(act.timestamp)})',
        promisedMb: act.promisedMb,
        priceXaf: act.priceXaf,
        validityHours: act.isp.defaultValidityHours,
        startedAt: act.timestamp,
        endedAt: matchingExhaustion?.timestamp,
        baselineRxBytes: baselineRx,
        baselineTxBytes: baselineTx,
        currentRxBytes: baselineRx + perBundleRx,
        currentTxBytes: baselineTx + perBundleTx,
        appUsages: await _networkService.getPerAppMobileUsage(
          startTime: act.timestamp,
          endTime: matchingExhaustion?.timestamp ?? now,
          totalSessionConsumedBytes: perBundleTotal,
        ),
        status: matchingExhaustion != null
            ? AuditSessionStatus.exhausted
            : AuditSessionStatus.active,
        workloadTag: matchingExhaustion != null
            ? 'SMS Camtel: Activé à ${_formatHm(act.timestamp)} → Coupé à ${_formatHm(matchingExhaustion.timestamp)}'
            : 'SMS Camtel: Activé à ${_formatHm(act.timestamp)} (En cours)',
      );

      final withReport = bundleSession.copyWith(
        report: _calculator.buildDeterministicReport(
          bundleSession,
          modelLabel: 'Audit SMS Réel + Sonde OS',
        ),
      );
      _upsertUserSession(withReport);
    }

    // 2. Build the primary Cumulative Today Session (e.g. 3 x 1024 Mo = 3072 Mo)
    final cumulativeSession = DataPlanSession(
      id: 'SMS-CUMUL-TODAY',
      isp: latestActivation.isp,
      planName: bundleCount > 1
          ? 'Cumul $bundleCount Forfaits SMS (${cumulativePromisedMb.toStringAsFixed(0)} Mo)'
          : latestActivation.planName,
      promisedMb: cumulativePromisedMb,
      priceXaf: cumulativePriceXaf,
      validityHours: latestActivation.isp.defaultValidityHours * bundleCount,
      startedAt: firstActivation.timestamp,
      endedAt: latestExhaustionAfterLast?.timestamp,
      baselineRxBytes: baselineRx,
      baselineTxBytes: baselineTx,
      currentRxBytes: baselineRx + measuredRx,
      currentTxBytes: baselineTx + measuredTx,
      appUsages: apps,
      status: latestExhaustionAfterLast != null
          ? AuditSessionStatus.exhausted
          : AuditSessionStatus.active,
      workloadTag:
          '$bundleCount SMS d\'activation lus dans la boîte SMS depuis ${_formatHm(firstActivation.timestamp)}',
    );

    final cumulativeWithReport = cumulativeSession.copyWith(
      report: _calculator.buildDeterministicReport(
        cumulativeSession,
        modelLabel: 'Audit Boîte SMS + NetworkStats OS',
      ),
    );
    _upsertUserSession(cumulativeWithReport);
    return cumulativeWithReport;
  }

  String _formatHm(DateTime dt) {
    final h = dt.hour.toString().padLeft(2, '0');
    final m = dt.minute.toString().padLeft(2, '0');
    return '${h}h$m';
  }

  void _upsertUserSession(DataPlanSession session) {
    final idx = _userSessions.indexWhere((s) => s.id == session.id);
    if (idx >= 0) {
      _userSessions[idx] = session;
    } else {
      _userSessions.insert(0, session);
    }
  }

  /// Refreshes an active session with the latest OS cellular counters and per-UID breakdown.
  Future<DataPlanSession> refreshSessionFromOs(DataPlanSession session) async {
    final now = DateTime.now();
    final snap = await _networkService.getMobileCountersSnapshot(
      startTime: session.startedAt,
      endTime: now,
    );

    final liveDeltaRx = (snap.rxBytes - session.baselineRxBytes).clamp(
      0,
      1 << 60,
    );
    final liveDeltaTx = (snap.txBytes - session.baselineTxBytes).clamp(
      0,
      1 << 60,
    );

    final updatedRx = (session.baselineRxBytes + liveDeltaRx) >=
            session.currentRxBytes
        ? (session.baselineRxBytes + liveDeltaRx)
        : session.currentRxBytes;
    final updatedTx = (session.baselineTxBytes + liveDeltaTx) >=
            session.currentTxBytes
        ? (session.baselineTxBytes + liveDeltaTx)
        : session.currentTxBytes;
    final totalConsumed =
        (updatedRx - session.baselineRxBytes) +
        (updatedTx - session.baselineTxBytes);

    final apps = await _networkService.getPerAppMobileUsage(
      startTime: session.startedAt,
      endTime: now,
      totalSessionConsumedBytes: totalConsumed,
    );

    final updated = session.copyWith(
      currentRxBytes: updatedRx,
      currentTxBytes: updatedTx,
      appUsages: apps,
    );
    _upsertUserSession(updated);
    return updated;
  }

  /// Marks a session as exhausted/finished and invokes Gemma 4 to produce the final report.
  Future<DataPlanSession> finalizeSessionWithGemma(
    DataPlanSession session, {
    bool isExhaustedByIsp = true,
  }) async {
    final refreshed = await refreshSessionFromOs(session);
    final camtelRef = _userSessions
            .where((s) => s.isp == IspProvider.camtel)
            .firstOrNull ??
        _referenceBenchmarks
            .where((s) => s.isp == IspProvider.camtel)
            .firstOrNull;

    final GemmaAuditReport report = await _gemmaService.generateAuditReport(
      session: refreshed,
      camtelReference: camtelRef,
    );

    final finalized = refreshed.copyWith(
      endedAt: DateTime.now(),
      status: isExhaustedByIsp
          ? AuditSessionStatus.exhausted
          : AuditSessionStatus.completed,
      report: report,
    );

    _upsertUserSession(finalized);
    return finalized;
  }
}
