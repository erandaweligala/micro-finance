import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/config/app_config.dart';
import '../../../core/providers.dart';
import '../../../core/router/app_router.dart';
import '../../../core/widgets/common_widgets.dart';
import '../../auth/domain/session.dart';

/// User and organisation settings.
class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final Session? session = ref.watch(currentSessionProvider);
    final TenantBranding? branding = ref.watch(tenantBrandingProvider);
    final ThemeModeSetting themeMode = ref.watch(themeModeProvider);
    final ThemeData theme = Theme.of(context);

    if (session == null) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }

    return Scaffold(
      appBar: AppBar(title: const Text('Settings')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: <Widget>[
          SectionCard(
            child: Row(
              children: <Widget>[
                CircleAvatar(
                  radius: 28,
                  backgroundColor: theme.colorScheme.primaryContainer,
                  child: Text(
                    session.fullName.isEmpty ? '?' : session.fullName[0].toUpperCase(),
                    style: theme.textTheme.titleLarge
                        ?.copyWith(color: theme.colorScheme.onPrimaryContainer),
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      Text(session.fullName, style: theme.textTheme.titleMedium),
                      Text(session.username, style: theme.textTheme.bodySmall),
                      const SizedBox(height: 6),
                      Wrap(
                        spacing: 6,
                        runSpacing: 6,
                        children: session.roles
                            .map((UserRole role) => StatusChip(role.wireName, compact: true))
                            .toList(),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          SectionCard(
            title: 'Organisation',
            child: Column(
              children: <Widget>[
                DetailRow('Name', branding?.name ?? session.tenantSlug ?? 'Platform'),
                DetailRow('Identifier', session.tenantSlug ?? '-'),
                DetailRow('Currency', branding?.defaultCurrency ?? '-'),
              ],
            ),
          ),
          const SizedBox(height: 16),
          SectionCard(
            title: 'Appearance',
            child: Column(
              children: <Widget>[
                RadioListTile<ThemeModeSetting>(
                  value: ThemeModeSetting.system,
                  groupValue: themeMode,
                  onChanged: (ThemeModeSetting? value) =>
                      ref.read(themeModeProvider.notifier).state = value!,
                  title: const Text('Match the device'),
                  contentPadding: EdgeInsets.zero,
                ),
                RadioListTile<ThemeModeSetting>(
                  value: ThemeModeSetting.light,
                  groupValue: themeMode,
                  onChanged: (ThemeModeSetting? value) =>
                      ref.read(themeModeProvider.notifier).state = value!,
                  title: const Text('Light'),
                  contentPadding: EdgeInsets.zero,
                ),
                RadioListTile<ThemeModeSetting>(
                  value: ThemeModeSetting.dark,
                  groupValue: themeMode,
                  onChanged: (ThemeModeSetting? value) =>
                      ref.read(themeModeProvider.notifier).state = value!,
                  title: const Text('Dark'),
                  contentPadding: EdgeInsets.zero,
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          SectionCard(
            title: 'Security',
            child: Column(
              children: <Widget>[
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: const Icon(Icons.lock_reset_outlined),
                  title: const Text('Change password'),
                  subtitle: const Text('Signs you out of all devices'),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () => context.push(Routes.changePassword),
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),
          OutlinedButton.icon(
            onPressed: () async {
              final bool? confirmed = await showDialog<bool>(
                context: context,
                builder: (BuildContext context) => AlertDialog(
                  title: const Text('Sign out?'),
                  content: const Text('You will need to sign in again to continue.'),
                  actions: <Widget>[
                    TextButton(
                      onPressed: () => Navigator.of(context).pop(false),
                      child: const Text('Cancel'),
                    ),
                    FilledButton(
                      onPressed: () => Navigator.of(context).pop(true),
                      child: const Text('Sign out'),
                    ),
                  ],
                ),
              );
              if (confirmed == true) {
                await ref.read(sessionProvider.notifier).signOut();
              }
            },
            icon: const Icon(Icons.logout),
            label: const Text('Sign out'),
          ),
          const SizedBox(height: 24),
          Center(
            child: Text(
              'Version 1.0.0 · ${AppConfig.environment}',
              style: theme.textTheme.bodySmall
                  ?.copyWith(color: theme.colorScheme.onSurfaceVariant),
            ),
          ),
          const SizedBox(height: 32),
        ],
      ),
    );
  }
}
