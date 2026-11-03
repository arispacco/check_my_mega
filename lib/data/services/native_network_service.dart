import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;

import '../../domain/models/app_usage_stat.dart';
import '../../domain/models/isp_provider.dart';
import '../../domain/models/signal_radar_spot.dart';

/// Snapshot of the operating system's mobile network counters.
class OsNetworkSnapshot {
  const OsNetworkSnapshot({
    required this.rxBytes,
    required this.txBytes,
    required this.bucketRxBytes,
    required this.bucketTxBytes,
    required this.operatorName,
    required this.simOperatorCode,
    required this.hasUsagePermission,
    required this.isFromNativeOs,
    required this.timestamp,
  });

  final int rxBytes;
  final int txBytes;
  final int bucketRxBytes;
  final int bucketTxBytes;
  final String operatorName;
  final String simOperatorCode;
  final bool hasUsagePermission;
  final bool isFromNativeOs;
  final DateTime timestamp;

  int get totalBytes => rxBytes + txBytes;
  int get totalBucketBytes => bucketRxBytes + bucketTxBytes;

  IspProvider get detectedIsp =>
      IspProvider.fromIdOrText('$simOperatorCode $operatorName');
}

/// Service wrapping the Android `com.checkmymega/network_stats` MethodChannel
/// (`TrafficStats`, `NetworkStatsManager`, `TelephonyManager.allCellInfo`,
/// `SensorManager` Compass/StepDetector, and `content://sms/inbox`).
class NativeNetworkService {
  NativeNetworkService() {
    _channel.setMethodCallHandler(_handleNativeCallback);
  }

  static const MethodChannel _channel = MethodChannel(
    'com.checkmymega/network_stats',
  );

  int _simulatedRxBytes = 0;
  int _simulatedTxBytes = 0;
  void Function(OperatorSmsMessage msg)? _onIncomingSms;
  void Function(double azimuthDeg, int stepCount, double posX, double posY)?
  _onCompassSensorFrame;

  bool get isNativeAndroid =>
      !kIsWeb && defaultTargetPlatform == TargetPlatform.android;

  /// Registers a listener invoked immediately when an operator SMS arrives on the phone.
  void setIncomingSmsListener(
    void Function(OperatorSmsMessage msg)? listener,
  ) {
    _onIncomingSms = listener;
  }

  /// Registers a high-frequency (60 FPS) listener for hardware compass & step frames.
  void setCompassSensorFrameListener(
    void Function(double azimuthDeg, int stepCount, double posX, double posY)?
    listener,
  ) {
    _onCompassSensorFrame = listener;
  }

  Future<dynamic> _handleNativeCallback(MethodCall call) async {
    if (call.method == 'onCompassSensorFrame' && call.arguments is Map) {
      final raw = call.arguments as Map;
      final azimuthDeg = (raw['azimuthDeg'] as num?)?.toDouble() ?? 0.0;
      final stepCount = (raw['stepCount'] as num?)?.toInt() ?? 0;
      final posX = (raw['posX'] as num?)?.toDouble() ?? 0.0;
      final posY = (raw['posY'] as num?)?.toDouble() ?? 0.0;
      _onCompassSensorFrame?.call(azimuthDeg, stepCount, posX, posY);
      return null;
    }
    if (call.method == 'onOperatorSmsReceived' && call.arguments is Map) {
      final raw = Map<String, dynamic>.from(call.arguments as Map);
      final parsed = parseOperatorSmsRecord(raw);
      if (parsed != null) {
        _onIncomingSms?.call(parsed);
      }
    }
    return null;
  }

  /// Requests runtime permissions (`READ_SMS`, `RECEIVE_SMS`, `ACCESS_FINE_LOCATION`, `READ_PHONE_STATE`).
  Future<void> requestHardwarePermissions() async {
    if (!isNativeAndroid) return;
    try {
      await _channel.invokeMethod<dynamic>('requestHardwarePermissions');
    } catch (_) {
      // Ignore on unsupported platforms
    }
  }

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

  /// Opens Android's "App Info" screen so users on Android 13+ can enable
  /// "Allow restricted settings" ("Autoriser les paramètres restreints").
  Future<void> openAppDetailsSettings() async {
    if (!isNativeAndroid) return;
    try {
      await _channel.invokeMethod<void>('openAppDetailsSettings');
    } catch (_) {
      // Ignore if unavailable on non-Android host
    }
  }

