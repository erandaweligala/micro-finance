import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/providers.dart';
import 'core/router/app_router.dart';
import 'core/theme/app_theme.dart';
import 'features/auth/domain/session.dart';

/// The application root.
///
/// Themes are rebuilt from the signed-in institution's brand colour, so an app
/// used by two organisations looks like each of them in turn without a rebuild
/// of the binary.
class MicrofinanceApp extends ConsumerWidget {
  const MicrofinanceApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final TenantBranding? branding = ref.watch(tenantBrandingProvider);
    final ThemeModeSetting mode = ref.watch(themeModeProvider);
    final Color seed = AppTheme.seedFromHex(branding?.primaryColor);

    return MaterialApp.router(
      title: branding?.name ?? 'Microfinance',
      debugShowCheckedModeBanner: false,
      routerConfig: ref.watch(routerProvider),
      theme: AppTheme.light(seed: seed),
      darkTheme: AppTheme.dark(seed: seed),
      themeMode: switch (mode) {
        ThemeModeSetting.system => ThemeMode.system,
        ThemeModeSetting.light => ThemeMode.light,
        ThemeModeSetting.dark => ThemeMode.dark,
      },
      builder: (BuildContext context, Widget? child) {
        // Cap text scaling: beyond this, financial tables stop being readable
        // and figures start to overlap.
        final MediaQueryData media = MediaQuery.of(context);
        return MediaQuery(
          data: media.copyWith(
            textScaler: media.textScaler.clamp(minScaleFactor: 0.8, maxScaleFactor: 1.6),
          ),
          child: child ?? const SizedBox.shrink(),
        );
      },
    );
  }
}
