import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../../../domain/models/signal_radar_spot.dart';
import '../../../core/theme/app_theme.dart';
import '../view_models/audit_view_model.dart';

/// Tab 2: Precision Aviation / Wayfinder 360° Magnetic Compass, Cellular LTE RF Radar,
/// Active Speed Probe, and Camtel Indoor Best-Spot Finder.
class SignalCompassTab extends StatefulWidget {
  const SignalCompassTab({super.key, required this.viewModel});

  final AuditViewModel viewModel;

  @override
  State<SignalCompassTab> createState() => _SignalCompassTabState();
}

class _SignalCompassTabState extends State<SignalCompassTab> {
  static const List<({String label, IconData icon})> _roomPresets = [
    (label: 'Salon', icon: Icons.weekend_rounded),
    (label: 'Près Fenêtre', icon: Icons.sensor_window_rounded),
    (label: 'Chambre', icon: Icons.bed_rounded),
    (label: 'Balcon / Véranda', icon: Icons.balcony_rounded),
    (label: 'Bureau', icon: Icons.desk_rounded),
    (label: 'Hauteur Meuble', icon: Icons.shelves),
  ];

  String _selectedRoom = _roomPresets.first.label;

  static String _cardinalFromDeg(double deg) {
    const dirs = ['N', 'NE', 'E', 'SE', 'S', 'SO', 'O', 'NO'];
    final idx = ((deg + 22.5) % 360.0 / 45.0).floor() % 8;
    return dirs[idx];
  }