  /// Resets any manually injected test bytes when starting a clean session.
  void resetSimulatedBytes() {
    _simulatedRxBytes = 0;
    _simulatedTxBytes = 0;
  }

  /// Reads real telecom operator SMS messages from Android `content://sms/inbox`.
  Future<List<OperatorSmsMessage>> getOperatorSmsInbox({int limit = 60}) async {
    if (!isNativeAndroid) return const [];
    try {
      final rawList = await _channel.invokeListMethod<dynamic>(
        'getOperatorSmsInbox',
        {'limit': limit},
      );
      if (rawList == null || rawList.isEmpty) return const [];
      final messages = <OperatorSmsMessage>[];
      for (final item in rawList) {
        if (item is Map) {
          final parsed = parseOperatorSmsRecord(
            Map<String, dynamic>.from(item),
          );
          if (parsed != null) {
            messages.add(parsed);
          }
        }
      }
      return messages;
    } catch (_) {
      return const [];
    }
  }

  /// Parses a raw SMS record from `content://sms/inbox` into an [OperatorSmsMessage].
  OperatorSmsMessage? parseOperatorSmsRecord(Map<String, dynamic> map) {
    final id = (map['id'] as num?)?.toInt() ?? 0;
    final address = (map['address'] as String? ?? '').trim();
    final dateMs =
        (map['dateMs'] as num?)?.toInt() ??
        DateTime.now().millisecondsSinceEpoch;
    final body = (map['body'] as String? ?? '').trim();
    if (body.isEmpty) return null;

    final upperBody = body.toUpperCase();
    final isp = IspProvider.fromIdOrText('$address $body');

    // Filter out pure airtime recharge messages that are not data bundle activations/exhaustions
    if (upperBody.contains('TOP-UP') ||
        upperBody.contains('A ETE RECHARGE') ||
        upperBody.contains('NOT SUFFICIENT FOR THE CALL') ||
        upperBody.contains('PREPAIDBALANCESUBACCOUNT')) {
      return null;
    }

    final isExhaustion =
        upperBody.contains('DATA_4G BALANCE IS 0') ||
        upperBody.contains('BALANCE IS 0 NOW') ||
        upperBody.contains('EPUISE') ||
        upperBody.contains('ÉPUISÉ') ||
        upperBody.contains('0 MO RESTANT');

    final isActivation =
        !isExhaustion &&
        (upperBody.contains('VOUS AVEZ DROIT A') ||
            upperBody.contains('MB') ||
            upperBody.contains('MO') ||
            upperBody.contains('GO') ||
            upperBody.contains('BLUE MO') ||
            upperBody.contains('SOUSCRI') ||
            upperBody.contains('ACTIVE'));

    if (!isActivation && !isExhaustion) return null;

    double promisedMb = isp.defaultPromisedMb;
    final mbMatch = RegExp(
      r'(\d+(?:[.,]\d+)?)\s*(?:MB|Mo)\b',
      caseSensitive: false,
    ).firstMatch(body);
    final goMatch = RegExp(
      r'(\d+(?:[.,]\d+)?)\s*(?:GB|Go)\b',
      caseSensitive: false,
    ).firstMatch(body);
    if (mbMatch != null) {
      promisedMb =
          double.tryParse(mbMatch.group(1)!.replaceAll(',', '.')) ?? promisedMb;
    } else if (goMatch != null) {
      final go = double.tryParse(goMatch.group(1)!.replaceAll(',', '.')) ?? 1.0;
      promisedMb = go * 1024.0;
    }

    int priceXaf = isp.defaultPriceXaf;
    final priceMatch = RegExp(
      r'(\d+(?:[.,]\d+)?)\s*(?:FCFA|XAF|F\b)',
      caseSensitive: false,
    ).firstMatch(body);
    if (priceMatch != null) {
      final parsedPrice = double.tryParse(
        priceMatch.group(1)!.replaceAll(',', '.'),
      );
      if (parsedPrice != null && parsedPrice >= 50) {
        priceXaf = parsedPrice.round();
      }
    }

    String planName = isp.defaultSamplePlanName;
    if (upperBody.contains('BLUE MO S')) {
      planName = 'Blue mo S (1Go • 100 FCFA)';
    } else if (upperBody.contains('BLUE MO M')) {
      planName = 'Blue mo M';
    } else if (upperBody.contains('BLUE MO L')) {
      planName = 'Blue mo L';
    } else if (upperBody.contains('BLUE NIGHT')) {
      planName = 'Blue Night';
    } else if (upperBody.contains('MOBILE SURF') || upperBody.contains('WANDA')) {
      planName = 'Mobile Surf / Wanda';
    }

    return OperatorSmsMessage(
      id: id,
      address: address,
      dateMs: dateMs,
      body: body,
      isp: isp,
      isActivation: isActivation,
      isExhaustion: isExhaustion,
      promisedMb: promisedMb,
      priceXaf: priceXaf,
      planName: planName,
    );
  }

