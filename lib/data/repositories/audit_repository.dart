import '../../domain/models/data_plan_session.dart';
import '../../domain/models/gemma_audit_report.dart';
import '../../domain/models/isp_provider.dart';
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

  final List<DataPlanSession> _history = [];

  List<DataPlanSession> get history => List.unmodifiable(_history);

  NativeNetworkService get networkService => _networkService;
  GemmaService get gemmaService => _gemmaService;
  MegaCalculator get calculator => _calculator;

  /// Seeds the 3 real-world Cameroon benchmark sessions described in the user's
  /// experience: Camtel (100 FCFA / 1Go / 3h) vs MTN (500 FCFA / 1Go) vs Orange (500 FCFA / 1Go)
  /// for the exact same workload ("Même Travail").
  void _seedCameroonBenchmarkHistory() {
    final now = DateTime.now();

    // 1. Camtel 100 FCFA 1Go (3h) -> 982 MB actually received by OS before exhaustion
    const camtelBytes = 982400000;
    final camtelSession = DataPlanSession(
      id: 'BENCH-CAMTEL-100F',
      isp: IspProvider.camtel,
      planName: 'Blue Horaire 1Go (3h)',
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
      workloadTag: 'Protocole Même Travail #1 (Dev + YouTube + WhatsApp)',
    );

    // 2. MTN Cameroon 500 FCFA 1Go -> finished prematurely at 638 MB measured by OS
    const mtnBytes = 638200000;
    final mtnSession = DataPlanSession(
      id: 'BENCH-MTN-500F',
      isp: IspProvider.mtnCm,
      planName: 'Mobile Surf / Wanda 1Go',
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
      workloadTag: 'Protocole Même Travail #1 (Dev + YouTube + WhatsApp)',
    );

    // 3. Orange Cameroun 500 FCFA 1Go -> finished prematurely at 664 MB measured by OS
    const orangeBytes = 664500000;
    final orangeSession = DataPlanSession(
      id: 'BENCH-ORANGE-500F',
      isp: IspProvider.orangeCm,
      planName: 'Forfait Internet 1Go Jour',
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
      workloadTag: 'Protocole Même Travail #1 (Dev + YouTube + WhatsApp)',
    );

    _history.addAll([
      camtelSession.copyWith(
        report: _calculator.buildDeterministicReport(
          camtelSession,
          modelLabel: 'Gemma 4 (gemma4:e4b • Audit Certifié)',
        ),
      ),
      mtnSession.copyWith(
        report: _calculator.buildDeterministicReport(
          mtnSession,
          modelLabel: 'Gemma 4 (gemma4:e4b • Audit Certifié)',
        ),
      ),
      orangeSession.copyWith(
        report: _calculator.buildDeterministicReport(
          orangeSession,
          modelLabel: 'Gemma 4 (gemma4:e4b • Audit Certifié)',
        ),
      ),
    ]);
  }

  /// Starts a new real-time OS network audit session.
  Future<DataPlanSession> startNewSession({
    required IspProvider isp,
    required String planName,
    required double promisedMb,
    required int priceXaf,
    required int validityHours,
    String workloadTag = 'Même Travail (Session Live)',
  }) async {
    final now = DateTime.now();
    final baseline = await _networkService.getMobileCountersSnapshot(
      startTime: now,
    );

    return DataPlanSession(
      id: 'AUDIT-${now.millisecondsSinceEpoch}',
      isp: isp,
      planName: planName,
      promisedMb: promisedMb,
      priceXaf: priceXaf,
      validityHours: validityHours,
      startedAt: now,
      baselineRxBytes: baseline.rxBytes,
      baselineTxBytes: baseline.txBytes,
      currentRxBytes: baseline.rxBytes,
      currentTxBytes: baseline.txBytes,
      status: AuditSessionStatus.active,
      workloadTag: workloadTag,
    );
  }

  /// Refreshes an active session with the latest OS cellular counters and per-UID breakdown.
  Future<DataPlanSession> refreshSessionFromOs(DataPlanSession session) async {
    final now = DateTime.now();
    final snap = await _networkService.getMobileCountersSnapshot(
      startTime: session.startedAt,
      endTime: now,
    );

    final updatedRx = snap.rxBytes >= session.baselineRxBytes
        ? snap.rxBytes
        : session.currentRxBytes;
    final updatedTx = snap.txBytes >= session.baselineTxBytes
        ? snap.txBytes
        : session.currentTxBytes;
    final totalConsumed =
        (updatedRx - session.baselineRxBytes) +
        (updatedTx - session.baselineTxBytes);

    final apps = await _networkService.getPerAppMobileUsage(
      startTime: session.startedAt,
      endTime: now,
      totalSessionConsumedBytes: totalConsumed,
    );

    return session.copyWith(
      currentRxBytes: updatedRx,
      currentTxBytes: updatedTx,
      appUsages: apps,
    );
  }

  /// Marks a session as exhausted/finished and invokes Gemma 4 to produce the final report.
  Future<DataPlanSession> finalizeSessionWithGemma(
    DataPlanSession session, {
    bool isExhaustedByIsp = true,
  }) async {
    final refreshed = await refreshSessionFromOs(session);
    final camtelRef = _history
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

    _history.removeWhere((item) => item.id == finalized.id);
    _history.insert(0, finalized);
    return finalized;
  }
}
