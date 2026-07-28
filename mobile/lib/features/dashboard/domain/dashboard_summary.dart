/// Headline figures for the home screen, plus any alerts worth surfacing.
class DashboardSummary {
  const DashboardSummary({
    required this.activeLoans,
    required this.overdueLoans,
    required this.outstandingPrincipal,
    required this.principalAtRisk30,
    required this.collectedThisMonth,
    required this.disbursedThisMonth,
    required this.alerts,
  });

  final int activeLoans;
  final int overdueLoans;
  final String outstandingPrincipal;
  final String principalAtRisk30;
  final String collectedThisMonth;
  final String disbursedThisMonth;
  final List<String> alerts;

  factory DashboardSummary.fromJson(Map<String, dynamic> json) => DashboardSummary(
        activeLoans: (json['activeLoans'] as num?)?.toInt() ?? 0,
        overdueLoans: (json['overdueLoans'] as num?)?.toInt() ?? 0,
        outstandingPrincipal: json['outstandingPrincipal']?.toString() ?? '0',
        principalAtRisk30: json['principalAtRisk30']?.toString() ?? '0',
        collectedThisMonth: json['collectedThisMonth']?.toString() ?? '0',
        disbursedThisMonth: json['disbursedThisMonth']?.toString() ?? '0',
        alerts: ((json['alerts'] as List<dynamic>?) ?? <dynamic>[])
            .map((dynamic alert) => alert.toString())
            .toList(),
      );
}

/// Portfolio report: the position of the whole loan book.
class PortfolioSummary {
  const PortfolioSummary({
    required this.activeLoans,
    required this.overdueLoans,
    required this.closedLoans,
    required this.outstandingPrincipal,
    required this.totalCollected,
    required this.principalAtRisk30,
    required this.par30Percentage,
  });

  final int activeLoans;
  final int overdueLoans;
  final int closedLoans;
  final String outstandingPrincipal;
  final String totalCollected;
  final String principalAtRisk30;
  final String par30Percentage;

  factory PortfolioSummary.fromJson(Map<String, dynamic> json) => PortfolioSummary(
        activeLoans: (json['activeLoans'] as num?)?.toInt() ?? 0,
        overdueLoans: (json['overdueLoans'] as num?)?.toInt() ?? 0,
        closedLoans: (json['closedLoans'] as num?)?.toInt() ?? 0,
        outstandingPrincipal: json['outstandingPrincipal']?.toString() ?? '0',
        totalCollected: json['totalCollected']?.toString() ?? '0',
        principalAtRisk30: json['principalAtRisk30']?.toString() ?? '0',
        par30Percentage: json['par30Percentage']?.toString() ?? '0',
      );
}

/// Arrears aged into the standard portfolio-at-risk buckets.
class ArrearsAging {
  const ArrearsAging({
    required this.loanCounts,
    required this.outstandingPrincipal,
    required this.totalOutstanding,
  });

  final Map<String, int> loanCounts;
  final Map<String, String> outstandingPrincipal;
  final String totalOutstanding;

  factory ArrearsAging.fromJson(Map<String, dynamic> json) {
    final Map<String, dynamic> counts =
        (json['loanCounts'] as Map<String, dynamic>?) ?? <String, dynamic>{};
    final Map<String, dynamic> principal =
        (json['outstandingPrincipal'] as Map<String, dynamic>?) ?? <String, dynamic>{};
    return ArrearsAging(
      loanCounts: counts.map(
        (String key, dynamic value) => MapEntry<String, int>(key, (value as num).toInt()),
      ),
      outstandingPrincipal: principal.map(
        (String key, dynamic value) => MapEntry<String, String>(key, value.toString()),
      ),
      totalOutstanding: json['totalOutstanding']?.toString() ?? '0',
    );
  }

  /// Human labels for the bucket keys, in reporting order.
  static const Map<String, String> bucketLabels = <String, String>{
    'CURRENT': 'Current',
    'PAR_1_30': '1-30 days',
    'PAR_31_60': '31-60 days',
    'PAR_61_90': '61-90 days',
    'PAR_90_PLUS': 'Over 90 days',
  };
}