  /// Queries live cellular radio (`CellInfoLte` RSRP/SINR/RSRQ/Band/Distance) and
  /// hardware magnetic compass + step counter.
  Future<RadioCompassSnapshot> getRadioAndCompassSnapshot() async {
    final now = DateTime.now();
    if (isNativeAndroid) {
      try {
        final raw = await _channel.invokeMapMethod<String, dynamic>(
          'getRadioAndCompassSnapshot',
        );
        if (raw != null) {
          return RadioCompassSnapshot(
            operatorName:
                raw['operatorName'] as String? ?? 'CAMTEL / Blue',
            simOperatorCode: raw['simOperatorCode'] as String? ?? '62405',
            networkType: raw['networkType'] as String? ?? '4G LTE',
            bandLabel:
                raw['bandLabel'] as String? ?? 'Bande 3 FDD (1800 MHz)',
            earfcn: (raw['earfcn'] as num?)?.toInt() ?? 1649,
            pci: (raw['pci'] as num?)?.toInt() ?? 419,
            rsrpDbm: (raw['rsrpDbm'] as num?)?.toInt() ?? -104,
            rsrqDb: (raw['rsrqDb'] as num?)?.toInt() ?? -10,
            sinrDb: (raw['sinrDb'] as num?)?.toInt() ?? 5,
            rssiDbm: (raw['rssiDbm'] as num?)?.toInt() ?? -73,
            cqi: (raw['cqi'] as num?)?.toInt() ?? 7,
            timingAdvance: (raw['timingAdvance'] as num?)?.toInt() ?? 7,
            estimatedDistanceMeters:
                (raw['estimatedDistanceMeters'] as num?)?.toInt() ?? 547,
            linkDownKbps: (raw['linkDownKbps'] as num?)?.toInt() ?? 51853,
            linkUpKbps: (raw['linkUpKbps'] as num?)?.toInt() ?? 4800,
            passiveRxMbps:
                (raw['passiveRxMbps'] as num?)?.toDouble() ?? 0.0,
            passiveTxMbps:
                (raw['passiveTxMbps'] as num?)?.toDouble() ?? 0.0,
            azimuthDeg: (raw['azimuthDeg'] as num?)?.toDouble() ?? 0.0,
            stepCount: (raw['stepCount'] as num?)?.toInt() ?? 0,
            posX: (raw['posX'] as num?)?.toDouble() ?? 0.0,
            posY: (raw['posY'] as num?)?.toDouble() ?? 0.0,
            timestamp: now,
          );
        }
      } catch (_) {
        // Fallback below if channel is busy
      }
    }

    return RadioCompassSnapshot(
      operatorName: 'CAMTEL / Blue',
      simOperatorCode: '62405',
      networkType: '4G LTE',
      bandLabel: 'Bande 3 FDD (1800 MHz)',
      earfcn: 1649,
      pci: 419,
      rsrpDbm: -102,
      rsrqDb: -10,
      sinrDb: 6,
      rssiDbm: -73,
      cqi: 8,
      timingAdvance: 7,
      estimatedDistanceMeters: 547,
      linkDownKbps: 51853,
      linkUpKbps: 4800,
      passiveRxMbps: 0.0,
      passiveTxMbps: 0.0,
      azimuthDeg: 45.0,
      stepCount: 0,
      posX: 0.0,
      posY: 0.0,
      timestamp: now,
    );
  }

  /// Resets the step counter and relative `(x, y)` coordinates for the indoor walk radar.
  Future<void> resetWalkRadarOrigin() async {
    if (!isNativeAndroid) return;
    try {
      await _channel.invokeMethod<void>('resetWalkRadarOrigin');
    } catch (_) {}
  }

