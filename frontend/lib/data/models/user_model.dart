import '../../domain/entities/user.dart';

class UserModel {
  const UserModel({
    required this.id,
    required this.email,
    required this.displayName,
  });

  final String id;
  final String email;
  final String displayName;

  factory UserModel.fromJson(Map<String, dynamic> json) {
    return UserModel(
      id: json['id']?.toString() ?? '',
      email: json['email'] as String? ?? '',
      displayName: (json['displayName'] ?? json['fullName'] ?? '') as String,
    );
  }

  User toEntity() => User(id: id, email: email, displayName: displayName);
}