  @override
  Widget build(BuildContext context) {
    final vm = widget.viewModel;
    final snap = vm.radioSnapshot;
    if (snap == null) {
      return const Center(child: CircularProgressIndicator());
    }

    final bestSpot = vm.bestIndoorSpot;
    final bearingToBest = vm.bearingToBestSpotDeg;
    final distToBest = vm.distanceToBestSpotMeters;
    final signalColor = _qualityColor(snap.rsrpDbm);

    // Relative turn angle (-180..+180) toward best spot
    final double? relTurnDeg = bearingToBest != null
        ? ((bearingToBest - vm.compassAzimuthNotifier.value + 540.0) % 360.0) -
              180.0
        : null;
    final bool isTargetLocked =
        relTurnDeg != null && (relTurnDeg.abs() <= 18.0 || distToBest < 0.8);

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 28),
      children: [
        // 1. Wayfinder 360° Precision Compass & RF Target-Lock Card
        GlassTelemetryCard(
          accentColor: isTargetLocked ? AppTheme.neonEmerald : signalColor,
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              TelemetrySectionHeader(
                icon: Icons.explore_rounded,
                title: 'Boussole Wayfinder & Radar RF',
                subtitle:
                    'Scannez vos pièces et suivez le vecteur émeraude vers le point de débit maximal',
                accentColor: isTargetLocked ? AppTheme.neonEmerald : signalColor,
                trailing: _LiveSignalBadge(
                  networkType: snap.networkType,
                  rsrpDbm: snap.rsrpDbm,
                  color: signalColor,
                ),
              ),
              const SizedBox(height: 18),

              // Top HUD Bar: Carrier • Band • Distance
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 14,
                  vertical: 9,
                ),
                decoration: BoxDecoration(
                  color: AppTheme.obsidianBg.withValues(alpha: 0.65),
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: AppTheme.borderSubtle),
                ),
                child: Wrap(
                  spacing: 14,
                  runSpacing: 6,
                  alignment: WrapAlignment.spaceAround,
                  children: [
                    _HudMiniItem(
                      icon: Icons.sim_card_rounded,
                      label: snap.operatorName,
                      color: AppTheme.sky,
                    ),
                    _HudMiniItem(
                      icon: Icons.settings_input_antenna_rounded,
                      label: snap.bandLabel,
                      color: AppTheme.emerald,
                    ),
                    _HudMiniItem(
                      icon: Icons.social_distance_rounded,
                      label: snap.estimatedDistanceMeters > 0
                          ? 'Antenne ~${snap.estimatedDistanceMeters}m'
                          : 'Cellule PCI #${snap.pci}',
                      color: AppTheme.amber,
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 22),

              // Locked 60–120 FPS RepaintBoundary Compass Instrument driven by ValueNotifier
              Center(
                child: RepaintBoundary(
                  child: ValueListenableBuilder<double>(
                    valueListenable: vm.compassAzimuthNotifier,
                    builder: (context, liveAzimuth, _) {
                      final normAzimuth = ((liveAzimuth % 360.0) + 360.0) % 360.0;
                      final liveRelTurn = bearingToBest != null
                          ? ((bearingToBest - normAzimuth + 540.0) % 360.0) -
                                180.0
                          : null;
                      final liveLocked =
                          liveRelTurn != null &&
                          (liveRelTurn.abs() <= 18.0 || distToBest < 0.8);
                      return SizedBox(
                        width: 296,
                        height: 296,
                        child: CustomPaint(
                          painter: _WayfinderCompassPainter(
                            azimuthDeg: normAzimuth,
                            bearingToBestSpotDeg: bearingToBest,
                            currentPosX: snap.posX,
                            currentPosY: snap.posY,
                            spots: vm.indoorSpots,
                            bestSpotId: bestSpot?.id,
                            signalColor: signalColor,
                            isTargetLocked: liveLocked,
                          ),
                          child: Center(
                            child: Container(
                              width: 128,
                              height: 128,
                              decoration: BoxDecoration(
                                shape: BoxShape.circle,
                                gradient: RadialGradient(
                                  colors: [
                                    const Color(0xFF0E1729),
                                    const Color(
                                      0xFF060A12,
                                    ).withValues(alpha: 0.96),
                                  ],
                                ),
                                border: Border.all(
                                  color: liveLocked
                                      ? AppTheme.neonEmerald.withValues(
                                          alpha: 0.7,
                                        )
                                      : signalColor.withValues(alpha: 0.45),
                                  width: 1.6,
                                ),
                                boxShadow: [
                                  BoxShadow(
                                    color: (liveLocked
                                            ? AppTheme.neonEmerald
                                            : signalColor)
                                        .withValues(alpha: 0.22),
                                    blurRadius: 18,
                                  ),
                                ],
                              ),
                              child: Column(
                                mainAxisAlignment: MainAxisAlignment.center,
                                children: [
                                  Text(
                                    _cardinalFromDeg(normAzimuth),
                                    style: TextStyle(
                                      fontSize: 12,
                                      fontWeight: FontWeight.w800,
                                      letterSpacing: 1.5,
                                      color: liveLocked
                                          ? AppTheme.neonEmerald
                                          : signalColor,
                                    ),
                                  ),
                                  Text(
                                    '${normAzimuth.toStringAsFixed(0)}°',
                                    style: const TextStyle(
                                      fontSize: 30,
                                      fontWeight: FontWeight.w900,
                                      color: AppTheme.textPrimary,
                                      height: 1.05,
                                      letterSpacing: -0.8,
                                    ),
                                  ),
                                  const SizedBox(height: 4),
                                  Container(
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 8,
                                      vertical: 2,
                                    ),
                                    decoration: BoxDecoration(
                                      color: signalColor.withValues(alpha: 0.16),
                                      borderRadius: BorderRadius.circular(10),
                                    ),
                                    child: Text(
                                      '${snap.rsrpDbm} dBm',
                                      style: TextStyle(
                                        fontSize: 11,
                                        fontWeight: FontWeight.w800,
                                        color: signalColor,
                                      ),
                                    ),
                                  ),
                                  const SizedBox(height: 2),
                                  Text(
                                    'SINR ${snap.sinrDb >= 0 ? "+${snap.sinrDb}" : snap.sinrDb} dB',
                                    style: const TextStyle(
                                      fontSize: 10,
                                      color: AppTheme.textSecondary,
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ),
                      );
                    },
                  ),
                ),
              ),
              const SizedBox(height: 20),

              // Dynamic Target Lock Guidance Banner
              if (bestSpot != null && relTurnDeg != null)
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 14,
                    vertical: 12,
                  ),
                  decoration: BoxDecoration(
                    color: (isTargetLocked
                            ? AppTheme.neonEmerald
                            : AppTheme.sky)
                        .withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(
                      color: (isTargetLocked
                              ? AppTheme.neonEmerald
                              : AppTheme.sky)
                          .withValues(alpha: 0.5),
                    ),
                  ),
                  child: Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.all(8),
                        decoration: BoxDecoration(
                          color: (isTargetLocked
                                  ? AppTheme.neonEmerald
                                  : AppTheme.sky)
                              .withValues(alpha: 0.2),
                          shape: BoxShape.circle,
                        ),
                        child: Icon(
                          isTargetLocked
                              ? Icons.gps_fixed_rounded
                              : Icons.navigation_rounded,
                          color: isTargetLocked
                              ? AppTheme.neonEmerald
                              : AppTheme.sky,
                          size: 20,
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              isTargetLocked
                                  ? 'VERROUILLÉ SUR LE MEILLEUR COIN : ${bestSpot.roomLabel.toUpperCase()}'
                                  : 'CAP VERS LE MEILLEUR COIN : ${bestSpot.roomLabel.toUpperCase()}',
                              style: TextStyle(
                                fontSize: 11.5,
                                fontWeight: FontWeight.w800,
                                color: isTargetLocked
                                    ? AppTheme.neonEmerald
                                    : AppTheme.sky,
                                letterSpacing: 0.4,
                              ),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              '${_guidanceText(relTurnDeg, distToBest)} • '
                              'Débit max ${bestSpot.measuredSpeedMbps.toStringAsFixed(1)} Mbps (${bestSpot.rsrpDbm} dBm)',
                              style: const TextStyle(
                                fontSize: 12.5,
                                color: AppTheme.textPrimary,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                )
              else
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 14,
                    vertical: 11,
                  ),
                  decoration: BoxDecoration(
                    color: AppTheme.obsidianBg.withValues(alpha: 0.55),
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(color: AppTheme.borderSubtle),
                  ),
                  child: const Row(
                    children: [
                      Icon(
                        Icons.directions_walk_rounded,
                        color: AppTheme.emerald,
                        size: 20,
                      ),
                      SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          'Sélectionnez une pièce ci-dessous et appuyez sur « Capturer Signal » ou « Speedtest 4G » dans 2 ou 3 coins pour activer le guidage directionnel.',
                          style: TextStyle(
                            fontSize: 12,
                            color: AppTheme.textSecondary,
                            height: 1.35,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),

              const SizedBox(height: 18),

              // Room Presets Horizontal Selector
              const Text(
                'PIÈCE OU EMPLACEMENT ACTUEL',
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 0.9,
                  color: AppTheme.textSecondary,
                ),
              ),
              const SizedBox(height: 8),
              SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: Row(
                  children: _roomPresets.map((preset) {
                    final selected = _selectedRoom == preset.label;
                    return Padding(
                      padding: const EdgeInsets.only(right: 8),
                      child: ChoiceChip(
                        selected: selected,
                        showCheckmark: false,
                        avatar: Icon(
                          preset.icon,
                          size: 16,
                          color: selected
                              ? AppTheme.neonEmerald
                              : AppTheme.textSecondary,
                        ),
                        label: Text(
                          preset.label,
                          style: TextStyle(
                            fontSize: 12.5,
                            fontWeight: selected
                                ? FontWeight.w800
                                : FontWeight.w500,
                            color: selected
                                ? AppTheme.textPrimary
                                : AppTheme.textSecondary,
                          ),
                        ),
                        onSelected: (_) {
                          setState(() {
                            _selectedRoom = preset.label;
                          });
                        },
                      ),
                    );
                  }).toList(),
                ),
              ),
              const SizedBox(height: 14),

              // Primary Action Controls
              Wrap(
                spacing: 10,
                runSpacing: 10,
                children: [
                  OutlinedButton.icon(
                    icon: const Icon(
                      Icons.radar_rounded,
                      size: 18,
                      color: AppTheme.neonEmerald,
                    ),
                    label: Text('Capturer $_selectedRoom (0 Mo)'),
                    onPressed: () => vm.recordInstantSpot(_selectedRoom),
                  ),
                  FilledButton.icon(
                    icon: vm.isRunningSpeedTest
                        ? const SizedBox(
                            width: 16,
                            height: 16,
                            child: CircularProgressIndicator(
                              strokeWidth: 2.2,
                              color: Color(0xFF04130D),
                            ),
                          )
                        : const Icon(Icons.speed_rounded, size: 18),
                    label: Text(
                      vm.isRunningSpeedTest
                          ? 'Mesure débit en cours...'
                          : 'Speedtest 4G ($_selectedRoom)',
                    ),
                    onPressed: vm.isRunningSpeedTest
                        ? null
                        : () => vm.runSpeedTestAndRecordSpot(
                            spotLabel: _selectedRoom,
                          ),
                  ),
                  if (vm.indoorSpots.isNotEmpty)
                    IconButton.outlined(
                      tooltip: 'Réinitialiser les points du radar',
                      icon: const Icon(Icons.restart_alt_rounded, size: 19),
                      onPressed: vm.clearIndoorRadarSpots,
                    ),
                ],
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),

        // 2. Bento Grid of Live Cellular Modem Telemetry
        GlassTelemetryCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const TelemetrySectionHeader(
                icon: Icons.cell_tower_rounded,
                title: 'Télémétrie Radio Matérielle 4G/LTE',
                subtitle:
                    'Mesures physiques directes du modem Android (CellSignalStrengthLte)',
                accentColor: AppTheme.sky,
              ),
              const SizedBox(height: 16),
              LayoutBuilder(
                builder: (context, constraints) {
                  final tileWidth = (constraints.maxWidth - 10) / 2;
                  return Wrap(
                    spacing: 10,
                    runSpacing: 10,
                    children: [
                      _BentoMetricTile(
                        width: tileWidth,
                        icon: Icons.signal_cellular_alt_rounded,
                        label: 'PUISSANCE RSRP',
                        value: '${snap.rsrpDbm} dBm',
                        subValue: snap.signalQualityLabel,
                        progress: ((snap.rsrpDbm + 120.0) / 45.0).clamp(
                          0.05,
                          1.0,
                        ),
                        accentColor: signalColor,
                      ),
                      _BentoMetricTile(
                        width: tileWidth,
                        icon: Icons.graphic_eq_rounded,
                        label: 'SINR & QUALITÉ CQI',
                        value:
                            '${snap.sinrDb >= 0 ? "+${snap.sinrDb}" : snap.sinrDb} dB',
                        subValue: 'RSRQ ${snap.rsrqDb} dB • CQI ${snap.cqi}/15',
                        progress: ((snap.sinrDb + 5.0) / 25.0).clamp(0.05, 1.0),
                        accentColor: snap.sinrDb >= 5
                            ? AppTheme.emerald
                            : AppTheme.amber,
                      ),
                      _BentoMetricTile(
                        width: tileWidth,
                        icon: Icons.speed_rounded,
                        label: 'DÉBIT & LATENCE',
                        value:
                            '${(vm.lastActiveSpeedMbps ?? snap.passiveRxMbps).toStringAsFixed(2)} Mbps',
                        subValue: vm.lastActivePingMs != null
                            ? 'Ping mesuré : ${vm.lastActivePingMs} ms'
                            : 'Capacité lien : ${(snap.linkDownKbps / 1000).toStringAsFixed(0)} Mbps',
                        progress:
                            ((vm.lastActiveSpeedMbps ?? snap.passiveRxMbps) /
                                    25.0)
                                .clamp(0.08, 1.0),
                        accentColor: AppTheme.neonEmerald,
                      ),
                      _BentoMetricTile(
                        width: tileWidth,
                        icon: Icons.compass_calibration_rounded,
                        label: 'DISTANCE ANTENNE',
                        value: snap.estimatedDistanceMeters > 0
                            ? '~${snap.estimatedDistanceMeters} m'
                            : 'PCI #${snap.pci}',
                        subValue:
                            'EARFCN ${snap.earfcn} • ${snap.stepCount} pas',
                        progress: 0.72,
                        accentColor: AppTheme.violet,
                      ),
                    ],
                  );
                },
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),

        // 3. Podium Ranking of House Spots ("Où poser le téléphone ?")
        GlassTelemetryCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              TelemetrySectionHeader(
                icon: Icons.emoji_events_rounded,
                title: 'Classement des Coins de la Maison',
                subtitle:
                    'Posez votre téléphone au #1 pour vos téléchargements ou en Hotspot Wi-Fi',
                accentColor: AppTheme.amber,
                trailing: Text(
                  '${vm.indoorSpots.length} pt(s)',
                  style: const TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w800,
                    color: AppTheme.amber,
                  ),
                ),
              ),
              const SizedBox(height: 14),
              if (vm.indoorSpots.isEmpty)
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: AppTheme.obsidianBg.withValues(alpha: 0.5),
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(color: AppTheme.borderSubtle),
                  ),
                  child: const Row(
                    children: [
                      Icon(
                        Icons.touch_app_rounded,
                        color: AppTheme.amber,
                        size: 22,
                      ),
                      SizedBox(width: 12),
                      Expanded(
                        child: Text(
                          'Aucun coin scanné. Testez 2 ou 3 pièces (ex. Salon, Près Fenêtre, Véranda) pour découvrir où Camtel passe le mieux chez vous.',
                          style: TextStyle(
                            fontSize: 12.5,
                            color: AppTheme.textSecondary,
                            height: 1.35,
                          ),
                        ),
                      ),
                    ],
                  ),
                )
              else
                ..._sortedSpots(vm.indoorSpots).asMap().entries.map((entry) {
                  final rank = entry.key + 1;
                  final spot = entry.value;
                  final isBest = bestSpot?.id == spot.id;
                  final spotColor = isBest
                      ? AppTheme.neonEmerald
                      : _qualityColor(spot.rsrpDbm);

                  return Container(
                    margin: const EdgeInsets.only(bottom: 10),
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: isBest
                          ? AppTheme.emerald.withValues(alpha: 0.12)
                          : AppTheme.obsidianBg.withValues(alpha: 0.55),
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(
                        color: isBest
                            ? AppTheme.neonEmerald.withValues(alpha: 0.65)
                            : AppTheme.borderSubtle,
                        width: isBest ? 1.5 : 1.0,
                      ),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 9,
                                vertical: 4,
                              ),
                              decoration: BoxDecoration(
                                color: spotColor.withValues(alpha: 0.18),
                                borderRadius: BorderRadius.circular(8),
                              ),
                              child: Text(
                                isBest ? '🏆 #1 MEILLEUR COIN' : '#$rank',
                                style: TextStyle(
                                  fontSize: 11,
                                  fontWeight: FontWeight.w900,
                                  color: spotColor,
                                ),
                              ),
                            ),
                            const SizedBox(width: 10),
                            Expanded(
                              child: Text(
                                spot.roomLabel,
                                style: const TextStyle(
                                  fontWeight: FontWeight.w800,
                                  fontSize: 15,
                                  color: AppTheme.textPrimary,
                                ),
                              ),
                            ),
                            Text(
                              '${spot.compositeSpotScore.toStringAsFixed(0)} / 100',
                              style: TextStyle(
                                fontWeight: FontWeight.w900,
                                fontSize: 14,
                                color: spotColor,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 10),
                        ClipRRect(
                          borderRadius: BorderRadius.circular(6),
                          child: LinearProgressIndicator(
                            value: (spot.compositeSpotScore / 100.0).clamp(
                              0.05,
                              1.0,
                            ),
                            minHeight: 6,
                            backgroundColor: Colors.white10,
                            valueColor: AlwaysStoppedAnimation<Color>(spotColor),
                          ),
                        ),
                        const SizedBox(height: 8),
                        Wrap(
                          spacing: 12,
                          runSpacing: 4,
                          children: [
                            _SpotStatChip(
                              label: 'Débit',
                              value:
                                  '${spot.measuredSpeedMbps.toStringAsFixed(2)} Mbps',
                            ),
                            _SpotStatChip(
                              label: 'Ping',
                              value: '${spot.pingMs} ms',
                            ),
                            _SpotStatChip(
                              label: 'RSRP',
                              value: '${spot.rsrpDbm} dBm',
                            ),
                            _SpotStatChip(
                              label: 'SINR',
                              value:
                                  '${spot.sinrDb >= 0 ? "+${spot.sinrDb}" : spot.sinrDb} dB',
                            ),
                            _SpotStatChip(
                              label: 'Cap',
                              value: '${spot.azimuthDeg.toStringAsFixed(0)}°',
                            ),
                          ],
                        ),
                      ],
                    ),
                  );
                }),
            ],
          ),
        ),
        const SizedBox(height: 16),

        // 4. Structured Camtel Coverage Diagnosis & Actionable Tips
        GlassTelemetryCard(
          accentColor: AppTheme.emerald,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const TelemetrySectionHeader(
                icon: Icons.tips_and_updates_rounded,
                title: 'Diagnostic Couverture & Optimisation Camtel',
                subtitle:
                    'Conclusion matérielle sur votre zone et astuces pour maximiser votre débit',
                accentColor: AppTheme.emerald,
              ),
              const SizedBox(height: 14),
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: AppTheme.obsidianBg.withValues(alpha: 0.6),
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: AppTheme.borderSubtle),
                ),
                child: SelectableText(
                  vm.buildCamtelCoverageAndTipsReport(),
                  style: const TextStyle(
                    fontSize: 13,
                    height: 1.5,
                    color: AppTheme.textPrimary,
                  ),
                ),
              ),
              const SizedBox(height: 12),
              const _TipRow(
                icon: Icons.wifi_tethering_rounded,
                title: 'Astuce #1 — Relais Hotspot Wi-Fi au Point Culminant',
                body:
                    'Sur la Bande 3 (1800 MHz) et Bande 40 TDD (2300 MHz), les murs en béton absorbent 12 à 18 dB. Posez le téléphone sur le coin #1 identifié par la boussole et activez le Partage de connexion Wi-Fi.',
              ),
              const SizedBox(height: 8),
              const _TipRow(
                icon: Icons.settings_cell_rounded,
                title: 'Astuce #2 — APN Camtel & Verrouillage LTE Only',
                body:
                    'Vérifiez que votre APN est configuré sur « ebolo » ou « camtel » (Type APN: default,supl) et verrouillez le modem en « LTE Only » (*#*#4636#*#*) pour éviter les bascules intempestives.',
              ),
              const SizedBox(height: 8),
              const _TipRow(
                icon: Icons.compare_arrows_rounded,
                title: 'Astuce #3 — Protocole Comparatif MTN/Orange → Camtel',
                body:
                    'Utilisez votre SIM MTN ou Orange en journée puis comparez le débit moyen et le coût réel au Go dans l\'onglet « Historique FAI » avant de basculer vos téléchargements lourds sur Camtel.',
              ),
            ],
          ),
        ),
      ],
    );
  }

  List<IndoorSignalSpot> _sortedSpots(List<IndoorSignalSpot> spots) {
    final copy = List<IndoorSignalSpot>.from(spots);
    copy.sort((a, b) => b.compositeSpotScore.compareTo(a.compositeSpotScore));
    return copy;
  }

  static Color _qualityColor(int rsrpDbm) {
    if (rsrpDbm >= -90) return AppTheme.neonEmerald;
    if (rsrpDbm >= -102) return AppTheme.sky;
    if (rsrpDbm >= -110) return AppTheme.amber;
    return AppTheme.crimson;
  }

  static String _guidanceText(double relAngleDeg, double distMeters) {
    if (distMeters < 0.8 || relAngleDeg.abs() <= 15) {
      return 'Aligné plein cap ↑ (${distMeters.toStringAsFixed(1)} m)';
    } else if (relAngleDeg > 15) {
      return 'Tournez à droite ↻ +${relAngleDeg.toStringAsFixed(0)}° (${distMeters.toStringAsFixed(1)} m)';
    } else {
      return 'Tournez à gauche ↺ ${relAngleDeg.toStringAsFixed(0)}° (${distMeters.toStringAsFixed(1)} m)';
    }
  }
}

