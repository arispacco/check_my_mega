import '../models/data_plan_session.dart';
import '../models/gemma_audit_report.dart';

/// Pure mathematical engine that computes OS byte conversions, veracity scores,
/// phantom data loss, and real cost per delivered Gigabyte in Francs CFA (XAF).
class MegaCalculator {
  const MegaCalculator();

  static const int bytesPerDecimalMb = 1000000;
  static const int bytesPerBinaryMib = 1048576;

  /// Converts raw bytes to commercial decimal Megabytes (1 MB = 10^6 bytes).
  double bytesToDecimalMb(int bytes) => bytes / bytesPerDecimalMb;

  /// Converts raw bytes to binary Mebibytes (1 MiB = 2^20 bytes).
  double bytesToBinaryMib(int bytes) => bytes / bytesPerBinaryMib;

  /// Computes the Veracity Score (%) comparing OS-measured MB to promised MB.
  double computeVeracityScorePct({
    required int totalConsumedBytes,
    required double promisedMb,
    bool useBinaryMib = false,
  }) {
    if (promisedMb <= 0) return 0.0;
    final measured = useBinaryMib
        ? bytesToBinaryMib(totalConsumedBytes)
        : bytesToDecimalMb(totalConsumedBytes);
    return ((measured / promisedMb) * 100.0).clamp(0.0, 120.0);
  }

  /// Computes the missing ("phantom") megabytes when an ISP marks a plan exhausted.
  double computeMissingMb({
    required int totalConsumedBytes,
    required double promisedMb,
  }) {
    final measuredMb = bytesToDecimalMb(totalConsumedBytes);
    return (promisedMb - measuredMb).clamp(0.0, promisedMb);
  }

  /// Computes the true effective cost in FCFA per 1,000 MB actually delivered to the OS.
  double computeRealCostPerGbXaf({
    required int priceXaf,
    required int totalConsumedBytes,
  }) {
    final measuredMb = bytesToDecimalMb(totalConsumedBytes);
    if (measuredMb <= 0.001) return priceXaf.toDouble();
    final deliveredGb = measuredMb / 1000.0;
    return priceXaf / deliveredGb;
  }

  /// Computes how much of the OS interface bytes are accounted for by individual apps.
  double computeAppAccountingCoveragePct(DataPlanSession session) {
    if (session.totalConsumedBytes <= 0) return 100.0;
    final sumAppBytes = session.appUsages.fold<int>(
      0,
      (sum, item) => sum + item.totalBytes,
    );
    if (sumAppBytes <= 0) return 100.0;
    return ((sumAppBytes / session.totalConsumedBytes) * 100.0).clamp(
      0.0,
      100.0,
    );
  }

  /// Builds a deterministic offline audit report when Gemma 4 is offline or as baseline.
  GemmaAuditReport buildDeterministicReport(
    DataPlanSession session, {
    String modelLabel = 'Moteur Mathématique CheckMyMega (Hors-ligne)',
  }) {
    final measuredMb = bytesToDecimalMb(session.totalConsumedBytes);
    final measuredMib = bytesToBinaryMib(session.totalConsumedBytes);
    final rxMb = bytesToDecimalMb(session.consumedRxBytes);
    final txMb = bytesToDecimalMb(session.consumedTxBytes);
    final scorePct = computeVeracityScorePct(
      totalConsumedBytes: session.totalConsumedBytes,
      promisedMb: session.promisedMb,
    );
    final missingMb = computeMissingMb(
      totalConsumedBytes: session.totalConsumedBytes,
      promisedMb: session.promisedMb,
    );
    final realCostPerGb = computeRealCostPerGbXaf(
      priceXaf: session.priceXaf,
      totalConsumedBytes: session.totalConsumedBytes,
    );
    final verdict = AuditVerdict.fromScore(scorePct);

    final topAppsText = session.appUsages
        .take(3)
        .map((a) => '${a.appName} (${a.totalMbDecimal.toStringAsFixed(1)} Mo)')
        .join(', ');

    final technicalExplanation =
        "Sur les ${session.promisedMb.toStringAsFixed(0)} Mo promis par ${session.isp.displayName} "
        "(${session.planName} à ${session.priceXaf} FCFA), l'interface cellulaire du système (OS) "
        "a enregistré exactement ${measuredMb.toStringAsFixed(1)} Mo en base décimale "
        "(${measuredMib.toStringAsFixed(1)} Mio en base binaire), répartis en "
        "${rxMb.toStringAsFixed(1)} Mo téléchargés (Rx) et ${txMb.toStringAsFixed(1)} Mo envoyés (Tx). "
        "${topAppsText.isNotEmpty ? 'Principales applications : $topAppsText. ' : ''}"
        "L'écart non livré ou absorbé par les arrondis de session opérateur est de ${missingMb.toStringAsFixed(1)} Mo "
        "(Coût réel : ${realCostPerGb.toStringAsFixed(0)} FCFA / Go réellement reçu).";

    final cameroonSummary = switch (verdict) {
      AuditVerdict.conforme =>
        'Respect ! Sur le forfait ${session.isp.shortName} (${session.priceXaf} FCFA), '
            'ton téléphone a bien reçu ${measuredMb.toStringAsFixed(0)} Mo sur ${session.promisedMb.toStringAsFixed(0)} Mo (${scorePct.toStringAsFixed(1)}%). '
            'Le forfait tient la route pour ton travail !',
      AuditVerdict.suspect =>
        'Attention chez ${session.isp.shortName} : sur ${session.promisedMb.toStringAsFixed(0)} Mo payés ${session.priceXaf} FCFA, '
            'le système Android ne compte que ${measuredMb.toStringAsFixed(0)} Mo (${missingMb.toStringAsFixed(0)} Mo disparus en route).',
      AuditVerdict.anomalieCritique =>
        'Les mégas ont fondu chez ${session.isp.shortName} ! Tu as payé ${session.priceXaf} FCFA pour '
            '${session.promisedMb.toStringAsFixed(0)} Mo, mais ton téléphone n\'a réellement reçu que '
            '${measuredMb.toStringAsFixed(0)} Mo (${scorePct.toStringAsFixed(1)}%). '
            'Il manque ${missingMb.toStringAsFixed(0)} Mo : le Go réel te revient à ${realCostPerGb.toStringAsFixed(0)} FCFA !',
    };

    return GemmaAuditReport(
      verdict: verdict,
      veracityScorePct: scorePct,
      missingMb: missingMb,
      realCostPerGbXaf: realCostPerGb,
      technicalExplanation: technicalExplanation,
      cameroonSummary: cameroonSummary,
      modelUsed: modelLabel,
      generatedAt: DateTime.now(),
    );
  }
}
