import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import '../../domain/models/app_usage_stat.dart';

/// Snapshot of the operating system's mobile network counters.
class OsNetworkSnapshot {
  const OsNetworkSnapshot({
    required this.rxBytes,
    required this.txBytes,
    required this.operatorName,
    required this.hasUsagePermission,
    required this.isFromNativeOs,
    required this.timestamp,
  });

  final int rxBytes;
  final int txBytes;
  final String operatorName;
  final bool hasUsagePermission;
  final bool isFromNativeOs;
  final DateTime timestamp;

  int get totalBytes => rxBytes + txBytes;
}

/// Service wrapping the Android `com.checkmymega/network_stats` MethodChannel
/// (`TrafficStats` and `NetworkStatsManager`), with a deterministic fallback
/// for desktop/web live coding previews.
class NativeNetworkService {
  static const MethodChannel _channel = MethodChannel(
    'com.checkmymega/network_stats',
  );

  int _simulatedRxBytes = 250 * 1000000;
  int _simulatedTxBytes = 35 * 1000000;

  bool get isNativeAndroid =>
      !kIsWeb && defaultTargetPlatform == TargetPlatform.android;

  /// Checks whether the user has granted `PACKAGE_USAGE_STATS` on Android.
  Future<bool> hasUsageStatsPermission() async {
    if (!isNativeAndroid) return true;
    try {
      final granted = await _channel.invokeMethod<bool>(
        'hasUsageStatsPermission',
      );
      return granted ?? false;
    } on PlatformException {
      return false;
    } on MissingPluginException {
      return false;
    }
  }

  /// Opens Android's "Usage Access" system settings screen.
  Future<void> openUsageStatsSettings() async {
    if (!isNativeAndroid) return;
    try {
      await _channel.invokeMethod<void>('openUsageStatsSettings');
    } catch (_) {
      // Ignore if unavailable on non-Android host
    }
  }

  /// Queries the current OS cellular counters (`RxBytes` and `TxBytes`).
  Future<OsNetworkSnapshot> getMobileCountersSnapshot({
    required DateTime startTime,
    DateTime? endTime,
  }) async {
    final effectiveEnd = endTime ?? DateTime.now();
    if (isNativeAndroid) {
      try {
        final raw = await _channel.invokeMapMethod<String, dynamic>(
          'getMobileCountersSnapshot',
          {
            'startTimeMs': startTime.millisecondsSinceEpoch,
            'endTimeMs': effectiveEnd.millisecondsSinceEpoch,
          },
        );
        if (raw != null) {
          final trafficRx = (raw['trafficRxBytes'] as num?)?.toInt() ?? 0;
          final trafficTx = (raw['trafficTxBytes'] as num?)?.toInt() ?? 0;
          final bucketRx = (raw['bucketRxBytes'] as num?)?.toInt() ?? 0;
          final bucketTx = (raw['bucketTxBytes'] as num?)?.toInt() ?? 0;
          final hasPerm = raw['hasPermission'] as bool? ?? false;
          final operatorName =
              raw['operatorName'] as String? ?? 'Réseau Cellulaire Android';

          // If hardware TrafficStats reports live cellular traffic, use it;
          // if the device is an emulator with 0 cellular bytes, add simulated delta if any.
          final rx = trafficRx > 0
              ? trafficRx
              : (bucketRx > 0 ? bucketRx : _simulatedRxBytes);
          final tx = trafficTx > 0
              ? trafficTx
              : (bucketTx > 0 ? bucketTx : _simulatedTxBytes);

          return OsNetworkSnapshot(
            rxBytes: rx,
            txBytes: tx,
            operatorName: operatorName,
            hasUsagePermission: hasPerm,
            isFromNativeOs: trafficRx > 0 || bucketRx > 0,
            timestamp: effectiveEnd,
          );
        }
      } catch (_) {
        // Fallback to simulated OS counters if channel call fails
      }
    }

    return OsNetworkSnapshot(
      rxBytes: _simulatedRxBytes,
      txBytes: _simulatedTxBytes,
      operatorName: 'Sonde OS / Mode Live Démo',
      hasUsagePermission: true,
      isFromNativeOs: false,
      timestamp: effectiveEnd,
    );
  }

  /// Queries per-application mobile data consumption (`NetworkStatsManager.querySummary`)
  /// between [startTime] and [endTime].
  Future<List<AppUsageStat>> getPerAppMobileUsage({
    required DateTime startTime,
    DateTime? endTime,
    required int totalSessionConsumedBytes,
  }) async {
    final effectiveEnd = endTime ?? DateTime.now();
    if (isNativeAndroid) {
      try {
        final rawList = await _channel.invokeListMethod<dynamic>(
          'getPerAppMobileUsage',
          {
            'startTimeMs': startTime.millisecondsSinceEpoch,
            'endTimeMs': effectiveEnd.millisecondsSinceEpoch,
          },
        );
        if (rawList != null && rawList.isNotEmpty) {
          return rawList.map((entry) {
            final map = Map<String, dynamic>.from(entry as Map);
            return AppUsageStat(
              uid: (map['uid'] as num?)?.toInt() ?? 0,
              packageName: map['packageName'] as String? ?? 'unknown',
              appName: map['appName'] as String? ?? 'Application',
              rxBytes: (map['rxBytes'] as num?)?.toInt() ?? 0,
              txBytes: (map['txBytes'] as num?)?.toInt() ?? 0,
            );
          }).toList();
        }
      } catch (_) {
        // Fall through to proportional workload distribution
      }
    }

    return buildStandardWorkloadBreakdown(totalSessionConsumedBytes);
  }

  /// Injects a calibrated workload step (e.g. "Même Travail" benchmark block)
  /// so live coding demos can test ISP depletion behavior deterministically.
  void injectWorkloadBytes({required int rxBytesDelta, required int txBytesDelta}) {
    _simulatedRxBytes += rxBytesDelta;
    _simulatedTxBytes += txBytesDelta;
  }

  /// Distributes a total byte count across a reproducible "Même Travail" app profile
  /// when running without per-UID buckets (or in Live Demo mode).
  List<AppUsageStat> buildStandardWorkloadBreakdown(int totalBytes) {
    if (totalBytes <= 0) return const [];
    final profiles = <(int, String, String, double, double)>[
      (10101, 'com.google.android.youtube', 'YouTube (Streaming 720p)', 0.44, 0.03),
      (10102, 'com.android.chrome', 'Google Chrome & Docs', 0.23, 0.03),
      (10103, 'com.whatsapp', 'WhatsApp (Appels & Médias)', 0.11, 0.05),
      (10104, 'com.tiktok.lite', 'TikTok / Réseaux Sociaux', 0.07, 0.01),
      (1000, 'android.system', 'Système Android (DNS / TLS)', 0.02, 0.01),
    ];

    return profiles.map((p) {
      final rx = (totalBytes * p.$4).round();
      final tx = (totalBytes * p.$5).round();
      return AppUsageStat(
        uid: p.$1,
        packageName: p.$2,
        appName: p.$3,
        rxBytes: rx,
        txBytes: tx,
      );
    }).toList();
  }
}
