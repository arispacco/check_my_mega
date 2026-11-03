import 'isp_provider.dart';

/// Real SMS message read from Android `content://sms/inbox` or intercepted via `SMS_RECEIVED`.
class OperatorSmsMessage {
  const OperatorSmsMessage({
    required this.id,
    required this.address,
    required this.dateMs,
    required this.body,
    required this.isp,
    required this.isActivation,
    required this.isExhaustion,
    required this.promisedMb,
    required this.priceXaf,
    required this.planName,
  });

  final int id;
  final String address;
  final int dateMs;
  final String body;
  final IspProvider isp;
  final bool isActivation;
  final bool isExhaustion;
  final double promisedMb;
  final int priceXaf;
  final String planName;

  DateTime get timestamp => DateTime.fromMillisecondsSinceEpoch(dateMs);
}

/// Live snapshot of the hardware cellular radio (`CellInfoLte`), magnetic compass,
/// step detector, and network throughput.
class RadioCompassSnapshot {
  const RadioCompassSnapshot({
    required this.operatorName,
    required this.simOperatorCode,
    required this.networkType,
    required this.bandLabel,
    required this.earfcn,
    required this.pci,
    required this.rsrpDbm,
    required this.rsrqDb,
    required this.sinrDb,
    required this.rssiDbm,
    required this.cqi,
    required this.timingAdvance,
    required this.estimatedDistanceMeters,
    required this.linkDownKbps,
    required this.linkUpKbps,
    required this.passiveRxMbps,
    required this.passiveTxMbps,
    required this.azimuthDeg,
    required this.stepCount,
    required this.posX,
    required this.posY,
    required this.timestamp,
  });

  final String operatorName;
  final String simOperatorCode;
  final String networkType;
  final String bandLabel;
  final int earfcn;
  final int pci;
  final int rsrpDbm;
  final int rsrqDb;
  final int sinrDb;
  final int rssiDbm;
  final int cqi;
  final int timingAdvance;
  final int estimatedDistanceMeters;
  final int linkDownKbps;
  final int linkUpKbps;
  final double passiveRxMbps;
  final double passiveTxMbps;
  final double azimuthDeg;
  final int stepCount;
  final double posX;
  final double posY;
  final DateTime timestamp;

  /// Composite radio & throughput quality score from 0 to 100.
  /// Combines RSRP (-120 to -75 dBm), SINR (-5 to +20 dB), and RSRQ (-20 to -5 dB).
  double get signalQualityScore {
    final rsrpNorm = ((rsrpDbm + 120.0) / 45.0).clamp(0.0, 1.0);
    final sinrNorm = ((sinrDb + 5.0) / 25.0).clamp(0.0, 1.0);
    final rsrqNorm = ((rsrqDb + 20.0) / 15.0).clamp(0.0, 1.0);
    return ((rsrpNorm * 0.45 + sinrNorm * 0.40 + rsrqNorm * 0.15) * 100.0)
        .clamp(0.0, 100.0);
  }

  String get cardinalDirection {
    const dirs = ['N', 'NE', 'E', 'SE', 'S', 'SO', 'O', 'NO'];
    final idx = ((azimuthDeg + 22.5) % 360.0 / 45.0).floor() % 8;
    return dirs[idx];
  }

  String get signalQualityLabel {
    if (rsrpDbm >= -85 && sinrDb >= 10) return 'Excellent (Idéal Téléchargement)';
    if (rsrpDbm >= -95 && sinrDb >= 5) return 'Bon (4G Stable)';
    if (rsrpDbm >= -105) return 'Moyen (Atténuation Murs Indoor)';
    return 'Faible (Zone d\'ombre / Risque de coupure)';
  }
}

/// A recorded indoor spot in the user's house during a Signal & Speed Walk ("Boussole Camtel").
class IndoorSignalSpot {
  const IndoorSignalSpot({
    required this.id,
    required this.roomLabel,
    required this.rsrpDbm,
    required this.rsrqDb,
    required this.sinrDb,
    required this.measuredSpeedMbps,
    required this.pingMs,
    required this.azimuthDeg,
    required this.posX,
    required this.posY,
    required this.stepMark,
    required this.bandLabel,
    required this.recordedAt,
  });

  final String id;
  final String roomLabel;
  final int rsrpDbm;
  final int rsrqDb;
  final int sinrDb;
  final double measuredSpeedMbps;
  final int pingMs;
  final double azimuthDeg;
  final double posX;
  final double posY;
  final int stepMark;
  final String bandLabel;
  final DateTime recordedAt;

  /// Combined Spot Score (0–100) weighting real speed (Mbps), SINR, and RSRP.
  double get compositeSpotScore {
    final rsrpNorm = ((rsrpDbm + 120.0) / 45.0).clamp(0.0, 1.0);
    final sinrNorm = ((sinrDb + 5.0) / 25.0).clamp(0.0, 1.0);
    final speedNorm = (measuredSpeedMbps / 25.0).clamp(0.0, 1.0);
    return ((rsrpNorm * 0.35 + sinrNorm * 0.30 + speedNorm * 0.35) * 100.0)
        .clamp(0.0, 100.0);
  }
}
