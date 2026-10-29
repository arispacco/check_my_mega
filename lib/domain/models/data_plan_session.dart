import 'app_usage_stat.dart';
import 'gemma_audit_report.dart';
import 'isp_provider.dart';

/// Status of an internet data plan audit session.
enum AuditSessionStatus { active, completed, exhausted }

/// Immutable representation of a mobile data plan audit session.
class DataPlanSession {
  const DataPlanSession({
    required this.id,
    required this.isp,
    required this.planName,
    required this.promisedMb,
    required this.priceXaf,
    required this.validityHours,
    required this.startedAt,
    this.endedAt,
    required this.baselineRxBytes,
    required this.baselineTxBytes,
    required this.currentRxBytes,
    required this.currentTxBytes,
    this.appUsages = const [],
    this.status = AuditSessionStatus.active,
    this.workloadTag = 'Même Travail (Standard)',
    this.report,
  });

  final String id;
  final IspProvider isp;
  final String planName;
  final double promisedMb;
  final int priceXaf;
  final int validityHours;
  final DateTime startedAt;
  final DateTime? endedAt;
  final int baselineRxBytes;
  final int baselineTxBytes;
  final int currentRxBytes;
  final int currentTxBytes;
  final List<AppUsageStat> appUsages;
  final AuditSessionStatus status;
  final String workloadTag;
  final GemmaAuditReport? report;

  /// Consumed download bytes since session start.
  int get consumedRxBytes =>
      (currentRxBytes - baselineRxBytes).clamp(0, 1 << 62);

  /// Consumed upload bytes since session start.
  int get consumedTxBytes =>
      (currentTxBytes - baselineTxBytes).clamp(0, 1 << 62);

  /// Total cellular interface bytes (Download + Upload).
  int get totalConsumedBytes => consumedRxBytes + consumedTxBytes;

  /// Duration of the audit session.
  Duration get elapsed => (endedAt ?? DateTime.now()).difference(startedAt);

  DataPlanSession copyWith({
    String? id,
    IspProvider? isp,
    String? planName,
    double? promisedMb,
    int? priceXaf,
    int? validityHours,
    DateTime? startedAt,
    DateTime? endedAt,
    int? baselineRxBytes,
    int? baselineTxBytes,
    int? currentRxBytes,
    int? currentTxBytes,
    List<AppUsageStat>? appUsages,
    AuditSessionStatus? status,
    String? workloadTag,
    GemmaAuditReport? report,
  }) {
    return DataPlanSession(
      id: id ?? this.id,
      isp: isp ?? this.isp,
      planName: planName ?? this.planName,
      promisedMb: promisedMb ?? this.promisedMb,
      priceXaf: priceXaf ?? this.priceXaf,
      validityHours: validityHours ?? this.validityHours,
      startedAt: startedAt ?? this.startedAt,
      endedAt: endedAt ?? this.endedAt,
      baselineRxBytes: baselineRxBytes ?? this.baselineRxBytes,
      baselineTxBytes: baselineTxBytes ?? this.baselineTxBytes,
      currentRxBytes: currentRxBytes ?? this.currentRxBytes,
      currentTxBytes: currentTxBytes ?? this.currentTxBytes,
      appUsages: appUsages ?? this.appUsages,
      status: status ?? this.status,
      workloadTag: workloadTag ?? this.workloadTag,
      report: report ?? this.report,
    );
  }
}
