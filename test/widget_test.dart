import 'package:check_my_mega/data/repositories/audit_repository.dart';
import 'package:check_my_mega/data/services/gemma_service.dart';
import 'package:check_my_mega/data/services/native_network_service.dart';
import 'package:check_my_mega/domain/models/isp_provider.dart';
import 'package:check_my_mega/domain/use_cases/mega_calculator.dart';
import 'package:check_my_mega/main.dart';
import 'package:check_my_mega/ui/features/audit/view_models/audit_view_model.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('MegaCalculator — Cameroon ISP Veracity Math', () {
    const calculator = MegaCalculator();

    test(
      'computes veracity score and real cost per GB for Camtel vs MTN/Orange',
      () {
        // Camtel: 100 FCFA for 1024 MB promised, 982.4 MB received by OS
        const camtelBytes = 982400000;
        final camtelScore = calculator.computeVeracityScorePct(
          totalConsumedBytes: camtelBytes,
          promisedMb: 1024,
        );
        final camtelRealCost = calculator.computeRealCostPerGbXaf(
          priceXaf: 100,
          totalConsumedBytes: camtelBytes,
        );

        // MTN: 500 FCFA for 1024 MB promised, 638.2 MB received by OS before cutoff
        const mtnBytes = 638200000;
        final mtnScore = calculator.computeVeracityScorePct(
          totalConsumedBytes: mtnBytes,
          promisedMb: 1024,
        );
        final mtnRealCost = calculator.computeRealCostPerGbXaf(
          priceXaf: 500,
          totalConsumedBytes: mtnBytes,
        );

        expect(camtelScore, greaterThan(95.0));
        expect(mtnScore, lessThan(65.0));
        expect(camtelRealCost, closeTo(101.8, 1.0));
        expect(mtnRealCost, closeTo(783.4, 1.0));
      },
    );
  });

  group('GemmaService — Operator SMS Extraction', () {
    test('extracts Camtel, MTN, and Orange data plans from SMS text', () async {
      final gemma = GemmaService()
        ..backendMode = GemmaBackendMode.offlineDeterministic;

      final camtelPlan = await gemma.parseOperatorSms(
        IspProvider.camtel.sampleSms,
      );
      expect(camtelPlan.isp, IspProvider.camtel);
      expect(camtelPlan.priceXaf, 100);
      expect(camtelPlan.validityHours, 3);

      final mtnPlan = await gemma.parseOperatorSms(IspProvider.mtnCm.sampleSms);
      expect(mtnPlan.isp, IspProvider.mtnCm);
      expect(mtnPlan.priceXaf, 500);
      expect(mtnPlan.validityHours, 24);
    });
  });

  testWidgets('CheckMyMegaApp renders main shell and navigation tabs', (
    WidgetTester tester,
  ) async {
    final repo = AuditRepository(
      networkService: NativeNetworkService(),
      gemmaService: GemmaService()
        ..backendMode = GemmaBackendMode.offlineDeterministic,
    );
    final vm = AuditViewModel(repository: repo);

    await tester.pumpWidget(CheckMyMegaApp(viewModel: vm));
    await tester.pump();

    expect(find.text('CheckMyMega CM'), findsOneWidget);
    expect(find.text('Sonde OS Live'), findsOneWidget);
    expect(find.text('Camtel vs MTN vs Orange'), findsOneWidget);
    expect(find.text('Certificat Gemma 4'), findsOneWidget);

    vm.dispose();
  });
}
