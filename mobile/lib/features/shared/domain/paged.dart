/// The pagination envelope every list endpoint returns.
class Paged<T> {
  const Paged({
    required this.content,
    required this.page,
    required this.size,
    required this.totalElements,
    required this.totalPages,
    required this.last,
  });

  final List<T> content;
  final int page;
  final int size;
  final int totalElements;
  final int totalPages;
  final bool last;

  bool get isEmpty => content.isEmpty;

  bool get hasMore => !last;

  factory Paged.fromJson(
    Map<String, dynamic> json,
    T Function(Map<String, dynamic> item) itemFromJson,
  ) {
    final List<dynamic> items = (json['content'] as List<dynamic>?) ?? <dynamic>[];
    return Paged<T>(
      content: items
          .map((dynamic item) => itemFromJson(item as Map<String, dynamic>))
          .toList(),
      page: (json['page'] as num?)?.toInt() ?? 0,
      size: (json['size'] as num?)?.toInt() ?? 0,
      totalElements: (json['totalElements'] as num?)?.toInt() ?? 0,
      totalPages: (json['totalPages'] as num?)?.toInt() ?? 0,
      last: json['last'] as bool? ?? true,
    );
  }

  static Paged<T> empty<T>() => Paged<T>(
        content: const <Never>[],
        page: 0,
        size: 0,
        totalElements: 0,
        totalPages: 0,
        last: true,
      );
}