class _LiveSignalBadge extends StatelessWidget {
  const _LiveSignalBadge({
    required this.networkType,
    required this.rsrpDbm,
    required this.color,
  });

  final String networkType;
  final int rsrpDbm;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.16),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: color.withValues(alpha: 0.55)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 7,
            height: 7,
            decoration: BoxDecoration(color: color, shape: BoxShape.circle),
          ),
          const SizedBox(width: 6),
          Text(
            '$networkType • $rsrpDbm dBm',
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w800,
              color: color,
            ),
          ),
        ],
      ),
    );
  }
}

class _HudMiniItem extends StatelessWidget {
  const _HudMiniItem({
    required this.icon,
    required this.label,
    required this.color,
  });

  final IconData icon;
  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 14, color: color),
        const SizedBox(width: 5),
        Text(
          label,
          style: const TextStyle(
            fontSize: 11.5,
            fontWeight: FontWeight.w700,
            color: AppTheme.textPrimary,
          ),
        ),
      ],
    );
  }
}

class _BentoMetricTile extends StatelessWidget {
  const _BentoMetricTile({
    required this.width,
    required this.icon,
    required this.label,
    required this.value,
    required this.subValue,
    required this.progress,
    required this.accentColor,
  });

  final double width;
  final IconData icon;
  final String label;
  final String value;
  final String subValue;
  final double progress;
  final Color accentColor;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: width,
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppTheme.obsidianBg.withValues(alpha: 0.55),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppTheme.borderSubtle),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, size: 15, color: accentColor),
              const SizedBox(width: 6),
              Expanded(
                child: Text(
                  label,
                  style: const TextStyle(
                    fontSize: 10,
                    fontWeight: FontWeight.w800,
                    letterSpacing: 0.6,
                    color: AppTheme.textSecondary,
                  ),
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            value,
            style: TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.w900,
              color: accentColor,
              letterSpacing: -0.3,
            ),
          ),
          const SizedBox(height: 3),
          Text(
            subValue,
            style: const TextStyle(
              fontSize: 11,
              color: AppTheme.textSecondary,
            ),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          const SizedBox(height: 8),
          ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: LinearProgressIndicator(
              value: progress,
              minHeight: 4,
              backgroundColor: Colors.white10,
              valueColor: AlwaysStoppedAnimation<Color>(accentColor),
            ),
          ),
        ],
      ),
    );
  }
}