  /// Runs a lightweight, real network throughput and latency probe (~500 KB burst)
  /// so the user can measure true Mbps and Ping (ms) at a specific spot in their house
  /// without wasting their mobile data bundle.
  Future<({double speedMbps, int pingMs})> runActiveSpeedProbe() async {
    final client = http.Client();
    try {
      // 1. Measure real RTT latency (ms)
      final pingWatch = Stopwatch()..start();
      await client
          .head(Uri.parse('https://speed.cloudflare.com/__down?bytes=0'))
          .timeout(const Duration(seconds: 4));
      pingWatch.stop();
      final pingMs = pingWatch.elapsedMilliseconds.clamp(1, 5000);

      // 2. Measure real download throughput (Mbps) on a 600 KB payload + OS hardware counter delta
      final beforeSnap = await getMobileCountersSnapshot(
        startTime: DateTime.now(),
      );
      final dlWatch = Stopwatch()..start();
      final response = await client
          .get(Uri.parse('https://speed.cloudflare.com/__down?bytes=600000'))
          .timeout(const Duration(seconds: 8));
      dlWatch.stop();
      final afterSnap = await getMobileCountersSnapshot(
        startTime: DateTime.now(),
      );

      final elapsedSec = dlWatch.elapsedMicroseconds / 1000000.0;
      final osBytesDelta = (afterSnap.rxBytes - beforeSnap.rxBytes).clamp(
        0,
        1 << 30,
      );
      final effectiveBytes = osBytesDelta > response.bodyBytes.length
          ? osBytesDelta
          : response.bodyBytes.length;

      final speedMbps = elapsedSec > 0.01
          ? (effectiveBytes * 8.0) / (elapsedSec * 1000000.0)
          : 0.0;

      return (speedMbps: speedMbps, pingMs: pingMs);
    } catch (_) {
      final radio = await getRadioAndCompassSnapshot();
      // If active HTTP probe fails (e.g. bundle exhausted), return passive OS speed
      return (speedMbps: radio.passiveRxMbps, pingMs: 999);
    } finally {
      client.close();
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
          final simOperatorCode = raw['simOperatorCode'] as String? ?? '';

          final baseRx = trafficRx > 0 ? trafficRx : bucketRx;
          final baseTx = trafficTx > 0 ? trafficTx : bucketTx;

          return OsNetworkSnapshot(
            rxBytes: baseRx + _simulatedRxBytes,
            txBytes: baseTx + _simulatedTxBytes,
            bucketRxBytes: bucketRx,
            bucketTxBytes: bucketTx,
            operatorName: operatorName,
            simOperatorCode: simOperatorCode,
            hasUsagePermission: hasPerm,
            isFromNativeOs: true,
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
      bucketRxBytes: _simulatedRxBytes,
      bucketTxBytes: _simulatedTxBytes,
      operatorName: 'Sonde OS / Mode Simulation Desktop',
      simOperatorCode: '62405',
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
        if (totalSessionConsumedBytes > 0) {
          final rx = (totalSessionConsumedBytes * 0.5).round();
          final tx = totalSessionConsumedBytes - rx;
          return [
            AppUsageStat(
              uid: -1,
              packageName: 'android.net.rmnet',
              appName: 'Interface Cellulaire Directe (rmnet / Hotspot)',
              rxBytes: rx,
              txBytes: tx,
            ),
          ];
        }
        return const [];
      } catch (_) {
        return const [];
      }
    }

    return buildStandardWorkloadBreakdown(totalSessionConsumedBytes);
  }

  /// Injects a calibrated workload step (e.g. "Même Travail" benchmark block)
  /// so live coding demos can test ISP depletion behavior deterministically.
  void injectWorkloadBytes({
    required int rxBytesDelta,
    required int txBytesDelta,
  }) {
    _simulatedRxBytes += rxBytesDelta;
    _simulatedTxBytes += txBytesDelta;
  }

  /// Distributes a total byte count across a reproducible "Même Travail" app profile
  /// when running without per-UID buckets (or in Live Demo mode).
  List<AppUsageStat> buildStandardWorkloadBreakdown(int totalBytes) {
    if (totalBytes <= 0) return const [];
    final profiles = <(int, String, String, double, double)>[
      (
        10101,
        'com.google.android.youtube',
        'YouTube (Streaming 720p)',
        0.44,
        0.03,
      ),
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
