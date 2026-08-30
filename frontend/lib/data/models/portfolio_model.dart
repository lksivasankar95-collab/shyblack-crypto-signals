import '../../domain/entities/portfolio.dart';

class PortfolioModel {
  const PortfolioModel({
    required this.id,
    required this.name,
    required this.accountType,
  });

  final String id;
  final String name;
  final String accountType;

  factory PortfolioModel.fromJson(Map<String, dynamic> json) {
    return PortfolioModel(
      id: json['id'] as String,
      name: json['name'] as String,
      accountType: json['accountType'] as String,
    );
  }

  Portfolio toEntity() => Portfolio(id: id, name: name, accountType: accountType);
}