class _SpotStatChip extends StatelessWidget {
  const _SpotStatChip({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return RichText(
      text: TextSpan(
        children: [
          TextSpan(
            text: '$label: ',
            style: const TextStyle(
              fontSize: 11.5,
              color: AppTheme.textSecondary,
            ),
          ),
          TextSpan(
            text: value,
            style: const TextStyle(
              fontSize: 11.5,
              fontWeight: FontWeight.w700,
              color: AppTheme.textPrimary,
            ),
          ),
        ],
      ),
    );
  }
}

class _TipRow extends StatelessWidget {
  const _TipRow({
    required this.icon,
    required this.title,
    required this.body,
  });

  final IconData icon;
  final String title;
  final String body;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppTheme.obsidianBg.withValues(alpha: 0.45),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppTheme.borderSubtle),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: AppTheme.emerald, size: 19),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    fontSize: 12.5,
                    fontWeight: FontWeight.w800,
                    color: AppTheme.textPrimary,
                  ),
                ),
                const SizedBox(height: 3),
                Text(
                  body,
                  style: const TextStyle(
                    fontSize: 11.5,
                    color: AppTheme.textSecondary,
                    height: 1.38,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// Precision Aviation / Apple Watch Ultra Wayfinder Compass & RF Sonar Painter.
class _WayfinderCompassPainter extends CustomPainter {
  _WayfinderCompassPainter({
    required this.azimuthDeg,
    required this.bearingToBestSpotDeg,
    required this.currentPosX,
    required this.currentPosY,
    required this.spots,
    required this.bestSpotId,
    required this.signalColor,
    required this.isTargetLocked,
  });

  final double azimuthDeg;
  final double? bearingToBestSpotDeg;
  final double currentPosX;
  final double currentPosY;
  final List<IndoorSignalSpot> spots;
  final String? bestSpotId;
  final Color signalColor;
  final bool isTargetLocked;

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final outerRadius = math.min(size.width, size.height) / 2 - 4;
    final bezelRadius = outerRadius - 8;
    final tickOuterRadius = bezelRadius - 6;
    final sonarRadius = tickOuterRadius - 32;

    final activeAccent = isTargetLocked ? AppTheme.neonEmerald : signalColor;

    // 1. Outer Metallic Bezel & Ambient Glow
    final outerBezelPaint = Paint()
      ..shader = RadialGradient(
        colors: [
          const Color(0xFF0E172A),
          const Color(0xFF09101E),
          activeAccent.withValues(alpha: 0.18),
        ],
        stops: const [0.0, 0.82, 1.0],
      ).createShader(Rect.fromCircle(center: center, radius: outerRadius));
    canvas.drawCircle(center, outerRadius, outerBezelPaint);

    canvas.drawCircle(
      center,
      outerRadius,
      Paint()
        ..color = activeAccent.withValues(alpha: isTargetLocked ? 0.85 : 0.4)
        ..style = PaintingStyle.stroke
        ..strokeWidth = isTargetLocked ? 2.6 : 1.5,
    );

    canvas.drawCircle(
      center,
      bezelRadius,
      Paint()
        ..color = AppTheme.borderHighlight.withValues(alpha: 0.6)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1.0,
    );

    // 2. Forward RF Signal Cone (pointing toward top 12 o'clock lubber line)
    final coneRect = Rect.fromCircle(center: center, radius: sonarRadius);
    final conePaint = Paint()
      ..shader = SweepGradient(
        startAngle: -math.pi * 0.72,
        endAngle: -math.pi * 0.28,
        colors: [
          Colors.transparent,
          activeAccent.withValues(alpha: 0.16),
          activeAccent.withValues(alpha: 0.28),
          activeAccent.withValues(alpha: 0.16),
          Colors.transparent,
        ],
        stops: const [0.0, 0.25, 0.5, 0.75, 1.0],
      ).createShader(coneRect);
    canvas.drawArc(
      coneRect,
      -math.pi * 0.72,
      math.pi * 0.44,
      true,
      conePaint,
    );

    // 3. Sonar Range Rings & Orthogonal Reticle
    final ringPaint = Paint()
      ..color = Colors.white.withValues(alpha: 0.09)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.0;
    canvas.drawCircle(center, sonarRadius, ringPaint);
    canvas.drawCircle(center, sonarRadius * 0.74, ringPaint);

    final crosshairPaint = Paint()
      ..color = Colors.white.withValues(alpha: 0.07)
      ..strokeWidth = 1.0;
    canvas.drawLine(
      Offset(center.dx, center.dy - sonarRadius),
      Offset(center.dx, center.dy + sonarRadius),
      crosshairPaint,
    );
    canvas.drawLine(
      Offset(center.dx - sonarRadius, center.dy),
      Offset(center.dx + sonarRadius, center.dy),
      crosshairPaint,
    );

    // 4. Rotate Canvas for Compass Rose, 180 Ticks, Degree Numerals, & Room Beacons
    canvas.save();
    canvas.translate(center.dx, center.dy);
    canvas.rotate(-azimuthDeg * math.pi / 180.0);

    // Draw 180 ticks (every 2 degrees)
    for (int deg = 0; deg < 360; deg += 2) {
      final rad = (deg - 90) * math.pi / 180.0;
      final isCardinal = deg % 90 == 0;
      final isMajor30 = deg % 30 == 0;
      final isMedium10 = deg % 10 == 0;

      final double tickLen = isCardinal
          ? 14.0
          : (isMajor30 ? 11.0 : (isMedium10 ? 7.0 : 3.8));
      final double strokeW = isCardinal
          ? 2.4
          : (isMajor30 ? 1.7 : (isMedium10 ? 1.1 : 0.7));
      final Color tickColor = deg == 0
          ? AppTheme.crimson
          : (isCardinal
                ? Colors.white
                : (isMajor30
                      ? Colors.white.withValues(alpha: 0.80)
                      : (isMedium10
                            ? Colors.white.withValues(alpha: 0.42)
                            : Colors.white.withValues(alpha: 0.18))));

      final p1 = Offset(
        tickOuterRadius * math.cos(rad),
        tickOuterRadius * math.sin(rad),
      );
      final p2 = Offset(
        (tickOuterRadius - tickLen) * math.cos(rad),
        (tickOuterRadius - tickLen) * math.sin(rad),
      );
      canvas.drawLine(
        p1,
        p2,
        Paint()
          ..color = tickColor
          ..strokeWidth = strokeW
          ..strokeCap = StrokeCap.round,
      );

      // Draw degree numerals (30, 60, 120, 150, 210, 240, 300, 330)
      if (isMajor30 && !isCardinal) {
        canvas.save();
        final textRadius = tickOuterRadius - 21;
        canvas.translate(
          textRadius * math.cos(rad),
          textRadius * math.sin(rad),
        );
        canvas.rotate(deg * math.pi / 180.0);
        final tp = TextPainter(
          text: TextSpan(
            text: '$deg',
            style: TextStyle(
              color: Colors.white.withValues(alpha: 0.58),
              fontSize: 9.5,
              fontWeight: FontWeight.w700,
            ),
          ),
          textDirection: TextDirection.ltr,
        )..layout();
        tp.paint(canvas, Offset(-tp.width / 2, -tp.height / 2));
        canvas.restore();
      }
    }

    // North crimson triangle marker on the rotating dial
    final northTipRadius = tickOuterRadius + 4;
    final northTriangle = Path()
      ..moveTo(0, -northTipRadius)
      ..lineTo(-5.5, -northTipRadius + 8)
      ..lineTo(5.5, -northTipRadius + 8)
      ..close();
    canvas.drawPath(
      northTriangle,
      Paint()
        ..color = AppTheme.crimson
        ..style = PaintingStyle.fill,
    );

    // Draw Cardinal Labels (N, E, S, O)
    const cardinals = <({String label, int deg, Color color})>[
      (label: 'N', deg: 0, color: AppTheme.crimson),
      (label: 'E', deg: 90, color: AppTheme.textPrimary),
      (label: 'S', deg: 180, color: AppTheme.textPrimary),
      (label: 'O', deg: 270, color: AppTheme.textPrimary),
    ];

    for (final c in cardinals) {
      final rad = (c.deg - 90) * math.pi / 180.0;
      final labelRadius = tickOuterRadius - 23;
      canvas.save();
      canvas.translate(
        labelRadius * math.cos(rad),
        labelRadius * math.sin(rad),
      );
      canvas.rotate(c.deg * math.pi / 180.0);
      final tp = TextPainter(
        text: TextSpan(
          text: c.label,
          style: TextStyle(
            color: c.color,
            fontSize: 13.5,
            fontWeight: FontWeight.w900,
          ),
        ),
        textDirection: TextDirection.ltr,
      )..layout();
      tp.paint(canvas, Offset(-tp.width / 2, -tp.height / 2));
      canvas.restore();
    }

    // Plot recorded room beacons on the radar canvas
    for (final spot in spots) {
      final dx = (spot.posX - currentPosX) * 16.0;
      final dy = -(spot.posY - currentPosY) * 16.0;
      final rawDist = math.sqrt(dx * dx + dy * dy);
      final clampedDist = rawDist.clamp(sonarRadius * 0.62, sonarRadius * 0.92);
      final angle = rawDist > 0.05
          ? math.atan2(dy, dx)
          : (spot.azimuthDeg - 90.0) * math.pi / 180.0;

      final beaconPos = Offset(
        clampedDist * math.cos(angle),
        clampedDist * math.sin(angle),
      );
      final isBest = spot.id == bestSpotId;
      final beaconColor = isBest ? AppTheme.neonEmerald : AppTheme.amber;

      // Outer pulse halo
      canvas.drawCircle(
        beaconPos,
        isBest ? 11.0 : 7.5,
        Paint()..color = beaconColor.withValues(alpha: 0.25),
      );
      // Solid beacon core
      canvas.drawCircle(
        beaconPos,
        isBest ? 5.5 : 4.0,
        Paint()..color = beaconColor,
      );
    }

    canvas.restore();

    // 5. Shortest-Turn Guidance Arc & Sculpted Wayfinder Arrow toward Best Spot
    if (bearingToBestSpotDeg != null) {
      final relDeg =
          ((bearingToBestSpotDeg! - azimuthDeg + 540.0) % 360.0) - 180.0;
      final relRad = relDeg * math.pi / 180.0;

      // Draw luminous turn arc along sonar perimeter from 12 o'clock (-pi/2) to target angle
      final arcRect = Rect.fromCircle(center: center, radius: sonarRadius);
      canvas.drawArc(
        arcRect,
        -math.pi / 2,
        relRad,
        false,
        Paint()
          ..color = AppTheme.neonEmerald.withValues(alpha: 0.65)
          ..style = PaintingStyle.stroke
          ..strokeWidth = 3.0
          ..strokeCap = StrokeCap.round,
      );

      // Draw sculpted Wayfinder Target Arrow
      canvas.save();
      canvas.translate(center.dx, center.dy);
      canvas.rotate(relRad);

      final tipY = -sonarRadius - 2;
      final baseInnerY = -68.0;

      // Glow behind arrow
      canvas.drawLine(
        Offset(0, baseInnerY),
        Offset(0, tipY + 6),
        Paint()
          ..color = AppTheme.neonEmerald.withValues(alpha: 0.35)
          ..strokeWidth = 8
          ..strokeCap = StrokeCap.round,
      );

      // Main shaft
      canvas.drawLine(
        Offset(0, baseInnerY),
        Offset(0, tipY + 8),
        Paint()
          ..color = AppTheme.neonEmerald
          ..strokeWidth = 3.2
          ..strokeCap = StrokeCap.round,
      );

      // Sculpted chevron arrowhead
      final headPath = Path()
        ..moveTo(0, tipY)
        ..lineTo(-9, tipY + 15)
        ..lineTo(0, tipY + 11)
        ..lineTo(9, tipY + 15)
        ..close();
      canvas.drawPath(
        headPath,
        Paint()
          ..color = AppTheme.neonEmerald
          ..style = PaintingStyle.fill,
      );
      canvas.restore();
    }

    // 6. Fixed Top Lubber Line (Phone Forward Heading Reference at 12 o'clock)
    final lubberPath = Path()
      ..moveTo(center.dx, center.dy - outerRadius + 1)
      ..lineTo(center.dx - 7, center.dy - outerRadius - 9)
      ..lineTo(center.dx + 7, center.dy - outerRadius - 9)
      ..close();
    canvas.drawPath(
      lubberPath,
      Paint()
        ..color = activeAccent
        ..style = PaintingStyle.fill,
    );
  }

  @override
  bool shouldRepaint(covariant _WayfinderCompassPainter oldDelegate) {
    return oldDelegate.azimuthDeg != azimuthDeg ||
        oldDelegate.bearingToBestSpotDeg != bearingToBestSpotDeg ||
        oldDelegate.spots.length != spots.length ||
        oldDelegate.signalColor != signalColor ||
        oldDelegate.isTargetLocked != isTargetLocked;
  }
}
