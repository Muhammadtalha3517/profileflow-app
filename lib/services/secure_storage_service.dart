import 'dart:async';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:local_auth/local_auth.dart';
import '../models/user_profile.dart';
import 'autofill_channel_service.dart';

/// Secure on-device vault service for user profile data.
/// Uses hardware-backed AES encryption and enforces zero-cloud storage.
class SecureStorageService {
  static const FlutterSecureStorage _storage = FlutterSecureStorage(
    aOptions: AndroidOptions(
      encryptedSharedPreferences: true,
      resetOnError: true,
    ),
  );

  static final LocalAuthentication _localAuth = LocalAuthentication();
  static const String _profileKey = 'profileflow_vault_user_profile';
  static const String _biometricSettingKey = 'profileflow_setting_biometric';

  /// Authenticate user via biometric (Fingerprint/Face) or device PIN
  static Future<bool> authenticateWithBiometrics({String reason = 'Authenticate to access ProfileFlow vault'}) async {
    try {
      final bool canAuthenticate =
          await _localAuth.canCheckBiometrics || await _localAuth.isDeviceSupported();
      if (!canAuthenticate) return true; // Device has no screen lock

      return await _localAuth.authenticate(
        localizedReason: reason,
        options: const AuthenticationOptions(
          stickyAuth: true,
          biometricOnly: false,
        ),
      );
    } catch (_) {
      return false;
    }
  }

  /// Loads the stored UserProfile from encrypted storage
  static Future<UserProfile> loadProfile() async {
    try {
      final jsonStr = await _storage.read(key: _profileKey);
      if (jsonStr == null || jsonStr.isEmpty) {
        return UserProfile();
      }
      return UserProfile.fromJson(jsonStr);
    } catch (_) {
      return UserProfile();
    }
  }

  /// Saves the UserProfile to encrypted storage and immediately syncs
  /// with native Android Autofill bridge
  static Future<bool> saveProfile(UserProfile profile) async {
    try {
      await _storage.write(key: _profileKey, value: profile.toJson());

      final bool isBiometricRequired = await getBiometricSetting();

      // Synchronize with native Keystore bridge
      await AutofillChannelService.syncProfileToNativeStorage(
        profileData: profile.toAutofillMap(),
        isBiometricRequired: isBiometricRequired,
      );

      return true;
    } catch (_) {
      return false;
    }
  }

  static Future<bool> getBiometricSetting() async {
    final value = await _storage.read(key: _biometricSettingKey);
    return value == null ? true : value == 'true';
  }

  static Future<void> setBiometricSetting(bool required) async {
    await _storage.write(key: _biometricSettingKey, value: required.toString());
    await AutofillChannelService.setBiometricRequirement(required);
  }
}
