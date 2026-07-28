import 'package:flutter/material.dart';

import '../utils/formatters.dart';

/// Layout breakpoints. The same screens run on a loan officer's phone and a
/// branch manager's tablet, so every list and form adapts rather than stretching.
class Responsive {
  const Responsive._();

  static const double tabletBreakpoint = 720;
  static const double desktopBreakpoint = 1100;

  static bool isPhone(BuildContext context) =>
      MediaQuery.sizeOf(context).width < tabletBreakpoint;

  static bool isTablet(BuildContext context) {
    final double width = MediaQuery.sizeOf(context).width;
    return width >= tabletBreakpoint && width < desktopBreakpoint;
  }

  /// Number of grid columns appropriate to the current width.
  static int columns(BuildContext context) {
    final double width = MediaQuery.sizeOf(context).width;
    if (width >= desktopBreakpoint) {
      return 4;
    }
    if (width >= tabletBreakpoint) {
      return 3;
    }
    return 2;
  }

  /// Caps form width on large screens: a text field stretched across a tablet is
  /// harder to read, not easier.
  static Widget constrain(Widget child, {double maxWidth = 640}) {
    return Center(
      child: ConstrainedBox(
        constraints: BoxConstraints(maxWidth: maxWidth),
        child: child,
      ),
    );
  }
}

/// A labelled amount, right-aligned with tabular figures so columns of money align.
class MoneyText extends StatelessWidget {
  const MoneyText(
    this.amount, {
    this.currency,
    this.style,
    this.emphasise = false,
    super.key,
  });

  final Object? amount;
  final String? currency;
  final TextStyle? style;
  final bool emphasise;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Text(
      Formatters.money(amount, currency: currency),
      style: (style ?? (emphasise ? theme.textTheme.titleLarge : theme.textTheme.bodyMedium))
          ?.merge(const TextStyle(fontFeatures: <FontFeature>[FontFeature.tabularFigures()]))
          .copyWith(fontWeight: emphasise ? FontWeight.w700 : FontWeight.w600),
    );
  }
}

/// A key/value row, the building block of every detail screen.
class DetailRow extends StatelessWidget {
  const DetailRow(this.label, this.value, {this.valueWidget, this.dense = false, super.key});

  final String label;
  final String? value;
  final Widget? valueWidget;
  final bool dense;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Padding(
      padding: EdgeInsets.symmetric(vertical: dense ? 4 : 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Expanded(
            flex: 4,
            child: Text(
              label,
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            flex: 5,
            child: valueWidget ??
                Text(
                  value ?? '-',
                  textAlign: TextAlign.end,
                  style: theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w600),
                ),
          ),
        ],
      ),
    );
  }
}

/// A card with a title, used to group related detail rows.
class SectionCard extends StatelessWidget {
  const SectionCard({
    required this.child,
    this.title,
    this.trailing,
    this.padding = const EdgeInsets.all(16),
    super.key,
  });

  final Widget child;
  final String? title;
  final Widget? trailing;
  final EdgeInsets padding;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: padding,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            if (title != null) ...<Widget>[
              Row(
                children: <Widget>[
                  Expanded(
                    child: Text(title!, style: theme.textTheme.titleSmall),
                  ),
                  if (trailing != null) trailing!,
                ],
              ),
              const SizedBox(height: 12),
            ],
            child,
          ],
        ),
      ),
    );
  }
}

/// Status pill. Colour communicates severity at a glance in a list of hundreds
/// of loans, so the mapping is centralised rather than chosen per screen.
class StatusChip extends StatelessWidget {
  const StatusChip(this.status, {this.compact = false, super.key});

  final String? status;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final _StatusTone tone = _toneFor(status, theme.colorScheme);

    return Container(
      padding: EdgeInsets.symmetric(horizontal: compact ? 8 : 10, vertical: compact ? 2 : 4),
      decoration: BoxDecoration(
        color: tone.background,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: tone.foreground.withValues(alpha: 0.3)),
      ),
      child: Text(
        Formatters.humanise(status),
        style: (compact ? theme.textTheme.labelSmall : theme.textTheme.labelMedium)
            ?.copyWith(color: tone.foreground, fontWeight: FontWeight.w600),
      ),
    );
  }

  _StatusTone _toneFor(String? status, ColorScheme scheme) {
    switch (status) {
      case 'ACTIVE':
      case 'APPROVED':
      case 'VERIFIED':
      case 'PAID':
      case 'POSTED':
      case 'CLOSED':
        return _StatusTone(
          scheme.primary,
          scheme.primaryContainer.withValues(alpha: 0.5),
        );
      case 'OVERDUE':
      case 'DEFAULTED':
      case 'REJECTED':
      case 'REVERSED':
      case 'WRITTEN_OFF':
        return _StatusTone(
          scheme.error,
          scheme.errorContainer.withValues(alpha: 0.5),
        );
      case 'PENDING':
      case 'UNDER_REVIEW':
      case 'SUBMITTED':
      case 'PARTIALLY_PAID':
      case 'PENDING_ACTIVATION':
        return _StatusTone(
          scheme.tertiary,
          scheme.tertiaryContainer.withValues(alpha: 0.5),
        );
      default:
        return _StatusTone(
          scheme.onSurfaceVariant,
          scheme.surfaceContainerHighest,
        );
    }
  }
}

class _StatusTone {
  const _StatusTone(this.foreground, this.background);

  final Color foreground;
  final Color background;
}

/// Headline figure for dashboards.
class StatTile extends StatelessWidget {
  const StatTile({
    required this.label,
    required this.value,
    this.caption,
    this.icon,
    this.tone,
    this.onTap,
    super.key,
  });

  final String label;
  final String value;
  final String? caption;
  final IconData? icon;
  final Color? tone;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final Color accent = tone ?? theme.colorScheme.primary;

    return Card(
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              Row(
                children: <Widget>[
                  if (icon != null) ...<Widget>[
                    Icon(icon, size: 18, color: accent),
                    const SizedBox(width: 6),
                  ],
                  Expanded(
                    child: Text(
                      label,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.labelMedium?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              FittedBox(
                fit: BoxFit.scaleDown,
                alignment: Alignment.centerLeft,
                child: Text(
                  value,
                  style: theme.textTheme.headlineSmall?.copyWith(
                    fontWeight: FontWeight.w700,
                    fontFeatures: const <FontFeature>[FontFeature.tabularFigures()],
                  ),
                ),
              ),
              if (caption != null) ...<Widget>[
                const SizedBox(height: 4),
                Text(
                  caption!,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

/// Shows a message without the caller having to remember ScaffoldMessenger.
void showMessage(BuildContext context, String message, {bool isError = false}) {
  final ThemeData theme = Theme.of(context);
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: isError ? theme.colorScheme.errorContainer : null,
        duration: Duration(seconds: isError ? 6 : 3),
      ),
    );
}
