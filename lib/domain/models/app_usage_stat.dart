/// Represents mobile data consumption for a single application (UID) measured
/// by the operating system's network interface.
class AppUsageStat {
  const AppUsageStat({
    required this.uid,
    required this.packageName,
    required this.appName,
    required this.rxBytes,
    required this.txBytes,
  });

  final int uid;
  final String packageName;
  final String appName;
  final int rxBytes;
  final int txBytes;

  int get totalBytes => rxBytes + txBytes;

  /// Commercial megabytes (Base 10: 1 MB = 1,000,000 bytes).
  double get totalMbDecimal => totalBytes / 1000000.0;

  /// Binary mebibytes (Base 2: 1 MiB = 1,048,576 bytes).
  double get totalMibBinary => totalBytes / 1048576.0;

  AppUsageStat copyWith({
    int? uid,
    String? packageName,
    String? appName,
    int? rxBytes,
    int? txBytes,
  }) {
    return AppUsageStat(
      uid: uid ?? this.uid,
      packageName: packageName ?? this.packageName,
      appName: appName ?? this.appName,
      rxBytes: rxBytes ?? this.rxBytes,
      txBytes: txBytes ?? this.txBytes,
    );
  }

  Map<String, dynamic> toJson() => {
    'uid': uid,
    'packageName': packageName,
    'appName': appName,
    'rxBytes': rxBytes,
    'txBytes': txBytes,
    'totalMbDecimal': double.parse(totalMbDecimal.toStringAsFixed(2)),
  };
}
