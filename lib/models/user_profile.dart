import 'dart:convert';

/// Data model representing the user's stored profile fields.
/// Contains both standard detectible form fields and complex fields for copy-helper.
class UserProfile {
  final String firstName;
  final String lastName;
  final String email;
  final String password;
  final String phone;
  final String country;
  final String city;
  final String address;
  final String postalCode;

  // Complex unmatchable fields for the Copy Helper
  final String bio;
  final String skills;
  final String experience;
  final String portfolioUrl;
  final String linkedInUrl;

  UserProfile({
    this.firstName = '',
    this.lastName = '',
    this.email = '',
    this.password = '',
    this.phone = '',
    this.country = '',
    this.city = '',
    this.address = '',
    this.postalCode = '',
    this.bio = '',
    this.skills = '',
    this.experience = '',
    this.portfolioUrl = '',
    this.linkedInUrl = '',
  });

  String get fullName => '$firstName $lastName'.trim();

  Map<String, String> toAutofillMap() {
    return {
      'firstName': firstName,
      'lastName': lastName,
      'fullName': fullName,
      'email': email,
      'password': password,
      'phone': phone,
      'country': country,
      'city': city,
      'address': address,
      'postalCode': postalCode,
    };
  }

  Map<String, dynamic> toMap() {
    return {
      'firstName': firstName,
      'lastName': lastName,
      'email': email,
      'password': password,
      'phone': phone,
      'country': country,
      'city': city,
      'address': address,
      'postalCode': postalCode,
      'bio': bio,
      'skills': skills,
      'experience': experience,
      'portfolioUrl': portfolioUrl,
      'linkedInUrl': linkedInUrl,
    };
  }

  factory UserProfile.fromMap(Map<String, dynamic> map) {
    return UserProfile(
      firstName: map['firstName'] as String? ?? '',
      lastName: map['lastName'] as String? ?? '',
      email: map['email'] as String? ?? '',
      password: map['password'] as String? ?? '',
      phone: map['phone'] as String? ?? '',
      country: map['country'] as String? ?? '',
      city: map['city'] as String? ?? '',
      address: map['address'] as String? ?? '',
      postalCode: map['postalCode'] as String? ?? '',
      bio: map['bio'] as String? ?? '',
      skills: map['skills'] as String? ?? '',
      experience: map['experience'] as String? ?? '',
      portfolioUrl: map['portfolioUrl'] as String? ?? '',
      linkedInUrl: map['linkedInUrl'] as String? ?? '',
    );
  }

  String toJson() => json.encode(toMap());

  factory UserProfile.fromJson(String source) =>
      UserProfile.fromMap(json.decode(source) as Map<String, dynamic>);

  UserProfile copyWith({
    String? firstName,
    String? lastName,
    String? email,
    String? password,
    String? phone,
    String? country,
    String? city,
    String? address,
    String? postalCode,
    String? bio,
    String? skills,
    String? experience,
    String? portfolioUrl,
    String? linkedInUrl,
  }) {
    return UserProfile(
      firstName: firstName ?? this.firstName,
      lastName: lastName ?? this.lastName,
      email: email ?? this.email,
      password: password ?? this.password,
      phone: phone ?? this.phone,
      country: country ?? this.country,
      city: city ?? this.city,
      address: address ?? this.address,
      postalCode: postalCode ?? this.postalCode,
      bio: bio ?? this.bio,
      skills: skills ?? this.skills,
      experience: experience ?? this.experience,
      portfolioUrl: portfolioUrl ?? this.portfolioUrl,
      linkedInUrl: linkedInUrl ?? this.linkedInUrl,
    );
  }
}
