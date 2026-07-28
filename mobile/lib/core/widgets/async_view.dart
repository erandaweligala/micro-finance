import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../error/api_exception.dart';

/// Renders the four states every asynchronous screen must handle: loading, error,
/// empty and data.
///
/// Having one widget own this is what stops "empty" from being rendered as a
/// blank screen in one place and a spinner-that-never-stops in another. Screens
/// supply only the data builder; the rest is consistent by construction.
class AsyncView<T> extends StatelessWidget {
  const AsyncView({
    required this.value,
    required this.data,
    this.onRetry,
    this.isEmpty,
    this.emptyTitle = 'Nothing here yet',
    this.emptyMessage,
    this.emptyIcon = Icons.inbox_outlined,
    this.emptyAction,
    this.loadingLabel,
    super.key,
  });

  final AsyncValue<T> value;
  final Widget Function(T data) data;

  /// Called when the user taps "Try again"; usually invalidates the provider.
  final VoidCallback? onRetry;

  /// Decides whether loaded data should render as the empty state.
  final bool Function(T data)? isEmpty;

  final String emptyTitle;
  final String? emptyMessage;
  final IconData emptyIcon;
  final Widget? emptyAction;
  final String? loadingLabel;

  @override
  Widget build(BuildContext context) {
    return value.when(
      loading: () => LoadingState(label: loadingLabel),
      error: (Object error, StackTrace stackTrace) =>
          ErrorState(error: error, onRetry: onRetry),
      data: (T loaded) {
        if (isEmpty != null && isEmpty!(loaded)) {
          return EmptyState(
            title: emptyTitle,
            message: emptyMessage,
            icon: emptyIcon,
            action: emptyAction,
          );
        }
        return data(loaded);
      },
    );
  }
}

class LoadingState extends StatelessWidget {
  const LoadingState({this.label, super.key});

  final String? label;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          const CircularProgressIndicator(),
          if (label != null) ...<Widget>[
            const SizedBox(height: 16),
            Text(label!, style: Theme.of(context).textTheme.bodyMedium),
          ],
        ],
      ),
    );
  }
}

/// Failure state. Shows the server's message when there is one, and the trace id
/// so a user can quote it to support without sharing anything sensitive.
class ErrorState extends StatelessWidget {
  const ErrorState({required this.error, this.onRetry, super.key});

  final Object error;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final ApiException? api = error is ApiException ? error as ApiException : null;
    final bool canRetry = onRetry != null && (api?.isRetryable ?? true);

    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            Icon(
              api?.isForbidden ?? false ? Icons.lock_outline : Icons.error_outline,
              size: 48,
              color: theme.colorScheme.error,
            ),
            const SizedBox(height: 16),
            Text(
              api?.message ?? 'Something went wrong. Please try again.',
              textAlign: TextAlign.center,
              style: theme.textTheme.bodyLarge,
            ),
            if (api?.traceId != null) ...<Widget>[
              const SizedBox(height: 8),
              SelectableText(
                'Reference: ${api!.traceId}',
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ],
            if (canRetry) ...<Widget>[
              const SizedBox(height: 24),
              FilledButton.icon(
                onPressed: onRetry,
                icon: const Icon(Icons.refresh),
                label: const Text('Try again'),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class EmptyState extends StatelessWidget {
  const EmptyState({
    required this.title,
    this.message,
    this.icon = Icons.inbox_outlined,
    this.action,
    super.key,
  });

  final String title;
  final String? message;
  final IconData icon;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            Icon(icon, size: 48, color: theme.colorScheme.onSurfaceVariant),
            const SizedBox(height: 16),
            Text(title, style: theme.textTheme.titleMedium, textAlign: TextAlign.center),
            if (message != null) ...<Widget>[
              const SizedBox(height: 8),
              Text(
                message!,
                textAlign: TextAlign.center,
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ],
            if (action != null) ...<Widget>[const SizedBox(height: 24), action!],
          ],
        ),
      ),
    );
  }
}
