import 'package:flutter/material.dart';

import 'data/repositories/audit_repository.dart';
import 'data/services/gemma_service.dart';
import 'data/services/native_network_service.dart';
import 'ui/core/theme/app_theme.dart';
import 'ui/features/audit/view_models/audit_view_model.dart';
import 'ui/features/audit/views/check_my_mega_shell.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();

  final networkService = NativeNetworkService();
  final gemmaService = GemmaService();
  final auditRepository = AuditRepository(
    networkService: networkService,
    gemmaService: gemmaService,
  );
  final auditViewModel = AuditViewModel(repository: auditRepository);

  runApp(CheckMyMegaApp(viewModel: auditViewModel));
}

/// Root widget for **CheckMyMega** — Open-Source ISP Data Bundle Auditor
/// built for Hacktoberfest with Google Gemma 4.
class CheckMyMegaApp extends StatelessWidget {
  const CheckMyMegaApp({super.key, required this.viewModel});

  final AuditViewModel viewModel;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'CheckMyMega — Audit Forfaits Internet × Gemma 4',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.darkTheme,
      home: CheckMyMegaShell(viewModel: viewModel),
    );
  }
}
